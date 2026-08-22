package percepcao;

import core.Angulo;

/**
 * Kalman de dois estados -- posicao e velocidade -- sobre um eixo escalar.
 *
 * <h2>Por que um eixo de cada vez</h2>
 *
 * <p>O filtro natural para um robo seria de quatro estados, {@code [x, y, vx, vy]}.
 * Acontece que, com modelo de velocidade constante, ruido de processo igual nos
 * dois eixos e medidas de x e y independentes, a matriz 4x4 e exatamente
 * bloco-diagonal: x nunca influencia y. Rodar dois filtros de 2 estados nao e
 * simplificacao, e a MESMA conta -- so que com matrizes 2x2 escritas a mao, sem
 * biblioteca de algebra linear e sem inverter matriz nenhuma.
 *
 * <p>O mesmo filtro serve para a orientacao, com {@code angular = true}: ai o
 * par vira {@code [theta, omega]} e a unica diferenca e que a inovacao passa por
 * {@link Angulo#diferenca} -- sem isso, um robo cruzando 180 graus produziria
 * uma inovacao de quase 2*PI e o filtro entenderia que ele girou meia volta num
 * quadro.
 *
 * <h2>Modelo</h2>
 *
 * <p>Velocidade constante com aceleracao desconhecida tratada como ruido branco.
 * Isto e proposital: um robo da SSL acelera quando quer, e nao ha como saber o
 * comando que ele recebeu -- do lado de fora so se veem posicoes. O parametro
 * {@code sigmaAceleracao} e, literalmente, "quanta aceleracao eu admito que
 * pode ter acontecido sem eu ver". Aumentar deixa o filtro mais rapido e mais
 * nervoso; diminuir deixa mais liso e mais atrasado.
 *
 * <p>{@code Q = sigmaAceleracao^2 * [[dt^4/4, dt^3/2], [dt^3/2, dt^2]]} e a
 * discretizacao usual desse modelo, e e o que faz o filtro ser correto em taxa
 * variavel: com a visao chegando a 60 Hz ou a 30 Hz, ou com um quadro perdido no
 * meio, o resultado continua consistente porque tudo depende de {@code dt}.
 */
public final class FiltroKalman1D {

    private final double varAceleracao; // sigma_a^2
    private final double varMedida;     // sigma_z^2
    private final boolean angular;

    private double p;   // posicao (ou angulo)
    private double v;   // velocidade (ou omega)

    // Covariancia 2x2, simetrica: [[p00, p01], [p01, p11]]
    private double p00, p01, p11;

    private boolean iniciado;

    /**
     * @param sigmaAceleracao desvio padrao da aceleracao nao modelada (mm/s^2 ou rad/s^2)
     * @param sigmaMedida     desvio padrao do ruido da visao (mm ou rad)
     * @param angular         true para tratar o estado como angulo
     */
    public FiltroKalman1D(double sigmaAceleracao, double sigmaMedida, boolean angular) {
        this.varAceleracao = sigmaAceleracao * sigmaAceleracao;
        this.varMedida = sigmaMedida * sigmaMedida;
        this.angular = angular;
    }

    public boolean iniciado()  { return iniciado; }
    public double posicao()    { return p; }
    public double velocidade() { return v; }

    /** Variancia da posicao estimada; a raiz dela e o raio de incerteza em mm. */
    public double variancia()  { return p00; }

    /**
     * Primeira medida: vira o estado, sem filtrar.
     *
     * <p>A velocidade comeca em zero mas com variancia enorme -- a velocidade
     * maxima de um robo ao quadrado. E o jeito de dizer ao filtro "nao faco ideia
     * de quao rapido isso esta indo", e e o que faz ele convergir em poucos
     * quadros em vez de arrastar o zero inicial por meio segundo.
     */
    public void iniciar(double medida, double sigmaVelocidadeInicial) {
        p = medida;
        v = 0;
        p00 = varMedida;
        p01 = 0;
        p11 = sigmaVelocidadeInicial * sigmaVelocidadeInicial;
        iniciado = true;
    }

    /**
     * Planta uma velocidade obtida por diferenca de duas posicoes medidas.
     *
     * <p>Usada quando a trilha reconhece uma manobra e se reinicializa. A
     * incerteza dessa velocidade nao e a ignorancia total com que {@link #iniciar}
     * comeca: e o ruido de duas medidas propagado pelo intervalo entre elas,
     * {@code 2*sigma_z^2 / dt^2}. Declarar isso e o que faz o filtro confiar na
     * semente em vez de leva-la de volta para zero no quadro seguinte.
     */
    public void semear(double velocidade, double dt) {
        v = velocidade;
        if (dt > 1e-9) p11 = Math.min(p11, 2 * varMedida / (dt * dt));
    }

    /** Avanca o estado em {@code dt} segundos, sem medida nova. */
    public void prever(double dt) {
        if (!iniciado || dt <= 0) return;

        p += v * dt;
        if (angular) p = Angulo.normalizar(p);

        // P = F P F' + Q, com F = [[1, dt], [0, 1]]
        double dt2 = dt * dt;
        double dt3 = dt2 * dt;
        double dt4 = dt2 * dt2;

        double n00 = p00 + 2 * dt * p01 + dt2 * p11 + varAceleracao * dt4 / 4;
        double n01 = p01 + dt * p11 + varAceleracao * dt3 / 2;
        double n11 = p11 + varAceleracao * dt2;

        p00 = n00;
        p01 = n01;
        p11 = n11;
    }

    /** Inovacao da medida em relacao ao estado previsto. */
    public double inovacao(double medida) {
        return angular ? Angulo.diferenca(medida, p) : medida - p;
    }

    /** Variancia da inovacao, {@code S = P00 + R}. Usada tambem no portao. */
    public double varianciaInovacao() { return p00 + varMedida; }

    /**
     * Corrige com uma medida de posicao.
     *
     * @return a inovacao aplicada, util para diagnostico
     */
    public double corrigir(double medida) {
        double y = inovacao(medida);
        double s = varianciaInovacao();

        double k0 = p00 / s;
        double k1 = p01 / s;

        p += k0 * y;
        v += k1 * y;
        if (angular) p = Angulo.normalizar(p);

        // P = (I - K H) P, com H = [1, 0]
        double n00 = (1 - k0) * p00;
        double n01 = (1 - k0) * p01;
        double n11 = p11 - k1 * p01;

        p00 = n00;
        p01 = n01;
        p11 = n11;
        return y;
    }
}
