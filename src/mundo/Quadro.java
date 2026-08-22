package mundo;

import model.Cor;
import model.Geometria;

import java.util.List;

/**
 * Retrato imutavel de um instante, do jeito que a estrategia enxerga.
 *
 * <p>Mesma ideia do {@code EstadoMundo} do simulador, e pelo mesmo motivo: a
 * thread de rede escreve, a Swing le, e um retrato imutavel publicado de uma vez
 * evita que a tela misture metade de um quadro com metade do seguinte.
 *
 * @param tempo   segundos, o {@code t_capture} da visao -- relogio de quem gerou
 *                o quadro, nao o relogio local
 * @param recebidoEm nanossegundos locais ({@code System.nanoTime}), para medir
 *                atraso e taxa sem depender do relogio remoto
 */
public record Quadro(
        long frame,
        double tempo,
        long recebidoEm,
        Geometria geometria,
        EstadoBola bola,
        List<EstadoRobo> robos
) {
    public static Quadro vazio() {
        return new Quadro(0, 0, System.nanoTime(), Geometria.DIVISAO_B,
                EstadoBola.AUSENTE, List.of());
    }

    public Quadro { robos = List.copyOf(robos); }

    public List<EstadoRobo> robos(Cor cor) {
        return robos.stream().filter(r -> r.cor() == cor).toList();
    }

    public int quantidade(Cor cor) { return robos(cor).size(); }

    public EstadoRobo robo(Cor cor, int id) {
        for (EstadoRobo r : robos) if (r.cor() == cor && r.id() == id) return r;
        return null;
    }

    /** Robo que esta com a bola no sensor, se algum estiver. */
    public EstadoRobo comPosse() {
        for (EstadoRobo r : robos) if (r.bolaNoSensor()) return r;
        return null;
    }
}
