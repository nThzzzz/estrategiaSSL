package percepcao;

import ajuste.Parametro;
import core.Vec2;
import model.Cor;
import model.Geometria;
import mundo.EstadoBola;
import mundo.EstadoRobo;
import mundo.Quadro;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionBall;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionFrame;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionRobot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforma quadros de visao crus no {@link Quadro} que a estrategia usa.
 *
 * <p>Existe porque o protocolo entrega menos do que qualquer estrategia precisa:
 * a camera ve posicao e orientacao, e mais nada. Velocidade e velocidade angular
 * saem daqui, de um {@link Trilha Kalman} por objeto; o sensor de bola sai de
 * {@link SensorDeBola}. Todo time da liga tem uma peca equivalente.
 *
 * <h2>Prever, depois corrigir</h2>
 *
 * <p>A cada quadro TODAS as trilhas sao previstas ate o instante do quadro, e so
 * as que aparecem nele sao corrigidas. E isso que faz um robo ocluido continuar
 * andando na tela, pela velocidade estimada, em vez de congelar no ultimo ponto
 * visto -- e a covariancia do filtro cresce enquanto ele nao volta, o que da o
 * raio de incerteza que a interface desenha.
 *
 * <h2>Cada objeto no seu proprio relogio</h2>
 *
 * <p>Um campo de verdade tem quatro cameras, cada uma publicando seu proprio
 * {@code SSL_DetectionFrame} com apenas os robos que ela enxerga. Como a previsao
 * e por trilha e usa o {@code t_capture} do quadro, a uniao das cameras funciona
 * sem nenhum codigo especifico para isso: cada trilha e corrigida quando alguma
 * camera a ve, e prevista quando nenhuma ve.
 *
 * <p>Os quadros ainda chegam fora de ordem entre cameras, e a previsao ignora
 * {@code dt} negativo em vez de tentar retroceder o filtro. Um retrocesso
 * correto exigiria guardar o historico e reprocessar, o que so vale a pena
 * quando houver quatro cameras de verdade para justificar.
 */
public final class Rastreador {

    private final Map<String, Trilha> robos = new HashMap<>();
    private final Trilha bola = new Trilha(Ajuste.bola(), false);

    private Geometria geometria = Geometria.DIVISAO_B;
    private double agora;
    private long frame;
    private long medidasRecusadas;

    public Geometria getGeometria()       { return geometria; }
    public void setGeometria(Geometria g) { this.geometria = g; }

    /** Quantas medidas o portao de Mahalanobis recusou desde o inicio. */
    public long getMedidasRecusadas() { return medidasRecusadas; }

    /**
     * Incorpora um quadro de deteccao e devolve o retrato resultante.
     *
     * <p>Um {@code t_capture} que anda para tras significa que a fonte reiniciou
     * (o simulador foi reaberto, tipicamente). Manter as trilhas nesse caso
     * daria dt negativo e um portao recusando tudo, entao o estado e zerado.
     */
    public Quadro incorporar(SSL_DetectionFrame d) {
        double t = d.getTCapture();
        if (t + 1e-9 < agora) esquecerTudo();
        agora = t;
        frame = d.getFrameNumber();

        bola.prever(t);
        for (Trilha tr : robos.values()) tr.prever(t);

        for (SSL_DetectionBall b : d.getBallsList()) {
            if (!bola.corrigir(new Vec2(b.getX(), b.getY()), Double.NaN, t)) medidasRecusadas++;
            bola.setZ(b.hasZ() ? b.getZ() : geometria.raioBola());
            break; // a visao pode reportar varias bolas candidatas; a primeira e a mais confiavel
        }

        for (SSL_DetectionRobot r : d.getRobotsBlueList())   corrigirRobo(Cor.AZUL, r, t);
        for (SSL_DetectionRobot r : d.getRobotsYellowList()) corrigirRobo(Cor.AMARELO, r, t);

        return montar();
    }

    /** Quadro atual sem incorporar nada novo -- para a tela redesenhar entre pacotes. */
    public Quadro atual() { return montar(); }

    public void esquecerTudo() {
        robos.clear();
        agora = 0;
    }

    private void corrigirRobo(Cor cor, SSL_DetectionRobot r, double t) {
        // O id e opcional no proto2. Sem id nao da para manter trilha nenhuma, e
        // associar por proximidade seria adivinhacao -- entao ignora.
        if (!r.hasRobotId()) return;

        String chave = cor.tag() + "_" + r.getRobotId();
        Trilha trilha = robos.get(chave);
        if (trilha == null) {
            trilha = new Trilha(Ajuste.robo(), true);
            trilha.prever(t);
            robos.put(chave, trilha);
        }

        double theta = r.hasOrientation() ? r.getOrientation() : Double.NaN;
        if (!trilha.corrigir(new Vec2(r.getX(), r.getY()), theta, t)) medidasRecusadas++;
    }

    // ----------------------------------------------------------------- retrato

    private Quadro montar() {
        robos.values().removeIf(tr -> tr.idade(agora) > Parametro.ESQUECIMENTO_DA_TRILHA.valor());

        EstadoBola eb = !bola.viva() || bola.idade(agora) > Parametro.ESQUECIMENTO_DA_TRILHA.valor()
                ? EstadoBola.AUSENTE
                : new EstadoBola(bola.posicao(), bola.getZ(), bola.velocidade(),
                                 bola.idade(agora), bola.incerteza());

        List<EstadoRobo> lista = new ArrayList<>(robos.size());
        for (Map.Entry<String, Trilha> e : robos.entrySet()) {
            String[] partes = e.getKey().split("_");
            Cor cor = partes[0].equals(Cor.AZUL.tag()) ? Cor.AZUL : Cor.AMARELO;
            Trilha tr = e.getValue();

            boolean sensor = eb != EstadoBola.AUSENTE && !eb.perdida()
                    && SensorDeBola.detectar(tr.posicao(), tr.theta(), eb.posicao(), eb.z(),
                                             geometria.raioBola());

            lista.add(new EstadoRobo(Integer.parseInt(partes[1]), cor, tr.posicao(), tr.theta(),
                    tr.velocidade(), tr.omega(), sensor, tr.idade(agora), tr.incerteza()));
        }
        lista.sort((a, b) -> a.cor() == b.cor() ? Integer.compare(a.id(), b.id())
                                               : a.cor().compareTo(b.cor()));

        return new Quadro(frame, agora, System.nanoTime(), geometria, eb, lista);
    }
}
