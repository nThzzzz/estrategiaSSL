package percepcao;

import core.Vec2;
import model.Robo;

/**
 * Emula o sensor de bola do robo a partir de geometria.
 *
 * <p>O robo de verdade tem uma barreira infravermelha atravessando a boca do
 * dribbler: quando a bola entra, o feixe corta e o firmware sabe que esta com
 * ela. Esse bit nao existe no protocolo de visao -- {@code SSL_DetectionRobot}
 * so carrega posicao e orientacao -- entao um software de time que queira exibir
 * "bola no sensor" tem de reconstruir o bit olhando onde a bola esta em relacao
 * a boca. E o que esta classe faz.
 *
 * <p>O teste e feito no referencial LOCAL do robo, que e onde ele fica trivial:
 * a boca e um segmento em x = {@link Robo#DIST_FACE_FRONTAL}, entao basta a bola
 * estar logo a frente dele e dentro da largura da boca.
 *
 * <p>Duas condicoes que parecem detalhe e nao sao:
 *
 * <ul>
 *   <li><b>Largura da boca.</b> Sem ela, uma bola encostada na lateral da capa,
 *       a 90 graus do dribbler, contaria como posse.
 *   <li><b>Altura.</b> Uma bola passando por cima nao corta feixe nenhum. Como a
 *       visao reporta z do CENTRO da bola, "no chao" e {@code z <= raio * 2.5},
 *       nao {@code z == 0}.
 * </ul>
 */
public final class SensorDeBola {

    private SensorDeBola() {}

    /** Folga alem do contato geometrico, cobrindo ruido de visao e a fresta do rolete. */
    public static final double TOLERANCIA = 15.0; // mm

    /**
     * True se a bola estaria cortando o feixe da boca deste robo.
     *
     * @param zBola altura do centro da bola, em mm, como a visao reporta
     */
    public static boolean detectar(Vec2 posRobo, double theta,
                                   Vec2 posBola, double zBola, double raioBola) {
        if (zBola > raioBola * 2.5) return false; // passou por cima do dribbler

        Vec2 local = posBola.menos(posRobo).paraLocal(theta);

        double contato = Robo.DIST_FACE_FRONTAL + raioBola;
        boolean naFrente = local.x() > 0 && local.x() <= contato + TOLERANCIA;
        boolean dentroDaBoca = Math.abs(local.y()) <= Robo.MEIA_BOCA + raioBola * 0.5;

        return naFrente && dentroDaBoca;
    }
}
