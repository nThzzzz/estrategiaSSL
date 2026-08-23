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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;

/**
 * Painel da esquerda: estado da rede, quem somos e o que aparece no campo.
 *
 * <p>As portas nao moram aqui: ficam no {@link DialogoRede}, atras do botao
 * "Configurar...", no mesmo desenho que o simulador usa. Cinco campos de rede
 * ocupavam metade do painel para algo que se mexe uma vez por bancada, enquanto
 * o que se olha o tempo todo -- se esta chegando visao, a que taxa, para onde
 * vao os comandos -- ficava espremido embaixo.
 *
 * <p>Cor da equipe e nomes continuam aqui porque nao sao configuracao de rede:
 * dizem quem somos. A cor decide para qual porta os comandos vao, mas essa e
 * consequencia da escolha, nao a escolha.
 */
public final class PainelRede extends JPanel {

    private final Cliente cliente;
    private final Campo campo;

    private final JComboBox<String> nossaCor = new JComboBox<>(new String[]{"Azul", "Amarelo"});
    private final JTextField nomeAzul = new JTextField(12);
    private final JTextField nomeAmarelo = new JTextField(12);

    private final JLabel escuta = rotulo("--", Paleta.APAGADO);
    private final JLabel taxa = rotulo("--", Paleta.APAGADO);
    private final JLabel envio = rotulo("--", Paleta.APAGADO);

    private final JComboBox<String> fonteArbitro =
            new JComboBox<>(new String[]{"Game Controller", "local (teste)"});
    private final JLabel estadoArbitro = rotulo("--", Paleta.APAGADO);
    private final JLabel estadoEstrategia = rotulo("desligada", Paleta.APAGADO);
    private final JCheckBox caixaEstrategia = new JCheckBox("comandar os robos");

    public PainelRede(Cliente cliente, Campo campo) {
        this.cliente = cliente;
        this.campo = campo;

        caixaEstrategia.setBackground(Paleta.PAINEL);
        caixaEstrategia.setForeground(Paleta.TEXTO);
        caixaEstrategia.setFont(caixaEstrategia.getFont().deriveFont(11f));
        caixaEstrategia.setAlignmentX(Component.LEFT_ALIGNMENT);
        caixaEstrategia.addActionListener(e ->
                cliente.setEstrategiaLigada(caixaEstrategia.isSelected()));

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Paleta.PAINEL);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(238, 100));

        carregar();

        add(titulo("Rede"));
        add(escuta);
        add(taxa);
        add(Box.createVerticalStrut(6));
        add(rotulo("comandos", Paleta.APAGADO));
        add(envio);
        add(Box.createVerticalStrut(10));

        JButton configurar = new JButton("Configurar...");
        configurar.setAlignmentX(Component.LEFT_ALIGNMENT);
        configurar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        configurar.addActionListener(e -> DialogoRede.abrir(campo, cliente));
        add(configurar);

        add(Box.createVerticalStrut(20));
        add(titulo("Arbitro"));
        add(linha("fonte", fonteArbitro));
        add(estadoArbitro);
        add(Box.createVerticalStrut(8));

        JButton simular = new JButton("Simular...");
        simular.setAlignmentX(Component.LEFT_ALIGNMENT);
        simular.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        simular.addActionListener(e -> DialogoArbitro.abrir(campo, cliente));
        add(simular);

        fonteArbitro.addActionListener(e -> {
            cliente.getArbitro().setModoLocal(fonteArbitro.getSelectedIndex() == 1);
            atualizarEstado();
        });

        add(Box.createVerticalStrut(20));
        add(titulo("Equipe"));
        add(linha("jogamos de", nossaCor));
        add(linha("nome azul", nomeAzul));
        add(linha("nome amarelo", nomeAmarelo));
        add(Box.createVerticalStrut(8));

        JButton aplicar = new JButton("Aplicar");
        aplicar.setAlignmentX(Component.LEFT_ALIGNMENT);
        aplicar.addActionListener(e -> aplicarEquipe());
        add(aplicar);

        add(Box.createVerticalStrut(20));
        add(titulo("Estrategia"));
        add(caixaEstrategia);
        add(estadoEstrategia);
        add(Box.createVerticalStrut(8));

        JButton log = new JButton("Ver log...");
        log.setAlignmentX(Component.LEFT_ALIGNMENT);
        log.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        log.addActionListener(e -> JanelaLog.abrir(campo, cliente));
        add(log);

        add(Box.createVerticalStrut(20));
        add(titulo("Exibicao"));
        add(caixa("vetores de velocidade", true, campo::setMostrarVetores));
        add(caixa("area do sensor de bola", true, campo::setMostrarSensor));
        add(caixa("incerteza do Kalman", true, campo::setMostrarIncerteza));
        add(caixa("prever ate o desenho", true, campo::setPreverAteODesenho));
        add(caixa("fantasma de 1 s", true, campo::setMostrarFantasma));
        add(Box.createVerticalStrut(8));

        JButton enquadrar = new JButton("Reenquadrar campo");
        enquadrar.setAlignmentX(Component.LEFT_ALIGNMENT);
        enquadrar.addActionListener(e -> { campo.enquadrar(); campo.repaint(); });
        add(enquadrar);

        add(Box.createVerticalGlue());
        add(rodape());

        atualizarEstado(); // sem isso os rotulos ficam em "--" ate o primeiro tique
    }

    /**
     * Recarrega os controles a partir do estado real, e nao do que o painel
     * presume ter configurado. A fonte do arbitro pode ter sido trocada por fora
     * -- por exemplo por um teste ou pela linha de comando -- e um seletor que
     * mente sobre o modo em que se esta e pior do que nao ter seletor.
     */
    private void carregar() {
        nossaCor.setSelectedIndex(cliente.getEquipe() == Cor.AZUL ? 0 : 1);
        nomeAzul.setText(cliente.getNomeAzul());
        nomeAmarelo.setText(cliente.getNomeAmarelo());
        fonteArbitro.setSelectedIndex(cliente.getArbitro().isModoLocal() ? 1 : 0);
        caixaEstrategia.setSelected(cliente.isEstrategiaLigada());
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

        var jogo = cliente.estadoDeJogo();
        boolean local = cliente.getArbitro().isModoLocal();
        if (fonteArbitro.getSelectedIndex() != (local ? 1 : 0)) {
            fonteArbitro.setSelectedIndex(local ? 1 : 0);
        }
        if (local) {
            estadoArbitro.setForeground(jogo.semArbitro() ? Paleta.APAGADO : Paleta.OK);
            estadoArbitro.setText(jogo.semArbitro() ? "nenhum comando ainda" : jogo.descricao());
        } else if (cliente.recebendoArbitro()) {
            estadoArbitro.setForeground(Paleta.OK);
            estadoArbitro.setText(jogo.descricao() + "  ·  " + cliente.pacotesArbitro() + " pacotes");
        } else {
            estadoArbitro.setForeground(Paleta.ALERTA);
            estadoArbitro.setText(cliente.pacotesArbitro() == 0
                    ? "GC nao encontrado em " + c.grupoArbitro() + ":" + c.portaArbitro()
                    : "GC calado ha " + String.format("%.0f s", cliente.getArbitro().silencio()));
        }
    }

    private static String encurtar(String texto) {
        return "<html><body style='width:200px'>"
                + (texto.length() > 90 ? texto.substring(0, 90) + "..." : texto)
                + "</body></html>";
    }

    // -------------------------------------------------------------- estilo

    private JComponent rodape() {
        JLabel l = rotulo("<html><body style='width:200px'>Com a estrategia desligada nenhum "
                + "comando sai, e os robos ficam parados, como no grSim."
                + "<br><br>scroll aplica zoom, botao direito arrasta</body></html>",
                new Color(120, 120, 130));
        l.setFont(l.getFont().deriveFont(10f));
        return l;
    }

    private JPanel linha(String nome, JComponent campo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel l = rotulo(nome, Paleta.APAGADO);
        l.setPreferredSize(new Dimension(92, 22));
        l.setMaximumSize(new Dimension(92, 22));

        campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(l);
        p.add(campo);
        return p;
    }

    private JCheckBox caixa(String texto, boolean marcada, java.util.function.Consumer<Boolean> ao) {
        JCheckBox c = new JCheckBox(texto, marcada);
        c.setBackground(Paleta.PAINEL);
        c.setForeground(Paleta.TEXTO);
        c.setFont(c.getFont().deriveFont(11f));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.addActionListener(e -> { ao.accept(c.isSelected()); campo.repaint(); });
        return c;
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
