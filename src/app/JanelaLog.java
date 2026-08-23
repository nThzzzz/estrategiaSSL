package app;

import estrategia.Diario;
import view.Paleta;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Janela de log: o que a estrategia esta fazendo, e por que um robo parou.
 *
 * <p>Duas metades. Em cima, uma linha por robo com o papel, a tatica e a skill
 * que ele esta executando, cada um em VERDE quando esta rodando e VERMELHO quando
 * nao esta. Embaixo, o que foi acontecendo, com o carimbo do relogio da visao.
 *
 * <p>As cores existem porque a pergunta que se faz olhando um robo travado nunca
 * e "qual e a tatica dele" e sim "ele esta executando alguma coisa". Ler o nome
 * da tatica nao responde: uma tatica concluida continua tendo nome. Verde e
 * vermelho respondem de longe.
 *
 * <p>Janela separada, e nao um painel na principal, de proposito: ela e larga,
 * so interessa quando se esta depurando, e roubaria espaco do campo o resto do
 * tempo. Nao e modal, entao da para ver o log e o campo ao mesmo tempo, que e
 * justamente o uso.
 */
public final class JanelaLog extends JFrame {

    private static JanelaLog aberta;

    private final Cliente cliente;

    private JanelaLog(Cliente _cliente) {
        super("Log da estrategia");
        this.cliente = _cliente;

        PainelSituacoes situacoes = new PainelSituacoes();
        PainelLinhas linhas = new PainelLinhas();

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Paleta.FUNDO);
        raiz.add(situacoes, BorderLayout.NORTH);
        raiz.add(linhas, BorderLayout.CENTER);

        setContentPane(raiz);
        setSize(620, 620);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Mais lento que a tela do campo: log que rola rapido demais nao se le,
        // e o estado dos robos nao muda de forma util a cada 16 ms.
        Timer relogio = new Timer(120, e -> {
            // Ajustar o tamanho DENTRO de paintComponent nao funciona: o layout ja
            // aconteceu quando a pintura roda, e o painel fica cortado ate o
            // proximo ciclo. Aqui, antes do repaint, o revalidate tem efeito.
            situacoes.vAjustarAltura();
            situacoes.repaint();
            linhas.repaint();
        });
        relogio.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                relogio.stop();
                aberta = null;
            }
        });
    }

    /** Abre a janela, ou traz para a frente a que ja estava aberta. */
    public static void abrir(Component origem, Cliente cliente) {
        if (aberta != null) {
            aberta.toFront();
            return;
        }
        aberta = new JanelaLog(cliente);
        aberta.setLocationRelativeTo(SwingUtilities.getWindowAncestor(origem));
        aberta.setVisible(true);
    }

    // ------------------------------------------------------------- situacoes

    /** Uma linha por robo: papel, tatica e skill, em verde ou vermelho. */
    private final class PainelSituacoes extends JPanel {

        private static final int ALTURA_LINHA = 30;

        PainelSituacoes() {
            setBackground(Paleta.PAINEL);
            vAjustarAltura();
        }

        /** Uma faixa por robo, mais o cabecalho. */
        void vAjustarAltura() {
            int robos = Math.max(1, cliente.getExecutor().diario().situacoes().size());
            int altura = 34 + robos * ALTURA_LINHA;
            if (getPreferredSize().height != altura) {
                setPreferredSize(new Dimension(600, altura));
                revalidate();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            List<Diario.Situacao> lista = cliente.getExecutor().diario().situacoes();

            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.setColor(Paleta.APAGADO);
            g2.drawString("ROBO", 14, 20);
            g2.drawString("ROLE", 60, 20);
            g2.drawString("TACTIC", 230, 20);
            g2.drawString("SKILL", 400, 20);
            g2.drawString("SAINDO", 545, 20);

            if (lista.isEmpty()) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Paleta.APAGADO);
                g2.drawString("nenhum robo nosso na visao", 14, 46);
                g2.dispose();
                return;
            }

            int y = 28;
            for (Diario.Situacao s : lista) {
                distintivo(g2, s, y);
                celula(g2, s.role(), 60, y, 160, s.temRole());
                celula(g2, s.tactic() + (s.taticaConcluida() ? "  fim" : ""),
                        230, y, 160, s.temTactic());
                celula(g2, s.skill() + (s.skillConcluida() ? "  fim" : ""),
                        400, y, 130, s.temSkill());
                celula(g2, s.comandado() ? "sim" : "nada", 540, y, 60, s.comandado());
                y += ALTURA_LINHA;
            }
            g2.dispose();
        }

        private void distintivo(Graphics2D g, Diario.Situacao s, int y) {
            Color cor = s.comBola() ? Paleta.SENSOR : Paleta.de(cliente.getEquipe());
            g.setColor(cor);
            g.fill(new RoundRectangle2D.Double(14, y, 26, 22, 6, 6));
            g.setColor(s.comBola() ? new Color(10, 40, 10) : Paleta.textoSobre(cliente.getEquipe()));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            String id = String.valueOf(s.id());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(id, 27 - fm.stringWidth(id) / 2, y + 16);
        }

        /**
         * Verde quando a camada existe, vermelho quando falta.
         *
         * <p>O sufixo "fim" marca o que se concluiu, e isso fica em VERDE de
         * proposito: concluir e ter dado certo. Vermelho e so para o que nao
         * existe -- robo sem papel, papel sem tatica, tatica sem skill -- e para a
         * coluna do comando, onde vermelho quer dizer que nada esta saindo.
         */
        private void celula(Graphics2D g, String texto, int x, int y, int largura,
                            boolean presente) {
            Color cor = presente ? Paleta.OK : Paleta.ERRO;
            g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 40));
            g.fill(new RoundRectangle2D.Double(x, y, largura, 22, 6, 6));
            g.setColor(cor);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(x, y, largura, 22, 6, 6));

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(encurtar(texto, fm, largura - 12), x + 8, y + 15);
        }
    }

    // ---------------------------------------------------------------- linhas

    /**
     * As ultimas linhas do diario, mais recentes embaixo.
     *
     * <p>Sem barra de rolagem: mostra o que cabe e acompanha sozinho. Um log de
     * depuracao que exige rolar ate o fim a cada olhada atrapalha mais do que
     * ajuda, e o historico antigo interessa menos que o que acabou de acontecer.
     */
    private final class PainelLinhas extends JPanel {

        PainelLinhas() { setBackground(Paleta.FUNDO); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int alturaLinha = fm.getHeight();

            List<Diario.Linha> todas = cliente.getExecutor().diario().linhas();
            int cabem = Math.max(1, (getHeight() - 10) / alturaLinha);
            int primeira = Math.max(0, todas.size() - cabem);

            int y = getHeight() - 8 - (todas.size() - primeira - 1) * alturaLinha;
            for (int i = primeira; i < todas.size(); i++) {
                Diario.Linha l = todas.get(i);
                g2.setColor(corDe(l.nivel()));
                String texto = String.format("%7.2f  %s", l.tempo(), l.texto());
                g2.drawString(encurtar(texto, fm, getWidth() - 20), 10, y);
                y += alturaLinha;
            }

            if (todas.isEmpty()) {
                g2.setColor(Paleta.APAGADO);
                g2.drawString("nada ainda; ligue a estrategia no painel da esquerda", 10, 24);
            }
            g2.dispose();
        }

        private Color corDe(Diario.Nivel n) {
            return switch (n) {
                case ACAO -> Paleta.SENSOR;
                case ALERTA -> Paleta.ALERTA;
                case INFO -> new Color(190, 190, 200);
            };
        }
    }

    private static String encurtar(String texto, FontMetrics fm, int largura) {
        if (fm.stringWidth(texto) <= largura) return texto;
        String s = texto;
        while (s.length() > 1 && fm.stringWidth(s + "...") > largura) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}
