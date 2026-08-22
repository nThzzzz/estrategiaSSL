package app;

import jogo.Acao;
import jogo.EstadoDeJogo;
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
 * Faixa do topo: quem esta jogando, de que cor, como esta o placar e o que o
 * arbitro esta mandando fazer.
 *
 * <p>Cada equipe ocupa um bloco pintado com a propria cor, nas mesmas cores dos
 * robos no campo -- a associacao nome/cor precisa ser lida de relance, no meio de
 * um jogo, e nao decorada.
 *
 * <p>Nome e placar vem do Game Controller quando ele esta no ar, porque e ele
 * quem sabe: {@code TeamInfo} carrega o nome inscrito e o placar oficial. Sem
 * arbitro, cai para os nomes configurados no painel da esquerda e o placar
 * simplesmente nao aparece -- exibir zero a zero sem fonte seria inventar um dado.
 *
 * <p>O comando do arbitro fica no meio, grande e colorido, porque e a informacao
 * que muda o que a equipe pode fazer no instante seguinte. Vermelho para
 * {@code PARAR}, laranja para {@code AFASTAR}, verde para jogo corrido.
 */
public final class BarraSuperior extends JPanel {

    private static final int ALTURA = 84;

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
        EstadoDeJogo jogo = cliente.estadoDeJogo();
        int larguraBloco = Math.min(300, getWidth() / 3);

        bloco(g2, Cor.AZUL, 8, larguraBloco, q.quantidade(Cor.AZUL), jogo, false);
        bloco(g2, Cor.AMARELO, getWidth() - larguraBloco - 8, larguraBloco,
                q.quantidade(Cor.AMARELO), jogo, true);

        centro(g2, q, jogo, larguraBloco);
        g2.dispose();
    }

    /** Bloco de uma equipe: fundo na cor dela, nome, placar e contagem de robos. */
    private void bloco(Graphics2D g, Cor cor, int x, int largura, int robos,
                       EstadoDeJogo jogo, boolean direita) {
        boolean nossa = cliente.getEquipe() == cor;

        g.setColor(Paleta.de(cor));
        g.fill(new RoundRectangle2D.Double(x, 10, largura, ALTURA - 20, 12, 12));

        if (nossa) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new RoundRectangle2D.Double(x + 1, 11, largura - 2, ALTURA - 22, 12, 12));
        }

        Color tinta = Paleta.textoSobre(cor);
        int margemPlacar = 0;

        // Placar so existe com arbitro no ar. Fica na borda de fora do bloco,
        // longe do miolo, para os dois numeros nao se confundirem com o relogio.
        if (!jogo.semArbitro()) {
            int gols = nossa ? jogo.golsNossos() : jogo.golsDeles();
            g.setFont(new Font("Monospaced", Font.BOLD, 30));
            FontMetrics fm = g.getFontMetrics();
            String texto = String.valueOf(gols);
            int px = direita ? x + largura - 16 - fm.stringWidth(texto) : x + 16;
            g.setColor(tinta);
            g.drawString(texto, px, 48);
            margemPlacar = fm.stringWidth(texto) + 18;
        }

        int base = direita ? x : x + margemPlacar;
        int util = largura - margemPlacar - 16;

        g.setColor(tinta);
        g.setFont(new Font("SansSerif", Font.BOLD, 19));
        FontMetrics fm = g.getFontMetrics();
        String nome = encurtar(cliente.getNomeExibido(cor), fm, util);
        int nx = direita ? x + largura - 16 - margemPlacar - fm.stringWidth(nome) : base + 14;
        g.drawString(nome, nx, 40);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm2 = g.getFontMetrics();
        String sub = (nossa ? "nos  ·  " : "") + cor.tag() + "  ·  " + robos + " robos";
        int sx = direita ? x + largura - 16 - margemPlacar - fm2.stringWidth(sub) : base + 14;
        g.setColor(new Color(tinta.getRed(), tinta.getGreen(), tinta.getBlue(), 190));
        g.drawString(sub, sx, 58);
    }

    /** Miolo: o comando do arbitro, o relogio e a linha de estado da visao. */
    private void centro(Graphics2D g, Quadro q, EstadoDeJogo jogo, int larguraBloco) {
        int meio = getWidth() / 2;

        String comando = jogo.descricao().toUpperCase();
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        FontMetrics fmc = g.getFontMetrics();
        int largura = fmc.stringWidth(comando) + 28;
        Color fundo = corDaAcao(jogo);

        g.setColor(fundo);
        g.fill(new RoundRectangle2D.Double(meio - largura / 2.0, 12, largura, 26, 13, 13));
        g.setColor(jogo.semArbitro() ? Paleta.APAGADO : new Color(20, 20, 22));
        g.drawString(comando, meio - fmc.stringWidth(comando) / 2, 31);

        // Relogio: o do arbitro quando existe, senao o da visao. Sao coisas
        // diferentes -- um e tempo de jogo, o outro e tempo de simulacao.
        String relogio = jogo.tempoRestante() >= 0
                ? formatar(jogo.tempoRestante())
                : formatar(q.tempo());
        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        FontMetrics fmr = g.getFontMetrics();
        g.setColor(cliente.recebendo() ? Paleta.TEXTO : Paleta.APAGADO);
        g.drawString(relogio, meio - fmr.stringWidth(relogio) / 2, 62);

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
                    q.frame(), cliente.taxaHz(), q.robos().size());
            cor = Paleta.APAGADO;
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm2 = g.getFontMetrics();
        g.setColor(cor);
        estado = encurtar(estado, fm2, getWidth() - larguraBloco * 2 - 40);
        g.drawString(estado, meio - fm2.stringWidth(estado) / 2, 76);
    }

    private static Color corDaAcao(EstadoDeJogo jogo) {
        if (jogo.semArbitro()) return new Color(52, 52, 58);
        if (jogo.acao() == Acao.PARAR)   return new Color(235, 90, 90);
        if (jogo.acao() == Acao.AFASTAR) return new Color(245, 170, 60);
        if (jogo.acao() == Acao.JOGAR)   return new Color(120, 215, 130);
        return jogo.nosso() ? new Color(120, 180, 255) : new Color(190, 190, 200);
    }

    /** mm:ss.d -- o decimo ajuda a ver que o tempo esta correndo. */
    static String formatar(double segundos) {
        if (segundos < 0) return "--:--";
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
