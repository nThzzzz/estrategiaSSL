package app.componentes;

import app.Cliente;

import view.Paleta;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
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

    /**
     * Aponta os comandos para a maquina de onde a visao esta vindo.
     *
     * <p>Nasce escondido e so aparece quando os dois enderecos divergem. Existe
     * porque o aviso sozinho nao bastava: ele dizia que estava errado sem dizer
     * o que fazer, e o campo que resolve mora atras de dois cliques, noutra
     * janela, com nome que nao lembra o problema.
     *
     * <p>E um botao, e nao correcao automatica, de proposito: ha arranjos
     * legitimos em que a visao vem de uma maquina -- a ssl-vision -- e os
     * comandos vao para outra. Trocar sozinho acertaria a bancada e quebraria a
     * competicao.
     */
    private final JButton apontarComandos = new JButton();

    /** O aviso de destino errado, junto do botao que o resolve. */
    private final JLabel aviso = rotulo(" ", Paleta.ALERTA);

    /** Guarda o lugar do aviso e do botao mesmo quando nao estao visiveis. */
    private final JPanel espacoDoBotao = new JPanel();

    private static final int ALTURA_DA_LINHA = 17;
    /**
     * O bloco do aviso: duas linhas de texto mais o botao.
     *
     * <p>Reservado sempre, e por isso fica um vao quando nao ha o que avisar. E o
     * preco de nao quicar, e ele e menor que a alternativa: com o aviso e o botao
     * entrando e saindo do BoxLayout, TUDO abaixo saltava -- incluindo o painel do
     * arbitro inteiro -- justamente quando se esta olhando para a tela.
     */
    private static final int ALTURA_DO_BOTAO = 78;

    private final JLabel estadoEstrategia = rotulo("desligada", Paleta.APAGADO);
    private final JCheckBox caixaEstrategia = new JCheckBox("comandar os robos");

    /**
     * @param aoAbrirLog o que fazer quando pedirem o log. Vem de fora de
     *        proposito: o componente sabe que ALGUEM pediu, e nao qual tela
     *        atende. Chamar {@code TelaLog.abrir} daqui punha componentes/
     *        dependendo de telas/, e telas/ ja depende de componentes/ -- as
     *        duas juntas fechavam um ciclo.
     */
    public PainelRede(Cliente cliente, Campo campo, Runnable aoAbrirLog) {
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
        add(alturaFixa(taxa, 2));
        add(Box.createVerticalStrut(6));
        add(rotulo("comandos", Paleta.APAGADO));
        add(alturaFixa(envio, 1));

        apontarComandos.setAlignmentX(Component.LEFT_ALIGNMENT);
        apontarComandos.setMaximumSize(new Dimension(Integer.MAX_VALUE, ALTURA_DO_BOTAO));
        apontarComandos.setPreferredSize(new Dimension(LARGURA, ALTURA_DO_BOTAO));
        apontarComandos.setVisible(false);
        apontarComandos.addActionListener(e -> {
            String ip = cliente.origemDaVisaoDiferente();
            if (ip == null) return;
            try {
                cliente.reconfigurar(cliente.getConfig().comHost(ip), cliente.getEquipe());
            } catch (Exception erro) {
                envio.setForeground(Paleta.ERRO);
                envio.setText("nao consegui trocar: " + erro.getMessage());
            }
        });
        // O botao mora dentro de uma caixa de altura fixa: aparecendo e sumindo
        // solto no BoxLayout, ele empurrava tudo abaixo para cima e para baixo.
        espacoDoBotao.setLayout(new BoxLayout(espacoDoBotao, BoxLayout.Y_AXIS));
        espacoDoBotao.setBackground(Paleta.PAINEL);
        espacoDoBotao.setAlignmentX(Component.LEFT_ALIGNMENT);
        aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
        espacoDoBotao.add(aviso);
        espacoDoBotao.add(apontarComandos);
        add(alturaFixaPx(espacoDoBotao, ALTURA_DO_BOTAO));

        add(Box.createVerticalStrut(18));
        add(titulo("Arbitro"));
        add(painelArbitro);

        add(Box.createVerticalStrut(18));
        add(titulo("Estrategia"));
        add(caixaEstrategia);
        add(alturaFixa(estadoEstrategia, 2));
        add(Box.createVerticalStrut(8));
        add(botao("Ver log...", aoAbrirLog));

        add(Box.createVerticalStrut(18));
        add(botao("Configuracao avancada...",
                () -> DialogoConfiguracao.abrir(campo, cliente, campo)));

        add(Box.createVerticalGlue());
        add(rodape());

        atualizarEstado(); // sem isso os rotulos ficam em "--" ate o primeiro tique
    }

    /**
     * Reserva a altura de {@code linhas} linhas, sempre.
     *
     * <p>Os rotulos daqui mudam de uma para duas ou tres linhas conforme o que ha
     * para dizer -- "nada recebido ainda" vira uma taxa, o aviso de destino
     * aparece e some. Sem altura reservada, cada uma dessas trocas reflui o
     * BoxLayout e TUDO abaixo salta, inclusive o texto de ajuda no pe da coluna.
     * Ficava quicando justamente quando se liga a estrategia, que e quando se
     * esta olhando.
     *
     * <p>A altura vai no componente, e nao no painel: o painel vive dentro de uma
     * rolagem, e altura chumbada nele faria o viewport achar que o conteudo cabe
     * e a barra nunca apareceria.
     */
    private static <T extends JComponent> T alturaFixa(T c, int linhas) {
        return alturaFixaPx(c, linhas * ALTURA_DA_LINHA);
    }

    /** A mesma coisa, com a altura ja em pixels. */
    private static <T extends JComponent> T alturaFixaPx(T c, int h) {
        // A largura fica a do painel, e nao zero: no BoxLayout a largura
        // preferida e a que se usa, e zero colapsava o rotulo -- a secao inteira
        // sumia da tela deixando um buraco.
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(LARGURA, h));
        c.setMinimumSize(new Dimension(0, h));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return c;
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
                    cliente.taxaHz(), cliente.pacotesRecebidos())
                    // So aparecem quando existem: numero que fica sempre em zero
                    // vira decoracao e para de ser lido.
                    + (cliente.quadrosPerdidos() > 0
                            ? "  ·  " + cliente.quadrosPerdidos() + " perdidos" : "")
                    + (cliente.duplicadosDaVisao() > 0
                            ? "  ·  " + cliente.duplicadosDaVisao() + " repetidos" : ""));
        } else {
            taxa.setForeground(Paleta.ALERTA);
            taxa.setText(cliente.pacotesRecebidos() == 0
                    ? "nada recebido ainda"
                    : String.format("parado ha %.0f s", cliente.silencio()));
        }

        // Os dois caminhos sao independentes, e acertar a visao nao acerta os
        // comandos: e o engano que mais custa tempo ao ligar duas maquinas.
        String outraOrigem = cliente.origemDaVisaoDiferente();
        envio.setForeground(outraOrigem == null ? Paleta.APAGADO : Paleta.ALERTA);
        envio.setText(outraOrigem == null
                ? cliente.destinoDeComandos() + "  ·  " + cliente.pacotesEnviados() + " pacotes"
                : cliente.destinoDeComandos() + "  ·  " + cliente.pacotesEnviados() + " pacotes");

        aviso.setVisible(outraOrigem != null);
        apontarComandos.setVisible(outraOrigem != null);
        if (outraOrigem != null) {
            aviso.setText("<html>a visao vem de " + outraOrigem
                    + ",<br>e os comandos nao vao para la</html>");
            // Duas linhas: a coluna e estreita e a fonte monoespacada, e numa
            // linha so o IP -- que e a informacao -- era o pedaco cortado.
            apontarComandos.setText("<html><center>Mandar comandos<br>para "
                    + outraOrigem + "</center></html>");
        }

        if (caixaEstrategia.isSelected() != cliente.isEstrategiaLigada()) {
            caixaEstrategia.setSelected(cliente.isEstrategiaLigada());
        }

        // Sem arbitro nenhum -- nem Game Controller no ar, nem o de bancada -- o
        // estado e HALT e nada sai, entao marcar a caixa nao faz efeito nenhum.
        // Deixa-la clicavel ali era so confusao: a pessoa marca, nada acontece, e
        // nao ha nada na tela ligando uma coisa a outra.
        //
        // COM o Game Controller conectado ela continua liberada mesmo em HALT: na
        // partida se liga a estrategia antes do START, e bloquear aqui deixaria o
        // time parado no momento em que ele tem de sair.
        boolean semArbitro = cliente.estadoDeJogo().semArbitro();
        caixaEstrategia.setEnabled(!semArbitro);

        if (semArbitro) {
            estadoEstrategia.setForeground(Paleta.ALERTA);
            estadoEstrategia.setText("<html>sem arbitro: tudo em HALT,<br>nenhum comando sai</html>");
        } else if (!cliente.isEstrategiaLigada()) {
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
