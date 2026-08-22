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
 * <h2>Manobra nao e fantasma</h2>
 *
 * <p>O portao estatistico sozinho nao serve para decidir isso, e o chute mostra
 * por que. Com a bola parada na boca do robo, a covariancia do filtro encolhe
 * para poucos milimetros; ai o chute a lanca a 6,5 m/s, o que a 60 Hz da 108 mm
 * em um quadro. Medido contra a propria confianca do filtro, isso e uma surpresa
 * enorme, e o portao recusa. A bola congela na tela por dois quadros e depois
 * salta 325 mm de uma vez -- a "teleportada" que se ve no chute.
 *
 * <p>O erro esta na pergunta. O portao pergunta "isso me surpreende?", quando a
 * pergunta util e "isso e fisicamente possivel?". Uma bola nao passa de
 * {@link Ajuste#velocidadeMaxima}, que e o teto de chute da regra: dentro disso a
 * medida e possivel e tem de ser aceita AGORA, por mais surpreendente que seja.
 * Fora disso nao existe bola que chegue ali, e ai sim e fantasma.
 *
 * <p>Aceitar uma manobra e reinicializar sobre a medida nova, com a velocidade
 * semeada por {@code (medida - posicao anterior) / dt}. No primeiro quadro depois
 * do chute essa diferenca E a velocidade do chute, entao posicao e velocidade
 * ficam certas no mesmo quadro em que a bola sai: sem congelamento e sem salto.
 *
 * <h2>A valvula de escape</h2>
 *
 * <p>Para o que sobra no caminho do fantasma, o portao ainda e uma armadilha: se
 * a trilha se convence de uma posicao errada, passa a recusar justamente as
 * medidas certas e nunca mais volta. Por isso contam-se as recusas seguidas --
 * passou de {@link Ajuste#maxRejeicoes}, quem esta errado e o filtro, e ele se
 * reinicializa sobre as medidas novas.
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
            reiniciar(medida, theta, Vec2.ZERO, 0);
            visto = t;
            return true;
        }

        double ix = fx.inovacao(medida.x());
        double iy = fy.inovacao(medida.y());
        double nis = ix * ix / fx.varianciaInovacao() + iy * iy / fy.varianciaInovacao();

        if (nis > ajuste.portao()) {
            // Surpreendente, mas possivel? Entao e manobra, e a medida vale agora.
            double dt = t - visto;
            double deslocamento = medida.distancia(posicao());
            if (dt > 1e-9 && deslocamento <= limiteFisico(dt)) {
                Vec2 semente = medida.menos(posicao()).escala(1 / dt);
                reiniciar(medida, theta, semente, dt);
                visto = t;
                return true;
            }

            rejeicoes++;
            if (rejeicoes > ajuste.maxRejeicoes()) {
                // O filtro e que esta errado. Recomeca sobre as medidas novas,
                // aproveitando a recusada anterior para ja sair com velocidade.
                Vec2 semente = Vec2.ZERO;
                double dtRejeicao = t - tempoRejeitado;
                if (medidaRejeitada != null && dtRejeicao > 1e-6 && dtRejeicao < 0.5) {
                    semente = medida.menos(medidaRejeitada).escala(1 / dtRejeicao);
                }
                reiniciar(medida, theta, semente, dtRejeicao);
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

    /**
     * Maior deslocamento que ainda pode ser movimento de verdade em {@code dt}.
     *
     * <p>Nao e {@code velocidadeMaxima * dt} puro. Um chute no teto exato da regra
     * cai em cima desse valor, e ai quem decide se a medida passa e o arredondamento
     * do ponto flutuante -- o filtro funcionaria ou nao dependendo do ultimo bit. A
     * margem cobre isso, mais o ruido das duas posicoes envolvidas na diferenca e a
     * imprecisao do carimbo de tempo.
     *
     * <p>Continua muito abaixo de um fantasma: a 60 Hz isto da cerca de 200 mm para
     * a bola, contra os metros que uma deteccao falsa costuma pular.
     */
    private double limiteFisico(double dt) {
        return ajuste.velocidadeMaxima() * dt * 1.5 + 4 * ajuste.sigmaMedida();
    }

    private void reiniciar(Vec2 medida, double theta, Vec2 velocidadeSemente, double dt) {
        fx.iniciar(medida.x(), ajuste.sigmaVelocidadeInicial());
        fy.iniciar(medida.y(), ajuste.sigmaVelocidadeInicial());
        if (fang != null && !Double.isNaN(theta)) {
            fang.iniciar(theta, ajuste.sigmaAceleracaoAngular() > 0 ? 20 : 0);
        }
        if (velocidadeSemente.normaQuad() > 0) {
            fx.semear(velocidadeSemente.x(), dt);
            fy.semear(velocidadeSemente.y(), dt);
        }
        rejeicoes = 0;
        medidaRejeitada = null;
    }
}
