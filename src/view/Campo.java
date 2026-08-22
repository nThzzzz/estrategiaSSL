package view;

import core.Vec2;
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
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
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

    private final Supplier<Quadro> fonte;

    private double zoom = 1.0;
    private double panX = 0, panY = 0;
    private int mouseTelaX = -1, mouseTelaY = -1;

    private Cor corSelecionada;
    private int idSelecionado = -1;

    private boolean mostrarVetores = true;
    private boolean mostrarSensor = true;
    private boolean mostrarIncerteza = true;

    public Campo(Supplier<Quadro> fonte) {
        this.fonte = fonte;
        setPreferredSize(new Dimension(980, 720));
        setBackground(Paleta.FUNDO);
    }

    // ------------------------------------------------------------ interacao

    public Quadro quadro() { return fonte.get(); }

    public void aplicarZoom(double fator) { zoom = Math.max(0.25, Math.min(zoom * fator, 8.0)); }
    public void deslocar(double dx, double dy) { panX += dx; panY += dy; }
    public void enquadrar() { zoom = 1.0; panX = 0; panY = 0; }
    public void setMouseTela(int x, int y) { mouseTelaX = x; mouseTelaY = y; }

    public void setMostrarVetores(boolean b) { mostrarVetores = b; }
    public void setMostrarSensor(boolean b)  { mostrarSensor = b; }
    public void setMostrarIncerteza(boolean b) { mostrarIncerteza = b; }

    /**
     * Selecao guardada por identidade (cor + id), nao por referencia ao record.
     * O quadro e recriado a cada pacote de visao, entao guardar o objeto faria a
     * selecao se perder no quadro seguinte.
     */
    public void selecionar(Cor cor, int id) { this.corSelecionada = cor; this.idSelecionado = id; }
    public void limparSelecao() { selecionar(null, -1); }
    public Cor getCorSelecionada() { return corSelecionada; }
    public int getIdSelecionado()  { return idSelecionado; }

    /** Robo mais proximo do ponto de mundo, dentro do proprio raio. */
    public EstadoRobo roboEm(Vec2 ponto) {
        EstadoRobo achado = null;
        double melhor = Robo.RAIO * 1.6;
        for (EstadoRobo r : quadro().robos()) {
            double d = r.posicao().distancia(ponto);
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
        AffineTransform at = transform(q.geometria());

        Graphics2D gm = (Graphics2D) g2.create();
        gm.transform(at);
        desenharCampo(gm, q.geometria());
        for (EstadoRobo r : q.robos()) desenharRobo(gm, r, q.geometria());
        desenharBola(gm, q.bola(), q.geometria());
        gm.dispose();

        desenharIdentificadores(g2, at, q);
        desenharCursor(g2);
        g2.dispose();
    }

    private void desenharCampo(Graphics2D g, Geometria geo) {
        double meioX = geo.meioComprimento();
        double meioY = geo.meiaLargura();

        g.setColor(Paleta.ESCAPE);
        g.fill(new Rectangle2D.Double(-geo.limiteParedeX(), -geo.limiteParedeY(),
                geo.limiteParedeX() * 2, geo.limiteParedeY() * 2));

        g.setColor(Paleta.GRAMA);
        g.fill(new Rectangle2D.Double(-meioX, -meioY, geo.comprimento(), geo.largura()));

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

        double gl = geo.golLargura();
        double gp = geo.golProfundidade();
        g.setColor(Paleta.AZUL);
        g.fill(new Rectangle2D.Double(-meioX - gp, -gl / 2, gp, gl));
        g.setColor(Paleta.AMARELO);
        g.fill(new Rectangle2D.Double(meioX, -gl / 2, gp, gl));
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
    private void desenharRobo(Graphics2D g, EstadoRobo r, Geometria geo) {
        AffineTransform anterior = g.getTransform();
        g.translate(r.posicao().x(), r.posicao().y());
        g.rotate(r.theta());

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

        if (mostrarVetores && r.rapidez() > 40) desenharVetor(g, r.posicao(), r.velocidade());
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
        double profundidade = geo.raioBola() + SensorDeBola.TOLERANCIA;
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

    private void desenharBola(Graphics2D g, EstadoBola bola, Geometria geo) {
        if (bola == EstadoBola.AUSENTE) return;

        double raio = geo.raioBola();
        double x = bola.posicao().x();
        double y = bola.posicao().y();
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

        if (mostrarVetores && bola.rapidez() > 60) desenharVetor(g, bola.posicao(), bola.velocidade());
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
    private void desenharIdentificadores(Graphics2D g, AffineTransform at, Quadro q) {
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        Point2D destino = new Point2D.Double();

        for (EstadoRobo r : q.robos()) {
            at.transform(new Point2D.Double(r.posicao().x(), r.posicao().y()), destino);
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

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
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
