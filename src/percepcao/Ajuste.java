package percepcao;

/**
 * Parametros de sintonia de uma trilha.
 *
 * <p>Um Kalman nao tem "configuracao certa": ele tem uma declaracao de quanto se
 * confia na medida e quanto se admite que o mundo muda sozinho. Estes numeros
 * SAO essa declaracao, e por isso ficam nomeados e juntos, em vez de espalhados
 * como literais dentro do filtro.
 *
 * @param sigmaAceleracao       mm/s^2 de aceleracao nao observada admitida
 * @param sigmaMedida           mm de ruido da visao na posicao
 * @param sigmaAceleracaoAngular rad/s^2 nao observados admitidos
 * @param sigmaMedidaAngular    rad de ruido da visao na orientacao
 * @param portao                limite de qui-quadrado (2 graus de liberdade) para aceitar a medida
 * @param maxRejeicoes          rejeicoes seguidas antes de admitir que a trilha e que esta errada
 * @param sigmaVelocidadeInicial mm/s de incerteza da velocidade no primeiro avistamento
 */
public record Ajuste(
        double sigmaAceleracao,
        double sigmaMedida,
        double sigmaAceleracaoAngular,
        double sigmaMedidaAngular,
        double portao,
        int maxRejeicoes,
        double sigmaVelocidadeInicial
) {
    /**
     * Robo: acelera ate uns 3 m/s^2, e o que a visao erra na posicao e da ordem
     * de milimetros.
     *
     * <p>O portao de 16 corresponde a cerca de 4 sigma em duas dimensoes. Serve
     * para descartar deteccao fantasma e troca de id -- que existem de verdade
     * num campo com quatro cameras -- sem descartar manobra brusca legitima.
     */
    public static Ajuste robo() {
        return new Ajuste(3000, 12, 50, 0.05, 16.0, 4, 3000);
    }

    /**
     * Bola: o modelo de velocidade constante e uma mentira conveniente aqui.
     *
     * <p>Chute e impulso, nao aceleracao -- a bola vai de 0 a 6,5 m/s dentro de
     * um quadro, o que nenhuma sintonia de ruido de processo acompanha. Por isso
     * a bola tem portao mais largo e desiste mais rapido: duas rejeicoes seguidas
     * ja fazem a trilha se reinicializar sobre as medidas novas, com velocidade
     * semeada por diferenca. E o que transforma o chute em uns 30 ms de atraso em
     * vez de meio segundo de trilha perdida.
     */
    public static Ajuste bola() {
        return new Ajuste(8000, 10, 0, 0, 30.0, 2, 6500);
    }
}
