package teste;

import app.Cliente;

import core.Geo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.nio.file.Files;
import ajuste.Parametro;
import core.Caixa;
import core.Vec2;
import model.Comando;
import model.Cor;
import model.Geometria;
import model.Robo;
import mundo.EstadoRobo;
import mundo.Quadro;
import percepcao.Ajuste;
import percepcao.FiltroKalman1D;
import percepcao.Rastreador;
import percepcao.SensorDeBola;
import percepcao.Trilha;
import jogo.Acao;
import jogo.Arbitro;
import jogo.ArbitroLocal;
import jogo.EstadoDeJogo;
import proto.gc.SslGcRefereeMessage.Referee;
import proto.gc.SslGcRefereeMessage.Referee.Command;
import proto.sim.SslSimulationRobotControl.RobotControl;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionBall;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionFrame;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionRobot;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryFieldSize;
import rede.ConfigRede;
import rede.ReceptorDeVisao;
import app.componentes.Campo;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import rede.EmissorDeComandos;
import rede.ReceptorDeArbitro;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.Map;
import java.util.Random;

/**
 * Verificacao da percepcao e do protocolo, sem framework -- roda com
 * {@code java -cp out/production/estrategiaSSL:lib/* teste.Autoteste} e sai com
 * codigo != 0 se algo quebrar.
 *
 * <p>O que se testa aqui e justamente o que NAO vem pronto da rede: o filtro que
 * inventa velocidade, o portao que decide em que medida acreditar e a geometria
 * que decide se a bola esta no sensor. Erro em qualquer um dos tres produz uma
 * estrategia que age com convicao sobre um mundo que nao existe.
 */
public final class Autoteste {

    private static int falhas = 0;
    private static int total = 0;

    private static final double DT = 1.0 / 60.0;

    public static void main(String[] args) throws Exception {
        // --- Kalman ---
        convergeParaVelocidadeConstante();
        filtraRuidoMelhorQueMedidaCrua();
        estimativaIndependenteDaTaxa();
        extrapolaDuranteOclusao();
        incertezaCresceSemMedida();
        portaoRecusaDeteccaoFantasma();
        portaoSeRendeDepoisDeInsistencia();
        chuteNaoTeleportaABola();
        ruidoNaoPassaPorManobra();
        anguloAtravessaPiSemSalto();

        // --- sensor de bola ---
        sensorPegaBolaNaBoca();
        sensorIgnoraBolaNaLateral();
        sensorIgnoraBolaPorCima();
        sensorIgnoraBolaLongeDaBoca();

        // --- arbitro ---
        arbitroTraduzOComandoParaNos();
        trocarDeCorReinterpretaSemPacoteNovo();
        semArbitroMandaParar();
        haltEStopImpoemLimites();
        reposicaoNossaNaoNosAfastaDaBola();
        ladoDoCampoSaiDoArbitro();
        placarENomeVemDoArbitro();
        modoLocalEModoRedeSeExcluem();
        arbitroChegaPelaRede();
        trocaDeModoParaOMesmoModoNaoApagaOComando();
        arbitroLocalDeclaraOGoleiroPorCor();

        // --- geometria ---
        retasCruzamOndeADiagonalManda();
        segmentoSoCruzaDentroDoTrecho();
        semirretaNaoOlhaParaTras();
        pontoNoSegmentoPrendeNasPontas();
        segmentoCortaCirculoPelaFolga();

        // --- ajuste ---
        ajusteTrocaSoOQueOArquivoCita();
        ajusteRecusaNomeDesconhecido();
        ajusteRecusaJsonQuebrado();
        modeloDeAjusteVoltaASerLido();
        rotuloUsaOVocabularioDoProtocolo();

        // --- protocolo ---
        reconfigurarTrocaDePorta();
        reconfigurarRestauraSeOBindFalha();
        visaoViraQuadro();
        geometriaVemDaRede();
        comandoSaiEmMetrosPorSegundo();
        quadroRepetidoContaUmaVez();
        perdaDeQuadroEContadaEReinicioNao();
        arquiteturaRespeitaODeclarado();
        ladoNaoInverteSemODeclarado();
        padroesDeLadoNaoSeContradizem();
        zonaProibidaSegueONossoLado();
        zoomAncoraNoCursor();
        zoomNoBatenteNaoDesliza();
        zoomIgnoraPicoDoTrackpad();

        System.out.printf("%n%d/%d verificacoes passaram%n", total - falhas, total);
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ Kalman

    /** Alvo em velocidade constante: o filtro tem de achar a velocidade certa. */

    /**
     * Os dois padroes de lado tem de concordar entre si.
     *
     * <p>Nao concordavam: {@link EstadoDeJogo#SEM_ARBITRO} assumia {@code false}
     * e o {@link ArbitroLocal} nascia com {@code true}. Antes de tocar em qualquer
     * botao a zona proibida era desenhada em -x; no primeiro comando do painel ela
     * pulava para +x. E como e preciso mandar um START para a jogada rodar, o
     * salto acontecia exatamente ao rodar a jogada -- a nossa area ficava
     * desguarnecida e o corte passava a valer contra a area do adversario.
     *
     * <p>{@code -x} para o azul nao e escolha arbitraria: no simulador o eixo
     * {@code +x} aponta para o gol AMARELO.
     */
    private static void padroesDeLadoNaoSeContradizem() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        a.doPainel(new ArbitroLocal().comando(Command.NORMAL_START));

        verdadeiro("arbitro: o padrao do painel de teste concorda com o SEM_ARBITRO",
                a.estado(Cor.AZUL).nossoLado() == EstadoDeJogo.SEM_ARBITRO.nossoLado());
        verdadeiro("arbitro: e o azul defende -x, oposto ao gol amarelo em +x",
                a.estado(Cor.AZUL).nossoLado() == -1);
    }

    /**
     * O lado nao pode inverter quando o pacote omite quem esta no lado positivo.
     *
     * <p>{@code blue_team_on_positive_half} e OPTIONAL no protocolo. O codigo
     * antigo fazia {@code has... && get...}, e um pacote sem o campo virava
     * "azul no lado negativo" -- que nao e "nao sei", e uma AFIRMACAO trocada.
     * Com o Game Controller alternando pacotes com e sem o campo, a zona proibida
     * pulava para a outra metade e a nossa area ficava desguarnecida: robo de
     * linha entrava sem nada cortar o comando, que e penalti.
     *
     * <p>O teste antigo so usava o {@link ArbitroLocal}, que preenche o campo
     * sempre -- por isso o buraco nunca aparecia.
     */
    private static void ladoNaoInverteSemODeclarado() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();

        gc.setAzulNoLadoPositivo(true);
        a.doPainel(gc.comando(Command.STOP));
        verdadeiro("arbitro: com o campo declarado, azul defende +x",
                a.estado(Cor.AZUL).nossoLado() == 1);

        // O mesmo comando, agora SEM o campo -- e o que um GC pode mandar.
        Referee semLado = Referee.newBuilder(gc.comando(Command.STOP))
                .clearBlueTeamOnPositiveHalf()
                .build();
        a.doPainel(semLado);

        verdadeiro("arbitro: pacote sem o campo NAO inverte o lado do azul",
                a.estado(Cor.AZUL).nossoLado() == 1);
        verdadeiro("arbitro: nem o do amarelo",
                a.estado(Cor.AMARELO).nossoLado() == -1);
    }

    /** A zona proibida tem de seguir o lado, e nao pular de metade sozinha. */
    private static void zonaProibidaSegueONossoLado() {
        Geometria g = Geometria.DIVISAO_B;
        Caixa maisX = g.zonaProibida(1, 45, 0, 0);
        Caixa menosX = g.zonaProibida(-1, 45, 0, 0);

        verdadeiro("area: a zona de +x fica toda em x positivo",
                maisX.xMin() > 0 && maisX.xMax() > 0);
        verdadeiro("area: a zona de -x fica toda em x negativo",
                menosX.xMin() < 0 && menosX.xMax() < 0);
        verdadeiro("area: as duas nao se encostam",
                maisX.xMin() > menosX.xMax());
    }


    /**
     * O mesmo quadro chegando duas vezes conta uma so.
     *
     * <p>Vale desde que a visao pode chegar por mais de um caminho: o simulador
     * publica em multicast e pode publicar TAMBEM em unicast ou broadcast, para
     * atravessar uma rede que nao repassa multicast. Quem estiver nos dois
     * caminhos recebe cada quadro duas vezes, e a taxa em Hz -- que e o numero
     * que se olha para saber se esta funcionando -- apareceria dobrada.
     *
     * <p>A chave inclui a CAMERA: numa partida de verdade sao varias, cada uma
     * numerando os proprios quadros, e deduplicar so por {@code frame_number}
     * jogaria fora quadro legitimo.
     */
    private static void quadroRepetidoContaUmaVez() throws Exception {
        final int porta = 12106;
        ConfigRede c = ConfigRede.padrao().comPortaVisao(porta);

        try (ReceptorDeVisao rec = new ReceptorDeVisao(c, new Rastreador());
             DatagramSocket tx = new DatagramSocket()) {

            byte[] a = quadroDeVisao(7, 0);
            byte[] b = quadroDeVisao(8, 0);
            byte[] outraCamera = quadroDeVisao(7, 1);   // mesmo numero, camera 1

            InetAddress destino = InetAddress.getByName("127.0.0.1");
            for (byte[] p : new byte[][] {a, a, b, outraCamera}) {
                tx.send(new DatagramPacket(p, p.length, destino, porta));
                Thread.sleep(40);
            }
            Thread.sleep(200);

            // Os quatro pacotes CHEGARAM ao socket -- o descarte e da
            // deduplicacao, e nao da rede. Sem esta verificacao, um pacote
            // perdido no caminho daria o mesmo resultado da outra e o teste
            // passaria dizendo o que nao aconteceu.
            verdadeiro("visao: os quatro pacotes chegaram ao socket",
                    rec.getPacotesRecebidos() == 4);
            verdadeiro("visao: e exatamente um -- o repetido -- foi descartado",
                    rec.getDuplicadosDescartados() == 1);
        }
    }

    /**
     * Buraco na numeracao vira contagem de perda; reinicio nao.
     *
     * <p>"Parece que esta perdendo pacote" e impressao dificil de confirmar no
     * olho: movimento truncado tambem vem de tela lenta e de simulador engasgado,
     * e sao tres consertos diferentes. O {@code frame_number} responde sem
     * ambiguidade.
     *
     * <p>O reinicio do simulador manda o numero de volta a zero, e contar isso
     * como perda daria um numero gigante justo quando nada de errado aconteceu --
     * pior que nao contar nada, porque manda procurar defeito onde nao ha.
     */
    private static void perdaDeQuadroEContadaEReinicioNao() throws Exception {
        final int porta = 12206;
        ConfigRede c = ConfigRede.padrao().comPortaVisao(porta);

        try (ReceptorDeVisao rec = new ReceptorDeVisao(c, new Rastreador());
             DatagramSocket tx = new DatagramSocket()) {

            InetAddress destino = InetAddress.getByName("127.0.0.1");
            // 10, 11 seguidos; salta para 15 (perde 12, 13, 14); depois reinicia em 0.
            for (int frame : new int[] {10, 11, 15, 0}) {
                byte[] p = quadroDeVisao(frame, 0);
                tx.send(new DatagramPacket(p, p.length, destino, porta));
                Thread.sleep(40);
            }
            Thread.sleep(200);

            verdadeiro("visao: o salto de 11 para 15 conta 3 quadros perdidos",
                    rec.getQuadrosPerdidos() == 3);
            verdadeiro("visao: e o reinicio da numeracao nao conta como perda",
                    rec.getPacotesRecebidos() == 4);
        }
    }

    /** Um {@code SSL_WrapperPacket} minimo, so com o que a deduplicacao le. */
    private static byte[] quadroDeVisao(int frame, int camera) {
        return proto.vision.MessagesRobocupSslWrapper.SSL_WrapperPacket.newBuilder()
                .setDetection(SSL_DetectionFrame.newBuilder()
                        .setFrameNumber(frame)
                        .setCameraId(camera)
                        .setTCapture(frame * 0.016)
                        .setTSent(frame * 0.016))
                .build().toByteArray();
    }

    // -------------------------------------------------------- arquitetura

    /**
     * As zonas e as dependencias declaradas nos {@code package-info.java} valem.
     *
     * <p>O README sempre descreveu a arquitetura em prosa, e prosa nao segura
     * nada: o grafo REAL tinha dois ciclos que ninguem via -- {@code estrategia}
     * com {@code estrategia.esqueleto}, e depois {@code app.telas} com
     * {@code app.componentes} -- e o {@code view} dependia de tres pacotes que a
     * prosa nao citava. Cada pacote agora DECLARA sua zona e de quem depende, e
     * este caso le a declaracao e o codigo e falha quando os dois divergem.
     *
     * <p>A regra que mais importa e a de zona: {@code ESTAVEL} nao pode depender
     * de {@code TRABALHO} nem de {@code EXTENSAO}. E o que garante que mexer numa
     * play nunca obrigue a mexer na rede -- e o que deixa rodar mil partidas de
     * treino sem abrir janela nem tocar em socket.
     */
    private static void arquiteturaRespeitaODeclarado() {
        Path src = Path.of("src");
        if (!Files.isDirectory(src)) {
            verdadeiro("arquitetura: src/ encontrado (rode a partir da raiz do repo)", false);
            return;
        }

        Map<String, String> zona = new TreeMap<>();
        Map<String, Set<String>> declarado = new TreeMap<>();
        Map<String, Set<String>> real = new TreeMap<>();
        List<String> semInfo = new ArrayList<>();

        try (var caminhos = Files.walk(src)) {
            for (Path p : caminhos.filter(x -> x.toString().endsWith(".java")).toList()) {
                if (p.toString().contains("/proto/")) continue;
                String txt = Files.readString(p);
                Matcher mp = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE).matcher(txt);
                if (!mp.find()) continue;              // Main.java, no pacote padrao
                String pkg = mp.group(1);

                if (p.getFileName().toString().equals("package-info.java")) {
                    // A lista termina no ultimo ponto da LINHA: nome de pacote tem
                    // ponto no meio, e cortar no primeiro dava "estrategia" onde
                    // estava escrito "estrategia.coaches".
                    Matcher md = Pattern.compile(
                            "Zona:\\s*(\\w+)\\.\\s*Depende de:\\s*(.*)\\.\\s*$", Pattern.MULTILINE)
                            .matcher(txt);
                    if (!md.find()) { semInfo.add(pkg + " (formato)"); continue; }
                    zona.put(pkg, md.group(1));
                    Set<String> deps = new TreeSet<>();
                    for (String d : md.group(2).split(",")) {
                        d = d.trim();
                        if (!d.isEmpty() && !d.equals("(nada)")) deps.add(d);
                    }
                    declarado.put(pkg, deps);
                    continue;
                }

                real.putIfAbsent(pkg, new TreeSet<>());
                Matcher mi = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);", Pattern.MULTILINE)
                        .matcher(txt);
                while (mi.find()) {
                    String imp = mi.group(1);
                    if (imp.startsWith("java.") || imp.startsWith("javax.")
                            || imp.startsWith("com.formdev.")) continue;
                    String alvo = imp.substring(0, imp.lastIndexOf('.'));
                    if (alvo.startsWith("proto")) alvo = "proto";
                    if (!alvo.equals(pkg)) real.get(pkg).add(alvo);
                }
            }
        } catch (Exception e) {
            verdadeiro("arquitetura: leitura de src/ (" + e.getMessage() + ")", false);
            return;
        }

        for (String pkg : real.keySet()) if (!zona.containsKey(pkg)) semInfo.add(pkg);
        verdadeiro("arquitetura: todo pacote declara zona e dependencias"
                + (semInfo.isEmpty() ? "" : " -- faltam: " + semInfo), semInfo.isEmpty());

        List<String> naoDeclaradas = new ArrayList<>();
        for (var e : real.entrySet()) {
            Set<String> ok = declarado.getOrDefault(e.getKey(), Set.of());
            if (ok.contains("*")) continue;                       // teste, por natureza
            for (String alvo : e.getValue()) {
                if (alvo.equals("proto") || ok.contains(alvo)) continue;
                naoDeclaradas.add(e.getKey() + " -> " + alvo);
            }
        }
        verdadeiro("arquitetura: nenhum import fora do que o pacote declara"
                + (naoDeclaradas.isEmpty() ? "" : " -- " + naoDeclaradas), naoDeclaradas.isEmpty());

        List<String> furos = new ArrayList<>();
        for (var e : declarado.entrySet()) {
            if (!"ESTAVEL".equals(zona.get(e.getKey()))) continue;
            for (String alvo : e.getValue()) {
                String za = zona.get(alvo);
                if (za != null && !za.equals("ESTAVEL")) furos.add(e.getKey() + " -> " + alvo + " (" + za + ")");
            }
        }
        verdadeiro("arquitetura: ESTAVEL nao depende de TRABALHO nem de EXTENSAO"
                + (furos.isEmpty() ? "" : " -- " + furos), furos.isEmpty());

        String ciclo = acharCiclo(declarado);
        verdadeiro("arquitetura: nenhuma dependencia circular"
                + (ciclo == null ? "" : " -- " + ciclo), ciclo == null);
    }

    /** Busca em profundidade; devolve o caminho do primeiro ciclo, ou null. */
    private static String acharCiclo(Map<String, Set<String>> grafo) {
        Set<String> pronto = new HashSet<>();
        for (String inicio : grafo.keySet()) {
            Deque<String> pilha = new ArrayDeque<>();
            String r = visitar(inicio, grafo, new LinkedHashSet<>(), pronto, pilha);
            if (r != null) return r;
        }
        return null;
    }

    private static String visitar(String no, Map<String, Set<String>> grafo,
                                  LinkedHashSet<String> caminho, Set<String> pronto,
                                  Deque<String> pilha) {
        if (pronto.contains(no)) return null;
        if (!caminho.add(no)) return String.join(" -> ", caminho) + " -> " + no;
        for (String p : grafo.getOrDefault(no, Set.of())) {
            if (p.equals("*")) continue;
            if (caminho.contains(p)) return String.join(" -> ", caminho) + " -> " + p;
            String r = visitar(p, grafo, caminho, pronto, pilha);
            if (r != null) return r;
        }
        caminho.remove(no);
        pronto.add(no);
        return null;
    }

    // ------------------------------------------------------- zoom do campo

    /**
     * O ponto do mundo sob o cursor tem de ficar parado durante o zoom.
     *
     * <p>Antes o zoom ancorava no centro do painel, e quem olhava um canto via
     * o canto fugir da tela a cada passo. E um caso de tela, mas e aritmetica
     * pura -- da para verificar sem abrir janela, e sem isso a regressao volta
     * sem ninguem notar, porque o sintoma parece "mao pesada no trackpad".
     */
    private static void zoomAncoraNoCursor() {
        Campo campo = new Campo(Quadro::vazio);
        campo.setSize(1000, 700);

        int sx = 820, sy = 160; // longe do centro: era exatamente ali que fugia
        Vec2 alvo = campo.telaParaMundo(sx, sy);

        for (int i = 0; i < 15; i++) campo.aplicarZoom(1.1, sx, sy);
        aproximado("zoom: o ponto sob o cursor fica parado ao aproximar",
                campo.telaParaMundo(sx, sy).distancia(alvo), 0, 0.5);

        for (int i = 0; i < 30; i++) campo.aplicarZoom(1 / 1.1, sx, sy);
        aproximado("zoom: e continua parado ao afastar",
                campo.telaParaMundo(sx, sy).distancia(alvo), 0, 0.5);
    }

    /**
     * No batente o zoom nao acontece, e o pan nao pode andar mesmo assim.
     *
     * <p>E o que quebraria se a correcao do pan usasse o fator PEDIDO em vez do
     * aplicado: a imagem deslizaria sob o cursor com o zoom ja parado, bem na
     * hora em que se insiste no gesto.
     */
    private static void zoomNoBatenteNaoDesliza() {
        Campo campo = new Campo(Quadro::vazio);
        campo.setSize(1000, 700);

        int sx = 300, sy = 520;
        for (int i = 0; i < 60; i++) campo.aplicarZoom(1.1, sx, sy); // crava no teto
        Vec2 noTeto = campo.telaParaMundo(sx, sy);

        for (int i = 0; i < 20; i++) campo.aplicarZoom(1.1, sx, sy); // insiste
        aproximado("zoom: insistir no batente nao desliza a imagem",
                campo.telaParaMundo(sx, sy).distancia(noTeto), 0, 1e-6);
    }

    /**
     * Um pico do trackpad nao pode valer mais que um entalhe de roda.
     *
     * <p>O 4,7 nao e inventado: foi medido no trackpad, no meio de uma rajada de
     * 327 eventos cuja soma inteira era 2,1. Sem teto ele sozinho dava 58% de
     * escala num quadro.
     */
    private static void zoomIgnoraPicoDoTrackpad() {
        Campo campo = new Campo(Quadro::vazio);
        aproximado("zoom: pico de 4,7 do trackpad vale um entalhe, nao 4,7",
                Campo.fatorDeZoom(roda(campo, 4.7)), Campo.fatorDeZoom(roda(campo, 1.0)), 1e-9);
        verdadeiro("zoom: entalhe normal de roda passa intacto",
                Math.abs(Campo.fatorDeZoom(roda(campo, 1.0)) - 1 / 1.1) < 1e-9);
    }

    private static MouseWheelEvent roda(Campo campo, double precise) {
        return new MouseWheelEvent(campo, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                0, 0, 0, 0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1, (int) precise, precise);
    }

    private static void convergeParaVelocidadeConstante() {
        FiltroKalman1D f = new FiltroKalman1D(3000, 12, false);
        f.iniciar(0, 3000);

        double v = 2000; // mm/s
        for (int i = 1; i <= 60; i++) {
            f.prever(DT);
            f.corrigir(v * i * DT);
        }
        aproximado("kalman: acha 2000 mm/s em 1 s de quadros limpos", f.velocidade(), v, 40);
    }

    /**
     * Alvo parado com ruido: o erro do filtro tem de ser menor que o da medida
     * crua. E o unico motivo de existir um filtro.
     */
    private static void filtraRuidoMelhorQueMedidaCrua() {
        Random r = new Random(42); // semente fixa: o teste nao pode piscar
        double sigma = 20;

        FiltroKalman1D f = new FiltroKalman1D(500, sigma, false);
        f.iniciar(0, 100);

        double somaFiltro = 0, somaCrua = 0;
        int n = 400;
        for (int i = 0; i < n; i++) {
            double medida = r.nextGaussian() * sigma;
            f.prever(DT);
            f.corrigir(medida);
            if (i > 60) { // depois de convergir
                somaFiltro += f.posicao() * f.posicao();
                somaCrua += medida * medida;
            }
        }
        double rmsFiltro = Math.sqrt(somaFiltro / (n - 60));
        double rmsCrua = Math.sqrt(somaCrua / (n - 60));
        verdadeiro(String.format("kalman: filtra ruido (rms %.1f mm contra %.1f mm da medida crua)",
                rmsFiltro, rmsCrua), rmsFiltro < rmsCrua * 0.6);
    }

    /**
     * A MESMA trajetoria amostrada a 60 Hz e a 120 Hz tem de dar a mesma
     * velocidade. E a versao Kalman da independencia de dt que o simulador
     * tambem persegue: sem isso, mudar a taxa da visao mudaria a estrategia.
     */
    private static void estimativaIndependenteDaTaxa() {
        double v = 1500;
        aproximado("kalman: mesma velocidade a 60 Hz e a 120 Hz",
                velocidadeApos(v, 1.0 / 60.0), velocidadeApos(v, 1.0 / 120.0), 25);
    }

    private static double velocidadeApos(double v, double dt) {
        FiltroKalman1D f = new FiltroKalman1D(3000, 12, false);
        f.iniciar(0, 3000);
        for (double t = dt; t <= 1.0 + 1e-9; t += dt) {
            f.prever(dt);
            f.corrigir(v * t);
        }
        return f.velocidade();
    }

    /** Sem medida, a trilha tem de seguir andando pela velocidade estimada. */
    private static void extrapolaDuranteOclusao() {
        Trilha t = new Trilha(Ajuste.robo(), true);
        double v = 1800, tempo = 0;

        for (int i = 0; i < 40; i++, tempo += DT) {
            t.prever(tempo);
            t.corrigir(new Vec2(v * tempo, 0), 0, tempo);
        }

        double ocultoPor = 0.2;
        double tFinal = tempo + ocultoPor;
        t.prever(tFinal); // ninguem viu nada nesse intervalo

        aproximado("kalman: extrapola 200 ms de oclusao na posicao certa",
                t.posicao().x(), v * tFinal, 60);
    }

    /** E a incerteza tem de crescer enquanto isso, para a extrapolacao se declarar. */
    private static void incertezaCresceSemMedida() {
        Trilha t = new Trilha(Ajuste.robo(), true);
        double tempo = 0;
        for (int i = 0; i < 40; i++, tempo += DT) {
            t.prever(tempo);
            t.corrigir(new Vec2(0, 0), 0, tempo);
        }
        double vendo = t.incerteza();
        t.prever(tempo + 0.5);
        verdadeiro(String.format("kalman: incerteza cresce sem medida (%.0f mm -> %.0f mm)",
                vendo, t.incerteza()), t.incerteza() > vendo * 3);
    }

    /** Deteccao fantasma a dois metros nao pode arrastar a trilha. */
    private static void portaoRecusaDeteccaoFantasma() {
        Trilha t = new Trilha(Ajuste.robo(), true);
        double tempo = 0;
        for (int i = 0; i < 40; i++, tempo += DT) {
            t.prever(tempo);
            t.corrigir(new Vec2(1000, 500), 0, tempo);
        }

        tempo += DT;
        t.prever(tempo);
        boolean aceitou = t.corrigir(new Vec2(3000, 500), 0, tempo);

        verdadeiro("kalman: portao recusa deteccao fantasma a 2 m", !aceitou);
        aproximado("kalman: e a trilha nao se move com a recusa", t.posicao().x(), 1000, 30);
    }

    /**
     * Mas se o "fantasma" insistir, quem esta errado e a trilha. Sem essa
     * valvula, um filtro convencido da posicao errada recusa para sempre as
     * medidas certas.
     */
    private static void portaoSeRendeDepoisDeInsistencia() {
        Trilha t = new Trilha(Ajuste.robo(), true);
        double tempo = 0;
        for (int i = 0; i < 40; i++, tempo += DT) {
            t.prever(tempo);
            t.corrigir(new Vec2(0, 0), 0, tempo);
        }

        for (int i = 0; i < Ajuste.robo().maxRejeicoes() + 1; i++) {
            tempo += DT;
            t.prever(tempo);
            t.corrigir(new Vec2(2500, 0), 0, tempo);
        }
        aproximado("kalman: trilha se rende a medida insistente", t.posicao().x(), 2500, 60);
    }

    /**
     * Chute: a bola sai de parada a 6,5 m/s dentro de um quadro.
     *
     * <p>Regressao do bug da "teleportada". O portao estatistico recusava as
     * primeiras medidas do chute -- surpresa enorme perto da confianca que o
     * filtro tinha na bola parada -- e a bola congelava dois quadros antes de
     * saltar 325 mm. O que se cobra aqui e o comportamento certo: a POSICAO nao
     * pode errar feio em quadro nenhum, porque e ela que se ve na tela.
     */
    private static void chuteNaoTeleportaABola() {
        Trilha t = new Trilha(Ajuste.bola(), false);
        double v = 6500; // teto da regra
        int parados = 30;
        double pior = 0;

        for (int i = 0; i <= parados + 10; i++) {
            double tempo = i * DT;
            double verdade = i <= parados ? 1000 : 1000 + v * (i - parados) * DT;
            t.prever(tempo);
            t.corrigir(new Vec2(verdade, 0), Double.NaN, tempo);
            if (i > parados) pior = Math.max(pior, Math.abs(t.posicao().x() - verdade));
        }

        verdadeiro(String.format("kalman: chute nao teleporta a bola (pior erro %.0f mm)", pior),
                pior < 40);
        aproximado("kalman: e a velocidade do chute sai certa", t.velocidade().x(), v, 300);
    }

    /**
     * A contrapartida do caminho de manobra: ruido nao pode aciona-lo.
     *
     * <p>Se ruido comum passasse por manobra, a trilha se reinicializaria toda
     * hora e a velocidade viraria a diferenca crua entre dois quadros -- ou seja,
     * o filtro deixaria de filtrar exatamente onde mais importa.
     */
    private static void ruidoNaoPassaPorManobra() {
        Random r = new Random(7);
        Trilha t = new Trilha(Ajuste.bola(), false);
        double sigma = 10, pior = 0;

        for (int i = 0; i < 200; i++) {
            double tempo = i * DT;
            t.prever(tempo);
            t.corrigir(new Vec2(2000 + r.nextGaussian() * sigma,
                                r.nextGaussian() * sigma), Double.NaN, tempo);
            if (i > 30) pior = Math.max(pior, t.velocidade().norma());
        }
        verdadeiro(String.format("kalman: bola parada com ruido segue parada (pico %.0f mm/s)", pior),
                pior < 400);
    }

    /** Orientacao cruzando 180 graus nao pode virar uma volta inteira de omega. */
    private static void anguloAtravessaPiSemSalto() {
        Trilha t = new Trilha(Ajuste.robo(), true);
        double omega = 4.0; // rad/s, anti-horario
        double tempo = 0;
        double theta = Math.PI - 0.3;

        for (int i = 0; i < 40; i++) {
            t.prever(tempo);
            t.corrigir(new Vec2(0, 0), core.Angulo.normalizar(theta), tempo);
            tempo += DT;
            theta += omega * DT;
        }
        aproximado("kalman: omega correto cruzando +-PI", t.omega(), omega, 0.8);
    }

    // ------------------------------------------------------------ sensor de bola

    private static void sensorPegaBolaNaBoca() {
        Vec2 robo = new Vec2(0, 0);
        Vec2 bola = new Vec2(Robo.DIST_FACE_FRONTAL + 21.5, 0); // encostada na face
        verdadeiro("sensor: acusa bola encostada na boca",
                SensorDeBola.detectar(robo, 0, bola, 21.5, 21.5));
    }

    private static void sensorIgnoraBolaNaLateral() {
        Vec2 robo = new Vec2(0, 0);
        Vec2 bola = new Vec2(0, Robo.RAIO + 21.5); // encostada na lateral da capa
        verdadeiro("sensor: ignora bola encostada na lateral",
                !SensorDeBola.detectar(robo, 0, bola, 21.5, 21.5));
    }

    private static void sensorIgnoraBolaPorCima() {
        Vec2 robo = new Vec2(0, 0);
        Vec2 bola = new Vec2(Robo.DIST_FACE_FRONTAL + 21.5, 0);
        verdadeiro("sensor: ignora bola passando por cima do dribbler",
                !SensorDeBola.detectar(robo, 0, bola, 120, 21.5));
    }

    private static void sensorIgnoraBolaLongeDaBoca() {
        Vec2 robo = new Vec2(0, 0);
        // Na frente, mas fora da largura util do rolete.
        Vec2 bola = new Vec2(Robo.DIST_FACE_FRONTAL + 10, Robo.MEIA_BOCA + 40);
        verdadeiro("sensor: ignora bola fora da largura da boca",
                !SensorDeBola.detectar(robo, 0, bola, 21.5, 21.5));
    }

    // ------------------------------------------------------------------ protocolo

    // ------------------------------------------------------------------ arbitro

    /**
     * O comando nomeia uma cor; nos precisamos de "nosso" ou "deles".
     *
     * <p>E o ponto central do arbitro: a mensagem e uma so, transmitida para
     * todo mundo, e cada time a le do proprio ponto de vista.
     */
    private static void arbitroTraduzOComandoParaNos() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        a.doPainel(new ArbitroLocal().comando(Command.PREPARE_KICKOFF_BLUE));

        EstadoDeJogo azul = a.estado(Cor.AZUL);
        verdadeiro("arbitro: kickoff azul e KICKOFF nosso para o azul",
                azul.acao() == Acao.KICKOFF && azul.nosso());

        EstadoDeJogo amarelo = a.estado(Cor.AMARELO);
        verdadeiro("arbitro: o mesmo comando e KICKOFF deles para o amarelo",
                amarelo.acao() == Acao.KICKOFF && !amarelo.nosso());
    }

    /**
     * Trocar de cor na janela tem de virar "deles" na hora, sem esperar pacote
     * novo -- em HALT o Game Controller pode ficar quieto por bastante tempo.
     */
    private static void trocarDeCorReinterpretaSemPacoteNovo() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        a.doPainel(new ArbitroLocal().comando(Command.DIRECT_FREE_YELLOW));

        verdadeiro("arbitro: falta amarela e nossa jogando de amarelo",
                a.estado(Cor.AMARELO).nosso());
        verdadeiro("arbitro: e deles jogando de azul, sem pacote novo",
                !a.estado(Cor.AZUL).nosso());
    }

    /** Nao saber o estado do jogo nao autoriza a jogar. */
    private static void semArbitroMandaParar() {
        EstadoDeJogo j = new Arbitro().estado(Cor.AZUL);
        verdadeiro("arbitro: sem mensagem nenhuma, o estado e HALT",
                j.acao() == Acao.HALT && !j.podemosMover() && j.semArbitro());
    }

    /**
     * Reafirmar o modo que ja esta valendo nao pode apagar o comando corrente.
     *
     * <p>Trocar de fonte descarta o que a outra fonte disse, e isso e o certo: o
     * ultimo comando do Game Controller nao vale mais depois de passar para o
     * arbitro local. Mas a interface CHAMA esse mesmo metodo so para alinhar o
     * seletor com o estado real, e a chamada redundante zerava o arbitro -- o
     * painel exibia "nenhum comando ainda" logo depois de um STOP que ele mesmo
     * tinha acabado de mandar.
     */
    private static void trocaDeModoParaOMesmoModoNaoApagaOComando() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        a.doPainel(new ArbitroLocal().comando(Command.STOP));

        a.setModoLocal(true); // reafirmar o modo: nao e troca de fonte
        verdadeiro("arbitro: reafirmar o modo local preserva o comando",
                !a.estado(Cor.AZUL).semArbitro() && a.estado(Cor.AZUL).acao() == Acao.STOP);

        a.setModoLocal(false); // troca de verdade: dai sim descarta
        verdadeiro("arbitro: trocar de fonte descarta o comando da anterior",
                a.estado(Cor.AZUL).semArbitro());
    }

    /**
     * Goleiro e declaracao de EQUIPE, e viaja dentro de toda mensagem de arbitro.
     *
     * <p>E por isso que trocar de goleiro precisa reemitir: nao existe comando
     * "trocar goleiro" no protocolo, o numero simplesmente vai junto do proximo
     * {@code Referee}. Reemitir e o que evita ter de inventar um STOP e
     * interromper o jogo por causa de uma mudanca de configuracao.
     */
    private static void arbitroLocalDeclaraOGoleiroPorCor() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();
        gc.setGoleiro(Cor.AZUL, 3);
        gc.setGoleiro(Cor.AMARELO, 7);
        a.doPainel(gc.comando(Command.STOP));

        verdadeiro("arbitro: o goleiro azul e o nosso jogando de azul",
                a.estado(Cor.AZUL).goleiro() == 3);
        verdadeiro("arbitro: e o amarelo e o nosso jogando de amarelo",
                a.estado(Cor.AMARELO).goleiro() == 7);

        gc.setGoleiro(Cor.AZUL, 4);
        a.doPainel(gc.reemitir());
        verdadeiro("arbitro: reemitir leva o goleiro novo sem trocar o comando",
                a.estado(Cor.AZUL).goleiro() == 4
                        && a.estado(Cor.AZUL).acao() == Acao.STOP);
    }


    // -------------------------------------------------------------- geometria

    private static void retasCruzamOndeADiagonalManda() {
        Vec2 p = Geo.getVec2InterseccaoDeRetas(
                new Vec2(0, 0), new Vec2(10, 10),
                new Vec2(0, 10), new Vec2(10, 0));
        verdadeiro("geo: duas diagonais se cruzam no meio",
                p != null && Math.abs(p.x() - 5) < 1e-9 && Math.abs(p.y() - 5) < 1e-9);

        verdadeiro("geo: retas paralelas nao devolvem ponto inventado",
                Geo.getVec2InterseccaoDeRetas(new Vec2(0, 0), new Vec2(10, 0),
                        new Vec2(0, 5), new Vec2(10, 5)) == null);
    }

    /**
     * A diferenca que mais causa erro silencioso: reta responde sempre, segmento
     * so responde dentro do trecho.
     */
    private static void segmentoSoCruzaDentroDoTrecho() {
        Vec2 a1 = new Vec2(0, 0), a2 = new Vec2(10, 0);
        Vec2 b1 = new Vec2(5, 1), b2 = new Vec2(5, 9); // passa ACIMA do trecho a

        verdadeiro("geo: como reta, as duas se cruzam",
                Geo.getVec2InterseccaoDeRetas(a1, a2, b1, b2) != null);
        verdadeiro("geo: como segmento, nao se cruzam",
                Geo.getVec2InterseccaoDeSegmentos(a1, a2, b1, b2) == null);

        Vec2 dentro = Geo.getVec2InterseccaoRetaComSegmento(
                new Vec2(0, 5), new Vec2(10, 5), b1, b2);
        verdadeiro("geo: reta contra segmento acha o ponto dentro do trecho",
                dentro != null && Math.abs(dentro.y() - 5) < 1e-9);

        verdadeiro("geo: e recusa quando o cruzamento cai fora do trecho",
                Geo.getVec2InterseccaoRetaComSegmento(
                        new Vec2(0, 20), new Vec2(10, 20), b1, b2) == null);
    }

    /**
     * O caso do goleiro: bola se afastando tambem cruza a reta do gol, atras dela.
     *
     * <p>Sem a distincao, ele se posiciona para defender uma bola que ja passou.
     */
    private static void semirretaNaoOlhaParaTras() {
        Vec2 poste1 = new Vec2(4500, -500), poste2 = new Vec2(4500, 500);

        Vec2 vindo = Geo.getVec2SemirretaComSegmento(
                new Vec2(2000, 0), new Vec2(1000, 0), poste1, poste2);
        verdadeiro("geo: bola indo ao gol cruza a linha entre os postes",
                vindo != null && Math.abs(vindo.x() - 4500) < 1e-9);

        verdadeiro("geo: bola se afastando nao cruza nada a frente",
                Geo.getVec2SemirretaComSegmento(
                        new Vec2(2000, 0), new Vec2(-1000, 0), poste1, poste2) == null);

        verdadeiro("geo: bola indo por fora do poste tambem nao",
                Geo.getVec2SemirretaComSegmento(
                        new Vec2(2000, 0), new Vec2(1000, 900), poste1, poste2) == null);
    }

    private static void pontoNoSegmentoPrendeNasPontas() {
        Vec2 a = new Vec2(0, 0), b = new Vec2(10, 0);

        Vec2 meio = Geo.getVec2NoSegmento(new Vec2(5, 7), a, b);
        aproximado("geo: projecao no meio do trecho", meio.x(), 5, 1e-9);

        Vec2 antes = Geo.getVec2NoSegmento(new Vec2(-30, 4), a, b);
        aproximado("geo: fora do trecho, prende na ponta", antes.x(), 0, 1e-9);

        aproximado("geo: a reta infinita nao prende",
                Geo.getVec2NaReta(new Vec2(-30, 4), a, b).x(), -30, 1e-9);
    }

    /** A pergunta do passe: passar PERTO do robo ja mata a linha. */
    private static void segmentoCortaCirculoPelaFolga() {
        Vec2 de = new Vec2(0, 0), para = new Vec2(1000, 0);
        Vec2 quase = new Vec2(500, 120);

        verdadeiro("geo: robo a 120 mm nao corta uma folga de 100",
                !Geo.bSegmentoCortaCirculo(de, para, quase, 100));
        verdadeiro("geo: mas corta uma folga de 130",
                Geo.bSegmentoCortaCirculo(de, para, quase, 130));

        aproximado("geo: a folga do trecho e a menor distancia de todos",
                Geo.dFolgaDoSegmento(de, para,
                        List.of(new Vec2(500, 400), quase, new Vec2(900, 900))),
                120, 1e-9);
    }

    // ------------------------------------------------------------------ ajuste

    /** O arquivo diz o que MUDA; o que ele nao cita fica no padrao. */
    private static void ajusteTrocaSoOQueOArquivoCita() throws Exception {
        Parametro.vRestaurarPadroes();
        double intocado = Parametro.HISTERESE_DE_ATRIBUICAO.valor();

        List<String> mudancas = carregar("""
                {
                  // comentario de linha e aceito: e onde mora a unidade
                  "TOLERANCIA_DE_CHEGADA": 25,
                  "VEL_CONDUCAO": 900
                }
                """);

        aproximado("ajuste: o arquivo troca o que cita",
                Parametro.TOLERANCIA_DE_CHEGADA.valor(), 25, 1e-9);
        aproximado("ajuste: e o segundo tambem",
                Parametro.VEL_CONDUCAO.valor(), 900, 1e-9);
        aproximado("ajuste: o que ele nao cita fica como estava",
                Parametro.HISTERESE_DE_ATRIBUICAO.valor(), intocado, 1e-9);
        verdadeiro("ajuste: a carga devolve o que mudou", mudancas.size() == 2);

        Parametro.vRestaurarPadroes();
        aproximado("ajuste: restaurar volta ao valor de fabrica",
                Parametro.TOLERANCIA_DE_CHEGADA.valor(),
                Parametro.TOLERANCIA_DE_CHEGADA.padrao(), 1e-9);
    }

    /**
     * Nome errado e ERRO, e nao aviso.
     *
     * <p>Um parametro digitado errado que passa em silencio faz procurar o
     * problema no comportamento do robo, que e o lugar errado.
     */
    private static void ajusteRecusaNomeDesconhecido() {
        Parametro.vRestaurarPadroes();
        String erro = erroAoCarregar("{\"TOLERANCIA_DE_CHEGAD\": 25}");
        verdadeiro("ajuste: nome desconhecido e recusado, e a mensagem diz qual",
                erro != null && erro.contains("TOLERANCIA_DE_CHEGAD"));
        aproximado("ajuste: e nada e aplicado pela metade",
                Parametro.TOLERANCIA_DE_CHEGADA.valor(),
                Parametro.TOLERANCIA_DE_CHEGADA.padrao(), 1e-9);
    }

    /** Erro de sintaxe tem de dizer a POSICAO, senao procura-se por minutos. */
    private static void ajusteRecusaJsonQuebrado() {
        String semDoisPontos = erroAoCarregar("{\"VEL_CONDUCAO\" 900}");
        verdadeiro("ajuste: falta de ':' e apontada com a posicao",
                semDoisPontos != null && semDoisPontos.contains("posicao"));

        String naoNumero = erroAoCarregar("{\"VEL_CONDUCAO\": \"rapido\"}");
        verdadeiro("ajuste: valor que nao e numero e recusado", naoNumero != null);
    }

    /** O modelo gerado tem de ser um arquivo valido -- senao nao serve de modelo. */
    private static void modeloDeAjusteVoltaASerLido() throws Exception {
        Parametro.vRestaurarPadroes();
        Parametro.VEL_CONDUCAO.vSetValor(777);
        String modelo = Parametro.paraJson();

        Parametro.vRestaurarPadroes();
        carregar(modelo);
        aproximado("ajuste: o modelo gerado volta a ser lido, com os comentarios",
                Parametro.VEL_CONDUCAO.valor(), 777, 1e-9);
        Parametro.vRestaurarPadroes();
    }

    private static List<String> carregar(String json) throws Exception {
        Path arquivo = Files.createTempFile("ajuste", ".json");
        try {
            Files.writeString(arquivo, json);
            return Parametro.vCarregar(arquivo);
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }

    /** A mensagem do erro, ou null se por algum motivo passou. */
    private static String erroAoCarregar(String json) {
        try {
            carregar(json);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static void haltEStopImpoemLimites() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();

        a.doPainel(gc.comando(Command.HALT));
        EstadoDeJogo halt = a.estado(Cor.AZUL);
        verdadeiro("arbitro: HALT proibe mover", !halt.podemosMover());
        aproximado("arbitro: e zera o limite de velocidade", halt.limiteDeVelocidade(), 0, 1e-9);

        a.doPainel(gc.comando(Command.STOP));
        EstadoDeJogo stop = a.estado(Cor.AZUL);
        verdadeiro("arbitro: STOP permite mover", stop.podemosMover());
        aproximado("arbitro: com teto de 1,5 m/s", stop.limiteDeVelocidade(), 1500, 1e-9);
        aproximado("arbitro: e 0,5 m de distancia da bola", stop.afastamentoDaBola(), 500, 1e-9);

        a.doPainel(gc.comando(Command.FORCE_START));
        verdadeiro("arbitro: FORCE START poe a bola em jogo",
                a.estado(Cor.AZUL).bolaEmJogo());
    }

    /** Na nossa reposicao quem tem de dar espaco e o adversario, nao nos. */
    private static void reposicaoNossaNaoNosAfastaDaBola() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        a.doPainel(new ArbitroLocal().comando(Command.DIRECT_FREE_BLUE));

        aproximado("arbitro: falta nossa nao nos afasta da bola",
                a.estado(Cor.AZUL).afastamentoDaBola(), 0, 1e-9);
        aproximado("arbitro: falta deles nos afasta 0,5 m",
                a.estado(Cor.AMARELO).afastamentoDaBola(), 500, 1e-9);
    }

    /**
     * Para que lado atacamos. A visao nao diz, e sem isto nao da para escrever
     * estrategia nenhuma.
     */
    private static void ladoDoCampoSaiDoArbitro() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();
        gc.setAzulNoLadoPositivo(true);
        a.doPainel(gc.comando(Command.STOP));

        verdadeiro("arbitro: azul no lado positivo defende +x",
                a.estado(Cor.AZUL).defendemosLadoPositivo()
                        && a.estado(Cor.AZUL).nossoLado() == 1);
        verdadeiro("arbitro: e o amarelo defende -x",
                !a.estado(Cor.AMARELO).defendemosLadoPositivo()
                        && a.estado(Cor.AMARELO).nossoLado() == -1);

        gc.setAzulNoLadoPositivo(false);
        a.doPainel(gc.comando(Command.STOP));
        verdadeiro("arbitro: trocando de lado, o azul passa a defender -x",
                a.estado(Cor.AZUL).nossoLado() == -1);
    }

    private static void placarENomeVemDoArbitro() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();
        gc.setNomes("RoboFEI", "Adversario");
        gc.marcarGol(Cor.AZUL);
        gc.marcarGol(Cor.AZUL);
        gc.marcarGol(Cor.AMARELO);
        a.doPainel(gc.comando(Command.STOP));

        EstadoDeJogo azul = a.estado(Cor.AZUL);
        verdadeiro("arbitro: nome e placar do nosso lado",
                azul.nomeNosso().equals("RoboFEI") && azul.golsNossos() == 2);
        verdadeiro("arbitro: e do lado deles",
                azul.nomeDeles().equals("Adversario") && azul.golsDeles() == 1);

        EstadoDeJogo amarelo = a.estado(Cor.AMARELO);
        verdadeiro("arbitro: o placar inverte com a cor",
                amarelo.golsNossos() == 1 && amarelo.golsDeles() == 2);
    }

    /**
     * Um Game Controller na mesma rede nao pode atropelar o comando de teste, nem
     * o painel pode mexer no estado quando quem manda e a rede.
     */
    private static void modoLocalEModoRedeSeExcluem() {
        Arbitro a = new Arbitro();
        ArbitroLocal gc = new ArbitroLocal();

        a.setModoLocal(true);
        a.doPainel(gc.comando(Command.HALT));
        a.daRede(gc.comando(Command.FORCE_START));
        verdadeiro("arbitro: em modo local, pacote da rede e ignorado",
                a.estado(Cor.AZUL).acao() == Acao.HALT);

        a.setModoLocal(false);
        verdadeiro("arbitro: trocar de fonte descarta o estado anterior",
                a.estado(Cor.AZUL).semArbitro());

        a.doPainel(gc.comando(Command.HALT));
        verdadeiro("arbitro: em modo rede, o painel nao tem efeito",
                a.estado(Cor.AZUL).semArbitro());

        a.daRede(gc.comando(Command.FORCE_START));
        verdadeiro("arbitro: e o pacote da rede vale",
                a.estado(Cor.AZUL).bolaEmJogo());
    }

    /**
     * O caminho de rede do arbitro, ponta a ponta.
     *
     * <p>Publica um {@code Referee} de verdade no multicast e confere que o
     * receptor o entrega traduzido. Sem isto, o unico trecho nao exercitado seria
     * justamente o que so falha em campo, com o Game Controller ligado.
     */
    private static void arbitroChegaPelaRede() throws Exception {
        int porta = 11903;
        ConfigRede c = new ConfigRede(ConfigRede.VISAO_GRUPO, 11902,
                ConfigRede.ARBITRO_GRUPO, porta, "127.0.0.1", 11904, 11905);

        Arbitro arbitro = new Arbitro();
        try (ReceptorDeArbitro _ = new ReceptorDeArbitro(c, arbitro); // so precisa estar no ar
             MulticastSocket emissor = new MulticastSocket()) {

            byte[] dados = new ArbitroLocal().comando(Command.PREPARE_PENALTY_YELLOW).toByteArray();
            InetSocketAddress destino = new InetSocketAddress(
                    InetAddress.getByName(ConfigRede.ARBITRO_GRUPO), porta);

            // Multicast se perde; algumas tentativas evitam um teste instavel.
            EstadoDeJogo estado = EstadoDeJogo.SEM_ARBITRO;
            for (int i = 0; i < 20 && estado.semArbitro(); i++) {
                emissor.send(new DatagramPacket(dados, dados.length, destino));
                Thread.sleep(25);
                estado = arbitro.estado(Cor.AMARELO);
            }

            verdadeiro("arbitro: mensagem do GC chega pelo multicast", !estado.semArbitro());
            verdadeiro("arbitro: e chega traduzida (penalti nosso, jogando de amarelo)",
                    estado.acao() == Acao.PENALTY && estado.nosso());
        }
    }

    /** Na tela le-se o vocabulario do Game Controller, nao uma traducao. */
    private static void rotuloUsaOVocabularioDoProtocolo() {
        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();

        a.doPainel(gc.comando(Command.STOP));
        verdadeiro("rotulo: comando sem dono e so a acao",
                a.estado(Cor.AZUL).descricao().equals("STOP"));

        a.doPainel(gc.comando(Command.PREPARE_KICKOFF_BLUE));
        verdadeiro("rotulo: comando com dono leva a cor",
                a.estado(Cor.AZUL).descricao().equals("KICKOFF BLUE"));
        verdadeiro("rotulo: e a cor e a do comando, nao a nossa",
                a.estado(Cor.AMARELO).descricao().equals("KICKOFF BLUE"));

        a.doPainel(gc.comando(Command.BALL_PLACEMENT_YELLOW));
        verdadeiro("rotulo: BALL PLACEMENT YELLOW",
                a.estado(Cor.AZUL).descricao().equals("BALL PLACEMENT YELLOW"));
    }

    /** Trocar de porta com a janela aberta e o que o botao Configurar faz. */
    private static void reconfigurarTrocaDePorta() throws Exception {
        ConfigRede inicial = new ConfigRede("224.5.23.2", 11821,
                "224.5.23.1", 11891, "127.0.0.1", 11831, 11832);
        app.Cliente cliente = new app.Cliente(inicial, Cor.AZUL);
        cliente.conectar();

        ConfigRede nova = inicial.comPortaVisao(11822).comPortaAzul(11833);
        cliente.reconfigurar(nova, Cor.AZUL);

        verdadeiro("rede: reconfigurar troca a porta de escuta",
                cliente.getConfig().portaVisao() == 11822);
        verdadeiro("rede: e o destino dos comandos acompanha",
                cliente.destinoDeComandos().endsWith(":11833"));
        cliente.close();
    }

    /**
     * Porta ocupada so aparece no bind. Nesse caso a configuracao anterior tem de
     * voltar, em vez de deixar a estrategia surda por causa de um numero errado.
     */
    private static void reconfigurarRestauraSeOBindFalha() throws Exception {
        ConfigRede inicial = new ConfigRede("224.5.23.2", 11841,
                "224.5.23.1", 11892, "127.0.0.1", 11851, 11852);
        app.Cliente cliente = new app.Cliente(inicial, Cor.AZUL);
        cliente.conectar();

        try (DatagramSocket _ = new DatagramSocket(11842)) { // so ocupa a porta
            boolean lancou = false;
            try {
                cliente.reconfigurar(inicial.comPortaVisao(11842), Cor.AZUL);
            } catch (Exception e) {
                lancou = true;
            }
            verdadeiro("rede: bind em porta ocupada falha", lancou);
            verdadeiro("rede: e a configuracao anterior e restaurada",
                    cliente.getConfig().portaVisao() == 11841);
            verdadeiro("rede: com a escuta anterior de volta no ar", cliente.getFalha() == null);
        }
        cliente.close();
    }

    /** Um quadro montado como o simulador monta tem de virar Quadro com os robos certos. */
    private static void visaoViraQuadro() {
        Rastreador r = new Rastreador();
        Quadro q = null;

        for (int i = 0; i < 20; i++) {
            double t = i * DT;
            SSL_DetectionFrame.Builder f = SSL_DetectionFrame.newBuilder()
                    .setFrameNumber(i).setTCapture(t).setTSent(t).setCameraId(0);

            f.addBalls(SSL_DetectionBall.newBuilder()
                    .setConfidence(1).setX(500).setY(0).setZ(21.5f)
                    .setPixelX(0).setPixelY(0));
            f.addRobotsBlue(robo(3, 1000 * i * DT, 0, 0));
            f.addRobotsYellow(robo(7, -2000, 500, Math.PI / 2));

            q = r.incorporar(f.build());
        }

        verdadeiro("protocolo: quadro traz os dois robos", q.robos().size() == 2);
        EstadoRobo azul = q.robo(Cor.AZUL, 3);
        verdadeiro("protocolo: azul 3 existe", azul != null);
        aproximado("protocolo: velocidade reconstruida do azul 3",
                azul.rapidez(), 1000, 60);
        verdadeiro("protocolo: amarelo 7 na orientacao recebida",
                Math.abs(q.robo(Cor.AMARELO, 7).theta() - Math.PI / 2) < 0.05);
    }

    private static SSL_DetectionRobot.Builder robo(int id, double x, double y, double theta) {
        return SSL_DetectionRobot.newBuilder()
                .setConfidence(1).setRobotId(id)
                .setX((float) x).setY((float) y).setOrientation((float) theta)
                .setPixelX(0).setPixelY(0).setHeight(150);
    }

    /** Geometria e dado de rede, nao constante do programa. */
    private static void geometriaVemDaRede() {
        Geometria g = Geometria.de(SSL_GeometryFieldSize.newBuilder()
                .setFieldLength(12000).setFieldWidth(9000)
                .setGoalWidth(1800).setGoalDepth(180)
                .setBoundaryWidth(300)
                .build());
        verdadeiro("protocolo: le campo de Divisao A da geometria recebida",
                g.comprimento() == 12000 && g.divisao().equals("Divisao A"));
        aproximado("protocolo: campos ausentes caem no padrao",
                g.raioCirculoCentral(), Geometria.DIVISAO_B.raioCirculoCentral(), 0.001);
    }

    /**
     * O erro de mil vezes: dentro do programa e mm/s, no protocolo e m/s. Este
     * teste fala pelo socket de verdade, montando e lendo protobuf, que e o unico
     * jeito de provar o que sai no fio.
     */
    private static void comandoSaiEmMetrosPorSegundo() throws Exception {
        try (DatagramSocket espia = new DatagramSocket(0)) {
            espia.setSoTimeout(500);
            int porta = espia.getLocalPort();

            ConfigRede c = new ConfigRede(ConfigRede.VISAO_GRUPO, ConfigRede.VISAO_PORTA,
                    ConfigRede.ARBITRO_GRUPO, ConfigRede.ARBITRO_PORTA,
                    "127.0.0.1", porta, porta + 1);

            try (EmissorDeComandos e = new EmissorDeComandos(c, Cor.AZUL)) {
                e.enviar(Map.of(5, new Comando(1500, -500, 2.0, 6000,
                        Comando.ANGULO_CHIP_PADRAO, true)));
            }

            byte[] buffer = new byte[8192];
            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            espia.receive(p);
            RobotControl rc = RobotControl.parseFrom(java.util.Arrays.copyOf(p.getData(), p.getLength()));

            var cmd = rc.getRobotCommands(0);
            var mv = cmd.getMoveCommand().getLocalVelocity();

            verdadeiro("protocolo: id do robo preservado", cmd.getId() == 5);
            aproximado("protocolo: 1500 mm/s viram 1,5 m/s no fio", mv.getForward(), 1.5, 1e-4);
            aproximado("protocolo: -500 mm/s viram -0,5 m/s", mv.getLeft(), -0.5, 1e-4);
            aproximado("protocolo: rad/s nao muda de unidade", mv.getAngular(), 2.0, 1e-4);
            aproximado("protocolo: chute de 6000 mm/s vira 6 m/s", cmd.getKickSpeed(), 6.0, 1e-4);
            aproximado("protocolo: chip em radianos vira 45 graus", cmd.getKickAngle(), 45.0, 1e-3);
            verdadeiro("protocolo: dribbler booleano vira RPM", cmd.getDribblerSpeed() > 0);
        }
    }

    // ------------------------------------------------------------------ apoio

    private static void verdadeiro(String nome, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("  ok    " + nome);
        } else {
            falhas++;
            System.out.println("  FALHA " + nome);
        }
    }

    private static void aproximado(String nome, double obtido, double esperado, double tol) {
        total++;
        if (Math.abs(obtido - esperado) <= tol) {
            System.out.printf("  ok    %s%n", nome);
        } else {
            falhas++;
            System.out.printf("  FALHA %s  (obtido %.4f, esperado %.4f +- %.4f)%n",
                    nome, obtido, esperado, tol);
        }
    }
}
