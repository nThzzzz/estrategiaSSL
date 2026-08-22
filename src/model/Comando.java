package model;

/**
 * Setpoint enviado a um robo, no referencial LOCAL dele.
 *
 * <p>Espelha o {@code MoveLocalVelocity{forward, left, angular}} do
 * {@code ssl_simulation_robot_control}, e por isso e o referencial local e nao o
 * global: e o que o robo de verdade recebe pelo radio, e o que o firmware sabe
 * executar. Converter para global e trabalho de quem planeja a trajetoria, nao
 * de quem transmite.
 *
 * <p>As unidades aqui sao as de dentro do programa -- mm/s e radianos, como a
 * visao. A conversao para metros e graus acontece so em
 * {@link rede.EmissorDeComandos}, na borda.
 *
 * @param velTangencial mm/s no eixo frontal (+x local = frente, na direcao do dribbler)
 * @param velNormal     mm/s no eixo lateral (+y local = esquerda)
 * @param velAngular    rad/s, positivo anti-horario
 * @param velChute      mm/s do chute; 0 = sem chute
 * @param anguloChute   elevacao do chute em radianos; 0 = rasteiro, >0 = chip
 * @param dribbler      true com o rolete acionado
 */
public record Comando(
        double velTangencial,
        double velNormal,
        double velAngular,
        double velChute,
        double anguloChute,
        boolean dribbler
) {
    /** Elevacao tipica do mecanismo de chip de um robo da SSL. */
    public static final double ANGULO_CHIP_PADRAO = Math.toRadians(45);

    public static final Comando PARADO = new Comando(0, 0, 0, 0, 0, false);

    public static Comando mover(double velTangencial, double velNormal, double velAngular) {
        return new Comando(velTangencial, velNormal, velAngular, 0, 0, false);
    }

    public boolean temChute() { return velChute > 0; }
    public boolean ehChip()   { return velChute > 0 && anguloChute > 0; }

    public Comando comChute(double v)      { return new Comando(velTangencial, velNormal, velAngular, v, 0, dribbler); }
    public Comando comChip(double v)       { return new Comando(velTangencial, velNormal, velAngular, v, ANGULO_CHIP_PADRAO, dribbler); }
    public Comando comDribbler(boolean on) { return new Comando(velTangencial, velNormal, velAngular, velChute, anguloChute, on); }
}
