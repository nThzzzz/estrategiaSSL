package estrategia;

import ajuste.Parametro;
import core.Caixa;
import core.Vec2;
import jogo.EstadoDeJogo;
import model.Cor;
import model.Geometria;
import mundo.EstadoBola;
import mundo.EstadoRobo;
import mundo.Quadro;

import java.util.List;

/**
 * Tudo que a estrategia pode ler, e nada alem disso.
 *
 * <p>Equivale ao {@code AmbienteCampo} do SSL-Strategy: e o objeto que atravessa
 * todas as camadas, de {@link estrategia.esqueleto.Coach} a
 * {@link estrategia.esqueleto.Skill}. Junta as duas fontes de verdade que
 * existem -- o {@link Quadro} da visao e o {@link EstadoDeJogo} do arbitro --
 * mais a cor que estamos jogando, que e o que transforma "azul" em "nos".
 *
 * <p>E somente leitura de proposito. Nenhuma camada de estrategia deve alcancar
 * rede, Swing ou o {@code Cliente}: quem decide o que os robos fazem tem de poder
 * rodar mil partidas sem abrir janela, que e a condicao para treinar o coach.
 *
 * <p>O tempo aqui e o {@code t_capture} da visao, e nao o relogio de parede. Com
 * o simulador rodando acelerado sao coisas bem diferentes, e e o tempo simulado
 * que faz o timeout de uma play significar a mesma coisa nas duas velocidades.
 *
 * @param margemDaArea folga somada a area de defesa para formar a zona onde so o
 *        goleiro entra, em mm. E o unico numero daqui que NAO vem da visao nem do
 *        arbitro: e escolha nossa, e por isso e ajustavel na interface
 */
public record Ambiente(Quadro quadro, EstadoDeJogo jogo, Cor nossaCor, double margemDaArea) {

    /** Sem margem declarada, vale {@link Parametro#MARGEM_DA_AREA}. */
    public Ambiente(Quadro quadro, EstadoDeJogo jogo, Cor nossaCor) {
        this(quadro, jogo, nossaCor, Parametro.MARGEM_DA_AREA.valor());
    }

    public Geometria geometria() { return quadro.geometria(); }
    public EstadoBola bola()     { return quadro.bola(); }

    /** Segundos no relogio de quem gerou o quadro. */
    public double tempo() { return quadro.tempo(); }

    public Cor corDeles() { return nossaCor.oposta(); }

    public List<EstadoRobo> nossos() { return quadro.robos(nossaCor); }
    public List<EstadoRobo> deles()  { return quadro.robos(corDeles()); }

    public EstadoRobo nosso(int id) { return quadro.robo(nossaCor, id); }

    /**
     * Robo nosso que esta com a bola no sensor, ou {@code null}.
     *
     * <p>Cuidado ao usar: o sensor e inferido por geometria, entao ele responde
     * "a bola esta encostada na boca", que nao e exatamente "a posse e nossa".
     */
    public EstadoRobo nossoComABola() {
        for (EstadoRobo r : nossos()) if (r.bolaNoSensor()) return r;
        return null;
    }

    /** Centro do gol que defendemos. O lado vem do arbitro, nao da visao. */
    public Vec2 nossoGol() { return geometria().centroGol(jogo.nossoLado()); }

    /** Centro do gol que atacamos. */
    public Vec2 golDeles() { return geometria().centroGol(-jogo.nossoLado()); }

    /** True se o ponto esta na metade que defendemos. */
    public boolean noNossoCampo(Vec2 p) { return p.x() * jogo.nossoLado() > 0; }

    /**
     * O retangulo da nossa area de defesa, ja com a folga de seguranca.
     *
     * <p>E zona proibida para todo robo nosso que nao seja o goleiro -- a regra
     * da SSL e essa, e quem a aplica e o {@link Executor}, no fim do tique, pelo
     * mesmo motivo que os limites do arbitro moram la: basta uma play esquecer
     * para o time cometer falta.
     */
    public Caixa nossaAreaProibida() {
        return geometria().areaDefesa(jogo.nossoLado(), margemDaArea);
    }

    /** True se este robo nosso pode entrar na area: so o goleiro declarado pode. */
    public boolean podeEntrarNaArea(int id) { return id == jogo.goleiro(); }
}
