package app;

import rede.ConfigRede;
import view.Paleta;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;

/**
 * Janela de configuracao das portas, aplicavel com a estrategia rodando.
 *
 * <p>Mesma forma do dialogo do simulador, de proposito: quem opera os dois na
 * mesma bancada nao deveria ter de aprender duas telas para fazer a mesma coisa.
 *
 * <p>A validacao acontece em duas camadas. {@link ConfigRede#problema()} rejeita
 * o que da para saber sem tocar no sistema -- endereco fora da faixa multicast,
 * porta fora da faixa, azul e amarelo na mesma porta. Ja "a porta esta ocupada
 * por outro processo" so aparece na hora do bind, e chega aqui como excecao de
 * {@link Cliente#reconfigurar}, que ja restaurou a configuracao anterior antes
 * de lancar.
 */
public final class DialogoRede extends JDialog {

    private final Cliente cliente;

    private final JTextField grupo = new JTextField(14);
    private final JTextField portaVisao = new JTextField(6);
    private final JTextField grupoArbitro = new JTextField(14);
    private final JTextField portaArbitro = new JTextField(6);
    private final JTextField host = new JTextField(14);
    private final JTextField portaAzul = new JTextField(6);
    private final JTextField portaAmarelo = new JTextField(6);
    private final JLabel mensagem = new JLabel(" ");

    private DialogoRede(Window dono, Cliente cliente) {
        super(dono, "Rede", ModalityType.APPLICATION_MODAL);
        this.cliente = cliente;

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 8));
        campos.setBackground(Paleta.PAINEL);
        campos.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        linha(campos, "Grupo multicast da visao", grupo);
        linha(campos, "Porta da visao", portaVisao);
        linha(campos, "Grupo do arbitro (GC)", grupoArbitro);
        linha(campos, "Porta do arbitro", portaArbitro);
        linha(campos, "Host do simulador", host);
        linha(campos, "Porta RobotControl azul", portaAzul);
        linha(campos, "Porta RobotControl amarelo", portaAmarelo);

        mensagem.setForeground(Paleta.TEXTO);
        mensagem.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        JButton aplicar = new JButton("Aplicar");
        aplicar.addActionListener(e -> aplicar());
        JButton restaurar = new JButton("Restaurar padrao");
        restaurar.addActionListener(e -> {
            preencher(ConfigRede.padrao());
            dizer("valores padrao carregados, clique em Aplicar", Paleta.TEXTO);
        });
        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dispose());

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        botoes.setBackground(Paleta.PAINEL);
        botoes.add(restaurar);
        botoes.add(aplicar);
        botoes.add(fechar);

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setBackground(Paleta.PAINEL);
        corpo.add(campos);
        corpo.add(mensagem);
        corpo.add(Box.createVerticalStrut(4));

        setLayout(new BorderLayout());
        add(corpo, BorderLayout.CENTER);
        add(botoes, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(aplicar);

        preencher(cliente.getConfig());
        dizer("aplicar reabre os sockets; a visao pisca por um instante",
                new Color(150, 150, 160));

        pack();
        setMinimumSize(new Dimension(440, getHeight()));
        setLocationRelativeTo(dono);
    }

    public static void abrir(Component origem, Cliente cliente) {
        new DialogoRede(SwingUtilities.getWindowAncestor(origem), cliente).setVisible(true);
    }

    private void aplicar() {
        ConfigRede nova;
        try {
            nova = new ConfigRede(grupo.getText().trim(),
                    inteiro(portaVisao, "porta da visao"),
                    grupoArbitro.getText().trim(),
                    inteiro(portaArbitro, "porta do arbitro"),
                    host.getText().trim(),
                    inteiro(portaAzul, "porta RobotControl azul"),
                    inteiro(portaAmarelo, "porta RobotControl amarelo"));
        } catch (IllegalArgumentException e) {
            dizer(e.getMessage(), Paleta.ERRO);
            return;
        }

        try {
            // A cor da equipe nao se mexe aqui: ela decide para qual porta
            // mandamos, mas e escolha de quem somos, nao de configuracao de rede.
            cliente.reconfigurar(nova, cliente.getEquipe());
            dizer("aplicado: escutando " + nova.grupoVisao() + ":" + nova.portaVisao()
                    + ", mandando em " + cliente.destinoDeComandos(), Paleta.OK);
        } catch (IOException e) {
            dizer(e.getMessage(), Paleta.ERRO);
            preencher(cliente.getConfig()); // mostra o que ficou valendo de fato
        }
    }

    private static int inteiro(JTextField campo, String nome) {
        try {
            return Integer.parseInt(campo.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + ": \"" + campo.getText().trim()
                    + "\" nao e um numero");
        }
    }

    private void preencher(ConfigRede c) {
        grupo.setText(c.grupoVisao());
        portaVisao.setText(String.valueOf(c.portaVisao()));
        grupoArbitro.setText(c.grupoArbitro());
        portaArbitro.setText(String.valueOf(c.portaArbitro()));
        host.setText(c.hostComandos());
        portaAzul.setText(String.valueOf(c.portaAzul()));
        portaAmarelo.setText(String.valueOf(c.portaAmarelo()));
    }

    private void dizer(String texto, Color cor) {
        mensagem.setForeground(cor);
        mensagem.setText("<html><body style='width:360px'>" + texto + "</body></html>");
        pack();
    }

    private static void linha(JPanel painel, String rotulo, JTextField campo) {
        JLabel l = new JLabel(rotulo);
        l.setForeground(Paleta.TEXTO);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        painel.add(l);
        painel.add(campo);
    }
}
