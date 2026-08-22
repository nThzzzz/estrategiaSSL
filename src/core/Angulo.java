package core;

/** Utilitarios de angulo em radianos. */
public final class Angulo {

    private Angulo() {}

    /** Normaliza para o intervalo (-PI, PI]. */
    public static double normalizar(double a) {
        double r = Math.IEEEremainder(a, 2 * Math.PI);
        return r == -Math.PI ? Math.PI : r;
    }

    /** Menor diferenca angular assinada de {@code atual} ate {@code alvo}. */
    public static double diferenca(double alvo, double atual) {
        return normalizar(alvo - atual);
    }

    public static double grausParaRad(double graus) { return Math.toRadians(graus); }
    public static double radParaGraus(double rad)   { return Math.toDegrees(rad); }
}
