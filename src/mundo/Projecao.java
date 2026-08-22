package mundo;

import core.Vec2;
import model.Robo;

/**
 * Onde as coisas VAO estar, a partir de onde elas estao e para onde vao.
 *
 * <p>E navegacao estimada, a mesma conta que um navio faz na carta: posicao atual
 * mais rumo e velocidade, integrados no tempo. O Kalman ja entrega posicao e
 * velocidade de cada objeto, entao projetar e barato -- e sem projetar, toda
 * decisao e tomada sobre um mundo que ja passou.
 *
 * <h2>So o robo, e so em linha reta</h2>
 *
 * <p>Projeta ROBO, e de proposito nao projeta bola. Robo mantem a velocidade
 * enquanto o comando mandar, entao a reta e uma aproximacao honesta por um
 * segundo. A bola desacelera contra o carpete, quica e muda de dono, e prever
 * isso direito e um problema por si -- que fica para ser escrito a parte, quando
 * for a hora.
 *
 * <p>A projecao ignora que o robo pode estar virando ou freando. Isso NAO e
 * descuido: e a mesma simplificacao do vetor de rumo num radar de navio, e a
 * utilidade dela vem justamente de ser simples. Um robo que esta girando aparece
 * com o fantasma fora do lugar, e isso e informacao -- diz que ele nao esta indo
 * para onde parece.
 */
public final class Projecao {

    private Projecao() {}

    /**
     * Atraso tipico entre a camera ver e o robo obedecer, em segundos.
     *
     * <p>Soma de captura, rede, processamento e radio. Sessenta milissegundos e
     * um chute conservador para uma bancada com simulador; em campo, com radio
     * de verdade, costuma ser maior. Vale medir antes de confiar.
     */
    public static final double ATRASO_PADRAO = 0.06;

    // ------------------------------------------------------------------ robo

    /** Onde o robo estara daqui a {@code dt}, mantida a velocidade atual. */
    public static Vec2 robo(EstadoRobo r, double dt) {
        return r.posicao().mais(r.velocidade().escala(dt));
    }

    /** Para onde o robo estara olhando daqui a {@code dt}. */
    public static double theta(EstadoRobo r, double dt) {
        return core.Angulo.normalizar(r.theta() + r.omega() * dt);
    }

    // ------------------------------------------------------- chegada e encontro

    /**
     * Estimativa de quanto tempo o robo leva para chegar em {@code destino}.
     *
     * <p>Perfil trapezoidal: acelera ate o teto, cruza o meio na velocidade
     * maxima e freia para parar no ponto. Ignora para onde ele esta indo agora,
     * o que subestima quando ele vai para o lado contrario -- serve para
     * comparar candidatos, nao para cronometrar.
     */
    public static double tempoParaChegar(EstadoRobo r, Vec2 destino) {
        double d = r.posicao().distancia(destino);
        double vMax = Robo.VEL_MAX;
        double a = Robo.ACEL_MAX;

        double dAceleracao = vMax * vMax / a; // acelerar mais frear
        if (d <= dAceleracao) return 2 * Math.sqrt(d / a);
        return 2 * (vMax / a) + (d - dAceleracao) / vMax;
    }

    /**
     * Distancia minima entre dois corpos, se ambos mantiverem o rumo.
     *
     * <p>O CPA da navegacao: se ele for menor que a soma dos raios, os dois se
     * encontram. Nao esta em uso ainda -- fica para quando houver desvio de
     * obstaculo -- mas e a mesma projecao, e por isso mora aqui.
     *
     * @return distancia no ponto de maior aproximacao, em mm
     */
    public static double menorDistancia(Vec2 pA, Vec2 vA, Vec2 pB, Vec2 vB) {
        Vec2 dp = pB.menos(pA);
        Vec2 dv = vB.menos(vA);
        double vv = dv.normaQuad();
        if (vv < 1e-9) return dp.norma(); // rumo paralelo: a distancia nao muda

        double t = -dp.escalar(dv) / vv;
        if (t <= 0) return dp.norma(); // ja passaram do ponto mais proximo
        return dp.mais(dv.escala(t)).norma();
    }

    /** Instante do ponto de maior aproximacao, em segundos. Negativo vira zero. */
    public static double tempoDaMenorDistancia(Vec2 pA, Vec2 vA, Vec2 pB, Vec2 vB) {
        Vec2 dv = vB.menos(vA);
        double vv = dv.normaQuad();
        if (vv < 1e-9) return 0;
        return Math.max(0, -pB.menos(pA).escalar(dv) / vv);
    }
}
