package estrategia.tactics;

import core.Vec2;
import estrategia.Tactic;
import estrategia.skills.AndarPara;
import estrategia.skills.OlharPara;
import model.Robo;
import mundo.Projecao;

/**
 * Vai ate a bola e a captura no dribbler.
 *
 * <h2>Aonde ir</h2>
 *
 * <p>Nao para cima da bola: para o PONTO DE ENCONTRO, onde ela vai estar quando o
 * robo chegar. Perseguir a posicao atual de uma bola em movimento desenha uma
 * curva de perseguicao, que chega sempre atras e por tras.
 *
 * <h2>De que lado chegar</h2>
 *
 * <p>O destino fica ATRAS da bola em relacao ao gol adversario, recuado o
 * bastante para a boca do dribbler encostar nela. Chegar por qualquer lado
 * captura a bola do mesmo jeito, mas deixa o robo de costas para o ataque, e a
 * jogada seguinte comeca com um giro carregando a bola -- que e justamente
 * quando ela escapa.
 */
public final class BuscarBola extends Tactic {

    private static final int SKILL_ANDAR = 1;

    private final AndarPara andar = new AndarPara();
    private final OlharPara olhar = new OlharPara();

    public BuscarBola(int _id) {
        super(_id);
        bAddSkill(SKILL_ANDAR, andar);
    }

    @Override
    public String strName() { return "BuscarBola"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        Vec2 encontro = Projecao.pontoDeEncontro(jogador.estado(), ambiente.bola());

        // Recuo suficiente para a face do dribbler tocar a bola quando o centro
        // do robo estiver no destino.
        double recuo = Robo.DIST_FACE_FRONTAL + ambiente.geometria().raioBola();
        Vec2 doGolParaABola = encontro.menos(ambiente.golDeles());
        Vec2 destino = doGolParaABola.norma() < 1e-6
                ? encontro
                : encontro.mais(doGolParaABola.comNorma(recuo));

        andar.vSetDestino(destino);
        olhar.vSetAlvo(encontro);
        jogador.vDribbler(true);

        vExecutar(olhar);
    }

    /** Acaba quando a bola esta no sensor, e nao quando o robo chegou ao ponto. */
    @Override
    public void vCheckTacticFinished() {
        if (jogador != null && jogador.bComABola()) vFinalizar();
    }
}
