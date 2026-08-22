package mundo;

import core.Vec2;

/**
 * A bola em um quadro.
 *
 * @param posicao   mm, no gramado
 * @param z         mm, altura do CENTRO da bola -- e assim que a visao reporta
 * @param velocidade mm/s, INFERIDA por {@link percepcao.Rastreador}; nao vem da rede
 * @param idade     segundos desde o ultimo avistamento real; 0 significa vista agora
 * @param incerteza mm de desvio padrao da posicao estimada, direto da covariancia
 *                  do filtro -- cresce enquanto a bola nao e vista
 */
public record EstadoBola(Vec2 posicao, double z, Vec2 velocidade, double idade, double incerteza) {

    public static final EstadoBola AUSENTE =
            new EstadoBola(Vec2.ZERO, 0, Vec2.ZERO, Double.POSITIVE_INFINITY, 0);

    public double rapidez() { return velocidade.norma(); }

    /** True quando a bola nao e vista ha tempo demais para ser levada a serio. */
    public boolean perdida() { return idade > 0.5; }
}
