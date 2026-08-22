package view;

import java.awt.Color;

/**
 * Cores da interface, em um lugar so.
 *
 * <p>As do campo sao propositalmente as mesmas do simuladorSSL: as duas janelas
 * costumam ficar lado a lado na mesma tela, e um verde diferente em cada uma
 * faria parecer que sao dois campos, e nao a mesma cena vista de dois lugares.
 */
public final class Paleta {

    private Paleta() {}

    public static final Color FUNDO      = new Color(18, 18, 20);
    public static final Color PAINEL     = new Color(32, 32, 36);
    public static final Color PAINEL_ALT = new Color(26, 26, 30);
    public static final Color BORDA      = new Color(58, 58, 64);
    public static final Color TEXTO      = new Color(225, 225, 230);
    public static final Color APAGADO    = new Color(150, 150, 160);

    public static final Color ESCAPE  = new Color(30, 60, 35);
    public static final Color GRAMA   = new Color(25, 110, 45);
    public static final Color LINHA   = new Color(235, 235, 235);
    public static final Color AZUL    = new Color(50, 120, 255);
    public static final Color AMARELO = new Color(255, 205, 0);
    public static final Color BOLA    = new Color(255, 140, 0);
    public static final Color BOLA_ALTA = new Color(255, 190, 90);
    public static final Color CORPO   = new Color(38, 38, 42);

    /** Verde do sensor de bola: o mesmo tom que o simulador usa para posse. */
    public static final Color SENSOR      = new Color(120, 255, 120);
    public static final Color SENSOR_FRACO = new Color(120, 255, 120, 40);

    public static final Color OK    = new Color(120, 220, 130);
    public static final Color ALERTA = new Color(255, 180, 70);
    public static final Color ERRO  = new Color(255, 110, 110);

    public static Color de(model.Cor cor) { return cor == model.Cor.AZUL ? AZUL : AMARELO; }

    /** Texto legivel sobre um fundo da cor da equipe. */
    public static Color textoSobre(model.Cor cor) {
        return cor == model.Cor.AZUL ? Color.WHITE : new Color(40, 30, 0);
    }
}
