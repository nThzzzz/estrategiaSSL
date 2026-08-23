package view;

import core.Geo;
import ajuste.Parametro;
import core.Caixa;
import core.Vec2;
import jogo.EstadoDeJogo;
import model.Cor;
import model.Geometria;
import model.Robo;
import mundo.EstadoBola;
import mundo.EstadoRobo;
import mundo.Quadro;
import percepcao.SensorDeBola;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Desenha o campo a partir do {@link Quadro} que a visao entregou.
 *
 * <p>Nao conhece rede nem estrategia: recebe um fornecedor de quadro e desenha.
 * Como no simulador, cada {@code paintComponent} tira UM retrato e usa do inicio
 * ao fim -- ler o fornecedor varias vezes no meio do desenho costuraria
 * instantes diferentes, e aqui isso seria pior ainda, porque o quadro chega de
 * outra thread a qualquer momento.
 *
 * <p>A diferenca de fundo em relacao a tela do simulador: la o desenho e do
 * mundo real da simulacao; aqui e do que a estrategia ACHA que esta vendo,
 * depois de filtrar. Vetor de velocidade e area do sensor sao inferencias, nao
 * medidas -- e e util que sejam desenhadas, porque e assim que se descobre que o
 * filtro esta mentindo.
 */
public final class Campo extends JPanel {

    /** Quantos segundos de velocidade o vetor representa. */
    private static final double SEGUNDOS_DO_VETOR = 0.35;

    /**
     * Teto da previsao ate o instante do desenho.
     *
     * <p>Se a visao parar, o desenho nao pode sair correndo campo afora: passado
     * este tempo sem quadro novo, a cena congela onde estava. E preferivel ver a
     * cena parar do que ve-la inventar.
     */
    private static final double MAX_PREVISAO = 0.08; // s

    /** Quanto a frente o fantasma mostra o robo, em segundos. */
    public static final double HORIZONTE_FANTASMA = 1.0;

    /**
     * Abaixo desta velocidade o robo nao ganha fantasma, em mm/s.
     *
     * <p>Sem o corte, robo parado ganharia um fantasma em cima de si mesmo e o
     * campo viraria doze circulos tracejados sobrepostos aos robos, dizendo nada.
     */
    private static final double VEL_MINIMA_FANTASMA = 150;

    /** Abaixo disto a bola nao esta indo a lugar nenhum, e a mira nao aparece. */
    private static final double VEL_MINIMA_DE_MIRA = 200;

    /** Traco da zona proibida: 120 mm de risco, 90 de vao. */
    private static final BasicStroke TRACO_DA_ZONA = new BasicStroke(
            22f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[]{120f, 90f}, 0f);

    /** Traco da zona do goleiro, mais fino: ela e permissao, nao proibicao. */
    private static final BasicStroke TRACO_DO_GOLEIRO = new BasicStroke(
            12f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[]{60f, 60f}, 0f);

    private final Supplier<Quadro> fonte;

    /**
     * Contexto de defesa: qual lado e o nosso e qual a folga sobre a area.
     *
     * <p>Vem de fora porque nao esta no {@link Quadro}: qual metade defendemos e
     * do arbitro, e a folga e escolha nossa. Sem contexto ligado o retangulo
     * simplesmente nao aparece, e o campo continua desenhavel sozinho.
     */
    private Supplier<EstadoDeJogo> fonteDeJogo;
    private DoubleSupplier margemDaArea;

    /**
     * De que cor jogamos.
     *
     * <p>O {@link Quadro} nao carrega isso -- ele so tem azuis e amarelos -- e sem
     * saber a linha de chute apontaria para o gol errado quando quem esta com a
     * bola e o adversario.
     */
    private Supplier<Cor> nossaCor = () -> Cor.AZUL;

    private double zoom = 1.0;
    private double panX = 0, panY = 0;
    private int mouseTelaX = -1, mouseTelaY = -1;

    private Cor corSelecionada;
    private int idSelecionado = -1;

    private boolean mostrarTextura = true;
    private boolean mostrarAreaProibida = true;
    private boolean mostrarLinhas = true;
    private boolean mostrarVetores = true;
    private boolean mostrarSensor = true;
    private boolean mostrarIncerteza = true;
    private boolean preverAteODesenho = true;
    private boolean mostrarFantasma = true;

    public Campo(Supplier<Quadro> fonte) {
        this.fonte = fonte;
        setPreferredSize(new Dimension(980, 720));
        setBackground(Paleta.FUNDO);
    }

    // ------------------------------------------------------------ interacao

    public Quadro quadro() { return fonte.get(); }

    /**
     * Maior peso que UM evento pode ter: um entalhe.
     *
     * <p>Uma roda de mouse com entalhe manda {@code preciseWheelRotation == 1}
     * por clique, e um clique por gesto. O trackpad do mac nao: ele manda uma
     * rajada -- medidos 327 eventos em 4 s, ~80 por segundo -- e o peso de cada
     * um varia de 0,006 a 4,7. Sem teto, aquele 4,7 virava {@code 1.1^-4,7}, um
     * salto de 58% da escala num quadro so, no meio de centenas de eventos que
     * nao faziam nada visivel. Era esse contraste, e nao a sensibilidade media,
     * que fazia o zoom parecer instavel: quase parado e de repente um pulo.
     *
     * <p>Cortar em um entalhe da o limite pela coisa certa -- nenhum evento vale
     * mais do que um clique de roda valeria -- e por isso nao estraga a roda de
     * mouse, onde o valor ja e 1 e o corte nunca age.
     */
    private static final double PASSO_MAX = 1.0;

    /** Quanto a escala muda por entalhe. O mesmo de sempre para roda de mouse. */
    private static final double POR_PASSO = 1.1;

    /**
     * Traduz um evento de roda ou trackpad no fator de zoom.
     *
     * <p>Usa {@code getPreciseWheelRotation}, e nao {@code getWheelRotation}: o
     * inteiro devolve 0 para quase todo evento de trackpad -- medida na mesma
     * rajada de 327 eventos: soma 3 -- o que deixava o zoom morto entre passos
     * secos. O fracionario com teto responde ao gesto inteiro sem os saltos.
     */
    public static double fatorDeZoom(MouseWheelEvent e) {
        double passos = Math.max(-PASSO_MAX, Math.min(e.getPreciseWheelRotation(), PASSO_MAX));
        return Math.pow(POR_PASSO, -passos);
    }

    /** Batentes do zoom. Fora deles o campo vira um borrao ou some da tela. */
    private static final double ZOOM_MIN = 0.25;
    private static final double ZOOM_MAX = 8.0;

    /**
     * Amplia ou reduz mantendo fixo o ponto do mundo que esta sob o cursor.
     *
     * <p>Antes isto so multiplicava o zoom, e a transformada ancora em
     * {@code getWidth()/2 + panX} -- o CENTRO do painel. Quem estivesse olhando
     * um canto do campo via o canto fugir da tela ao aproximar, e tinha de
     * arrastar de volta a cada passo. Ancorar no cursor e o que faz o gesto
     * parecer que aproxima a cena, em vez de mover a camera por conta propria.
     *
     * <p>A correcao do pan sai do zoom REALMENTE aplicado, e nao do pedido: no
     * batente o pedido nao acontece, e usar o pedido faria a imagem deslizar sob
     * o cursor com o zoom ja parado -- que era o pior sintoma, porque acontece
     * justamente quando se insiste no gesto.
     */
    public void aplicarZoom(double fator, int telaX, int telaY) {
        double antes = zoom;
        zoom = Math.max(ZOOM_MIN, Math.min(zoom * fator, ZOOM_MAX));

        double k = zoom / antes;
        if (k == 1.0) return;
        panX = (telaX - getWidth() / 2.0) * (1 - k) + panX * k;
        panY = (telaY - getHeight() / 2.0) * (1 - k) + panY * k;
    }
    public void deslocar(double dx, double dy) { panX += dx; panY += dy; }
    public void enquadrar() { zoom = 1.0; panX = 0; panY = 0; }
    public void setMouseTela(int x, int y) { mouseTelaX = x; mouseTelaY = y; }

    /** Liga o campo ao estado de jogo e a folga, para desenhar a area proibida. */
    public void setContextoDeDefesa(Supplier<EstadoDeJogo> jogo, DoubleSupplier margem) {
        this.fonteDeJogo = jogo;
        this.margemDaArea = margem;
    }

    /** De que cor jogamos, para a linha de chute saber qual gol e o alvo. */
    public void setNossaCor(Supplier<Cor> cor) { this.nossaCor = cor; }

    public void setMostrarTextura(boolean b) { mostrarTextura = b; }
    public void setMostrarAreaProibida(boolean b) { mostrarAreaProibida = b; }
    public void setMostrarLinhas(boolean b) { mostrarLinhas = b; }
    public void setMostrarVetores(boolean b) { mostrarVetores = b; }
    public void setMostrarSensor(boolean b)  { mostrarSensor = b; }
    public void setMostrarIncerteza(boolean b) { mostrarIncerteza = b; }
    public void setPreverAteODesenho(boolean b) { preverAteODesenho = b; }
    public void setMostrarFantasma(boolean b) { mostrarFantasma = b; }

    public boolean isMostrarTextura()      { return mostrarTextura; }
    public boolean isMostrarAreaProibida() { return mostrarAreaProibida; }
    public boolean isMostrarLinhas()       { return mostrarLinhas; }
    public boolean isMostrarVetores()    { return mostrarVetores; }
    public boolean isMostrarSensor()     { return mostrarSensor; }
    public boolean isMostrarIncerteza()  { return mostrarIncerteza; }
    public boolean isPreverAteODesenho() { return preverAteODesenho; }
    public boolean isMostrarFantasma()   { return mostrarFantasma; }

    /**
     * Selecao guardada por identidade (cor + id), nao por referencia ao record.
     * O quadro e recriado a cada pacote de visao, entao guardar o objeto faria a
     * selecao se perder no quadro seguinte.
     */
    public void selecionar(Cor cor, int id) { this.corSelecionada = cor; this.idSelecionado = id; }
    public void limparSelecao() { selecionar(null, -1); }
    public Cor getCorSelecionada() { return corSelecionada; }
    public int getIdSelecionado()  { return idSelecionado; }

    private static Vec2 prever(Vec2 posicao, Vec2 velocidade, double dt) {
        return dt <= 0 ? posicao : posicao.mais(velocidade.escala(dt));
    }

    /**
     * Robo mais proximo do ponto de mundo, dentro do proprio raio.
     *
     * <p>Usa a mesma previsao do desenho: clicar tem de acertar o robo que esta
     * na tela, nao o lugar onde ele estava quando o ultimo pacote chegou.
     */
    public EstadoRobo roboEm(Vec2 ponto) {
        EstadoRobo achado = null;
        double melhor = Robo.RAIO * 1.6;
        Quadro q = quadro();
        double avanco = avanco(q);
        for (EstadoRobo r : q.robos()) {
            double d = prever(r.posicao(), r.velocidade(), avanco).distancia(ponto);
            if (d < melhor) { melhor = d; achado = r; }
        }
        return achado;
    }

    public Vec2 telaParaMundo(int x, int y) {
        try {
            Point2D p = transform(quadro().geometria())
                    .inverseTransform(new Point2D.Double(x, y), null);
            return new Vec2(p.getX(), p.getY());
        } catch (NoninvertibleTransformException e) {
            return Vec2.ZERO; // so com escala zero, que nao acontece
        }
    }

    /**
     * Quanto tempo passou desde que o quadro desenhado chegou.
     *
     * <p>A tela repinta a 60 Hz e a visao chega a 60 Hz, mas as duas cadencias nao
     * sao a mesma nem estao em fase: sem isto, alguns repaints mostram o mesmo
     * quadro duas vezes e outros pulam um, e o movimento treme mesmo com o filtro
     * perfeito. Na janela do simulador o problema nao existe, porque la a fisica
     * e integrada no mesmo instante em que se pinta.
     *
     * <p>A cura e usar o que o Kalman ja fornece: cada objeto tem velocidade
     * estimada, entao desenha-lo em {@code p + v*dt} coloca a cena no instante do
     * desenho, e nao no instante do ultimo pacote. E a mesma previsao que o filtro
     * faz internamente, so que levada ate a tela.
     */
    private double avanco(Quadro q) {
        if (!preverAteODesenho) return 0;
        double dt = (System.nanoTime() - q.recebidoEm()) / 1e9;
        return Math.max(0, Math.min(dt, MAX_PREVISAO));
    }

    private AffineTransform transform(Geometria g) {
        double escala = getWidth() <= 0 ? 0.1
                : Math.min(getWidth() / (g.limiteParedeX() * 2),
                           getHeight() / (g.limiteParedeY() * 2)) * 0.98 * zoom;
        AffineTransform at = new AffineTransform();
        at.translate(getWidth() / 2.0 + panX, getHeight() / 2.0 + panY);
        at.scale(escala, -escala); // inverte Y: mundo cartesiano -> tela
        return at;
    }

    // ------------------------------------------------------------- desenho

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Quadro q = quadro();
        double avanco = avanco(q);
        AffineTransform at = transform(q.geometria());

        Graphics2D gm = (Graphics2D) g2.create();
        gm.transform(at);
        desenharCampo(gm, q.geometria());
        // Fantasmas primeiro, para os robos de verdade ficarem por cima.
        if (mostrarFantasma) for (EstadoRobo r : q.robos()) desenharFantasma(gm, r, avanco);
        for (EstadoRobo r : q.robos()) desenharRobo(gm, r, q.geometria(), avanco);
        desenharBola(gm, q.bola(), q.geometria(), avanco);
        if (mostrarLinhas) desenharLinhas(gm, q, avanco);
        gm.dispose();

        desenharIdentificadores(g2, at, q, avanco);
        desenharCursor(g2);
        g2.dispose();
    }

    private void desenharCampo(Graphics2D g, Geometria geo) {
        double meioX = geo.meioComprimento();
        double meioY = geo.meiaLargura();

        g.setColor(Paleta.ESCAPE);
        g.fill(new Rectangle2D.Double(-geo.limiteParedeX(), -geo.limiteParedeY(),
                geo.limiteParedeX() * 2, geo.limiteParedeY() * 2));

        desenharGramado(g, geo);

        g.setColor(Paleta.LINHA);
        g.setStroke(new BasicStroke((float) geo.espessuraLinha(),
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Rectangle2D.Double(-meioX, -meioY, geo.comprimento(), geo.largura()));
        g.draw(new Line2D.Double(0, -meioY, 0, meioY));

        double rc = geo.raioCirculoCentral();
        g.draw(new Ellipse2D.Double(-rc, -rc, rc * 2, rc * 2));

        double ad = geo.areaDefesaProfundidade();
        double al = geo.areaDefesaLargura();
        g.draw(new Rectangle2D.Double(-meioX, -al / 2, ad, al));
        g.draw(new Rectangle2D.Double(meioX - ad, -al / 2, ad, al));

        desenharGol(g, geo, -1, Paleta.AZUL);
        desenharGol(g, geo, 1, Paleta.AMARELO);
        desenharAreaProibida(g, geo);
    }

    /**
     * O gramado: verde chapado ou faixas de corte, conforme a exibicao.
     *
     * <p>As faixas nao sao enfeite. O campo da estrategia e uma tela de leitura --
     * e nela que se acompanha se o filtro esta mentindo -- e um retangulo verde
     * uniforme nao da referencia nenhuma de POSICAO. Com o corte marcado, um robo
     * que andou meia faixa e um que andou tres se distinguem de relance, sem ler
     * numero.
     *
     * <p>A largura da faixa e a PROFUNDIDADE DA AREA DE DEFESA, e as faixas sao
     * contadas a partir de cada linha de fundo para dentro. Duas consequencias, e
     * as duas sao o motivo da escolha: a primeira faixa de cada lado coincide
     * exatamente com a area, que e a regua que mais interessa em campo; e o
     * desenho fica ESPELHADO, igual visto de qualquer um dos dois gols, como num
     * campo cortado de verdade. Antes eram doze faixas de tamanho arbitrario,
     * iguais nos dois lados por acaso e alinhadas com nada.
     *
     * <p>A faixa do meio e o que sobra depois das inteiras, e por isso pode ser
     * mais larga ou mais estreita que as outras: em Divisao B sobram 1000 mm e ela
     * fica igual as demais, em Divisao A sobram 1200. Ela e simetrica em torno da
     * linha central nos dois casos, que e o que sustenta o espelho.
     *
     * <p>A base e pintada inteira de escuro e so as faixas claras vao por cima.
     * Duas pinturas lado a lado deixariam um fio de costura entre elas pelo
     * antialias; assim, o que aparece na emenda e o proprio tom escuro.
     */
    private void desenharGramado(Graphics2D g, Geometria geo) {
        double meioX = geo.meioComprimento();
        double meioY = geo.meiaLargura();
        double faixa = geo.areaDefesaProfundidade();

        if (!mostrarTextura || faixa <= 0) {
            g.setColor(Paleta.GRAMA);
            g.fill(new Rectangle2D.Double(-meioX, -meioY, geo.comprimento(), geo.largura()));
            return;
        }

        g.setColor(Paleta.GRAMA_ESCURA);
        g.fill(new Rectangle2D.Double(-meioX, -meioY, geo.comprimento(), geo.largura()));

        g.setColor(Paleta.GRAMA_CLARA);
        int inteiras = (int) (meioX / faixa);
        for (int i = 0; i < inteiras; i += 2) {
            g.fill(new Rectangle2D.Double(meioX - (i + 1) * faixa, -meioY, faixa, geo.largura()));
            g.fill(new Rectangle2D.Double(-meioX + i * faixa, -meioY, faixa, geo.largura()));
        }

        // A faixa central so e clara quando o indice dela e par, como as outras.
        double sobra = meioX - inteiras * faixa;
        if (sobra > 0 && inteiras % 2 == 0) {
            g.fill(new Rectangle2D.Double(-sobra, -meioY, sobra * 2, geo.largura()));
        }
    }

    /**
     * O retangulo onde so o goleiro pode entrar.
     *
     * <p>E a area de defesa que a visao informou mais a folga de seguranca que
     * escolhemos, e e exatamente a mesma conta que o {@code Executor} usa para
     * cortar comando -- as duas saem de {@code Geometria.areaDefesa}. Ver na tela
     * uma borda diferente da que esta sendo obedecida seria pior que nao ver
     * borda nenhuma.
     *
     * <p>Aparece so do NOSSO lado. A area deles tambem e proibida pela regra, mas
     * a estrategia ainda nao a trata, e desenhar uma restricao que nao esta em
     * vigor prometeria o que o codigo nao cumpre.
     */
    private void desenharAreaProibida(Graphics2D g, Geometria geo) {
        if (!mostrarAreaProibida || fonteDeJogo == null || margemDaArea == null) return;
        int lado = fonteDeJogo.get().nossoLado();

        // Vermelha: robo de linha nao entra. Vai ate a parede, porque o corredor
        // atras do gol nao tem jogada nenhuma e um robo que entra la errou o
        // caminho -- e volta atravessando a area.
        Caixa proibida = geo.zonaProibida(lado, margemDaArea.getAsDouble(),
                Parametro.ZONA_PROFUNDIDADE.valor(), Parametro.ZONA_LARGURA.valor());
        g.setColor(Paleta.ZONA_PROIBIDA_FRACA);
        g.fill(retangulo(proibida));
        g.setColor(Paleta.ZONA_PROIBIDA);
        g.setStroke(TRACO_DA_ZONA);
        g.draw(retangulo(proibida));

        // Verde, por dentro: onde o goleiro PODE ficar. Ela passa um pouco da
        // borda da area, e e essa sobra que deixa ele ficar em cima da linha em
        // vez de so atras dela.
        Caixa goleiro = geo.zonaDoGoleiro(lado, Robo.RAIO);
        g.setColor(Paleta.ZONA_DO_GOLEIRO);
        g.setStroke(TRACO_DO_GOLEIRO);
        g.draw(retangulo(goleiro));
    }

    /**
     * As duas linhas que dizem para onde a bola vai.
     *
     * <p>A de MIRA sai da bola e termina onde a trajetoria dela cruza a linha do
     * nosso gol -- so aparece quando a bola realmente vem para ca, porque e
     * semirreta contra segmento e nao reta contra reta. Vermelha e continua: as
     * zonas sao regra e vao tracejadas, esta e previsao do que esta acontecendo
     * agora.
     *
     * <p>A de CHUTE sai de quem esta com a bola e vai ao gol adversario, verde
     * quando ninguem corta e laranja quando alguem corta. E a mesma conta que uma
     * play usaria para decidir chutar; ve-la na tela e o que permite discordar
     * dela olhando.
     *
     * <p>As duas usam a posicao PREVISTA, como o resto do desenho, senao ficariam
     * atrasadas em relacao aos robos ao lado delas.
     */
    private void desenharLinhas(Graphics2D g, Quadro q, double avanco) {
        if (fonteDeJogo == null) return;
        Geometria geo = q.geometria();
        int nosso = fonteDeJogo.get().nossoLado();

        desenharMiraNoGol(g, q, geo, nosso, avanco);
        desenharLinhaDeChute(g, q, geo, nosso, avanco);
    }

    private void desenharMiraNoGol(Graphics2D g, Quadro q, Geometria geo,
                                   int nosso, double avanco) {
        EstadoBola b = q.bola();
        if (b.perdida() || b.rapidez() < VEL_MINIMA_DE_MIRA) return;

        double x = nosso * geo.meioComprimento();
        double meia = geo.golLargura() / 2.0;
        Vec2 de = prever(b.posicao(), b.velocidade(), avanco);
        Vec2 alvo = Geo.getVec2SemirretaComSegmento(de, b.velocidade(),
                new Vec2(x, -meia), new Vec2(x, meia));
        if (alvo == null) return;

        g.setColor(Paleta.LINHA_DE_MIRA);
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(de.x(), de.y(), alvo.x(), alvo.y()));
        double r = 45;
        g.fill(new Ellipse2D.Double(alvo.x() - r, alvo.y() - r, r * 2, r * 2));
    }

    private void desenharLinhaDeChute(Graphics2D g, Quadro q, Geometria geo,
                                      int nosso, double avanco) {
        EstadoRobo dono = null;
        for (EstadoRobo r : q.robos()) if (r.bolaNoSensor()) { dono = r; break; }
        if (dono == null) return;

        Vec2 de = prever(dono.posicao(), dono.velocidade(), avanco);
        // O gol atacado por quem esta com a bola, e nao sempre o deles: com o
        // adversario na posse a linha que interessa e a que ameaca a gente.
        int ladoDoAlvo = dono.cor() == nossaCor.get() ? -nosso : nosso;
        Vec2 alvo = geo.centroGol(ladoDoAlvo);

        boolean livre = true;
        for (EstadoRobo r : q.robos()) {
            if (r == dono) continue;
            if (Geo.bSegmentoCortaCirculo(de, alvo, prever(r.posicao(), r.velocidade(), avanco),
                    Robo.RAIO + geo.raioBola())) { livre = false; break; }
        }

        g.setColor(livre ? Paleta.LINHA_LIVRE : Paleta.LINHA_BLOQUEADA);
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{90f, 70f}, 0f));
        g.draw(new Line2D.Double(de.x(), de.y(), alvo.x(), alvo.y()));
    }

    private static Rectangle2D.Double retangulo(Caixa c) {
        return new Rectangle2D.Double(c.xMin(), c.yMin(), c.extensaoX(), c.extensaoY());
    }

    /**
     * Um gol, ate onde a visao permite desenhar.
     *
     * <p>Antes era um retangulo chapado atras da linha de fundo, o que fazia o gol
     * parecer macico. Ele nao e: e uma estrutura aberta pela boca, e a bola que
     * entra fica dentro dela -- no simuladorSSL isso agora e fisica de verdade, e
     * uma bola parada dentro do gol ficava escondida atras do bloco chapado.
     *
     * <p>O contorno e uma LINHA, e nao uma parede com espessura, e isso e
     * deliberado. {@code SSL_GeometryFieldSize} manda largura e profundidade do
     * gol, e nada mais: espessura de parede nao existe no protocolo. Desenha-la
     * com os 20 mm do regulamento seria chumbar no cliente um numero que nao veio
     * da rede, o mesmo erro de chumbar 9000 x 6000 e quebrar em Divisao A. A
     * espessura usada aqui e a das outras linhas do campo, e essa vem no pacote.
     */
    private void desenharGol(Graphics2D g, Geometria geo, int lado, Color cor) {
        double meiaBoca = geo.golLargura() / 2;
        double boca = lado * geo.meioComprimento();
        double fundo = lado * (geo.meioComprimento() + geo.golProfundidade());

        g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 45));
        g.fill(new Rectangle2D.Double(Math.min(boca, fundo), -meiaBoca,
                geo.golProfundidade(), meiaBoca * 2));

        Path2D.Double contorno = new Path2D.Double();
        contorno.moveTo(boca, meiaBoca);
        contorno.lineTo(fundo, meiaBoca);
        contorno.lineTo(fundo, -meiaBoca);
        contorno.lineTo(boca, -meiaBoca);

        g.setColor(cor);
        g.setStroke(new BasicStroke((float) geo.espessuraLinha(),
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(contorno);
    }

    /**
     * Onde o robo estaria daqui a um segundo, mantido o rumo, e a reta ate la.
     *
     * <p>E o vetor de rumo de um radar de navio, com a mesma simplificacao: linha
     * reta na velocidade atual, ignorando que ele pode estar virando ou freando.
     * A utilidade vem justamente disso. Quando o fantasma nao bate com o que o
     * robo faz, o que se esta vendo e ele mudando de ideia -- e isso e
     * informacao, nao erro de desenho.
     *
     * <p>O avanco do repaint entra nos dois, robo e fantasma, senao a distancia
     * entre os dois piscaria a cada quadro de visao.
     */
    private void desenharFantasma(Graphics2D g, EstadoRobo r, double avanco) {
        if (r.rapidez() < VEL_MINIMA_FANTASMA) return;

        Vec2 agora = prever(r.posicao(), r.velocidade(), avanco);
        Vec2 futuro = prever(r.posicao(), r.velocidade(), avanco + HORIZONTE_FANTASMA);
        double theta = r.theta() + r.omega() * (avanco + HORIZONTE_FANTASMA);

        Color cor = Paleta.de(r.cor());
        // Opaco o bastante para ser lido de relance no zoom do campo inteiro, onde
        // um robo tem uns dez pixels. Mais fraco que isso vira sujeira na tela.
        Color fraca = new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 175);

        g.setColor(fraca);
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{45f, 30f}, 0f));
        g.draw(new Line2D.Double(agora.x(), agora.y(), futuro.x(), futuro.y()));

        AffineTransform anterior = g.getTransform();
        g.translate(futuro.x(), futuro.y());
        g.rotate(theta);

        g.setStroke(new BasicStroke(11f));
        g.draw(new Ellipse2D.Double(-Robo.RAIO, -Robo.RAIO, Robo.RAIO * 2, Robo.RAIO * 2));

        // A mesma cunha do robo, para o fantasma dizer tambem PARA ONDE ele estara
        // olhando -- posicao sem orientacao nao chega para julgar uma jogada.
        Path2D cunha = new Path2D.Double();
        cunha.moveTo(Robo.RAIO * 0.40, -Robo.MEIA_BOCA * 0.55);
        cunha.lineTo(Robo.RAIO * 0.40, Robo.MEIA_BOCA * 0.55);
        cunha.lineTo(Robo.DIST_FACE_FRONTAL - 3, 0);
        cunha.closePath();
        g.fill(cunha);

        g.setTransform(anterior);
    }

    /**
     * Um robo, com a area do sensor de bola desenhada sobre a boca do dribbler.
     *
     * <p>A area desenhada e EXATAMENTE a regiao que {@link SensorDeBola} testa:
     * a faixa a frente da face plana, com a largura util do rolete. Desenhar a
     * propria condicao, em vez de um icone qualquer aceso ao lado do robo, e o
     * que permite ver por que o sensor disparou ou nao -- uma bola que passa
     * raspando fica visivelmente fora da faixa.
     */
    private void desenharRobo(Graphics2D g, EstadoRobo r, Geometria geo, double avanco) {
        Vec2 pos = prever(r.posicao(), r.velocidade(), avanco);
        AffineTransform anterior = g.getTransform();
        g.translate(pos.x(), pos.y());
        g.rotate(r.theta() + r.omega() * avanco);

        if (mostrarSensor) desenharAreaDoSensor(g, r, geo);
        if (mostrarIncerteza) desenharIncerteza(g, r.incerteza(), r.idade());

        // Capa circular truncada pela face plana do dribbler.
        Area corpo = new Area(new Ellipse2D.Double(-Robo.RAIO, -Robo.RAIO, Robo.RAIO * 2, Robo.RAIO * 2));
        corpo.intersect(new Area(new Rectangle2D.Double(
                -Robo.RAIO, -Robo.RAIO, Robo.RAIO + Robo.DIST_FACE_FRONTAL, Robo.RAIO * 2)));

        // Robo nao visto ha alguns quadros continua desenhado, mas apagado: some
        // da visao aos poucos, em vez de piscar a cada oclusao.
        java.awt.Composite composto = g.getComposite();
        if (r.fantasma()) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.35f));
        }

        g.setColor(Paleta.CORPO);
        g.fill(corpo);

        if (r.cor() == corSelecionada && r.id() == idSelecionado) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(9f));
            g.draw(new Ellipse2D.Double(-Robo.RAIO - 14, -Robo.RAIO - 14,
                    (Robo.RAIO + 14) * 2, (Robo.RAIO + 14) * 2));
        }

        g.setColor(Paleta.de(r.cor()));
        g.fill(new Ellipse2D.Double(-26, -26, 52, 52));

        // Cunha da frente: diz para onde o robo olha sem depender do numero.
        Path2D cunha = new Path2D.Double();
        cunha.moveTo(Robo.RAIO * 0.40, -Robo.MEIA_BOCA * 0.55);
        cunha.lineTo(Robo.RAIO * 0.40, Robo.MEIA_BOCA * 0.55);
        cunha.lineTo(Robo.DIST_FACE_FRONTAL - 3, 0); // apice a FRENTE da base
        cunha.closePath();
        g.setColor(new Color(255, 255, 255, 150));
        g.fill(cunha);

        g.setComposite(composto);
        g.setTransform(anterior);

        if (mostrarVetores && r.rapidez() > 40) desenharVetor(g, pos, r.velocidade());
    }

    /**
     * Anel de um desvio padrao da posicao estimada, direto da covariancia do Kalman.
     *
     * <p>Enquanto a visao enxerga o objeto o anel fica dentro do proprio robo e
     * some do desenho, que e o comportamento desejado: incerteza pequena nao
     * merece tinta. Quando o robo some atras de outro, o anel cresce a olhos
     * vistos e diz que aquela posicao passou a ser extrapolacao, nao medida.
     */
    private void desenharIncerteza(Graphics2D g, double incerteza, double idade) {
        if (incerteza < Robo.RAIO * 0.5) return;

        double raio = Math.min(incerteza, 1200);
        int alfa = (int) Math.min(150, 40 + idade * 200);
        g.setColor(new Color(255, 200, 90, alfa));
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{26f, 22f}, 0f));
        g.draw(new Ellipse2D.Double(-raio, -raio, raio * 2, raio * 2));
    }

    /** A faixa a frente da boca; acesa quando o sensor esta cortado. */
    private void desenharAreaDoSensor(Graphics2D g, EstadoRobo r, Geometria geo) {
        double profundidade = geo.raioBola() + Parametro.TOLERANCIA_DO_SENSOR.valor();
        double meiaLargura = Robo.MEIA_BOCA + geo.raioBola() * 0.5;

        RoundRectangle2D faixa = new RoundRectangle2D.Double(
                Robo.DIST_FACE_FRONTAL, -meiaLargura, profundidade, meiaLargura * 2, 14, 14);

        if (r.bolaNoSensor()) {
            // Halo por fora e preenchimento por dentro: o halo e o que faz o
            // robo com a bola saltar aos olhos num campo com doze deles.
            g.setColor(new Color(120, 255, 120, 70));
            g.setStroke(new BasicStroke(26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(faixa);
            g.setColor(new Color(120, 255, 120, 190));
            g.fill(faixa);
            g.setColor(Paleta.SENSOR);
            g.setStroke(new BasicStroke(6f));
            g.draw(faixa);
        } else {
            g.setColor(Paleta.SENSOR_FRACO);
            g.setStroke(new BasicStroke(4f));
            g.draw(faixa);
        }
    }

    private void desenharBola(Graphics2D g, EstadoBola bola, Geometria geo, double avanco) {
        if (bola == EstadoBola.AUSENTE) return;

        Vec2 pos = prever(bola.posicao(), bola.velocidade(), avanco);
        double raio = geo.raioBola();
        double x = pos.x();
        double y = pos.y();
        boolean noAr = bola.z() > raio * 2.5;

        if (noAr) {
            // Sombra no chao e bola deslocada para cima: mesma convencao da tela
            // do simulador, para as duas janelas contarem a mesma historia.
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new Ellipse2D.Double(x - raio, y - raio, raio * 2, raio * 2));
        }

        double alturaDesenho = y + (noAr ? bola.z() * 0.35 : 0);
        double raioDesenho = raio * (noAr ? 1 + bola.z() / 300.0 : 1);

        g.setColor(noAr ? Paleta.BOLA_ALTA : Paleta.BOLA);
        g.fill(new Ellipse2D.Double(x - raioDesenho, alturaDesenho - raioDesenho,
                raioDesenho * 2, raioDesenho * 2));
        g.setColor(new Color(0, 0, 0, 190));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(x - raioDesenho, alturaDesenho - raioDesenho,
                raioDesenho * 2, raioDesenho * 2));

        if (mostrarIncerteza && bola.incerteza() > raio * 2) {
            double inc = Math.min(bola.incerteza(), 1200);
            g.setColor(new Color(255, 200, 90, (int) Math.min(150, 40 + bola.idade() * 200)));
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{26f, 22f}, 0f));
            g.draw(new Ellipse2D.Double(x - inc, y - inc, inc * 2, inc * 2));
        }

        if (mostrarVetores && bola.rapidez() > 60) desenharVetor(g, pos, bola.velocidade());
    }

    /** Seta de velocidade, em espaco de mundo: ela representa mm/s vezes tempo. */
    private void desenharVetor(Graphics2D g, Vec2 origem, Vec2 velocidade) {
        Vec2 ponta = origem.mais(velocidade.escala(SEGUNDOS_DO_VETOR));
        g.setColor(new Color(255, 255, 255, 160));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(origem.x(), origem.y(), ponta.x(), ponta.y()));

        double ang = velocidade.angulo();
        double asa = 55;
        for (int lado = -1; lado <= 1; lado += 2) {
            Vec2 p = ponta.mais(Vec2.dePolar(asa, ang + Math.PI - lado * 0.5));
            g.draw(new Line2D.Double(ponta.x(), ponta.y(), p.x(), p.y()));
        }
    }

    /** Numeros em espaco de tela, para nao herdarem escala nem o espelho do eixo Y. */
    private void desenharIdentificadores(Graphics2D g, AffineTransform at, Quadro q, double avanco) {
        g.setFont(Estilo.fonte(Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        Point2D destino = new Point2D.Double();

        for (EstadoRobo r : q.robos()) {
            Vec2 pos = prever(r.posicao(), r.velocidade(), avanco);
            at.transform(new Point2D.Double(pos.x(), pos.y()), destino);
            String id = String.valueOf(r.id());
            g.setColor(Paleta.textoSobre(r.cor()));
            g.drawString(id, (float) (destino.getX() - fm.stringWidth(id) / 2.0),
                    (float) (destino.getY() + fm.getAscent() / 2.0 - 1));
        }
    }

    private void desenharCursor(Graphics2D g) {
        if (mouseTelaX < 0) return;
        Vec2 p = telaParaMundo(mouseTelaX, mouseTelaY);
        String texto = String.format("x %.0f   y %.0f mm", p.x(), p.y());

        g.setFont(Estilo.fonte(Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(texto) + 12;
        int h = fm.getHeight() + 6;
        int x = Math.min(mouseTelaX + 14, getWidth() - w - 4);
        int y = Math.min(mouseTelaY + 14, getHeight() - h - 4);

        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(texto, x + 6, y + fm.getAscent() + 3);
    }
}
