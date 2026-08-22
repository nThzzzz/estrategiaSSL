package app;

import core.Vec2;
import model.Cor;
import mundo.EstadoRobo;
import mundo.Quadro;
import view.Paleta;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Painel da direita: um cartao por robo, com o que a estrategia sabe dele.
 *
 * <p>Desenhado em um componente so, e nao montado com um {@code JPanel} por
 * robo. A lista muda de tamanho a cada quadro -- robo entra, robo some por
 * oclusao -- e recriar componentes Swing sessenta vezes por segundo custaria
 * mais do que redesenhar tudo, alem de perder foco e selecao no meio.
 *
 * <p>Dos tres numeros de cada cartao, so a ORIENTACAO vem da rede. Velocidade e
 * bola no sensor sao inferencias, e por isso aparecem marcadas com o sinal "~"
 * quando a trilha e recente demais para o filtro ter convergido: mostrar um
 * numero derivado com a mesma autoridade de um medido e o comeco de toda sessao
 * de depuracao perdida.
 */
public final class PainelRobos extends JPanel {

    private static final int LARGURA = 268;
    private static final int ALTURA_LINHA = 52;
    private static final int ALTURA_TITULO = 30;

    private final Cliente cliente;
    private final BiConsumer<Cor, Integer> aoSelecionar;

    /** Faixas verticais desenhadas no ultimo quadro, para traduzir clique em robo. */
    private final List<Faixa> faixas = new ArrayList<>();

    private Cor corSelecionada;
    private int idSelecionado = -1;

    private record Faixa(int y, int altura, Cor cor, int id) {}

    public PainelRobos(Cliente cliente, BiConsumer<Cor, Integer> aoSelecionar) {
        this.cliente = cliente;
        this.aoSelecionar = aoSelecionar;
        setPreferredSize(new Dimension(LARGURA, 100));
        setBackground(Paleta.PAINEL);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (Faixa f : faixas) {
                    if (e.getY() >= f.y() && e.getY() < f.y() + f.altura()) {
                        selecionar(f.cor(), f.id());
                        aoSelecionar.accept(f.cor(), f.id());
                        return;
                    }
                }
                selecionar(null, -1);
                aoSelecionar.accept(null, -1);
            }
        });
    }

    public void selecionar(Cor cor, int id) {
        this.corSelecionada = cor;
        this.idSelecionado = id;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        faixas.clear();

        Quadro q = cliente.quadro();
        int y = 6;
        y = secao(g2, q, cliente.getEquipe(), y);
        y = secao(g2, q, cliente.getAdversario(), y);

        if (q.robos().isEmpty()) {
            g2.setColor(Paleta.APAGADO);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.drawString("nenhum robo na visao", 16, y + 22);
        }

        setPreferredSize(new Dimension(LARGURA, Math.max(y + 10, 100)));
        g2.dispose();
    }

    private int secao(Graphics2D g, Quadro q, Cor cor, int y) {
        List<EstadoRobo> robos = q.robos(cor);

        g.setColor(Paleta.de(cor));
        g.fill(new RoundRectangle2D.Double(10, y + 8, 8, 14, 4, 4));
        g.setColor(Paleta.TEXTO);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(cliente.getNome(cor), 26, y + 20);

        g.setColor(Paleta.APAGADO);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        String contagem = robos.size() + (robos.size() == 1 ? " robo" : " robos");
        FontMetrics fm = g.getFontMetrics();
        g.drawString(contagem, LARGURA - 14 - fm.stringWidth(contagem), y + 20);

        y += ALTURA_TITULO;
        for (EstadoRobo r : robos) {
            desenharCartao(g, r, y);
            faixas.add(new Faixa(y, ALTURA_LINHA, r.cor(), r.id()));
            y += ALTURA_LINHA;
        }
        return y + 8;
    }

    private void desenharCartao(Graphics2D g, EstadoRobo r, int y) {
        boolean selecionado = r.cor() == corSelecionada && r.id() == idSelecionado;

        g.setColor(selecionado ? new Color(52, 52, 60) : Paleta.PAINEL_ALT);
        g.fill(new RoundRectangle2D.Double(8, y + 2, LARGURA - 16, ALTURA_LINHA - 6, 8, 8));
        if (selecionado) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Double(8, y + 2, LARGURA - 16, ALTURA_LINHA - 6, 8, 8));
        }

        // Distintivo com o numero, na cor da equipe: e como o robo aparece no campo.
        g.setColor(Paleta.de(r.cor()));
        g.fill(new RoundRectangle2D.Double(16, y + 10, 30, 30, 8, 8));
        g.setColor(Paleta.textoSobre(r.cor()));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        String id = String.valueOf(r.id());
        FontMetrics fmId = g.getFontMetrics();
        g.drawString(id, 31 - fmId.stringWidth(id) / 2, y + 31);

        boolean cru = r.fantasma();
        Color tinta = cru ? Paleta.APAGADO : Paleta.TEXTO;

        // Velocidade, o numero que mais se olha durante um jogo.
        g.setColor(tinta);
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.drawString(String.format("%.2f m/s", r.rapidez() / 1000.0), 56, y + 23);

        // Direcao: seta desenhada no angulo real, mais o valor em graus. A seta
        // sozinha e ambigua em cima de 90 graus; o numero sozinho nao se le de
        // relance. Os dois juntos resolvem.
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(cru ? Paleta.APAGADO : new Color(190, 190, 200));
        g.drawString(String.format("%+.0f°", Math.toDegrees(r.theta())), 76, y + 39);
        seta(g, 62, y + 35, r.theta(), cru);

        if (Math.abs(r.omega()) > 0.15) {
            g.drawString(String.format("%+.1f rad/s", r.omega()), 118, y + 39);
        }

        indicadorDeSensor(g, r, y);

        if (cru) {
            g.setColor(Paleta.ALERTA);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(String.format("sem visao ha %.1fs", r.idade()), 56, y + 50);
        }
    }

    /** Seta apontando para onde o robo olha, no referencial da tela (y invertido). */
    private void seta(Graphics2D g, int cx, int cy, double theta, boolean apagado) {
        double raio = 7;
        Vec2 p = Vec2.dePolar(raio, theta);
        double px = cx + p.x(), py = cy - p.y();

        g.setColor(apagado ? Paleta.APAGADO : new Color(200, 200, 210));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx - p.x(), cy + p.y(), px, py));

        Path2D ponta = new Path2D.Double();
        for (int lado = -1; lado <= 1; lado += 2) {
            Vec2 asa = Vec2.dePolar(4.5, theta + Math.PI - lado * 0.6);
            ponta.moveTo(px, py);
            ponta.lineTo(px + asa.x(), py - asa.y());
        }
        g.draw(ponta);
    }

    /** Selo do sensor de bola: aceso e escrito, apagado e so contorno. */
    private void indicadorDeSensor(Graphics2D g, EstadoRobo r, int y) {
        int largura = 54, altura = 20;
        int x = LARGURA - largura - 18;
        RoundRectangle2D selo = new RoundRectangle2D.Double(x, y + 16, largura, altura, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();

        if (r.bolaNoSensor()) {
            g.setColor(Paleta.SENSOR);
            g.fill(selo);
            g.setColor(new Color(10, 40, 10));
            String texto = "BOLA";
            g.drawString(texto, x + (largura - fm.stringWidth(texto)) / 2, y + 30);
        } else {
            g.setColor(Paleta.BORDA);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(selo);
            g.setColor(Paleta.APAGADO);
            String texto = "sensor";
            g.drawString(texto, x + (largura - fm.stringWidth(texto)) / 2, y + 30);
        }
    }
}
