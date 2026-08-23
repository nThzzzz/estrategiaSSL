package estrategia.tactics;

import core.Vec2;
import estrategia.ids.Habilidade;
import estrategia.esqueleto.Tactic;
import estrategia.ids.Tatica;
import estrategia.skills.SkillAndarPara;
import estrategia.skills.SkillOlharPara;
import model.Robo;

/**
 * Vai ate a bola e a captura no dribbler.
 *
 * <h2>Vai na bola onde ela esta</h2>
 *
 * <p>Sem prever para onde ela vai. Com a bola em movimento isso desenha uma curva
 * de perseguicao e chega atrasado -- e e assim de proposito, porque previsao de
 * bola e assunto a parte, para ser escrito com cuidado depois. O que esta aqui e
 * o comportamento mais simples que funciona.
 *
 * <h2>De que lado chegar</h2>
 *
 * <p>O destino fica ATRAS da bola em relacao ao gol adversario, recuado o
 * bastante para a boca do dribbler encostar nela. Chegar por qualquer lado
 * captura a bola do mesmo jeito, mas deixa o robo de costas para o ataque, e a
 * jogada seguinte comeca com um giro carregando a bola -- que e justamente
 * quando ela escapa.
 */
public final class TacticBuscarBola extends Tactic {


    private final SkillAndarPara andar = new SkillAndarPara();
    private final SkillOlharPara olhar = new SkillOlharPara();

    /**
     * O id vem de {@link Tatica}, e nao de quem registra a tatica.
     *
     * <p>O construtor que recebe {@code int} continua existindo so enquanto os
     * papeis ainda numeram por conta propria; ele pode sair assim que o ultimo
     * deles passar a usar este.
     */
    public TacticBuscarBola() { this(Tatica.BUSCAR_BOLA.id()); }

    public TacticBuscarBola(int _id) {
        super(_id);
        // Tolerancia menor que a folga do sensor. Ver vRun: parar com a folga
        // padrao deixa o robo a um palmo da bola, sem nunca captura-la.
        andar.vSetTolerancia(30);
        bAddSkill(Habilidade.ANDAR_PARA, andar);
    }

    @Override
    public String strName() { return "TacticBuscarBola"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        Vec2 bola = ambiente.bola().posicao();

        // O destino poe a BOCA em cima do centro da bola, e nao encostada nela.
        //
        // Mirar o ponto de contato exato (face + raio da bola) parece certo e nao
        // e: o robo declara chegada dentro da propria tolerancia, e nessa folga a
        // bola fica alem do alcance do sensor. O resultado e um robo parado a um
        // palmo da bola, para sempre, que foi exatamente o sintoma observado.
        // Mirando o centro da bola, ele encosta com sobra e o rolete a captura.
        double recuo = Robo.DIST_FACE_FRONTAL;
        Vec2 doGolParaABola = bola.menos(ambiente.golDeles());
        Vec2 destino = doGolParaABola.norma() < 1e-6
                ? bola
                : bola.mais(doGolParaABola.comNorma(recuo));

        andar.vSetDestino(destino);
        olhar.vSetAlvo(bola);
        jogador.vDribbler(true);

        vExecutar(olhar);
    }

    /** Acaba quando a bola esta no sensor, e nao quando o robo chegou ao ponto. */
    @Override
    public void vCheckTacticFinished() {
        if (jogador != null && jogador.bComABola()) vFinalizar();
    }
}
