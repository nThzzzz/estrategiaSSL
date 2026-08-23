package estrategia.tactics;

import core.Vec2;
import estrategia.Habilidade;
import estrategia.Tactic;
import estrategia.Tatica;
import estrategia.skills.AndarPara;
import estrategia.skills.OlharPara;
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
public final class BuscarBola extends Tactic {


    private final AndarPara andar = new AndarPara();
    private final OlharPara olhar = new OlharPara();

    /**
     * O id vem de {@link Tatica}, e nao de quem registra a tatica.
     *
     * <p>O construtor que recebe {@code int} continua existindo so enquanto os
     * papeis ainda numeram por conta propria; ele pode sair assim que o ultimo
     * deles passar a usar este.
     */
    public BuscarBola() { this(Tatica.BUSCAR_BOLA.id()); }

    public BuscarBola(int _id) {
        super(_id);
        // Tolerancia menor que a folga do sensor. Ver vRun: parar com a folga
        // padrao deixa o robo a um palmo da bola, sem nunca captura-la.
        andar.vSetTolerancia(30);
        bAddSkill(Habilidade.ANDAR_PARA, andar);
    }

    @Override
    public String strName() { return "BuscarBola"; }

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
