package core;

/**
 * Retangulo alinhado aos eixos, em milimetros.
 *
 * <p>Existe por causa da area de defesa, que e o unico obstaculo do campo que a
 * estrategia trata como regiao e nao como ponto. Alinhado aos eixos porque a
 * area tambem e: nada aqui precisa de retangulo girado.
 *
 * <p>Nao e o mesmo {@code core.Caixa} do simuladorSSL, ainda que a forma
 * coincida. La ele descreve parede que a fisica resolve; aqui, regiao que o
 * planejamento evita.
 */
public record Caixa(double xMin, double yMin, double xMax, double yMax) {

    /** Caixa a partir de dois cantos quaisquer, ordenando os extremos. */
    public static Caixa de(double x0, double y0, double x1, double y1) {
        return new Caixa(Math.min(x0, x1), Math.min(y0, y1),
                         Math.max(x0, x1), Math.max(y0, y1));
    }

    /** Extensao no eixo X. Nao se chama largura para nao colidir com a do campo. */
    public double extensaoX() { return xMax - xMin; }

    /** Extensao no eixo Y. */
    public double extensaoY() { return yMax - yMin; }

    public Vec2 centro() { return new Vec2((xMin + xMax) / 2.0, (yMin + yMax) / 2.0); }

    public boolean contem(Vec2 p) {
        return p.x() >= xMin && p.x() <= xMax && p.y() >= yMin && p.y() <= yMax;
    }

    /**
     * Quanto {@code p} esta DENTRO, medido ate a face mais proxima; 0 se fora.
     *
     * <p>Complemento de {@link #distancia}, que mede de fora e devolve 0 dentro.
     * Juntas, as duas dao uma medida continua atravessando a borda -- que e o que
     * permite um limitador nao dar solavanco exatamente na linha.
     */
    public double profundidade(Vec2 p) {
        if (!contem(p)) return 0;
        return Math.min(Math.min(p.x() - xMin, xMax - p.x()),
                        Math.min(p.y() - yMin, yMax - p.y()));
    }

    /** Ponto da caixa mais proximo de {@code p}; o proprio {@code p} se ele esta dentro. */
    public Vec2 pontoMaisProximo(Vec2 p) {
        return new Vec2(Math.min(Math.max(p.x(), xMin), xMax),
                        Math.min(Math.max(p.y(), yMin), yMax));
    }

    /** Distancia de {@code p} ate a caixa; zero se ele estiver dentro. */
    public double distancia(Vec2 p) { return p.distancia(pontoMaisProximo(p)); }

    /**
     * Para onde um ponto DENTRO da caixa tem de ir para sair, pela face mais
     * proxima. Vetor unitario alinhado a um eixo.
     */
    public Vec2 saidaMaisCurta(Vec2 p) {
        double aXMin = p.x() - xMin, aXMax = xMax - p.x();
        double aYMin = p.y() - yMin, aYMax = yMax - p.y();
        double menor = Math.min(Math.min(aXMin, aXMax), Math.min(aYMin, aYMax));

        if (menor == aXMin) return new Vec2(-1, 0);
        if (menor == aXMax) return new Vec2(1, 0);
        if (menor == aYMin) return new Vec2(0, -1);
        return new Vec2(0, 1);
    }

    /** Caixa crescida de {@code m} para todos os lados. */
    public Caixa dilatada(double m) {
        return new Caixa(xMin - m, yMin - m, xMax + m, yMax + m);
    }
}
