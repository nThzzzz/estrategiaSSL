package jogo;

/**
 * O que o arbitro esta mandando fazer, ja traduzido do protocolo.
 *
 * <p>O {@code Referee.Command} nomeia cores: {@code PREPARE_KICKOFF_BLUE},
 * {@code DIRECT_FREE_YELLOW}. Aqui a cor sumiu, porque quem le e a nossa
 * estrategia e para ela o que importa e "a favor" ou "contra" -- o lado fica em
 * {@link EstadoDeJogo#nosso()}. Sem essa traducao, cada pedaco do codigo teria de
 * lembrar de que cor jogamos hoje, e trocar de cor no painel viraria uma cacada a
 * comparacoes espalhadas.
 */
public enum Acao {

    /** {@code HALT}: ninguem se mexe. */
    PARAR("Parado"),

    /** {@code STOP}: jogo interrompido, com limite de velocidade e distancia da bola. */
    AFASTAR("Afastar"),

    /** {@code NORMAL_START} e {@code FORCE_START}: bola em jogo. */
    JOGAR("Jogo corrido"),

    /** {@code PREPARE_KICKOFF_*}: posicionamento para a saida de bola. */
    KICKOFF("Kickoff"),

    /** {@code PREPARE_PENALTY_*}. */
    PENALTI("Penalti"),

    /** {@code DIRECT_FREE_*}, e os {@code INDIRECT_FREE_*} que o protocolo aposentou. */
    FALTA("Falta"),

    /** {@code BALL_PLACEMENT_*}: alguem tem de levar a bola ate a posicao designada. */
    POSICIONAR_BOLA("Posicionar bola"),

    /** {@code TIMEOUT_*}. */
    TEMPO("Tempo tecnico");

    private final String rotulo;

    Acao(String rotulo) { this.rotulo = rotulo; }

    /** Nome curto para a interface. */
    public String rotulo() { return rotulo; }

    /** True quando a acao tem dono, ou seja quando "nosso" quer dizer alguma coisa. */
    public boolean temDono() {
        return this == KICKOFF || this == PENALTI || this == FALTA
                || this == POSICIONAR_BOLA || this == TEMPO;
    }
}
