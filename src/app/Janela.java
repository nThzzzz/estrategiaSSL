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
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
 *   |        |                                |        |
 *   +--------+--------------------------------+--------+
 *            ^                                ^
 *          divisores arrastaveis; o campo absorve a sobra
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
        // Qual metade defendemos vem do arbitro e a folga e escolha nossa: nenhum
        // dos dois esta no quadro da visao, e sem eles o campo nao sabe onde fica
        // a area proibida.
        this.campo.setContextoDeDefesa(cliente::estadoDeJogo, cliente::getMargemDaArea);
        this.campo.setNossaCor(cliente::getEquipe);
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

        JScrollPane esquerda = rolagem(painelRede, PainelRede.LARGURA);
        JScrollPane direita = rolagem(painelRobos, PainelRobos.LARGURA);
        campo.setMinimumSize(new Dimension(320, 240));

        // O campo fica na metade que CRESCE dos dois divisores: o de dentro
        // segura a coluna dos robos (peso 1 para a esquerda), o de fora segura a
        // coluna da rede (peso 0). Assim redimensionar a janela alarga o campo, e
        // arrastar um divisor troca largura entre aquela coluna e o campo, sem
        // mexer na outra.
        JSplitPane interno = divisor(campo, direita, false);
        JSplitPane externo = divisor(esquerda, interno, true);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Paleta.FUNDO);
        raiz.add(new BarraSuperior(cliente), BorderLayout.NORTH);
        raiz.add(externo, BorderLayout.CENTER);
        return raiz;
    }

    /**
     * Um divisor arrastavel entre dois componentes.
     *
     * <p>{@code fixaEsquerda} diz qual das duas metades e a COLUNA: e ela que
     * guarda a largura quando a janela muda de tamanho, e a outra metade -- a que
     * contem o campo -- absorve a diferenca. E por isso que redimensionar a
     * janela alarga o campo e nao as colunas.
     *
     * <p>{@code continuousLayout} porque o campo se reenquadra sozinho a cada
     * largura nova -- arrastar mostrando so uma linha e soltar no escuro seria
     * escolher a largura sem ver o efeito dela, que e justamente o que se esta
     * tentando ajustar.
     *
     * <p>{@code oneTouchExpandable} da as setinhas que recolhem a coluna inteira
     * de uma vez. Numa bancada apertada, tirar as colunas e ficar so com o campo
     * e o gesto mais pedido, e ele nao devia exigir arrastar ate a borda.
     */
    private static JSplitPane divisor(JComponent esquerda, JComponent direita,
                                      boolean fixaEsquerda) {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, esquerda, direita) {
            private boolean primeiroLayout = true;

            /**
             * Assenta o divisor na largura pedida pela coluna, uma vez so.
             *
             * <p>Enquanto ninguem tiver mexido no divisor, o JSplitPane reparte
             * pelo MINIMO das duas metades sempre que a janela e menor que a soma
             * dos tamanhos preferidos, e joga a sobra toda para o lado de peso
             * maior. Com a coluna do lado de peso zero isso a deixava nascer nos
             * 120 px do minimo: "Configurar..." virava "Configu..." e os botoes do
             * arbitro ficavam ilegiveis. Fixar a posicao assim que existe largura
             * de verdade resolve, e nao atrapalha arrasto nenhum feito depois --
             * dali em diante quem manda e a posicao escolhida.
             */
            @Override
            public void doLayout() {
                if (primeiroLayout && getWidth() > 0) {
                    primeiroLayout = false;
                    int coluna = (fixaEsquerda ? esquerda : direita).getPreferredSize().width;
                    setDividerLocation(fixaEsquerda
                            ? coluna
                            : getWidth() - getDividerSize() - coluna);
                }
                super.doLayout();
            }
        };
        sp.setContinuousLayout(true);
        sp.setOneTouchExpandable(true);
        sp.setDividerSize(7);
        sp.setResizeWeight(fixaEsquerda ? 0.0 : 1.0);
        sp.setBorder(null);
        sp.setBackground(Paleta.BORDA);
        return sp;
    }

    private static JScrollPane rolagem(JComponent conteudo, int largura) {
        JScrollPane r = new JScrollPane(conteudo,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        r.setBorder(null);
        r.getViewport().setBackground(Paleta.PAINEL);
        r.getVerticalScrollBar().setUnitIncrement(16);
        r.setPreferredSize(new Dimension(largura, 100));
        r.setMinimumSize(new Dimension(120, 100));
        return r;
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
