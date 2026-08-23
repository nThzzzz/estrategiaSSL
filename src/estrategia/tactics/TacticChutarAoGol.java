package estrategia.tactics;

import ajuste.Parametro;
import estrategia.ids.Habilidade;
import estrategia.esqueleto.Tactic;
import estrategia.ids.Tatica;
import estrategia.skills.SkillChutar;
import estrategia.skills.SkillOlharPara;

/**
 * Mira o gol e chuta.
 *
 * <p>Duas skills em sequencia de verdade, e nao em paralelo: mirar e chutar sao
 * a mesma coisa acontecendo em ordem. Chutar antes de estar alinhado manda a bola
 * para a lateral, e o custo de esperar mais alguns quadros e baixo perto disso.
 *
 * <p>A tolerancia de mira e apertada porque a distancia amplifica o erro: a
 * 2,5 m do gol, dois graus de desvio ja deslocam a bola quase 9 cm no fundo da
 * rede, e o gol tem 1 m de largura.
 */
public final class TacticChutarAoGol extends Tactic {


    private final SkillOlharPara olhar = new SkillOlharPara();
    private final SkillChutar chutar = new SkillChutar();

    /** Instante em que a bola entrou no sensor, ou NaN se ela nao esta la. */
    private double dPosseDesde = Double.NaN;

    /**
     * O id vem de {@link Tatica}, e nao de quem registra a tatica.
     *
     * <p>O construtor que recebe {@code int} continua existindo so enquanto os
     * papeis ainda numeram por conta propria; ele pode sair assim que o ultimo
     * deles passar a usar este.
     */
    public TacticChutarAoGol() { this(Tatica.CHUTAR_AO_GOL.id()); }

    public TacticChutarAoGol(int _id) {
        super(_id);

        bAddSkill(Habilidade.OLHAR_PARA, olhar);
        bAddSkill(Habilidade.CHUTAR, chutar);
    }

    /** Velocidade do chute, em mm/s. */
    public void vSetVelocidade(double _mmPorSegundo) { chutar.vSetVelocidade(_mmPorSegundo); }

    @Override
    public String strName() { return "TacticChutarAoGol"; }

    @Override
    public void vInitialize() {
        bSetSkill(Habilidade.OLHAR_PARA);
        dPosseDesde = Double.NaN;
    }

    @Override
    protected void vRun() {
        // A cada tique, e nao no construtor: um ajuste carregado com o programa
        // rodando vale no proximo quadro.
        olhar.vSetTolerancia(Math.toRadians(Parametro.TOLERANCIA_DE_MIRA.valor()));
        olhar.vSetAlvo(ambiente.golDeles());

        boolean controlavel = ambiente.bola().rapidez() < Parametro.VEL_MAX_CONTROLE.valor();
        jogador.vDribbler(controlavel);

        if (!jogador.bComABola() || !controlavel) dPosseDesde = Double.NaN;
        else if (Double.isNaN(dPosseDesde)) dPosseDesde = ambiente.tempo();

        boolean mirado = Math.abs(olhar.dErro())
                <= Math.toRadians(Parametro.TOLERANCIA_DE_MIRA.valor());
        boolean assentada = !Double.isNaN(dPosseDesde)
                && ambiente.tempo() - dPosseDesde >= Parametro.POSSE_MINIMA.valor();

        bSetSkill(mirado && assentada ? Habilidade.CHUTAR : Habilidade.OLHAR_PARA);
    }

    /** Segundos com a bola no sensor, ou zero. Util para o log. */
    public double dTempoDePosse() {
        if (Double.isNaN(dPosseDesde) || ambiente == null) return 0;
        return ambiente.tempo() - dPosseDesde;
    }

    /** Acaba quando o chute saiu, ou se a bola escapou antes disso. */
    @Override
    public void vCheckTacticFinished() {
        if (chutar.bIsFinished()) vFinalizar();
    }
}
