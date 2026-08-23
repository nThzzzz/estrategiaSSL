package app;

import model.Cor;
import view.Campo;
import view.Paleta;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Configuracao avancada: quem somos e o que aparece no campo.
 *
 * <p>Estava tudo na coluna da esquerda, e a coluna nao dava conta: cinco caixas
 * de exibicao mais tres campos de equipe ocupavam a maior parte dela para
 * ajustes que se mexem uma vez por bancada, enquanto os comandos de arbitro --
 * que se apertam o tempo todo -- nao tinham onde caber. Mesmo desenho ja usado
 * pelo {@link DialogoRede}: o que se olha fica na coluna, o que se configura
 * fica atras de um botao.
 *
 * <p>Modeless de proposito. Ajustar exibicao com o campo congelado atras de um
 * dialogo modal seria ajustar as cegas -- cada caixa marcada aparece no campo no
 * mesmo instante, e e assim que se decide se ela ajuda ou atrapalha.
 *
 * <p>As caixas nascem lendo o estado REAL do {@link Campo}, e nao um {@code
 * true} escrito aqui. Como o dialogo abre e fecha quantas vezes se quiser,
 * marcar tudo de novo a cada abertura faria a segunda abertura desfazer o que a
 * primeira ajustou.
 */
public final class DialogoConfiguracao extends JDialog {

    private final Cliente cliente;
    private final Campo campo;

    private final JComboBox<String> nossaCor = new JComboBox<>(new String[]{"Azul", "Amarelo"});
    private final JTextField nomeAzul = new JTextField(12);
    private final JTextField nomeAmarelo = new JTextField(12);

    private DialogoConfiguracao(Window dono, Cliente cliente, Campo campo) {
        super(dono, "Configuracao avancada", ModalityType.MODELESS);
        this.cliente = cliente;
        this.campo = campo;

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setBackground(Paleta.PAINEL);
        corpo.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        corpo.add(equipe());
        corpo.add(Box.createVerticalStrut(10));
        corpo.add(exibicao());

        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dispose());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rodape.setBackground(Paleta.PAINEL);
        rodape.add(fechar);

        setLayout(new BorderLayout());
        add(corpo, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        carregar();
        pack();
        setMinimumSize(new Dimension(320, getHeight()));
        setLocationRelativeTo(dono);
    }

    public static void abrir(Component origem, Cliente cliente, Campo campo) {
        new DialogoConfiguracao(SwingUtilities.getWindowAncestor(origem), cliente, campo)
                .setVisible(true);
    }

    // ------------------------------------------------------------- secoes

    private JComponent equipe() {
        JPanel p = secao("Equipe");
        p.add(linha("jogamos de", nossaCor));
        p.add(linha("nome azul", nomeAzul));
        p.add(linha("nome amarelo", nomeAmarelo));
        p.add(Box.createVerticalStrut(6));

        JButton aplicar = new JButton("Aplicar");
        aplicar.setAlignmentX(Component.LEFT_ALIGNMENT);
        aplicar.addActionListener(e -> aplicarEquipe());
        p.add(aplicar);
        return p;
    }

    private JComponent exibicao() {
        JPanel p = secao("Exibicao");
        p.add(caixa("textura do gramado", campo::isMostrarTextura, campo::setMostrarTextura));
        p.add(caixa("vetores de velocidade", campo::isMostrarVetores, campo::setMostrarVetores));
        p.add(caixa("area do sensor de bola", campo::isMostrarSensor, campo::setMostrarSensor));
        p.add(caixa("incerteza do Kalman", campo::isMostrarIncerteza, campo::setMostrarIncerteza));
        p.add(caixa("prever ate o desenho", campo::isPreverAteODesenho, campo::setPreverAteODesenho));
        p.add(caixa("fantasma de 1 s", campo::isMostrarFantasma, campo::setMostrarFantasma));
        p.add(Box.createVerticalStrut(6));

        JButton enquadrar = new JButton("Reenquadrar campo");
        enquadrar.setAlignmentX(Component.LEFT_ALIGNMENT);
        enquadrar.addActionListener(e -> { campo.enquadrar(); campo.repaint(); });
        p.add(enquadrar);
        return p;
    }

    // -------------------------------------------------------------- acoes

    private void carregar() {
        nossaCor.setSelectedIndex(cliente.getEquipe() == Cor.AZUL ? 0 : 1);
        nomeAzul.setText(cliente.getNomeAzul());
        nomeAmarelo.setText(cliente.getNomeAmarelo());
    }

    /**
     * Troca cor e nomes.
     *
     * <p>Mudar de cor reabre os sockets, porque a porta de destino dos comandos
     * depende dela -- por isso passa pelo {@link Cliente#reconfigurar} e nao por
     * um setter simples.
     */
    private void aplicarEquipe() {
        cliente.setNomes(nomeAzul.getText(), nomeAmarelo.getText());
        Cor cor = nossaCor.getSelectedIndex() == 0 ? Cor.AZUL : Cor.AMARELO;
        try {
            cliente.reconfigurar(cliente.getConfig(), cor);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Equipe", JOptionPane.WARNING_MESSAGE);
        }
        carregar();
    }

    // -------------------------------------------------------------- estilo

    private static JPanel secao(String titulo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.BORDA), titulo,
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 11), Paleta.APAGADO),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)));
        return p;
    }

    private JCheckBox caixa(String texto, BooleanSupplier estado, Consumer<Boolean> ao) {
        JCheckBox c = new JCheckBox(texto, estado.getAsBoolean());
        c.setBackground(Paleta.PAINEL);
        c.setForeground(Paleta.TEXTO);
        c.setFont(c.getFont().deriveFont(11f));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.addActionListener(e -> { ao.accept(c.isSelected()); campo.repaint(); });
        return c;
    }

    private static JPanel linha(String nome, JComponent controle) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel l = new JLabel(nome);
        l.setForeground(Paleta.APAGADO);
        l.setFont(l.getFont().deriveFont(11f));
        l.setPreferredSize(new Dimension(92, 22));
        l.setMaximumSize(new Dimension(92, 22));

        controle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(l);
        p.add(controle);
        return p;
    }
}
