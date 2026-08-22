package core;

/**
 * Vetor 2D imutavel, em milimetros (mm) para posicao e mm/s para velocidade --
 * as unidades em que a SSL-Vision fala, mantidas sem conversao ate a borda da
 * rede. So o {@code EmissorDeComandos} converte para metros, porque o
 * ssl_simulation_protocol usa metros do outro lado.
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public static Vec2 dePolar(double magnitude, double angulo) {
        return new Vec2(magnitude * Math.cos(angulo), magnitude * Math.sin(angulo));
    }

    public Vec2 mais(Vec2 o)      { return new Vec2(x + o.x, y + o.y); }
    public Vec2 menos(Vec2 o)     { return new Vec2(x - o.x, y - o.y); }
    public Vec2 escala(double k)  { return new Vec2(x * k, y * k); }
    public Vec2 negado()          { return new Vec2(-x, -y); }

    public double escalar(Vec2 o) { return x * o.x + y * o.y; }
    public double norma()         { return Math.hypot(x, y); }
    public double normaQuad()     { return x * x + y * y; }
    public double distancia(Vec2 o) { return Math.hypot(x - o.x, y - o.y); }
    public double angulo()        { return Math.atan2(y, x); }

    public Vec2 normalizado() {
        double n = norma();
        return n < 1e-9 ? ZERO : new Vec2(x / n, y / n);
    }

    public Vec2 comNorma(double n) { return normalizado().escala(n); }

    /** Satura a magnitude do vetor em {@code max}, preservando a direcao. */
    public Vec2 limitado(double max) {
        double n = norma();
        return n <= max ? this : comNorma(max);
    }

    public Vec2 rotacionado(double angulo) {
        double c = Math.cos(angulo), s = Math.sin(angulo);
        return new Vec2(x * c - y * s, x * s + y * c);
    }

    /** Converte do referencial global para o referencial do robo (frente = +x). */
    public Vec2 paraLocal(double theta)  { return rotacionado(-theta); }

    /** Converte do referencial do robo de volta para o global. */
    public Vec2 paraGlobal(double theta) { return rotacionado(theta); }

    @Override
    public String toString() { return String.format("(%.1f, %.1f)", x, y); }
}
