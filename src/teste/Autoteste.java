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
import proto.sim.SslSimulationRobotControl.RobotControl;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionBall;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionFrame;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionRobot;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryFieldSize;
import rede.ConfigRede;
import rede.EmissorDeComandos;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
        chuteEhAbsorvidoEmPoucosQuadros();
        anguloAtravessaPiSemSalto();

        // --- sensor de bola ---
        sensorPegaBolaNaBoca();
        sensorIgnoraBolaNaLateral();
        sensorIgnoraBolaPorCima();
        sensorIgnoraBolaLongeDaBoca();

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
     * Chute: a bola sai de parada a 6 m/s dentro de um quadro. Nenhum modelo de
     * velocidade constante preve isso, entao o que se cobra e recuperacao rapida
     * -- poucos quadros ate a velocidade estimada bater com a real.
     */
    private static void chuteEhAbsorvidoEmPoucosQuadros() {
        Trilha t = new Trilha(Ajuste.bola(), false);
        double tempo = 0;
        for (int i = 0; i < 30; i++, tempo += DT) {
            t.prever(tempo);
            t.corrigir(Vec2.ZERO, Double.NaN, tempo);
        }

        double v = 6000; // mm/s
        double inicio = tempo;
        for (int i = 1; i <= 8; i++) {
            tempo += DT;
            t.prever(tempo);
            t.corrigir(new Vec2(v * (tempo - inicio), 0), Double.NaN, tempo);
        }

        double erro = Math.abs(t.velocidade().x() - v);
        verdadeiro(String.format("kalman: absorve chute de 6 m/s em 8 quadros (erro %.0f mm/s)", erro),
                erro < 900);
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

    /** Trocar de porta com a janela aberta e o que o botao Configurar faz. */
    private static void reconfigurarTrocaDePorta() throws Exception {
        ConfigRede inicial = new ConfigRede("224.5.23.2", 11821, "127.0.0.1", 11831, 11832);
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
        ConfigRede inicial = new ConfigRede("224.5.23.2", 11841, "127.0.0.1", 11851, 11852);
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
