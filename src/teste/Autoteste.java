package teste;

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
import proto.gc.SslGcRefereeMessage.Referee.Command;
import proto.sim.SslSimulationRobotControl.RobotControl;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionBall;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionFrame;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionRobot;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryFieldSize;
import rede.ConfigRede;
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
        rotuloUsaOVocabularioDoProtocolo();

        // --- protocolo ---
        reconfigurarTrocaDePorta();
        reconfigurarRestauraSeOBindFalha();
        visaoViraQuadro();
        geometriaVemDaRede();
        comandoSaiEmMetrosPorSegundo();

        System.out.printf("%n%d/%d verificacoes passaram%n", total - falhas, total);
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ Kalman

    /** Alvo em velocidade constante: o filtro tem de achar a velocidade certa. */
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
