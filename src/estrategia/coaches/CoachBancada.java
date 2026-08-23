package estrategia.coaches;

import estrategia.esqueleto.Coach;
import estrategia.plays.PlayTesteAtacante;

/**
 * Coach minimo: uma play so, escolhida sozinha.
 *
 * <p>Automatico ja aqui, mesmo com uma unica play, porque e o caminho que vai
 * valer depois: quando entrar a segunda jogada, nao ha nada a mudar neste
 * arquivo. E porque em modo automatico a play e reiniciada sozinha quando termina
 * ou aborta, que e o comportamento que se quer numa bancada -- o robo faz gol,
 * a play conclui, o coach escolhe de novo e ele volta a atacar.
 */
public final class CoachBancada extends Coach {

    public CoachBancada() {
        super(1, Modo.AUTOMATICO);
        bAddPlay(new PlayTesteAtacante());
    }

    @Override
    public String strName() { return "CoachBancada"; }

    @Override
    public void vInitialize() { }
}
