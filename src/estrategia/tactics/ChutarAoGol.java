package estrategia.tactics;

import estrategia.Tactic;
import estrategia.skills.Chutar;
import estrategia.skills.OlharPara;

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
public final class ChutarAoGol extends Tactic {

    private static final int SKILL_OLHAR = 1;
    private static final int SKILL_CHUTAR = 2;

    /** Erro de mira aceito antes de acionar o chutador. */
    public static final double TOLERANCIA_DE_MIRA = Math.toRadians(3);

    private final OlharPara olhar = new OlharPara();
    private final Chutar chutar = new Chutar();

    public ChutarAoGol(int _id) {
        super(_id);
        olhar.vSetTolerancia(TOLERANCIA_DE_MIRA);
        bAddSkill(SKILL_OLHAR, olhar);
        bAddSkill(SKILL_CHUTAR, chutar);
    }

    /** Velocidade do chute, em mm/s. */
    public void vSetVelocidade(double _mmPorSegundo) { chutar.vSetVelocidade(_mmPorSegundo); }

    @Override
    public String strName() { return "ChutarAoGol"; }

    @Override
    public void vInitialize() { bSetSkill(SKILL_OLHAR); }

    @Override
    protected void vRun() {
        olhar.vSetAlvo(ambiente.golDeles());
        jogador.vDribbler(true);

        if (Math.abs(olhar.dErro()) <= TOLERANCIA_DE_MIRA) {
            bSetSkill(SKILL_CHUTAR);
        } else {
            bSetSkill(SKILL_OLHAR);
        }
    }

    /** Acaba quando o chute saiu, ou se a bola escapou antes disso. */
    @Override
    public void vCheckTacticFinished() {
        if (chutar.bIsFinished()) vFinalizar();
        else if (jogador != null && jogador.bEmCampo() && !jogador.bComABola()) vFinalizar();
    }
}
