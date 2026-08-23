package app;

import jogo.EstadoDeJogo;
import model.Cor;
import proto.gc.SslGcRefereeMessage.Referee.Command;
import view.Paleta;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Os comandos de arbitro, na coluna da esquerda.
 *
 * <p>Antes moravam num dialogo separado, atras de um botao "Simular...". Numa
 * bancada isso e uma janela a mais para abrir, posicionar e reencontrar toda vez
 * que se quer um STOP -- e STOP e o comando que mais se aperta. Aqui eles ficam
 * a um clique, ao lado do estado que eles mesmos mudam.
 *
 * <p>Os botoes montam uma mensagem {@code Referee} de verdade e a entregam pelo
 * mesmo caminho de traducao que a rede usa. Nao e um atalho que injeta o estado
 * ja pronto: se um comando estiver mapeado errado, erra aqui igual erraria em
 * jogo.
 *
 * <p>Os comandos com dono aparecem como NOSSO e DELES, e nao como "azul" e
 * "amarelo". E o mesmo motivo pelo qual {@link jogo.Acao} nao tem cor: na hora
 * de testar interessa saber se a estrategia se comporta como dona da jogada ou
 * como quem tem de dar espaco, e a cor e detalhe de configuracao. Quem resolve a
 * cor e o clique, e nao a montagem, entao trocar de lado nao exige remontar
 * botao nenhum.
 *
 * <p>Em modo REDE os botoes ficam DESABILITADOS, e nao apenas inertes. No
 * dialogo antigo bastava uma linha de aviso, porque quem abria a janela tinha
 * acabado de escolher abri-la; numa coluna sempre visivel, dezessete botoes que
 * parecem funcionar e nao fazem nada sao pior que aviso nenhum.
 */
public final class PainelArbitro extends JPanel {

    private final Cliente cliente;

    private final JComboBox<String> fonte =
            new JComboBox<>(new String[]{"Game Controller", "local (teste)"});
    private final JLabel estado = rotulo("--", Paleta.APAGADO);

    /** Tudo que so faz sentido com o arbitro local no comando. */
    private final List<JButton> botoesLocais = new ArrayList<>();

    /**
     * Ligado enquanto o seletor esta sendo alinhado ao estado real.
     *
     * <p>Espelhar nao pode virar comando: sem esta trava, o {@code
     * setSelectedIndex} do alinhamento dispara o mesmo ouvinte do clique humano e
     * a interface acaba MANDANDO no arbitro exatamente quando queria so
     * refleti-lo.
     */
    private boolean sincronizando;

    public PainelArbitro(Cliente cliente) {
        this.cliente = cliente;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Paleta.PAINEL);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        add(linha("fonte", fonte));
        add(estado);
        add(Box.createVerticalStrut(8));

        JPanel neutros = grade(2);
        neutros.add(botao("HALT", Command.HALT));
        neutros.add(botao("STOP", Command.STOP));
        neutros.add(botao("START", Command.NORMAL_START));
        neutros.add(botao("FORCE", Command.FORCE_START));
        add(fixar(neutros));

        add(Box.createVerticalStrut(8));
        add(cabecalhoDeDono());

        JPanel comDono = grade(3);
        par(comDono, "kickoff", Command.PREPARE_KICKOFF_BLUE, Command.PREPARE_KICKOFF_YELLOW);
        par(comDono, "penalti", Command.PREPARE_PENALTY_BLUE, Command.PREPARE_PENALTY_YELLOW);
        par(comDono, "falta", Command.DIRECT_FREE_BLUE, Command.DIRECT_FREE_YELLOW);
        par(comDono, "bola", Command.BALL_PLACEMENT_BLUE, Command.BALL_PLACEMENT_YELLOW);
        par(comDono, "tempo", Command.TIMEOUT_BLUE, Command.TIMEOUT_YELLOW);
        add(fixar(comDono));

        add(Box.createVerticalStrut(8));

        JPanel gols = grade(2);
        gols.add(acao("Gol nosso", () -> {
            cliente.getArbitroLocal().marcarGol(cliente.getEquipe());
            cliente.simularArbitro(Command.STOP);
        }));
        gols.add(acao("Gol deles", () -> {
            cliente.getArbitroLocal().marcarGol(cliente.getAdversario());
            cliente.simularArbitro(Command.STOP);
        }));
        add(fixar(gols));

        JPanel lado = grade(1);
        lado.add(acao("Trocar de lado", () -> {
            var local = cliente.getArbitroLocal();
            local.setAzulNoLadoPositivo(!local.isAzulNoLadoPositivo());
            cliente.simularArbitro(Command.STOP);
        }));
        add(fixar(lado));

        fonte.addActionListener(e -> {
            if (sincronizando) return;
            cliente.getArbitro().setModoLocal(fonte.getSelectedIndex() == 1);
            atualizar();
        });

        atualizar();
    }

    /**
     * Recarrega o estado e liga ou desliga os botoes.
     *
     * <p>Le a fonte do arbitro do proprio arbitro, e nao do que este painel
     * presume ter configurado: o modo pode ter sido trocado por fora, pela linha
     * de comando ou por um teste, e um seletor que mente sobre o modo em que se
     * esta e pior do que nao ter seletor.
     */
    public void atualizar() {
        EstadoDeJogo jogo = cliente.estadoDeJogo();
        boolean local = cliente.getArbitro().isModoLocal();

        if (fonte.getSelectedIndex() != (local ? 1 : 0)) {
            sincronizando = true;
            fonte.setSelectedIndex(local ? 1 : 0);
            sincronizando = false;
        }
        for (JButton b : botoesLocais) b.setEnabled(local);

        if (local) {
            estado.setForeground(jogo.semArbitro() ? Paleta.APAGADO : Paleta.OK);
            estado.setText(duasLinhas(
                    jogo.semArbitro() ? "nenhum comando ainda" : jogo.descricao(),
                    "nosso gol em " + (jogo.defendemosLadoPositivo() ? "+x" : "-x")));
        } else if (cliente.recebendoArbitro()) {
            estado.setForeground(Paleta.OK);
            estado.setText(duasLinhas(jogo.descricao(),
                    cliente.pacotesArbitro() + " pacotes"));
        } else {
            var c = cliente.getConfig();
            estado.setForeground(Paleta.ALERTA);
            estado.setText(cliente.pacotesArbitro() == 0
                    ? duasLinhas("GC nao encontrado em",
                            c.grupoArbitro() + ":" + c.portaArbitro())
                    : duasLinhas("GC calado",
                            String.format("ha %.0f s", cliente.getArbitro().silencio())));
        }
    }

    /**
     * Estado sempre em duas linhas curtas, e nunca numa longa.
     *
     * <p>Na coluna cabem cerca de 220 px, e "BALL PLACEMENT deles · nosso gol em
     * -x" nao cabe: um JLabel com HTML sem largura no estilo nao quebra, ele
     * transborda e some na borda. Duas linhas fixas tambem seguram a altura, entao
     * os botoes logo abaixo nao pulam a cada troca de comando.
     */
    private static String duasLinhas(String principal, String detalhe) {
        return "<html>" + principal + "<br>" + detalhe + "</html>";
    }

    // -------------------------------------------------------------- botoes

    private void par(JPanel painel, String nome, Command azul, Command amarelo) {
        painel.add(rotulo(nome, Paleta.APAGADO));
        painel.add(botaoDeLado("nosso", azul, amarelo, true));
        painel.add(botaoDeLado("deles", azul, amarelo, false));
    }

    private JButton botao(String texto, Command comando) {
        return acao(texto, () -> cliente.simularArbitro(comando));
    }

    private JButton botaoDeLado(String texto, Command azul, Command amarelo, boolean nosso) {
        return acao(texto, () -> {
            boolean somosAzul = cliente.getEquipe() == Cor.AZUL;
            cliente.simularArbitro(somosAzul == nosso ? azul : amarelo);
        });
    }

    private JButton acao(String texto, Runnable ao) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(10f));
        b.setMargin(new Insets(2, 2, 2, 2));
        b.setFocusPainted(false);
        b.addActionListener(e -> { ao.run(); atualizar(); });
        botoesLocais.add(b);
        return b;
    }

    // -------------------------------------------------------------- estilo

    private static JComponent cabecalhoDeDono() {
        JLabel l = rotulo("com dono", new Color(120, 120, 130));
        l.setFont(l.getFont().deriveFont(10f));
        return l;
    }

    private static JPanel grade(int colunas) {
        JPanel p = new JPanel(new GridLayout(0, colunas, 4, 4));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Trava a altura da grade: sem isso o BoxLayout a estica e os botoes incham. */
    private static JComponent fixar(JPanel p) {
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JPanel linha(String nome, JComponent controle) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel l = rotulo(nome, Paleta.APAGADO);
        l.setPreferredSize(new Dimension(42, 22));
        l.setMaximumSize(new Dimension(42, 22));

        controle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(l);
        p.add(controle);
        return p;
    }

    private static JLabel rotulo(String texto, Color cor) {
        JLabel l = new JLabel(texto);
        l.setForeground(cor);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setFont(l.getFont().deriveFont(11f));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        return l;
    }
}
