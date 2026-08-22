package app;

import core.Vec2;
import model.Cor;
import mundo.EstadoRobo;
import view.Campo;
import view.Paleta;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Monta a janela da estrategia.
 *
 * <pre>
 *   +--------------------------------------------------+
 *   |  BarraSuperior: equipes, cores, tempo de partida  |
 *   +--------+--------------------------------+--------+
 *   | Painel |                                | Painel |
 *   |  Rede  |             Campo              | Robos  |
 *   +--------+--------------------------------+--------+
 * </pre>
 *
 * <p>Os tres paineis conversam so pelo {@link Cliente}: nenhum tem referencia
 * para o outro. A unica excecao e a selecao de robo, que precisa mesmo ser
 * compartilhada -- clicar no campo acende o cartao, clicar no cartao acende o
 * robo -- e por isso passa por aqui, em vez de um painel chamar o outro.
 *
 * <p>O relogio de repaint e independente da taxa de visao. A tela redesenha a 60
 * Hz mesmo que a visao chegue a 30, ou nao chegue: uma janela que congela
 * quando a rede cai parece travada, quando o que houve foi silencio na rede -- e
 * e justamente a hora em que se precisa ler o estado na tela.
 *
 * <p>O mesmo relogio chama {@code vDecidir}, que so age quando chega quadro novo.
 * A estrategia fica assim atrelada a VISAO e nao a tela: reduzir o FPS da janela
 * nao pode deixar o time mais burro.
 */
public final class Janela {

    private static final int HZ_TELA = 60;

    private final Cliente cliente;
    private final Campo campo;
    private final PainelRobos painelRobos;
    private final PainelRede painelRede;

    private Janela(Cliente cliente) {
        this.cliente = cliente;
        this.campo = new Campo(cliente::quadro);
        this.painelRobos = new PainelRobos(cliente, this::selecionar);
        this.painelRede = new PainelRede(cliente, campo);
    }

    public static JFrame abrir(String titulo, Cliente cliente) {
        return new Janela(cliente).montar(titulo);
    }

    /**
     * Monta o conteudo da janela sem exibir nada.
     *
     * <p>Separado de {@link #abrir} para que a interface possa ser montada e
     * desenhada fora de uma janela visivel -- e assim que ela e renderizada para
     * conferencia, sem depender de alguem olhando a tela.
     */
    public static JComponent conteudo(Cliente cliente) {
        return new Janela(cliente).montarConteudo();
    }

    private JComponent montarConteudo() {
        instalarMouse();

        JScrollPane rolagem = new JScrollPane(painelRobos,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.setBorder(null);
        rolagem.getViewport().setBackground(Paleta.PAINEL);
        rolagem.getVerticalScrollBar().setUnitIncrement(16);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Paleta.FUNDO);
        raiz.add(new BarraSuperior(cliente), BorderLayout.NORTH);
        raiz.add(painelRede, BorderLayout.WEST);
        raiz.add(campo, BorderLayout.CENTER);
        raiz.add(rolagem, BorderLayout.EAST);
        return raiz;
    }

    /** Atualiza os rotulos que nao se redesenham sozinhos. */
    public void atualizar() { painelRede.atualizarEstado(); }

    private JFrame montar(String titulo) {
        JComponent raiz = montarConteudo();

        JFrame frame = new JFrame(titulo);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(raiz);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        new Timer(1000 / HZ_TELA, e -> {
            cliente.vDecidir();
            frame.repaint();
            painelRede.atualizarEstado();
        }).start();

        return frame;
    }

    /** Selecao vinda do painel da direita. */
    private void selecionar(Cor cor, int id) {
        campo.selecionar(cor, id);
        campo.repaint();
    }

    private void instalarMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            private int ultimoX, ultimoY;

            @Override
            public void mousePressed(MouseEvent e) {
                ultimoX = e.getX();
                ultimoY = e.getY();

                if (e.getButton() == MouseEvent.BUTTON1) {
                    Vec2 ponto = campo.telaParaMundo(e.getX(), e.getY());
                    EstadoRobo r = campo.roboEm(ponto);
                    campo.selecionar(r == null ? null : r.cor(), r == null ? -1 : r.id());
                    painelRobos.selecionar(r == null ? null : r.cor(), r == null ? -1 : r.id());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // Arrasto com o botao direito desloca o campo, igual ao simulador.
                if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
                    campo.deslocar(e.getX() - ultimoX, e.getY() - ultimoY);
                }
                ultimoX = e.getX();
                ultimoY = e.getY();
                campo.setMouseTela(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) { campo.setMouseTela(e.getX(), e.getY()); }

            @Override
            public void mouseExited(MouseEvent e) { campo.setMouseTela(-1, -1); }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                campo.aplicarZoom(e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1);
            }
        };
        campo.addMouseListener(mouse);
        campo.addMouseMotionListener(mouse);
        campo.addMouseWheelListener(mouse);
    }
}
