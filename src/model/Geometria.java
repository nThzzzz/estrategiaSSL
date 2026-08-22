package model;

import core.Vec2;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryFieldSize;

/**
 * Geometria do campo em milimetros, como a SSL-Vision descreve.
 *
 * <p>Diferenca importante em relacao ao simulador: aqui a geometria NAO e uma
 * constante do programa, ela CHEGA PELA REDE. O {@code SSL_GeometryData} vem
 * junto do fluxo de visao de tempos em tempos, e um software de time que
 * chumbasse 9000 x 6000 quebraria ao entrar num campo de Divisao A.
 *
 * <p>{@link #DIVISAO_B} existe so como valor inicial, para a janela ter o que
 * desenhar nos primeiros milissegundos, antes do primeiro pacote de geometria.
 *
 * <p>Origem (0,0) no centro, +x para um dos gols e +y para a lateral superior.
 * Qual gol e de quem depende do lado que a equipe defende, e isso o protocolo de
 * visao nao diz -- quem decide e o Game Controller ou a configuracao local.
 */
public record Geometria(
        double comprimento,
        double largura,
        double faixaExterna,
        double golLargura,
        double golProfundidade,
        double areaDefesaProfundidade,
        double areaDefesaLargura,
        double raioCirculoCentral,
        double espessuraLinha,
        double raioBola,
        double raioRobo
) {
    public static final Geometria DIVISAO_B =
            new Geometria(9000, 6000, 300, 1000, 180, 1000, 2000, 500, 10, 21.5, 90);

    /**
     * Le a geometria do pacote da visao.
     *
     * <p>Os campos opcionais caem no padrao da Divisao B quando ausentes: um
     * {@code ssl-vision} real nem sempre publica raio de bola e raio maximo de
     * robo, e um campo faltando nao pode zerar o desenho.
     */
    public static Geometria de(SSL_GeometryFieldSize f) {
        Geometria d = DIVISAO_B;
        return new Geometria(
                f.getFieldLength(),
                f.getFieldWidth(),
                f.hasBoundaryWidth() ? f.getBoundaryWidth() : d.faixaExterna(),
                f.getGoalWidth(),
                f.getGoalDepth(),
                f.hasPenaltyAreaDepth() ? f.getPenaltyAreaDepth() : d.areaDefesaProfundidade(),
                f.hasPenaltyAreaWidth() ? f.getPenaltyAreaWidth() : d.areaDefesaLargura(),
                f.hasCenterCircleRadius() ? f.getCenterCircleRadius() : d.raioCirculoCentral(),
                f.hasLineThickness() ? f.getLineThickness() : d.espessuraLinha(),
                f.hasBallRadius() ? f.getBallRadius() : d.raioBola(),
                f.hasMaxRobotRadius() ? f.getMaxRobotRadius() : d.raioRobo());
    }

    public double meioComprimento() { return comprimento / 2.0; }
    public double meiaLargura()     { return largura / 2.0; }

    public double limiteParedeX() { return meioComprimento() + faixaExterna; }
    public double limiteParedeY() { return meiaLargura() + faixaExterna; }

    /** Centro do gol de um dos lados; {@code sinal} e -1 ou +1 em x. */
    public Vec2 centroGol(int sinal) { return new Vec2(sinal * meioComprimento(), 0); }

    /** Nome curto da divisao, deduzido do comprimento -- so para exibir. */
    public String divisao() {
        if (comprimento >= 11000) return "Divisao A";
        if (comprimento >= 8000)  return "Divisao B";
        return String.format("%.0f x %.0f", comprimento, largura);
    }
}
