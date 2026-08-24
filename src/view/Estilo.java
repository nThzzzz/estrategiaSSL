package view;


import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import javax.swing.UIManager;
import java.awt.Font;

/**
 * Aparencia da janela: um visual so, igual em qualquer sistema operacional.
 *
 * <p>Antes daqui o programa pedia {@code getSystemLookAndFeelClassName()}, que e
 * literalmente "use o visual do SO": Aqua no macOS, WindowsLookAndFeel no
 * Windows. Os botoes saiam diferentes em cada maquina porque era isso que a
 * chamada mandava fazer. Pior, o Aqua IGNORA {@code setBackground} em botao --
 * ele desenha o controle nativo -- entao o painel escuro da {@link Paleta}
 * ficava com botao claro do sistema no mac, e obedecia no Windows. Estilizar na
 * mao componente a componente nao resolveria: cada L&F decide sozinho o que
 * respeitar.
 *
 * <p>O FlatLaf desenha tudo em Java, sem delegar nada ao sistema, e por isso sai
 * igual nos tres. Ele tambem respeita cor por componente de verdade, o que
 * permite alimentar o L&F com a {@link Paleta} que ja existia em vez de brigar
 * com ele.
 *
 * <p>A fonte e o segundo eixo, e independente do L&F. {@code new Font("SansSerif",
 * ...)} nao nomeia uma fonte: e um pedido que o JDK resolve para uma fonte FISICA
 * diferente em cada sistema -- SF no mac, Segoe UI no Windows. Larguras
 * diferentes movem texto desenhado a mao, e nenhum look-and-feel conserta isso,
 * porque {@link Campo} e {@code BarraSuperior} pintam no {@code Graphics}. A
 * JetBrains Mono vai embarcada no jar: mesma fonte em todo lugar, com ou sem ela
 * instalada na maquina.
 *
 * <p>Ela e monoespacada, e isso e proposital em um programa que e quase todo
 * numero na tela: coordenada, velocidade e tempo nao dancam de largura quando o
 * digito muda. O codigo ja separava telemetria de rotulo por {@code Font.BOLD} e
 * {@code Font.PLAIN}, e essa separacao continua sendo a que carrega o sentido --
 * o jar traz so os dois pesos, o que mantem a distincao honesta.
 */
public final class Estilo {

    private Estilo() {}

    /** Corpo da interface. O mesmo 12 do terminal, que foi de onde a fonte veio. */
    public static final float CORPO = 12f;

    /**
     * Instala o visual. Tem de rodar na thread do Swing e antes de qualquer
     * componente nascer: quem ja existe guardou a fonte e a cor do L&F anterior.
     */
    public static void instalar() {
        FlatJetBrainsMonoFont.install();
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", fonte(Font.PLAIN, CORPO));

        // A Paleta manda, e nao o tema: as duas janelas ficam lado a lado e o
        // cinza de fabrica do FlatDarkLaf nao e o mesmo cinza do simulador.
        UIManager.put("Panel.background", Paleta.PAINEL);
        UIManager.put("Panel.foreground", Paleta.TEXTO);
        UIManager.put("Label.foreground", Paleta.TEXTO);
        UIManager.put("Label.disabledForeground", Paleta.APAGADO);
        UIManager.put("Separator.foreground", Paleta.BORDA);

        UIManager.put("Button.background", Paleta.PAINEL_ALT);
        UIManager.put("Button.foreground", Paleta.TEXTO);
        UIManager.put("Button.borderColor", Paleta.BORDA);
        UIManager.put("Button.hoverBorderColor", Paleta.APAGADO);
        UIManager.put("Button.disabledText", Paleta.APAGADO);

        UIManager.put("ToggleButton.background", Paleta.PAINEL_ALT);
        UIManager.put("ToggleButton.foreground", Paleta.TEXTO);

        UIManager.put("TextField.background", Paleta.FUNDO);
        UIManager.put("TextField.foreground", Paleta.TEXTO);
        UIManager.put("TextField.borderColor", Paleta.BORDA);
        UIManager.put("TextArea.background", Paleta.PAINEL);
        UIManager.put("TextArea.foreground", Paleta.TEXTO);
        UIManager.put("ComboBox.background", Paleta.PAINEL_ALT);
        UIManager.put("ComboBox.foreground", Paleta.TEXTO);
        UIManager.put("CheckBox.foreground", Paleta.TEXTO);

        UIManager.put("TabbedPane.background", Paleta.PAINEL);
        UIManager.put("TabbedPane.foreground", Paleta.TEXTO);
        UIManager.put("TabbedPane.underlineColor", Paleta.AZUL);
        UIManager.put("ScrollPane.background", Paleta.PAINEL);
        UIManager.put("Viewport.background", Paleta.PAINEL);
        UIManager.put("SplitPane.background", Paleta.FUNDO);
        UIManager.put("SplitPaneDivider.draggingColor", Paleta.AZUL);

        UIManager.put("TitlePane.background", Paleta.FUNDO);
        UIManager.put("TitlePane.foreground", Paleta.TEXTO);
        UIManager.put("OptionPane.background", Paleta.PAINEL);
        UIManager.put("ToolTip.background", Paleta.PAINEL_ALT);
        UIManager.put("ToolTip.foreground", Paleta.TEXTO);

        UIManager.put("Component.focusColor", Paleta.AZUL);
        UIManager.put("Component.focusedBorderColor", Paleta.AZUL);
        UIManager.put("Component.borderColor", Paleta.BORDA);

        // Canto levemente arredondado em botao e campo. O quadrado do Metal
        // parece Java de 2005; o pilula do Aqua nao cabe numa coluna estreita.
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("Component.focusWidth", 1);
    }

    /**
     * A fonte da interface, no estilo e tamanho pedidos.
     *
     * <p>Existe para que nenhum {@code new Font("...")} solto sobre no codigo:
     * um so seria suficiente para trazer de volta a fonte-do-sistema e a
     * diferenca entre maquinas que esta classe foi escrita para acabar.
     */
    public static Font fonte(int estilo, float tamanho) {
        return new Font(FlatJetBrainsMonoFont.FAMILY, estilo, Math.round(tamanho));
    }
}
