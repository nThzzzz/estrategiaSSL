package estrategia.tactics;

import core.Vec2;
import estrategia.Tactic;
import estrategia.skills.AndarPara;
import estrategia.skills.OlharPara;

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
public final class ConduzirAoGol extends Tactic {

    private static final int SKILL_ANDAR = 1;

    /** Teto de velocidade com a bola presa, em mm/s. */
    public static final double VEL_CONDUCAO = 1200;

    /**
     * A que distancia do gol o atacante passa a mirar, em mm.
     *
     * <p>Ver {@link #DISTANCIA_DE_CONDUCAO}: os dois numeros nao podem ser
     * iguais.
     */
    public static final double DISTANCIA_DE_CHUTE = 2500;

    /**
     * Ate onde a conducao leva a bola, em mm do gol.
     *
     * <p>Menor que {@link #DISTANCIA_DE_CHUTE} de proposito, e isso NAO e um
     * ajuste fino: com os dois iguais, o robo para exatamente em cima do limiar
     * que decide entre conduzir e chutar, e a tolerancia de chegada faz ele parar
     * ora um pouco dentro, ora um pouco fora. Quando para fora, ele fica parado
     * com a bola achando que ainda tem de conduzir, e a jogada morre ali. Com o
     * destino 500 mm dentro da zona de chute, a troca acontece durante a
     * aproximacao e o robo ja chega mirando.
     */
    public static final double DISTANCIA_DE_CONDUCAO = 2000;

    private final AndarPara andar = new AndarPara();
    private final OlharPara olhar = new OlharPara();

    public ConduzirAoGol(int _id) {
        super(_id);
        andar.vSetVelMax(VEL_CONDUCAO);
        andar.vSetTolerancia(120);
        bAddSkill(SKILL_ANDAR, andar);
    }

    @Override
    public String strName() { return "ConduzirAoGol"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        Vec2 gol = ambiente.golDeles();
        Vec2 daBolaAoGol = gol.menos(jogador.estado().posicao());

        Vec2 destino = daBolaAoGol.norma() <= DISTANCIA_DE_CONDUCAO
                ? jogador.estado().posicao()
                : gol.menos(daBolaAoGol.comNorma(DISTANCIA_DE_CONDUCAO));

        andar.vSetDestino(destino);
        andar.vSetVelMax(VEL_CONDUCAO);
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
                <= DISTANCIA_DE_CONDUCAO;
        if (perdeuABola || chegou) vFinalizar();
    }
}
