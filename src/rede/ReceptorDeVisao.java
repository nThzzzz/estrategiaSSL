package rede;

import model.Geometria;
import mundo.Quadro;
import percepcao.Rastreador;
import proto.vision.MessagesRobocupSslWrapper.SSL_WrapperPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Escuta o multicast da visao e publica o {@link Quadro} resultante.
 *
 * <p>Este e o unico lugar do programa que fala com a rede de entrada. A thread
 * de rede escreve o quadro numa referencia atomica e a Swing le -- nada mais
 * atravessa a fronteira entre as duas, o que dispensa lock e garante que a tela
 * sempre veja um retrato inteiro.
 *
 * <p>O ingresso no grupo e tentado em TODAS as interfaces que suportam
 * multicast, e nao so na padrao. Numa maquina com Wi-Fi, Ethernet e as
 * interfaces virtuais que o Docker e as VPNs criam, a rota padrao quase nunca e
 * a que recebe o trafego -- entrar so nela e o motivo classico de "o simulador
 * publica, o cliente nao recebe nada".
 */
public final class ReceptorDeVisao implements AutoCloseable {

    private final MulticastSocket socket;
    private final InetSocketAddress grupo;
    private final Rastreador rastreador;
    private final Thread thread;

    private final AtomicReference<Quadro> ultimo = new AtomicReference<>(Quadro.vazio());
    private volatile long pacotesRecebidos;
    private volatile long duplicadosDescartados;

    /**
     * IP de onde o ultimo pacote de visao veio.
     *
     * <p>Guardado porque ele responde sozinho uma pergunta que so se resolvia por
     * tentativa: para onde mandar os comandos. Se a visao vem de outra maquina e
     * os comandos estao indo para 127.0.0.1, isso e quase certamente engano -- e
     * da para dizer isso na tela em vez de deixar a pessoa descobrindo.
     */
    private volatile java.net.InetAddress origem;

    /**
     * Quadros que a visao numerou e nunca chegaram.
     *
     * <p>"Parece que esta perdendo pacote" e uma impressao dificil de confirmar
     * no olho, porque movimento truncado tambem vem de tela lenta, de rede
     * congestionada e de simulador engasgado -- tres consertos diferentes. O
     * {@code frame_number} responde sem ambiguidade: se pulou de 100 para 103,
     * dois se perderam NO CAMINHO, e o problema esta na rede e nao na tela.
     */
    private volatile long quadrosPerdidos;

    /**
     * Ultimo {@code frame_number} visto de cada camera.
     *
     * <p>Existe porque a mesma visao pode chegar por mais de um caminho: o
     * simulador publica em multicast e pode publicar TAMBEM em unicast ou
     * broadcast para atravessar uma rede que nao repassa multicast. Quem estiver
     * nos dois caminhos receberia cada quadro duas vezes -- e a taxa em Hz, que e
     * o numero que se olha para saber se esta funcionando, apareceria dobrada.
     *
     * <p>A chave inclui a CAMERA porque numa partida de verdade sao varias, cada
     * uma numerando os proprios quadros: deduplicar so por {@code frame_number}
     * jogaria fora quadro legitimo de outra camera.
     *
     * <p>So a thread de escuta toca aqui.
     */
    private final java.util.Map<Integer, Integer> ultimoFramePorCamera = new java.util.HashMap<>();
    private volatile long pacotesGeometria;
    private volatile long ultimoPacoteNano;
    private volatile double taxaHz;
    private long janelaNano;
    private int quadrosNaJanela;

    public ReceptorDeVisao(ConfigRede config, Rastreador rastreador) throws IOException {
        this.rastreador = rastreador;
        this.socket = new MulticastSocket(config.portaVisao());
        this.socket.setReuseAddress(true);
        this.grupo = new InetSocketAddress(
                InetAddress.getByName(config.grupoVisao().trim()), config.portaVisao());

        boolean alguma = false;
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || !ni.supportsMulticast()) continue;
            try { socket.joinGroup(grupo, ni); alguma = true; } catch (IOException ignorado) { }
        }
        if (!alguma) {
            socket.close();
            throw new IOException("nenhuma interface de rede aceitou o grupo " + config.grupoVisao());
        }

        this.thread = new Thread(this::escutar, "ssl-visao");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public Quadro ultimoQuadro()     { return ultimo.get(); }
    public long getPacotesRecebidos() { return pacotesRecebidos; }

    /**
     * Quadros jogados fora por ja terem chegado.
     *
     * <p>Diferente de zero nao e erro: quer dizer que a visao esta chegando por
     * mais de um caminho ao mesmo tempo -- tipicamente multicast e unicast
     * juntos. Vale saber, porque e desperdicio de rede que da para desligar.
     */
    public long getDuplicadosDescartados() { return duplicadosDescartados; }

    /**
     * Quantos quadros a visao numerou e nunca chegaram.
     *
     * <p>Zero com movimento truncado quer dizer que a rede esta entregando tudo e
     * o engasgo esta em outro lugar -- tela, ou o proprio simulador.
     */
    public long getQuadrosPerdidos() { return quadrosPerdidos; }

    /** De onde a visao esta vindo, ou {@code null} se nada chegou ainda. */
    public java.net.InetAddress getOrigem() { return origem; }
    public long getPacotesGeometria() { return pacotesGeometria; }

    /** Segundos desde o ultimo pacote, ou infinito se nunca chegou nenhum. */
    public double silencio() {
        long t = ultimoPacoteNano;
        return t == 0 ? Double.POSITIVE_INFINITY : (System.nanoTime() - t) / 1e9;
    }

    public boolean recebendo() { return silencio() < 0.5; }

    /**
     * Quadros de DETECCAO por segundo, contados numa janela de meio segundo.
     *
     * <p>Medida no relogio LOCAL, e nao no {@code t_capture}: o que interessa e a
     * taxa com que a estrategia esta sendo alimentada de fato. Um simulador
     * rodando acelerado carimba tempo simulado que anda mais rapido que o relogio
     * de parede, e usar esse carimbo esconderia uma rede entregando devagar.
     *
     * <p>Contagem por janela, e nao media exponencial do intervalo entre pacotes.
     * O intervalo instantaneo e pessimo estimador aqui: geometria e deteccao
     * chegam praticamente juntas quando a geometria e reenviada, e esse par
     * separado por microssegundos vira um pico de dezenas de milhares de hertz
     * que contamina a media por segundos. Contar quantos chegaram no periodo e
     * imune a isso.
     */
    public double taxaHz() { return recebendo() ? taxaHz : 0; }

    private void escutar() {
        byte[] buffer = new byte[ConfigRede.TAMANHO_BUFFER];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p);
                SSL_WrapperPacket w = SSL_WrapperPacket.parseFrom(
                        Arrays.copyOf(p.getData(), p.getLength()));
                pacotesRecebidos++;
                origem = p.getAddress();
                ultimoPacoteNano = System.nanoTime();

                // Geometria e deteccao vem em pacotes separados e em ritmos
                // diferentes: a geometria e reenviada de vez em quando, para
                // quem chegou depois. Aplicar antes da deteccao mantem o quadro
                // coerente com o campo em que ele foi medido.
                if (w.hasGeometry()) {
                    rastreador.setGeometria(Geometria.de(w.getGeometry().getField()));
                    pacotesGeometria++;
                }
                if (w.hasDetection()) {
                    var d = w.getDetection();
                    Integer visto = ultimoFramePorCamera.put(d.getCameraId(), d.getFrameNumber());
                    if (visto != null && visto == d.getFrameNumber()) {
                        duplicadosDescartados++;
                        continue;   // o mesmo quadro por outro caminho
                    }
                    // Buraco na numeracao = pacote que nao chegou. So conta salto
                    // para a FRENTE e pequeno: reinicio do simulador manda o
                    // numero de volta a zero, e isso nao e perda.
                    if (visto != null) {
                        long salto = (long) d.getFrameNumber() - visto;
                        if (salto > 1 && salto < 1000) quadrosPerdidos += salto - 1;
                    }
                    ultimo.set(rastreador.incorporar(d));
                    contarNaJanela(ultimoPacoteNano);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) System.err.println("visao: " + e.getMessage());
            }
        }
    }

    /** Fecha a janela de meio segundo e publica a taxa. */
    private void contarNaJanela(long agora) {
        if (janelaNano == 0) { janelaNano = agora; return; }
        quadrosNaJanela++;
        long decorrido = agora - janelaNano;
        if (decorrido >= 500_000_000L) {
            taxaHz = quadrosNaJanela * 1e9 / decorrido;
            quadrosNaJanela = 0;
            janelaNano = agora;
        }
    }

    @Override
    public void close() {
        try { socket.leaveGroup(grupo, null); } catch (IOException ignorado) { }
        socket.close();
    }
}
