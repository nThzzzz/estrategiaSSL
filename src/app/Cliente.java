package app;

import ajuste.Parametro;
import estrategia.Ambiente;
import estrategia.motor.Executor;
import estrategia.coaches.CoachBancada;
import jogo.Arbitro;
import jogo.ArbitroLocal;
import jogo.EstadoDeJogo;
import model.Comando;
import model.Cor;
import mundo.Quadro;
import percepcao.Rastreador;
import proto.gc.SslGcRefereeMessage.Referee;
import rede.ConfigRede;
import rede.EmissorDeComandos;
import rede.ReceptorDeArbitro;
import rede.ReceptorDeVisao;

import java.io.IOException;
import java.util.Map;

/**
 * Presenca da estrategia na rede: escuta a visao e o arbitro, manda comando.
 *
 * <p>Duas entradas e uma saida. As entradas vem de programas diferentes -- a
 * visao da {@code ssl-vision} ou do simulador, o arbitro do
 * {@code ssl-game-controller} -- e sao independentes: a janela continua util com
 * so uma delas no ar, e e comum desenvolver com visao e sem arbitro.
 *
 * <p>Concentra o estado que a janela inteira compartilha -- configuracao, cor da
 * equipe, nomes -- para que os paineis nao precisem se conhecer. O painel da
 * esquerda troca a configuracao, o de cima le os nomes e o da direita le o
 * quadro, e nenhum dos tres tem referencia para os outros.
 *
 * <p>Os nomes das equipes ficam aqui e nao chegam pela rede porque o protocolo de
 * visao nao os carrega: {@code SSL_DetectionFrame} tem robos azuis e robos
 * amarelos, sem nome nenhum. Em competicao os nomes vem do Game Controller, que
 * o simulador ainda nao publica; ate la, sao configuracao local.
 */
public final class Cliente implements AutoCloseable {

    private final Rastreador rastreador = new Rastreador();
    private final Arbitro arbitro = new Arbitro();
    private final ArbitroLocal arbitroLocal = new ArbitroLocal();
    private final Executor executor = new Executor(new CoachBancada());

    private boolean bEstrategiaLigada;
    private long frameDaUltimaDecisao = -1;

    private ConfigRede config;
    private Cor equipe;
    private String nomeAzul = "RoboFEI";
    private String nomeAmarelo = "Adversario";

    private ReceptorDeVisao receptor;
    private ReceptorDeArbitro receptorArbitro;
    private EmissorDeComandos emissor;
    private String falha;

    public Cliente(ConfigRede config, Cor equipe) {
        this.config = config;
        this.equipe = equipe;
        // O arbitro de teste tambem carrega os nomes, e eles ganham dos locais na
        // hora de exibir. Sem alinhar aqui, a barra de cima mostrava "Azul" e
        // "Amarelo" ate alguem abrir a configuracao e clicar em Aplicar.
        setNomes(nomeAzul, nomeAmarelo);
    }

    // ------------------------------------------------------------- estado

    public ConfigRede getConfig()   { return config; }
    public Cor getEquipe()          { return equipe; }
    public Cor getAdversario()      { return equipe.oposta(); }
    public String getNomeAzul()     { return nomeAzul; }
    public String getNomeAmarelo()  { return nomeAmarelo; }
    public String getNome(Cor c)    { return c == Cor.AZUL ? nomeAzul : nomeAmarelo; }

    public void setNomes(String azul, String amarelo) {
        this.nomeAzul = azul == null || azul.isBlank() ? "Azul" : azul.trim();
        this.nomeAmarelo = amarelo == null || amarelo.isBlank() ? "Amarelo" : amarelo.trim();
        arbitroLocal.setNomes(this.nomeAzul, this.nomeAmarelo);
    }

    // ------------------------------------------------------------- arbitro

    public Arbitro getArbitro()           { return arbitro; }
    public ArbitroLocal getArbitroLocal() { return arbitroLocal; }

    /** Estado de jogo ja traduzido para a cor que estamos jogando. */
    public EstadoDeJogo estadoDeJogo() { return arbitro.estado(equipe); }

    /** Aplica um comando fabricado pelo painel de teste. */
    public void simularArbitro(Referee.Command comando) {
        arbitro.doPainel(arbitroLocal.comando(comando));
    }

    /**
     * Repete o ultimo comando de teste, para uma declaracao trocada entrar em vigor.
     *
     * <p>Goleiro e lado do campo viajam DENTRO de toda mensagem de arbitro, e nao
     * sao comandos: trocar um deles so chega em quem escuta na proxima mensagem.
     * Reemitir o comando corrente evita ter de inventar um STOP e interromper o
     * jogo por causa de uma mudanca de configuracao.
     */
    public void reemitirArbitro() {
        arbitro.doPainel(arbitroLocal.reemitir());
    }

    /**
     * Folga somada a area de defesa para formar a zona onde so o goleiro entra.
     *
     * <p>Nao ha copia local: o dono do numero e {@link Parametro#MARGEM_DA_AREA},
     * o mesmo que o arquivo de ajuste e a aba de parametros escrevem. Uma copia
     * aqui faria a folga ter dois valores conforme quem perguntasse.
     */
    public double getMargemDaArea() { return Parametro.MARGEM_DA_AREA.valor(); }

    public void setMargemDaArea(double mm) {
        Parametro.MARGEM_DA_AREA.vSetValor(Math.max(0, mm));
    }

    /**
     * Nome que o arbitro da para a equipe, com o nome local como reserva.
     *
     * <p>O Game Controller e a fonte melhor: e o nome inscrito na competicao. Mas
     * ele pode nao estar no ar, e nesse caso a janela nao pode ficar sem rotulo.
     */
    public String getNomeExibido(Cor cor) {
        EstadoDeJogo j = estadoDeJogo();
        if (!j.semArbitro()) {
            String nome = cor == equipe ? j.nomeNosso() : j.nomeDeles();
            if (nome != null && !nome.isBlank()) return nome;
        }
        return getNome(cor);
    }

    /** Ultima falha de rede, ou {@code null} se esta tudo bem. */
    public String getFalha() { return falha; }

    public Quadro quadro() {
        return receptor != null ? receptor.ultimoQuadro() : rastreador.atual();
    }

    public boolean recebendo()      { return receptor != null && receptor.recebendo(); }
    public double silencio()        { return receptor == null ? Double.POSITIVE_INFINITY : receptor.silencio(); }
    public long pacotesRecebidos()  { return receptor == null ? 0 : receptor.getPacotesRecebidos(); }
    public long pacotesGeometria()  { return receptor == null ? 0 : receptor.getPacotesGeometria(); }
    public long pacotesEnviados()   { return emissor == null ? 0 : emissor.getPacotesEnviados(); }
    public double taxaHz()          { return receptor == null ? 0 : receptor.taxaHz(); }
    public long pacotesArbitro()    { return arbitro.getPacotesRecebidos(); }
    public boolean recebendoArbitro() { return arbitro.silencio() < 5.0; }

    public String destinoDeComandos() {
        return emissor == null ? "--"
                : emissor.getDestino().getHostString() + ":" + emissor.getDestino().getPort();
    }

    // -------------------------------------------------------------- rede

    /** Abre a rede com a configuracao atual. */
    public void conectar() throws IOException {
        abrir(config, equipe);
    }

    /**
     * Troca configuracao e/ou cor da equipe com a janela aberta.
     *
     * <p>Se a nova configuracao falhar -- porta ocupada por outro processo, que e
     * o caso comum quando ja existe uma instancia rodando -- a anterior e
     * restaurada. Deixar a estrategia surda porque alguem digitou uma porta
     * errada seria pior do que ignorar a troca.
     */
    public synchronized void reconfigurar(ConfigRede nova, Cor novaEquipe) throws IOException {
        String problema = nova.problema();
        if (problema != null) throw new IOException(problema);
        if (nova.equals(config) && novaEquipe == equipe) return;

        ConfigRede anterior = config;
        Cor equipeAnterior = equipe;
        fechar();
        try {
            abrir(nova, novaEquipe);
        } catch (IOException erro) {
            try {
                abrir(anterior, equipeAnterior);
                throw new IOException(erro.getMessage()
                        + ". A configuracao anterior foi mantida.", erro);
            } catch (IOException perdida) {
                throw new IOException(erro.getMessage()
                        + ". E a anterior tambem nao voltou: " + perdida.getMessage(), erro);
            }
        }
    }

    private void abrir(ConfigRede c, Cor cor) throws IOException {
        try {
            receptor = new ReceptorDeVisao(c, rastreador);
            receptorArbitro = new ReceptorDeArbitro(c, arbitro);
            emissor = new EmissorDeComandos(c, cor);
            this.config = c;
            this.equipe = cor;
            this.falha = null;
        } catch (IOException e) {
            fechar();
            this.falha = e.getMessage();
            throw e;
        }
    }

    // ---------------------------------------------------------- estrategia

    public Executor getExecutor()       { return executor; }
    public boolean isEstrategiaLigada() { return bEstrategiaLigada; }

    /**
     * Liga e desliga a estrategia.
     *
     * <p>Ao desligar, manda um ciclo de comando parado antes de calar. Sem isso o
     * ultimo comando enviado continuaria valendo e os robos seguiriam andando
     * sozinhos, porque o simulador guarda o ultimo comando de cada robo ate
     * receber outro.
     */
    public void setEstrategiaLigada(boolean ligada) {
        if (bEstrategiaLigada && !ligada) vPararTodos();
        this.bEstrategiaLigada = ligada;
    }

    private void vPararTodos() {
        Map<Integer, Comando> parado = new java.util.LinkedHashMap<>();
        for (var j : executor.jogadores()) parado.put(j.id(), Comando.PARADO);
        enviar(parado);
    }

    /**
     * Roda um ciclo de decisao e manda o resultado.
     *
     * <p>Amarrado ao QUADRO da visao, e nao ao relogio da tela: decidir duas vezes
     * sobre o mesmo quadro gasta processamento para chegar na mesma conclusao, e
     * decidir sem quadro novo nenhum e decidir sobre um mundo que parou de chegar.
     */
    public void vDecidir() {
        if (!bEstrategiaLigada || !recebendo()) return;

        Quadro q = quadro();
        if (q.frame() == frameDaUltimaDecisao) return;
        frameDaUltimaDecisao = q.frame();

        enviar(executor.vTick(new Ambiente(q, estadoDeJogo(), equipe, getMargemDaArea())));
    }

    /** Manda um ciclo de comandos para a nossa equipe. */
    public void enviar(Map<Integer, Comando> comandos) {
        if (emissor == null) return;
        try {
            emissor.enviar(comandos);
            falha = null;
        } catch (IOException e) {
            falha = "envio: " + e.getMessage();
        }
    }

    private void fechar() {
        if (receptor != null) { receptor.close(); receptor = null; }
        if (receptorArbitro != null) { receptorArbitro.close(); receptorArbitro = null; }
        if (emissor != null)  { emissor.close();  emissor = null; }
    }

    @Override
    public void close() { fechar(); }
}
