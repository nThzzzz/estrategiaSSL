package percepcao;

import core.Vec2;

/**
 * Um objeto rastreado: dois filtros de posicao, um de orientacao e a decisao de
 * acreditar ou nao na medida que chegou.
 *
 * <h2>O portao</h2>
 *
 * <p>Nem toda deteccao merece ser incorporada. Uma camera pode inventar um robo
 * onde ha um reflexo, dois robos podem trocar de id ao se cruzarem, e a bola
 * pode ser confundida com o casaco laranja de alguem na arquibancada -- todos
 * casos reais em competicao. O filtro sabe dizer o quanto uma medida o
 * surpreende: {@code inovacao^2 / variancia_da_inovacao}, somado nos dois eixos,
 * e uma distancia de Mahalanobis que segue qui-quadrado com 2 graus de
 * liberdade. Acima de {@link Ajuste#portao} a medida e recusada.
 *
 * <h2>Por que o portao precisa de uma valvula de escape</h2>
 *
 * <p>Um portao sozinho e uma armadilha: se a trilha se convence de uma posicao
 * errada, ela passa a recusar justamente as medidas certas, e nunca mais volta.
 * Por isso conta-se as recusas seguidas -- passou de {@link Ajuste#maxRejeicoes},
 * quem esta errado e o filtro, e ele se reinicializa sobre as medidas novas.
 *
 * <p>Nessa reinicializacao a velocidade nao volta a zero: ela e semeada pela
 * diferenca entre a medida atual e a que foi recusada antes. E o que faz um
 * chute -- que o modelo de velocidade constante jamais preveria -- ser absorvido
 * em dois quadros, ja com a velocidade quase certa, em vez de precisar de meio
 * segundo para o filtro reconvergir do zero.
 */
public final class Trilha {

    private final Ajuste ajuste;
    private final FiltroKalman1D fx, fy;
    private final FiltroKalman1D fang; // null para a bola, que nao tem orientacao

    private double previstoAte = Double.NaN;
    private double visto = Double.NaN;

    private int rejeicoes;
    private Vec2 medidaRejeitada;
    private double tempoRejeitado;

    private double z; // altura da bola; a visao mede, o filtro nao precisa tratar

    public Trilha(Ajuste ajuste, boolean comOrientacao) {
        this.ajuste = ajuste;
        this.fx = new FiltroKalman1D(ajuste.sigmaAceleracao(), ajuste.sigmaMedida(), false);
        this.fy = new FiltroKalman1D(ajuste.sigmaAceleracao(), ajuste.sigmaMedida(), false);
        this.fang = comOrientacao
                ? new FiltroKalman1D(ajuste.sigmaAceleracaoAngular(),
                                     ajuste.sigmaMedidaAngular(), true)
                : null;
    }

    public boolean viva()      { return !Double.isNaN(visto); }
    public double ultimoVisto() { return visto; }
    public double getZ()       { return z; }
    public void setZ(double z) { this.z = z; }

    public Vec2 posicao()    { return new Vec2(fx.posicao(), fy.posicao()); }
    public Vec2 velocidade() { return new Vec2(fx.velocidade(), fy.velocidade()); }
    public double theta()    { return fang == null ? 0 : fang.posicao(); }
    public double omega()    { return fang == null ? 0 : fang.velocidade(); }

    /**
     * Raio de incerteza da posicao, em mm (um desvio padrao).
     *
     * <p>Cresce sozinho enquanto ninguem ve o objeto, e e por isso que ele e
     * util na tela: mostra o quanto a estimativa ja e chute. Um numero que o
     * passa-baixa anterior nao tinha como fornecer.
     */
    public double incerteza() {
        return Math.sqrt(Math.max(fx.variancia(), fy.variancia()));
    }

    /** Segundos desde a ultima medida aceita. */
    public double idade(double agora) {
        return Double.isNaN(visto) ? Double.POSITIVE_INFINITY : agora - visto;
    }

    /**
     * Leva o estado ate {@code t} sem medida nova.
     *
     * <p>E aqui que o Kalman ganha do passa-baixa: durante uma oclusao ele
     * continua movendo o objeto pela velocidade estimada, em vez de deixa-lo
     * congelado no ultimo ponto visto, e ainda diz quanto essa extrapolacao ja
     * merece desconfianca.
     */
    public void prever(double t) {
        if (Double.isNaN(previstoAte)) { previstoAte = t; return; }
        double dt = t - previstoAte;
        if (dt <= 0) return;

        fx.prever(dt);
        fy.prever(dt);
        if (fang != null) fang.prever(dt);
        previstoAte = t;
    }

    /**
     * Incorpora uma medida.
     *
     * @param theta orientacao medida, ou {@code NaN} se o objeto nao tem
     * @return true se a medida foi aceita; false se o portao a recusou
     */
    public boolean corrigir(Vec2 medida, double theta, double t) {
        if (!fx.iniciado()) {
            reiniciar(medida, theta, Vec2.ZERO);
            visto = t;
            return true;
        }

        double ix = fx.inovacao(medida.x());
        double iy = fy.inovacao(medida.y());
        double nis = ix * ix / fx.varianciaInovacao() + iy * iy / fy.varianciaInovacao();

        if (nis > ajuste.portao()) {
            rejeicoes++;
            if (rejeicoes > ajuste.maxRejeicoes()) {
                // O filtro e que esta errado. Recomeca sobre as medidas novas,
                // aproveitando a recusada anterior para ja sair com velocidade.
                Vec2 semente = Vec2.ZERO;
                double dt = t - tempoRejeitado;
                if (medidaRejeitada != null && dt > 1e-6 && dt < 0.5) {
                    semente = medida.menos(medidaRejeitada).escala(1 / dt);
                }
                reiniciar(medida, theta, semente);
                visto = t;
                return true;
            }
            medidaRejeitada = medida;
            tempoRejeitado = t;
            return false;
        }

        rejeicoes = 0;
        fx.corrigir(medida.x());
        fy.corrigir(medida.y());
        if (fang != null && !Double.isNaN(theta)) fang.corrigir(theta);
        visto = t;
        return true;
    }

    private void reiniciar(Vec2 medida, double theta, Vec2 velocidadeSemente) {
        fx.iniciar(medida.x(), ajuste.sigmaVelocidadeInicial());
        fy.iniciar(medida.y(), ajuste.sigmaVelocidadeInicial());
        if (fang != null && !Double.isNaN(theta)) {
            fang.iniciar(theta, ajuste.sigmaAceleracaoAngular() > 0 ? 20 : 0);
        }
        if (velocidadeSemente.normaQuad() > 0) {
            fx.semear(velocidadeSemente.x());
            fy.semear(velocidadeSemente.y());
        }
        rejeicoes = 0;
        medidaRejeitada = null;
    }
}
