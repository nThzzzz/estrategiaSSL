package estrategia.tactics;

import ajuste.Parametro;
import core.Vec2;
import estrategia.ids.Habilidade;
import estrategia.esqueleto.Tactic;
import estrategia.ids.Tatica;
import estrategia.skills.SkillAndarPara;
import estrategia.skills.SkillOlharPara;

/**
 * Leva a bola presa no dribbler ate a distancia de chute.
 *
 * <p>Anda devagar de proposito. Com a bola na boca, o que a mantem ali e o rolete
 * contra a inercia dela; acelerar ou virar rapido demais vence essa forca e a
 * bola escapa. O teto de conducao e bem menor que o do robo, e e o principal
 * numero a ajustar se a bola estiver fugindo.
 *
 * <p>Olha para o gol enquanto conduz, e nao para o destino. Chegar ja alinhado
 * economiza o giro final, que e o momento mais arriscado da jogada inteira: girar
 * com a bola na boca e o jeito mais comum de perde-la.
 */
public final class TacticConduzirAoGol extends Tactic {


    private final SkillAndarPara andar = new SkillAndarPara();
    private final SkillOlharPara olhar = new SkillOlharPara();

    /**
     * O id vem de {@link Tatica}, e nao de quem registra a tatica.
     *
     * <p>O construtor que recebe {@code int} continua existindo so enquanto os
     * papeis ainda numeram por conta propria; ele pode sair assim que o ultimo
     * deles passar a usar este.
     */
    public TacticConduzirAoGol() { this(Tatica.CONDUZIR_AO_GOL.id()); }

    public TacticConduzirAoGol(int _id) {
        super(_id);
        andar.vSetVelMax(Parametro.VEL_CONDUCAO.valor());
        andar.vSetTolerancia(Parametro.TOLERANCIA_DE_CONDUCAO.valor());
        bAddSkill(Habilidade.ANDAR_PARA, andar);
    }

    @Override
    public String strName() { return "TacticConduzirAoGol"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        Vec2 gol = ambiente.golDeles();
        Vec2 daBolaAoGol = gol.menos(jogador.estado().posicao());

        Vec2 destino = daBolaAoGol.norma() <= Parametro.DISTANCIA_DE_CONDUCAO.valor()
                ? jogador.estado().posicao()
                : gol.menos(daBolaAoGol.comNorma(Parametro.DISTANCIA_DE_CONDUCAO.valor()));

        andar.vSetDestino(destino);
        andar.vSetVelMax(Parametro.VEL_CONDUCAO.valor());
        olhar.vSetAlvo(gol);
        jogador.vDribbler(true);

        vExecutar(olhar);
    }

    /** Acaba ao chegar na distancia de chute, ou se a bola escapou. */
    @Override
    public void vCheckTacticFinished() {
        if (jogador == null || !jogador.bEmCampo()) return;
        boolean perdeuABola = !jogador.bComABola();
        boolean chegou = jogador.estado().posicao().distancia(ambiente.golDeles())
                <= Parametro.DISTANCIA_DE_CONDUCAO.valor();
        if (perdeuABola || chegou) vFinalizar();
    }
}
