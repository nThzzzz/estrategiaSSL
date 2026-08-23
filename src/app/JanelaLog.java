package app;

import estrategia.Diario;
import view.Estilo;
import view.Paleta;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/**
 * O que a estrategia esta fazendo, em texto corrido.
 *
 * <p>E o que sairia no terminal: uma linha por acontecimento, com o carimbo do
 * relogio da visao. Play que entra, papel que muda de robo, tatica que troca,
 * bola que entra no sensor, chute que sai -- e chute que o arbitro cortou, que e
 * o caso mais dificil de depurar sem log, porque o comando some sem rastro.
 *
 * <p>Quem esta executando o que fica no painel da direita da janela principal,
 * embaixo de cada robo. Sao perguntas diferentes: aqui se pergunta "o que
 * aconteceu", la se pergunta "o que esta acontecendo agora", e misturar as duas
 * na mesma tela faz a segunda rolar para fora junto com a primeira.
 *
 * <p>Janela separada e nao modal: da para ler o log e ver o campo ao mesmo tempo,
 * que e justamente o uso.
 */
public final class JanelaLog extends JFrame {

    private static JanelaLog aberta;

    private final Cliente cliente;

    private JanelaLog(Cliente _cliente) {
        super("Log da estrategia");
        this.cliente = _cliente;

        PainelLinhas linhas = new PainelLinhas();
        setContentPane(linhas);
        setSize(640, 460);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Mais lento que a tela do campo: log que rola rapido demais nao se le.
        Timer relogio = new Timer(120, e -> linhas.repaint());
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

    /**
     * As ultimas linhas, mais recentes embaixo.
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
            g2.setFont(Estilo.fonte(Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int alturaLinha = fm.getHeight();

            List<Diario.Linha> todas = cliente.getExecutor().diario().linhas();
            if (todas.isEmpty()) {
                g2.setColor(Paleta.APAGADO);
                g2.drawString("nada ainda; ligue a estrategia no painel da esquerda", 12, 26);
                g2.dispose();
                return;
            }

            int cabem = Math.max(1, (getHeight() - 12) / alturaLinha);
            int primeira = Math.max(0, todas.size() - cabem);

            int y = getHeight() - 10 - (todas.size() - primeira - 1) * alturaLinha;
            for (int i = primeira; i < todas.size(); i++) {
                Diario.Linha l = todas.get(i);
                g2.setColor(corDe(l.nivel()));
                String texto = String.format("%7.2f  %s", l.tempo(), l.texto());
                g2.drawString(encurtar(texto, fm, getWidth() - 24), 12, y);
                y += alturaLinha;
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
