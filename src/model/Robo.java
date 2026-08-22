package model;

/**
 * Dimensoes fisicas de um robo da SSL, em milimetros.
 *
 * <p>Estas nao sao medidas observadas: o protocolo de visao reporta posicao e
 * orientacao, e {@code height}, nada sobre a forma do robo. Sao premissas do
 * regulamento e da construcao tipica, e e delas que sai a inferencia de bola no
 * sensor -- por isso ficam num lugar so, nomeadas, em vez de espalhadas como
 * numeros soltos pelo desenho e pela percepcao.
 *
 * <p>O {@code SSL_GeometryData} traz {@code max_robot_radius}, entao o RAIO pode
 * vir da rede; ja a distancia da face do dribbler nao existe em campo nenhum do
 * protocolo, porque varia de time para time.
 */
public final class Robo {

    private Robo() {}

    /** Envelope do regulamento: 180 mm de diametro. */
    public static final double RAIO = 90.0;

    /** Face plana do dribbler, medida do centro do robo. Premissa de construcao. */
    public static final double DIST_FACE_FRONTAL = 72.5;

    /** Teto do regulamento: 150 mm. */
    public static final double ALTURA = 150.0;

    /**
     * Meia largura da boca, derivada de truncar a capa circular na face plana.
     * E a largura util do rolete: fora dela nao ha o que segurar a bola.
     */
    public static final double MEIA_BOCA =
            Math.sqrt(RAIO * RAIO - DIST_FACE_FRONTAL * DIST_FACE_FRONTAL);

    // ------------------------------------------------------------- limites
    //
    // Nao sao medidos: sao o que o nosso robo consegue. Servem para saturar
    // comando e para estimar quanto tempo ele leva para chegar em algum lugar.
    // Se o robo real for mais lento, o planejamento fica otimista e ele chega
    // atrasado nos lugares -- por isso e melhor errar para baixo.

    /** mm/s. */
    public static final double VEL_MAX = 3000.0;

    /** mm/s^2. */
    public static final double ACEL_MAX = 3000.0;

    /** rad/s. */
    public static final double OMEGA_MAX = 20.0;

    /** rad/s^2. */
    public static final double ACEL_ANGULAR_MAX = 50.0;

    /** mm/s do chute mais forte que a regra permite. */
    public static final double VEL_CHUTE_MAX = 6500.0;
}
