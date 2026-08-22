package estrategia.coaches;

import estrategia.Coach;
import estrategia.plays.TesteAtacante;

/**
 * Coach minimo: uma play so, escolhida sozinha.
 *
 * <p>Automatico ja aqui, mesmo com uma unica play, porque e o caminho que vai
 * valer depois: quando entrar a segunda jogada, nao ha nada a mudar neste
 * arquivo. E porque em modo automatico a play e reiniciada sozinha quando termina
 * ou aborta, que e o comportamento que se quer numa bancada -- o robo faz gol,
 * a play conclui, o coach escolhe de novo e ele volta a atacar.
 */
public final class Bancada extends Coach {

    public static final int PLAY_TESTE = 1;

    public Bancada() {
        super(1, Modo.AUTOMATICO);
        bAddPlay(new TesteAtacante(PLAY_TESTE));
    }

    @Override
    public String strName() { return "Bancada"; }

    @Override
    public void vInitialize() { }
}
