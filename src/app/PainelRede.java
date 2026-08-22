package app;

import model.Cor;
import rede.ConfigRede;
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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;

/**
 * Painel da esquerda: para onde a estrategia escuta e para onde ela manda.
 *
 * <p>Tudo aqui pode ser trocado com a janela aberta. Numa bancada de teste
 * troca-se de porta o tempo todo -- duas instancias do simulador, um colega
 * rodando outra estrategia na mesma rede -- e obrigar a fechar o programa para
 * mudar um numero tornaria isso insuportavel.
 *
 * <p>A validacao segue a mesma divisao do simulador: {@link ConfigRede#problema()}
 * responde o que da para saber sem tocar no sistema e o erro aparece antes de
 * qualquer socket ser mexido; o que so aparece no bind -- porta ocupada -- vira
 * dialogo com a configuracao anterior ja restaurada por {@link Cliente}.
 */
public final class PainelRede extends JPanel {

    private final Cliente cliente;
    private final Campo campo;

    private final JTextField grupo = campoTexto(12);
    private final JSpinner portaVisao = porta(ConfigRede.VISAO_PORTA);
    private final JTextField host = campoTexto(12);
    private final JSpinner portaAzul = porta(ConfigRede.CONTROLE_AZUL_PORTA);
    private final JSpinner portaAmarelo = porta(ConfigRede.CONTROLE_AMARELO_PORTA);

    private final JComboBox<String> nossaCor = new JComboBox<>(new String[]{"Azul", "Amarelo"});
    private final JTextField nomeAzul = campoTexto(12);
    private final JTextField nomeAmarelo = campoTexto(12);

    private final JLabel estadoVisao = rotulo("--", Paleta.APAGADO);
    private final JLabel estadoEnvio = rotulo("--", Paleta.APAGADO);

    public PainelRede(Cliente cliente, Campo campo) {
        this.cliente = cliente;
        this.campo = campo;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Paleta.PAINEL);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(238, 100));

        carregar();

        add(titulo("Visao  (entrada)"));
        add(linha("grupo", grupo));
        add(linha("porta", portaVisao));
        add(estadoVisao);

        add(espaco(16));
        add(titulo("Comandos  (saida)"));
        add(linha("host", host));
        add(linha("porta azul", portaAzul));
        add(linha("porta amarelo", portaAmarelo));
        add(estadoEnvio);

        add(espaco(16));
        add(titulo("Equipe"));
        add(linha("jogamos de", nossaCor));
        add(linha("nome azul", nomeAzul));
        add(linha("nome amarelo", nomeAmarelo));

        add(espaco(14));
        JButton aplicar = new JButton("Aplicar");
        aplicar.setAlignmentX(Component.LEFT_ALIGNMENT);
        aplicar.addActionListener(e -> aplicar());
        add(aplicar);

        add(espaco(18));
        add(titulo("Exibicao"));
        add(caixa("vetores de velocidade", true, campo::setMostrarVetores));
        add(caixa("area do sensor de bola", true, campo::setMostrarSensor));
        add(caixa("incerteza do Kalman", true, campo::setMostrarIncerteza));

        JButton enquadrar = new JButton("Reenquadrar campo");
        enquadrar.setAlignmentX(Component.LEFT_ALIGNMENT);
        enquadrar.addActionListener(e -> { campo.enquadrar(); campo.repaint(); });
        add(espaco(8));
        add(enquadrar);

        add(Box.createVerticalGlue());
        add(rodape());

        atualizarEstado(); // sem isso os rotulos ficam em "--" ate o primeiro tique
    }

    /** Recarrega os campos a partir do que o cliente esta usando de fato. */
    private void carregar() {
        ConfigRede c = cliente.getConfig();
        grupo.setText(c.grupoVisao());
        portaVisao.setValue(c.portaVisao());
        host.setText(c.hostComandos());
        portaAzul.setValue(c.portaAzul());
        portaAmarelo.setValue(c.portaAmarelo());
        nossaCor.setSelectedIndex(cliente.getEquipe() == Cor.AZUL ? 0 : 1);
        nomeAzul.setText(cliente.getNomeAzul());
        nomeAmarelo.setText(cliente.getNomeAmarelo());
    }

    private void aplicar() {
        cliente.setNomes(nomeAzul.getText(), nomeAmarelo.getText());

        ConfigRede nova = new ConfigRede(
                grupo.getText().trim(), (Integer) portaVisao.getValue(),
                host.getText().trim(), (Integer) portaAzul.getValue(),
                (Integer) portaAmarelo.getValue());
        Cor cor = nossaCor.getSelectedIndex() == 0 ? Cor.AZUL : Cor.AMARELO;

        try {
            cliente.reconfigurar(nova, cor);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Configuracao de rede", JOptionPane.WARNING_MESSAGE);
        }
        carregar(); // o cliente pode ter voltado para a config anterior
    }

    /** Chamado pelo relogio da janela; so texto, sem recriar componente. */
    public void atualizarEstado() {
        if (cliente.recebendo()) {
            estadoVisao.setForeground(Paleta.OK);
            estadoVisao.setText(String.format("%.0f Hz  ·  %d pacotes",
                    cliente.taxaHz(), cliente.pacotesRecebidos()));
        } else {
            estadoVisao.setForeground(Paleta.ALERTA);
            estadoVisao.setText(cliente.pacotesRecebidos() == 0
                    ? "nada recebido ainda"
                    : String.format("parado ha %.0f s", cliente.silencio()));
        }

        estadoEnvio.setForeground(Paleta.APAGADO);
        estadoEnvio.setText(cliente.destinoDeComandos() + "  ·  "
                + cliente.pacotesEnviados() + " pacotes");
    }

    // -------------------------------------------------------------- estilo

    private JComponent rodape() {
        JLabel l = rotulo("<html><body style='width:200px'>Sem estrategia escrita nenhum "
                + "comando sai, e os robos ficam parados -- como no grSim.</body></html>",
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
        l.setFont(l.getFont().deriveFont(11f));

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

    private static Component espaco(int altura) { return Box.createVerticalStrut(altura); }

    private static JTextField campoTexto(int colunas) {
        JTextField t = new JTextField(colunas);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        return t;
    }

    /** Spinner de porta sem separador de milhar: 10006, nunca "10.006". */
    private static JSpinner porta(int inicial) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(inicial, 1024, 65535, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(s, "#");
        s.setEditor(editor);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        return s;
    }
}
