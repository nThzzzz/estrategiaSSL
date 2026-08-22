package app;

import jogo.EstadoDeJogo;
import model.Cor;
import proto.gc.SslGcRefereeMessage.Referee.Command;
import view.Paleta;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;

/**
 * Arbitro de bancada: manda comandos sem precisar do Game Controller no ar.
 *
 * <p>Os botoes montam uma mensagem {@code Referee} de verdade e a entregam pelo
 * mesmo caminho de traducao que a rede usa. Nao e um atalho que injeta o estado
 * ja pronto: se um comando estiver mapeado errado, erra aqui igual erraria em
 * jogo.
 *
 * <p>Os comandos com dono aparecem em duas colunas, NOSSO e DELES, e nao como
 * "azul" e "amarelo". E o mesmo motivo pelo qual {@link jogo.Acao} nao tem cor:
 * na hora de testar interessa saber se a estrategia se comporta como dona da
 * jogada ou como quem tem de dar espaco, e a cor e um detalhe de configuracao.
 */
public final class DialogoArbitro extends JDialog {

    private final Cliente cliente;
    private final JLabel estado = new JLabel(" ");

    private DialogoArbitro(Window dono, Cliente cliente) {
        super(dono, "Arbitro de teste", ModalityType.MODELESS);
        this.cliente = cliente;

        JPanel neutros = grade("Sem dono");
        neutros.add(botao("HALT", Command.HALT));
        neutros.add(botao("STOP", Command.STOP));
        neutros.add(botao("NORMAL START", Command.NORMAL_START));
        neutros.add(botao("FORCE START", Command.FORCE_START));

        JPanel comDono = grade("Com dono");
        par(comDono, "Kickoff", Command.PREPARE_KICKOFF_BLUE, Command.PREPARE_KICKOFF_YELLOW);
        par(comDono, "Penalti", Command.PREPARE_PENALTY_BLUE, Command.PREPARE_PENALTY_YELLOW);
        par(comDono, "Falta", Command.DIRECT_FREE_BLUE, Command.DIRECT_FREE_YELLOW);
        par(comDono, "Posicionar bola", Command.BALL_PLACEMENT_BLUE, Command.BALL_PLACEMENT_YELLOW);
        par(comDono, "Tempo", Command.TIMEOUT_BLUE, Command.TIMEOUT_YELLOW);

        JButton gol = new JButton("Gol nosso");
        gol.addActionListener(e -> {
            cliente.getArbitroLocal().marcarGol(cliente.getEquipe());
            cliente.simularArbitro(Command.STOP);
        });
        JButton golDeles = new JButton("Gol deles");
        golDeles.addActionListener(e -> {
            cliente.getArbitroLocal().marcarGol(cliente.getAdversario());
            cliente.simularArbitro(Command.STOP);
        });
        JButton lado = new JButton("Trocar de lado");
        lado.addActionListener(e -> {
            var local = cliente.getArbitroLocal();
            local.setAzulNoLadoPositivo(!local.isAzulNoLadoPositivo());
            cliente.simularArbitro(Command.STOP);
        });

        JPanel extras = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        extras.setBackground(Paleta.PAINEL);
        extras.add(gol);
        extras.add(golDeles);
        extras.add(lado);

        estado.setForeground(Paleta.TEXTO);
        estado.setFont(estado.getFont().deriveFont(11f));
        estado.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setBackground(Paleta.PAINEL);
        corpo.add(neutros);
        corpo.add(Box.createVerticalStrut(6));
        corpo.add(comDono);
        corpo.add(Box.createVerticalStrut(6));
        corpo.add(extras);
        corpo.add(estado);

        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dispose());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rodape.setBackground(Paleta.PAINEL);
        rodape.add(fechar);

        setLayout(new BorderLayout());
        add(corpo, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        Timer relogio = new Timer(200, e -> atualizar());
        relogio.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) { relogio.stop(); }
        });

        atualizar();
        pack();
        setMinimumSize(new Dimension(440, getHeight()));
        setLocationRelativeTo(dono);
    }

    public static void abrir(Component origem, Cliente cliente) {
        new DialogoArbitro(SwingUtilities.getWindowAncestor(origem), cliente).setVisible(true);
    }

    /**
     * Traduz "nosso"/"deles" para a cor certa na hora do clique.
     *
     * <p>Resolver a cor no clique, e nao na montagem, e o que faz o dialogo
     * continuar correto depois de trocar de lado no painel da esquerda sem
     * precisar remontar botao nenhum.
     */
    private void par(JPanel painel, String nome, Command azul, Command amarelo) {
        painel.add(botaoDeLado(nome + " nosso", azul, amarelo, true));
        painel.add(botaoDeLado(nome + " deles", azul, amarelo, false));
    }

    private JButton botao(String texto, Command comando) {
        JButton b = new JButton(texto);
        b.addActionListener(e -> cliente.simularArbitro(comando));
        return b;
    }

    private JButton botaoDeLado(String texto, Command azul, Command amarelo, boolean nosso) {
        JButton b = new JButton(texto);
        b.addActionListener(e -> {
            boolean somosAzul = cliente.getEquipe() == Cor.AZUL;
            cliente.simularArbitro(somosAzul == nosso ? azul : amarelo);
        });
        return b;
    }

    private void atualizar() {
        EstadoDeJogo j = cliente.estadoDeJogo();
        boolean local = cliente.getArbitro().isModoLocal();
        estado.setForeground(local ? Paleta.OK : Paleta.ALERTA);
        estado.setText(local
                ? String.format("<html>valendo: <b>%s</b> · nosso gol em %s · comando %d</html>",
                        j.descricao(), j.defendemosLadoPositivo() ? "+x" : "-x", j.contador())
                : "<html>o arbitro esta em modo REDE: estes botoes nao tem efeito. "
                        + "Troque no painel da esquerda.</html>");
    }

    private static JPanel grade(String titulo) {
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.setBackground(Paleta.PAINEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 12, 0, 12),
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.BORDA), titulo,
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 11), Paleta.APAGADO)));
        return p;
    }

}
