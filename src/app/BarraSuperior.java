package app;

import model.Cor;
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
import java.awt.geom.RoundRectangle2D;

/**
 * Faixa do topo: quem esta jogando, de que cor, e ha quanto tempo.
 *
 * <p>Cada equipe ocupa um bloco pintado com a propria cor, nas mesmas cores dos
 * robos no campo -- a associacao nome/cor precisa ser lida de relance, no meio
 * de um jogo, e nao decorada.
 *
 * <p>O tempo mostrado e o {@code t_capture} da visao, ou seja o relogio de quem
 * gera os quadros, e nao o relogio local. Com o simulador acelerado, ou parado,
 * e esse o tempo que corresponde ao que se ve no campo.
 *
 * <p>O que falta aqui, e falta de proposito: PLACAR e estado de jogo. Nao ha de
 * onde tirar. Placar, faltas e {@code HALT}/{@code STOP}/{@code RUNNING} vem do
 * Game Controller, num fluxo separado que o simulador ainda nao publica.
 * Inventar um zero a zero na tela seria mostrar um dado que ninguem apurou.
 */
public final class BarraSuperior extends JPanel {

    private static final int ALTURA = 74;

    private final Cliente cliente;

    public BarraSuperior(Cliente cliente) {
        this.cliente = cliente;
        setPreferredSize(new Dimension(100, ALTURA));
        setBackground(Paleta.PAINEL_ALT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Quadro q = cliente.quadro();
        int larguraBloco = Math.min(300, getWidth() / 3);

        bloco(g2, Cor.AZUL, 8, larguraBloco, q.quantidade(Cor.AZUL), false);
        bloco(g2, Cor.AMARELO, getWidth() - larguraBloco - 8, larguraBloco,
                q.quantidade(Cor.AMARELO), true);

        centro(g2, q, larguraBloco);
        g2.dispose();
    }

    /** Bloco de uma equipe: fundo na cor dela, nome grande, contagem de robos. */
    private void bloco(Graphics2D g, Cor cor, int x, int largura, int robos, boolean direita) {
        boolean nossa = cliente.getEquipe() == cor;

        g.setColor(Paleta.de(cor));
        g.fill(new RoundRectangle2D.Double(x, 10, largura, ALTURA - 20, 12, 12));

        if (nossa) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new RoundRectangle2D.Double(x + 1, 11, largura - 2, ALTURA - 22, 12, 12));
        }

        Color tinta = Paleta.textoSobre(cor);
        g.setColor(tinta);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();

        String nome = cliente.getNome(cor);
        nome = encurtar(nome, fm, largura - 90);
        int nx = direita ? x + largura - 14 - fm.stringWidth(nome) : x + 14;
        g.drawString(nome, nx, 38);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm2 = g.getFontMetrics();
        String sub = (nossa ? "nos  ·  " : "") + cor.tag() + "  ·  " + robos + " robos";
        int sx = direita ? x + largura - 14 - fm2.stringWidth(sub) : x + 14;
        g.setColor(new Color(tinta.getRed(), tinta.getGreen(), tinta.getBlue(), 190));
        g.drawString(sub, sx, 56);
    }

    /** Miolo: tempo de partida grande e a linha de estado da visao embaixo. */
    private void centro(Graphics2D g, Quadro q, int larguraBloco) {
        String tempo = formatar(q.tempo());

        g.setFont(new Font("Monospaced", Font.BOLD, 30));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(cliente.recebendo() ? Paleta.TEXTO : Paleta.APAGADO);
        g.drawString(tempo, (getWidth() - fm.stringWidth(tempo)) / 2, 40);

        String estado;
        Color cor;
        if (cliente.getFalha() != null) {
            estado = cliente.getFalha();
            cor = Paleta.ERRO;
        } else if (!cliente.recebendo()) {
            estado = "sem visao em " + cliente.getConfig().grupoVisao()
                    + ":" + cliente.getConfig().portaVisao();
            cor = Paleta.ALERTA;
        } else {
            estado = String.format("quadro %d  ·  %.0f Hz  ·  %d robos",
                    q.frame(), taxa(), q.robos().size());
            cor = Paleta.APAGADO;
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm2 = g.getFontMetrics();
        g.setColor(cor);
        estado = encurtar(estado, fm2, getWidth() - larguraBloco * 2 - 40);
        g.drawString(estado, (getWidth() - fm2.stringWidth(estado)) / 2, 60);
    }

    private double taxa() { return cliente.taxaHz(); }

    /** mm:ss.d -- decimo de segundo ajuda a ver que o tempo esta correndo. */
    static String formatar(double segundos) {
        if (segundos <= 0) return "--:--";
        int minutos = (int) (segundos / 60);
        double resto = segundos - minutos * 60;
        return String.format("%02d:%04.1f", minutos, resto);
    }

    private static String encurtar(String texto, FontMetrics fm, int largura) {
        if (largura <= 0 || fm.stringWidth(texto) <= largura) return texto;
        String s = texto;
        while (s.length() > 1 && fm.stringWidth(s + "...") > largura) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}
