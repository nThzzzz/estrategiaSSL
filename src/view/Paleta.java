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

    /**
     * Faixas do corte do gramado, claro e escuro.
     *
     * <p>A media exata das duas e {@link #GRAMA}: (31+19)/2 = 25, (124+96)/2 =
     * 110, (52+38)/2 = 45. Isso e proposital -- o simulador continua com o verde
     * chapado, e as duas janelas costumam ficar lado a lado. Com a media
     * preservada o campo texturizado ainda le como o MESMO verde de la, so que
     * cortado; um par de tons escolhido no olho deixaria um dos campos
     * visivelmente mais claro que o outro.
     */
    public static final Color GRAMA_CLARA  = new Color(31, 124, 52);
    public static final Color GRAMA_ESCURA = new Color(19,  96, 38);
    public static final Color LINHA   = new Color(235, 235, 235);
    public static final Color AZUL    = new Color(50, 120, 255);
    public static final Color AMARELO = new Color(255, 205, 0);
    public static final Color BOLA    = new Color(255, 140, 0);
    public static final Color BOLA_ALTA = new Color(255, 190, 90);
    public static final Color CORPO   = new Color(38, 38, 42);

    /**
     * Zona proibida: robo de linha nao entra. Vermelho, que e o que se le como
     * "nao passe daqui" sem legenda nenhuma.
     *
     * <p>Tracejado, e nao continuo: as linhas brancas do campo sao o que a visao
     * mediu, e este retangulo e decisao nossa. Desenhar os dois com o mesmo traco
     * faria a folga parecer pintada no chao.
     */
    public static final Color ZONA_PROIBIDA       = new Color(235, 70, 70, 190);
    public static final Color ZONA_PROIBIDA_FRACA = new Color(235, 70, 70, 24);

    /**
     * Zona do goleiro, dentro da proibida: onde ELE pode ficar.
     *
     * <p>Verde do sensor, porque as duas dizem a mesma coisa por caminhos
     * diferentes -- "aqui pode". Um segundo vermelho faria duas proibicoes onde
     * ha uma proibicao e uma permissao.
     */
    public static final Color ZONA_DO_GOLEIRO = new Color(120, 255, 120, 130);

    /**
     * Linha da bola incidindo no gol: para onde ela vai dar se ninguem tocar.
     *
     * <p>Vermelha e continua, e nao tracejada como as zonas: as zonas sao regra,
     * esta linha e PREVISAO, e continua justamente porque ela e o que esta
     * acontecendo agora. Some sozinha quando a bola muda de rumo.
     */
    public static final Color LINHA_DE_MIRA = new Color(255, 80, 80, 220);

    /** Linha de chute ou passe com o caminho livre. */
    public static final Color LINHA_LIVRE = new Color(120, 255, 120, 190);

    /** A mesma linha com alguem no caminho. */
    public static final Color LINHA_BLOQUEADA = new Color(255, 170, 60, 170);

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
