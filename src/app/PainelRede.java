package app;

import view.Campo;
import view.Paleta;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Coluna da esquerda: o que se OLHA e o que se APERTA durante uma bancada.
 *
 * <p>A divisao e essa, e ela decide o que mora aqui. Estado da rede, estado do
 * arbitro e estado da estrategia sao coisas que se le a todo instante; os
 * comandos de arbitro sao os que mais se apertam. Ja portas, nomes de equipe e
 * caixas de exibicao se mexem uma vez por bancada e nao merecem espaco
 * permanente -- ficam todos atras de "Configuracao avancada..."
 * ({@link DialogoConfiguracao}), portas inclusive.
 *
 * <p>Antes era o contrario: equipe e exibicao ocupavam a maior parte da coluna e
 * os comandos de arbitro estavam escondidos num dialogo, a duas aberturas de
 * distancia de um STOP.
 *
 * <p>A coluna e {@link PainelRolavel} porque a largura dela agora e arrastavel e
 * o conteudo passou a ser mais alto que a janela em telas baixas.
 */
public final class PainelRede extends PainelRolavel {

    /** Largura inicial da coluna; dai em diante quem manda e o divisor. */
    public static final int LARGURA = 246;

    private final Cliente cliente;
    private final Campo campo;
    private final PainelArbitro painelArbitro;

    private final JLabel escuta = rotulo("--", Paleta.APAGADO);
    private final JLabel taxa = rotulo("--", Paleta.APAGADO);
    private final JLabel envio = rotulo("--", Paleta.APAGADO);

    private final JLabel estadoEstrategia = rotulo("desligada", Paleta.APAGADO);
    private final JCheckBox caixaEstrategia = new JCheckBox("comandar os robos");

    public PainelRede(Cliente cliente, Campo campo) {
        this.cliente = cliente;
        this.campo = campo;
        this.painelArbitro = new PainelArbitro(cliente);

        caixaEstrategia.setBackground(Paleta.PAINEL);
        caixaEstrategia.setForeground(Paleta.TEXTO);
        caixaEstrategia.setFont(caixaEstrategia.getFont().deriveFont(11f));
        caixaEstrategia.setAlignmentX(Component.LEFT_ALIGNMENT);
        caixaEstrategia.setSelected(cliente.isEstrategiaLigada());
        caixaEstrategia.addActionListener(e ->
                cliente.setEstrategiaLigada(caixaEstrategia.isSelected()));

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Paleta.PAINEL);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(titulo("Rede"));
        add(escuta);
        add(taxa);
        add(Box.createVerticalStrut(6));
        add(rotulo("comandos", Paleta.APAGADO));
        add(envio);

        add(Box.createVerticalStrut(18));
        add(titulo("Arbitro"));
        add(painelArbitro);

        add(Box.createVerticalStrut(18));
        add(titulo("Estrategia"));
        add(caixaEstrategia);
        add(estadoEstrategia);
        add(Box.createVerticalStrut(8));
        add(botao("Ver log...", () -> JanelaLog.abrir(campo, cliente)));

        add(Box.createVerticalStrut(18));
        add(botao("Configuracao avancada...",
                () -> DialogoConfiguracao.abrir(campo, cliente, campo)));

        add(Box.createVerticalGlue());
        add(rodape());

        atualizarEstado(); // sem isso os rotulos ficam em "--" ate o primeiro tique
    }

    /**
     * Largura inicial pedida; a altura vem do conteudo.
     *
     * <p>Nao usa {@code setPreferredSize} com altura fixa porque o painel vive
     * dentro de uma rolagem: uma altura chumbada faria o viewport achar que o
     * conteudo cabe e a barra nunca apareceria.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(LARGURA, d.height);
    }

    /** Chamado pelo relogio da janela; so texto, sem recriar componente. */
    public void atualizarEstado() {
        var c = cliente.getConfig();
        escuta.setText(c.grupoVisao() + ":" + c.portaVisao());

        if (cliente.getFalha() != null) {
            taxa.setForeground(Paleta.ERRO);
            taxa.setText(encurtar(cliente.getFalha()));
        } else if (cliente.recebendo()) {
            taxa.setForeground(Paleta.OK);
            taxa.setText(String.format("%.0f Hz  ·  %d pacotes",
                    cliente.taxaHz(), cliente.pacotesRecebidos()));
        } else {
            taxa.setForeground(Paleta.ALERTA);
            taxa.setText(cliente.pacotesRecebidos() == 0
                    ? "nada recebido ainda"
                    : String.format("parado ha %.0f s", cliente.silencio()));
        }

        envio.setText(cliente.destinoDeComandos() + "  ·  "
                + cliente.pacotesEnviados() + " pacotes");

        if (caixaEstrategia.isSelected() != cliente.isEstrategiaLigada()) {
            caixaEstrategia.setSelected(cliente.isEstrategiaLigada());
        }
        if (!cliente.isEstrategiaLigada()) {
            estadoEstrategia.setForeground(Paleta.APAGADO);
            estadoEstrategia.setText("desligada");
        } else {
            var play = cliente.getExecutor().coach().currentPlay();
            estadoEstrategia.setForeground(play == null ? Paleta.ALERTA : Paleta.OK);
            estadoEstrategia.setText(play == null
                    ? "nenhuma play com pre-condicao"
                    : play.strName() + "  ·  " + String.format("%.0fs", play.dTempoRestante()));
        }

        painelArbitro.atualizar();
    }

    private static String encurtar(String texto) {
        return "<html><body style='width:200px'>"
                + (texto.length() > 90 ? texto.substring(0, 90) + "..." : texto)
                + "</body></html>";
    }

    // -------------------------------------------------------------- estilo

    private static JButton botao(String texto, Runnable ao) {
        JButton b = new JButton(texto);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        b.addActionListener(e -> ao.run());
        return b;
    }

    private static JComponent rodape() {
        return new Dicas("Com a estrategia desligada nenhum comando sai, e os robos ficam "
                + "parados, como no grSim.\n\nscroll aplica zoom, arraste para mover o campo");
    }

    /**
     * Texto corrido que quebra na largura que a coluna tiver no momento.
     *
     * <p>Um {@link JLabel} com HTML exigiria uma largura fixa no estilo, e a
     * coluna agora e arrastavel: a quebra sobrava numa coluna larga e vazava numa
     * estreita. Um {@link JTextArea} quebra sozinho, mas dentro de um
     * {@code BoxLayout} ele pede a altura do texto SEM quebra -- uma linha so, com
     * o resto cortado embaixo. Por isso a altura preferida e recalculada na
     * largura corrente, e nao na preferida.
     */
    private static final class Dicas extends JTextArea {

        Dicas(String texto) {
            super(texto);
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(null);
            setForeground(new Color(120, 120, 130));
            setFont(getFont().deriveFont(10f));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() {
            if (getWidth() > 0) setSize(getWidth(), Short.MAX_VALUE);
            return super.getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static JComponent titulo(String texto) {
        JLabel l = rotulo(texto, Paleta.TEXTO);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return l;
    }

    private static JLabel rotulo(String texto, Color cor) {
        JLabel l = new JLabel(texto);
        l.setForeground(cor);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setFont(l.getFont().deriveFont(11f));
        return l;
    }
}
