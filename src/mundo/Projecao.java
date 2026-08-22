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
 * <h2>Por que isso importa mais do que parece</h2>
 *
 * <p>Tres motivos, em ordem de impacto:
 *
 * <ul>
 *   <li><b>Atraso.</b> Entre a camera ver e o robo obedecer passam dezenas de
 *       milissegundos. Mirar onde a bola estava e o motivo classico de o robo
 *       passar sempre atras dela.
 *   <li><b>Interceptacao.</b> Para pegar bola em movimento e preciso ir aonde ela
 *       VAI estar. Perseguir a posicao atual desenha uma curva de perseguicao,
 *       que chega depois e por tras.
 *   <li><b>Quem vai buscar.</b> Decidir por distancia atual manda o robo errado:
 *       o que esta mais perto pode estar indo para o outro lado a 3 m/s.
 * </ul>
 *
 * <h2>A bola nao anda em linha reta no tempo</h2>
 *
 * <p>Robo mantem velocidade enquanto o comando mandar, entao para ele a projecao
 * e reta. A bola desacelera contra o carpete, e ignorar isso e o erro mais caro
 * aqui: uma bola a 6 m/s projetada sem atrito para 1 segundo a frente aparece
 * quase um metro alem de onde ela realmente para.
 *
 * <p>A desaceleracao usada e a de ROLAMENTO. Uma bola recem chutada ainda desliza,
 * e desliza freando bem mais forte, mas de fora nao da para observar o giro dela
 * e portanto nao da para saber em que fase esta. Assumir rolamento erra para o
 * lado seguro: a bola chega um pouco antes do previsto, e um robo que se antecipa
 * demais espera, enquanto um que se atrasa perde a bola.
 */
public final class Projecao {

    private Projecao() {}

    /**
     * Desaceleracao da bola em rolamento, mm/s^2.
     *
     * <p>Da ordem de 0,05 g em carpete de competicao. Vale medir no seu campo: e
     * so soltar a bola forte e olhar quanto ela rola, e ajustar aqui.
     */
    public static final double DESACELERACAO_BOLA = 490.0;

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

    // ------------------------------------------------------------------ bola

    /**
     * Onde a bola estara daqui a {@code dt}, ja com o atrito de rolamento.
     *
     * <p>Se ela parar antes de {@code dt}, o resultado e onde ela parou: projetar
     * alem disso faria a bola andar para tras, porque a formula do movimento
     * uniformemente desacelerado nao sabe que a bola nao volta.
     */
    public static Vec2 bola(EstadoBola b, double dt) {
        double v = b.rapidez();
        if (v < 1e-6) return b.posicao();

        double tParada = v / DESACELERACAO_BOLA;
        double t = Math.min(dt, tParada);
        double distancia = v * t - 0.5 * DESACELERACAO_BOLA * t * t;
        return b.posicao().mais(b.velocidade().normalizado().escala(distancia));
    }

    /** Onde a bola para sozinha, se ninguem tocar nela. */
    public static Vec2 ondeABolaPara(EstadoBola b) {
        return bola(b, Double.MAX_VALUE / 2);
    }

    /** Quanto tempo ate a bola parar, em segundos. */
    public static double tempoAteABolaParar(EstadoBola b) {
        return b.rapidez() / DESACELERACAO_BOLA;
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
     * Onde encontrar a bola: o ponto onde o robo consegue chegar junto com ela.
     *
     * <p>Resolvido por iteracao, e nao em forma fechada. A bola desacelera e o
     * robo tem perfil trapezoidal, entao a equacao do encontro nao tem solucao
     * limpa; mas as duas curvas sao monotonicas, e algumas passadas convergem.
     *
     * <p>Comeca chutando o tempo de chegada ate a posicao ATUAL da bola, ve onde
     * a bola vai estar nesse tempo, recalcula o tempo ate la, e repete. Se o robo
     * nao alcanca, isso aparece como o ponto convergindo para onde a bola para --
     * que e a resposta certa: espere ela la.
     */
    public static Vec2 pontoDeEncontro(EstadoRobo r, EstadoBola b) {
        Vec2 alvo = b.posicao();
        for (int i = 0; i < 8; i++) {
            double t = tempoParaChegar(r, alvo);
            Vec2 novo = bola(b, t);
            if (novo.distancia(alvo) < 5) return novo; // convergiu, em mm
            alvo = novo;
        }
        return alvo;
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
