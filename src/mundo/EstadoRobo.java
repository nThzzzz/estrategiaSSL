package mundo;

import core.Vec2;
import model.Cor;

/**
 * Um robo em um quadro, ja com o que a visao NAO manda.
 *
 * <p>O protocolo entrega exatamente {@code confidence, robot_id, x, y,
 * orientation, pixel_x, pixel_y, height}. Tudo que este record tem alem disso --
 * velocidade, velocidade angular e bola no sensor -- e reconstruido aqui dentro,
 * a partir de quadros consecutivos e de geometria. Ver {@link percepcao.Rastreador}
 * e {@link percepcao.SensorDeBola}.
 *
 * @param theta      radianos, orientacao no referencial global
 * @param velocidade mm/s, global; INFERIDA
 * @param omega      rad/s; INFERIDA
 * @param bolaNoSensor emulacao do sensor de bola; INFERIDA por geometria
 * @param idade      segundos desde o ultimo avistamento real
 * @param incerteza  mm de desvio padrao da posicao, direto da covariancia do Kalman
 */
public record EstadoRobo(
        int id,
        Cor cor,
        Vec2 posicao,
        double theta,
        Vec2 velocidade,
        double omega,
        boolean bolaNoSensor,
        double idade,
        double incerteza
) {
    public double rapidez() { return velocidade.norma(); }

    /** Chave estavel do robo, no formato usado tambem pelo simulador. */
    public String chave() { return cor.tag() + "_" + id; }

    /** True quando o robo nao e visto ha tempo demais: some da visao, nao do painel. */
    public boolean fantasma() { return idade > 0.25; }

    /** Ponta da face do dribbler, onde o sensor de bola olha. */
    public Vec2 faceDribbler() {
        return posicao.mais(Vec2.dePolar(model.Robo.DIST_FACE_FRONTAL, theta));
    }
}
