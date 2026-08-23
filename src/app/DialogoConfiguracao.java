package app;

import ajuste.Parametro;
import model.Cor;
import rede.ConfigRede;
import view.Campo;
import view.Estilo;
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
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Configuracao avancada: quem somos, para onde falamos e o que aparece no campo.
 *
 * <p>Estava tudo espalhado -- equipe e exibicao na coluna da esquerda, portas num
 * dialogo proprio -- e a coluna nao dava conta. O criterio agora e um so: o que se
 * OLHA durante uma bancada fica na coluna, o que se CONFIGURA fica aqui. Portas,
 * nomes, cor, lado, goleiro e caixas de exibicao se mexem uma vez por bancada.
 *
 * <p>Em abas e nao numa coluna unica porque as tres secoes juntas passariam de 700
 * px de altura, e ninguem rola um dialogo de configuracao para achar um campo de
 * porta.
 *
 * <p>Modeless de proposito. Ajustar exibicao com o campo congelado atras de um
 * dialogo modal seria ajustar as cegas -- cada caixa marcada aparece no campo no
 * mesmo instante, e e assim que se decide se ela ajuda ou atrapalha.
 *
 * <h2>O que vale na hora e o que espera o Aplicar</h2>
 *
 * <p>Cor e nomes esperam, porque trocar de cor REABRE os sockets: a porta de
 * destino dos comandos depende dela. Lado, goleiro, folga da area e exibicao valem
 * no clique, porque nao custam nada. A separacao visual entre os dois blocos existe
 * para isso -- um campo que parece ter sido aplicado e nao foi e o pior dos dois
 * mundos.
 */
public final class DialogoConfiguracao extends JDialog {

    /** Onde o botao Salvar grava, e de onde Carregar le. */
    private static final String ARQUIVO_DE_AJUSTE = "ajustes.json";

    private final Cliente cliente;
    private final Campo campo;

    // --- equipe ---
    private final JComboBox<String> nossaCor = new JComboBox<>(new String[]{"Azul", "Amarelo"});
    private final JTextField nomeAzul = new JTextField(12);
    private final JTextField nomeAmarelo = new JTextField(12);

    // --- jogo, valem na hora ---
    /**
     * Para que metade atacamos.
     *
     * <p>Rotulado por DIRECAO NA TELA e nao por cor do gol. As cores dos gols no
     * desenho sao convencao nossa -- o protocolo de visao nao diz de quem e cada
     * gol -- e as equipes trocam de lado no intervalo, entao "gol amarelo"
     * mentiria metade da partida. "+x, para a direita" e verdade sempre.
     */
    private final JComboBox<String> ladoDeAtaque =
            new JComboBox<>(new String[]{"+x, para a direita", "-x, para a esquerda"});
    private final JSpinner goleiro = new JSpinner(new SpinnerNumberModel(0, 0, 15, 1));
    private final JLabel avisoDoArbitro = new JLabel(" ");

    /** Controles que so tem efeito com o arbitro local no comando. */
    private final List<JComponent> soComArbitroLocal = new ArrayList<>();

    // --- rede ---
    private final JTextField grupo = new JTextField(14);
    private final JTextField portaVisao = new JTextField(6);
    private final JTextField grupoArbitro = new JTextField(14);
    private final JTextField portaArbitro = new JTextField(6);
    private final JTextField host = new JTextField(14);
    private final JTextField portaAzul = new JTextField(6);
    private final JTextField portaAmarelo = new JTextField(6);
    private final JLabel mensagemDaRede = new JLabel(" ");

    // --- ajustes ---
    private final Map<Parametro, JTextField> camposDeAjuste = new EnumMap<>(Parametro.class);
    private final JLabel mensagemDoAjuste = new JLabel(" ");

    /** Ligado enquanto os controles estao sendo alinhados ao estado real. */
    private boolean sincronizando;

    private DialogoConfiguracao(Window dono, Cliente cliente, Campo campo) {
        super(dono, "Configuracao avancada", ModalityType.MODELESS);
        this.cliente = cliente;
        this.campo = campo;

        JTabbedPane abas = new JTabbedPane();
        abas.setBackground(Paleta.PAINEL);
        abas.addTab("Equipe", aba(equipe(), jogo()));
        abas.addTab("Rede", aba(rede()));
        abas.addTab("Exibicao", aba(exibicao()));
        abas.addTab("Ajustes", rolagem(aba(ajustes())));

        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dispose());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rodape.setBackground(Paleta.PAINEL);
        rodape.add(fechar);

        setLayout(new BorderLayout());
        add(abas, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        sincronizar();
        instalarOuvintes();

        // Cor, lado e goleiro podem mudar por fora -- pelos botoes do arbitro na
        // coluna, ou pelo Game Controller. Um dialogo aberto que mente sobre isso
        // e pior que um dialogo fechado.
        Timer relogio = new Timer(200, e -> sincronizar());
        relogio.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) { relogio.stop(); }
        });

        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(dono);
    }

    public static void abrir(Component origem, Cliente cliente, Campo campo) {
        new DialogoConfiguracao(SwingUtilities.getWindowAncestor(origem), cliente, campo)
                .setVisible(true);
    }

    // ------------------------------------------------------------- secoes

    private JComponent equipe() {
        JPanel p = secao("Quem somos (precisa de Aplicar)");
        p.add(linha("jogamos de", nossaCor));
        p.add(linha("nome azul", nomeAzul));
        p.add(linha("nome amarelo", nomeAmarelo));
        p.add(Box.createVerticalStrut(6));

        JButton aplicar = new JButton("Aplicar");
        aplicar.setAlignmentX(Component.LEFT_ALIGNMENT);
        aplicar.addActionListener(e -> aplicarEquipe());
        p.add(aplicar);
        p.add(dica("Trocar de cor reabre os sockets, porque a porta de destino dos "
                + "comandos depende dela."));
        return p;
    }

    private JComponent jogo() {
        JPanel p = secao("Jogo e defesa (vale na hora)");
        p.add(linha("atacamos para", ladoDeAtaque));
        p.add(linha("goleiro", goleiro));

        soComArbitroLocal.add(ladoDeAtaque);
        soComArbitroLocal.add(goleiro);

        avisoDoArbitro.setFont(avisoDoArbitro.getFont().deriveFont(10f));
        avisoDoArbitro.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(avisoDoArbitro);
        p.add(dica("So o goleiro entra na zona vermelha do campo; para os outros o "
                + "comando de entrada e cortado. O tamanho dela fica na aba Ajustes."));
        return p;
    }

    private JComponent rede() {
        JPanel p = secao("Portas e enderecos");

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.setBackground(Paleta.PAINEL);
        campos.setAlignmentX(Component.LEFT_ALIGNMENT);
        campoDeRede(campos, "Grupo multicast da visao", grupo);
        campoDeRede(campos, "Porta da visao", portaVisao);
        campoDeRede(campos, "Grupo do arbitro (GC)", grupoArbitro);
        campoDeRede(campos, "Porta do arbitro", portaArbitro);
        campoDeRede(campos, "Host do simulador", host);
        campoDeRede(campos, "Porta RobotControl azul", portaAzul);
        campoDeRede(campos, "Porta RobotControl amarelo", portaAmarelo);
        campos.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                campos.getPreferredSize().height));
        p.add(campos);

        mensagemDaRede.setFont(mensagemDaRede.getFont().deriveFont(10f));
        mensagemDaRede.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Box.createVerticalStrut(6));
        p.add(mensagemDaRede);
        p.add(Box.createVerticalStrut(6));

        JButton restaurar = new JButton("Restaurar padrao");
        restaurar.addActionListener(e -> {
            preencherRede(ConfigRede.padrao());
            dizerDaRede("valores padrao carregados, clique em Aplicar", Paleta.TEXTO);
        });
        JButton aplicar = new JButton("Aplicar");
        aplicar.addActionListener(e -> aplicarRede());

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botoes.setBackground(Paleta.PAINEL);
        botoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        botoes.add(aplicar);
        botoes.add(restaurar);
        botoes.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                botoes.getPreferredSize().height));
        p.add(botoes);
        p.add(dica("Aplicar reabre os sockets; a visao pisca por um instante. Porta "
                + "ocupada por outro processo so aparece aqui, e nesse caso a "
                + "configuracao anterior e restaurada."));
        return p;
    }

    private JComponent exibicao() {
        JPanel p = secao("O que aparece no campo");
        p.add(caixa("textura do gramado", campo::isMostrarTextura, campo::setMostrarTextura));
        p.add(caixa("zonas da defesa",
                campo::isMostrarAreaProibida, campo::setMostrarAreaProibida));
        p.add(caixa("linha de mira e de chute",
                campo::isMostrarLinhas, campo::setMostrarLinhas));
        p.add(caixa("vetores de velocidade", campo::isMostrarVetores, campo::setMostrarVetores));
        p.add(caixa("area do sensor de bola", campo::isMostrarSensor, campo::setMostrarSensor));
        p.add(caixa("incerteza do Kalman", campo::isMostrarIncerteza, campo::setMostrarIncerteza));
        p.add(caixa("prever ate o desenho",
                campo::isPreverAteODesenho, campo::setPreverAteODesenho));
        p.add(caixa("fantasma de 1 s", campo::isMostrarFantasma, campo::setMostrarFantasma));
        p.add(Box.createVerticalStrut(6));

        JButton enquadrar = new JButton("Reenquadrar campo");
        enquadrar.setAlignmentX(Component.LEFT_ALIGNMENT);
        enquadrar.addActionListener(e -> { campo.enquadrar(); campo.repaint(); });
        p.add(enquadrar);
        return p;
    }

    /**
     * Um campo por {@link Parametro}, valendo no instante em que se confirma.
     *
     * <p>E o motivo de os parametros existirem: ajustar uma tolerancia numa
     * bancada e tentar seis valores em dois minutos, e nao editar, recompilar e
     * reabrir a janela seis vezes. Aqui nem o arquivo e preciso -- ele serve para
     * o ajuste SOBREVIVER ao fechar do programa.
     *
     * <p>Cada linha mostra o valor de fabrica ao lado. Sem isso, depois de meia
     * hora mexendo ninguem lembra de onde partiu, e "restaurar" vira a unica
     * saida em vez de um passo atras.
     */
    private JComponent ajustes() {
        JPanel p = secao("Tolerancias, distancias e tetos");

        for (Parametro parametro : Parametro.values()) {
            JTextField campo = new JTextField(8);
            campo.setHorizontalAlignment(JTextField.RIGHT);
            camposDeAjuste.put(parametro, campo);
            campo.addActionListener(e -> vAplicarAjuste(parametro));
            campo.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) { vAplicarAjuste(parametro); }
            });
            p.add(linhaDeAjuste(parametro, campo));
        }

        mensagemDoAjuste.setFont(mensagemDoAjuste.getFont().deriveFont(10f));
        mensagemDoAjuste.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Box.createVerticalStrut(6));
        p.add(mensagemDoAjuste);

        JButton restaurar = new JButton("Restaurar padroes");
        restaurar.addActionListener(e -> {
            Parametro.vRestaurarPadroes();
            sincronizar();
            campo.repaint();
            dizerDoAjuste("todos de volta ao valor de fabrica", Paleta.TEXTO);
        });
        JButton salvar = new JButton("Salvar em " + ARQUIVO_DE_AJUSTE);
        salvar.addActionListener(e -> vSalvarAjustes());
        JButton carregar = new JButton("Carregar");
        carregar.addActionListener(e -> vCarregarAjustes());

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botoes.setBackground(Paleta.PAINEL);
        botoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        botoes.add(salvar);
        botoes.add(carregar);
        botoes.add(restaurar);
        botoes.setMaximumSize(new Dimension(Integer.MAX_VALUE, botoes.getPreferredSize().height));
        p.add(Box.createVerticalStrut(6));
        p.add(botoes);
        p.add(dica("O que vale agora e o que esta nos campos; o arquivo so serve para o "
                + "ajuste sobreviver ao fechar do programa. Carregar le o arquivo e aplica "
                + "so o que ele cita."));
        return p;
    }

    private void vAplicarAjuste(Parametro parametro) {
        if (sincronizando) return;
        JTextField campo = camposDeAjuste.get(parametro);
        String texto = campo.getText().trim().replace(',', '.');
        try {
            double novo = Double.parseDouble(texto);
            if (novo == parametro.valor()) return;
            parametro.vSetValor(novo);
            dizerDoAjuste(parametro.name() + " = " + texto + " " + parametro.unidade(), Paleta.OK);
            campo.repaint();
            this.campo.repaint();
        } catch (NumberFormatException e) {
            dizerDoAjuste("\"" + texto + "\" nao e um numero", Paleta.ERRO);
            sincronizar(); // devolve o que estava valendo
        }
    }

    private void vSalvarAjustes() {
        try {
            Files.writeString(Path.of(ARQUIVO_DE_AJUSTE), Parametro.paraJson());
            dizerDoAjuste("gravado em " + Path.of(ARQUIVO_DE_AJUSTE).toAbsolutePath(), Paleta.OK);
        } catch (IOException e) {
            dizerDoAjuste(e.getMessage(), Paleta.ERRO);
        }
    }

    private void vCarregarAjustes() {
        try {
            List<String> mudancas = Parametro.vCarregar(Path.of(ARQUIVO_DE_AJUSTE));
            sincronizar();
            campo.repaint();
            dizerDoAjuste(mudancas.isEmpty()
                    ? "o arquivo nao muda nada"
                    : mudancas.size() + " parametro(s) trocado(s)", Paleta.OK);
        } catch (Exception e) {
            dizerDoAjuste(e.getMessage(), Paleta.ERRO);
        }
    }

    private void dizerDoAjuste(String texto, Color cor) {
        mensagemDoAjuste.setForeground(cor);
        mensagemDoAjuste.setText("<html><body style='width:340px'>" + texto + "</body></html>");
    }

    // -------------------------------------------------------------- acoes

    private void instalarOuvintes() {
        ladoDeAtaque.addActionListener(e -> {
            if (sincronizando) return;
            // O protocolo fala do AZUL; o seletor fala de NOS. Atacar +x e defender
            // -x, entao o azul fica no lado positivo quando somos amarelos.
            boolean atacamosPositivo = ladoDeAtaque.getSelectedIndex() == 0;
            boolean somosAzul = cliente.getEquipe() == Cor.AZUL;
            cliente.getArbitroLocal().setAzulNoLadoPositivo(somosAzul != atacamosPositivo);
            cliente.reemitirArbitro();
            campo.repaint();
        });

        goleiro.addChangeListener(e -> {
            if (sincronizando) return;
            cliente.getArbitroLocal().setGoleiro(cliente.getEquipe(), (Integer) goleiro.getValue());
            cliente.reemitirArbitro();
        });
    }

    /** Alinha os controles com o estado real, sem que espelhar vire comando. */
    private void sincronizar() {
        sincronizando = true;
        try {
            nossaCor.setSelectedIndex(cliente.getEquipe() == Cor.AZUL ? 0 : 1);
            if (!nomeAzul.isFocusOwner()) nomeAzul.setText(cliente.getNomeAzul());
            if (!nomeAmarelo.isFocusOwner()) nomeAmarelo.setText(cliente.getNomeAmarelo());

            boolean atacamosPositivo = !cliente.estadoDeJogo().defendemosLadoPositivo();
            ladoDeAtaque.setSelectedIndex(atacamosPositivo ? 0 : 1);

            int declarado = cliente.estadoDeJogo().goleiro();
            if (declarado >= 0 && declarado <= 15) goleiro.setValue(declarado);

            boolean local = cliente.getArbitro().isModoLocal();
            for (JComponent c : soComArbitroLocal) c.setEnabled(local);
            avisoDoArbitro.setForeground(local ? Paleta.APAGADO : Paleta.ALERTA);
            avisoDoArbitro.setText(local
                    ? "lado e goleiro vao na proxima mensagem do arbitro de teste"
                    : "em modo REDE quem declara lado e goleiro e o Game Controller");

            for (Map.Entry<Parametro, JTextField> e : camposDeAjuste.entrySet()) {
                if (e.getValue().isFocusOwner()) continue;
                e.getValue().setText(curto(e.getKey().valor()));
            }

            if (!grupo.isFocusOwner() && !portaVisao.isFocusOwner()
                    && !grupoArbitro.isFocusOwner() && !portaArbitro.isFocusOwner()
                    && !host.isFocusOwner() && !portaAzul.isFocusOwner()
                    && !portaAmarelo.isFocusOwner()) {
                preencherRede(cliente.getConfig());
            }
        } finally {
            sincronizando = false;
        }
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
        sincronizar();
    }

    /**
     * Troca as portas com a estrategia rodando.
     *
     * <p>A validacao acontece em duas camadas. {@link ConfigRede#problema()} rejeita
     * o que da para saber sem tocar no sistema -- endereco fora da faixa multicast,
     * porta fora da faixa, azul e amarelo na mesma porta. Ja "a porta esta ocupada
     * por outro processo" so aparece na hora do bind, e chega aqui como excecao de
     * {@link Cliente#reconfigurar}, que ja restaurou a configuracao anterior antes
     * de lancar.
     */
    private void aplicarRede() {
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
            dizerDaRede(e.getMessage(), Paleta.ERRO);
            return;
        }

        try {
            // A cor da equipe nao se mexe aqui: ela decide para qual porta
            // mandamos, mas e escolha de quem somos, nao de configuracao de rede.
            cliente.reconfigurar(nova, cliente.getEquipe());
            dizerDaRede("aplicado: escutando " + nova.grupoVisao() + ":" + nova.portaVisao()
                    + ", mandando em " + cliente.destinoDeComandos(), Paleta.OK);
        } catch (IOException e) {
            dizerDaRede(e.getMessage(), Paleta.ERRO);
            preencherRede(cliente.getConfig()); // mostra o que ficou valendo de fato
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

    private void preencherRede(ConfigRede c) {
        grupo.setText(c.grupoVisao());
        portaVisao.setText(String.valueOf(c.portaVisao()));
        grupoArbitro.setText(c.grupoArbitro());
        portaArbitro.setText(String.valueOf(c.portaArbitro()));
        host.setText(c.hostComandos());
        portaAzul.setText(String.valueOf(c.portaAzul()));
        portaAmarelo.setText(String.valueOf(c.portaAmarelo()));
    }

    private void dizerDaRede(String texto, Color cor) {
        mensagemDaRede.setForeground(cor);
        mensagemDaRede.setText("<html><body style='width:340px'>" + texto + "</body></html>");
    }

    // -------------------------------------------------------------- estilo

    private static JComponent aba(JComponent... secoes) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        for (int i = 0; i < secoes.length; i++) {
            if (i > 0) p.add(Box.createVerticalStrut(10));
            p.add(secoes[i]);
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    private static JPanel secao(String titulo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.BORDA), titulo,
                        TitledBorder.LEFT, TitledBorder.TOP,
                        Estilo.fonte(Font.BOLD, 11), Paleta.APAGADO),
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
        l.setPreferredSize(new Dimension(100, 22));
        l.setMaximumSize(new Dimension(100, 22));

        controle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(l);
        p.add(controle);
        return p;
    }

    private static void campoDeRede(JPanel painel, String rotulo, JTextField campo) {
        JLabel l = new JLabel(rotulo);
        l.setForeground(Paleta.TEXTO);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        painel.add(l);
        painel.add(campo);
    }

    /** Nome a esquerda, campo, unidade e o valor de fabrica em cinza. */
    private static JPanel linhaDeAjuste(Parametro parametro, JTextField campo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(Paleta.PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel nome = new JLabel(parametro.name().toLowerCase().replace('_', ' '));
        nome.setForeground(Paleta.APAGADO);
        nome.setFont(nome.getFont().deriveFont(11f));
        nome.setPreferredSize(new Dimension(178, 22));
        nome.setMaximumSize(new Dimension(178, 22));

        campo.setMaximumSize(new Dimension(90, 22));

        JLabel cauda = new JLabel("  " + parametro.unidade()
                + "   (padrao " + curto(parametro.padrao()) + ")");
        cauda.setForeground(new Color(120, 120, 130));
        cauda.setFont(cauda.getFont().deriveFont(10f));

        p.add(nome);
        p.add(campo);
        p.add(cauda);
        return p;
    }

    /** Sem casa decimal quando o numero e inteiro; a coluna fica legivel. */
    private static String curto(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v)
                ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static JScrollPane rolagem(JComponent conteudo) {
        JScrollPane r = new JScrollPane(conteudo,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        r.setBorder(null);
        r.getViewport().setBackground(Paleta.PAINEL);
        r.getVerticalScrollBar().setUnitIncrement(16);
        r.setPreferredSize(new Dimension(440, 430));
        return r;
    }

    private static JComponent dica(String texto) {
        JLabel l = new JLabel("<html><body style='width:340px'>" + texto + "</body></html>");
        l.setForeground(new Color(120, 120, 130));
        l.setFont(l.getFont().deriveFont(10f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        return l;
    }
}
