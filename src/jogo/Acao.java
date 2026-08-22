package jogo;

/**
 * O comando do arbitro, no vocabulario do proprio protocolo.
 *
 * <p>Os nomes sao os do {@code Referee.Command}, sem a cor: o protocolo tem
 * {@code PREPARE_KICKOFF_BLUE} e {@code PREPARE_KICKOFF_YELLOW}, e aqui isso e
 * {@link #KICKOFF} mais a cor, que vive em {@link EstadoDeJogo#corDaAcao()}.
 *
 * <p>Manter o vocabulario da liga em vez de traduzir foi escolha deliberada.
 * Quem opera a estrategia esta olhando o Game Controller ao lado, ouvindo o
 * arbitro humano falar "stop" e lendo o log da competicao, e tudo isso diz
 * {@code STOP}. Um rotulo traduzido obrigaria a fazer a conversao de cabeca
 * justamente no momento em que nao ha tempo para isso.
 *
 * <p>A pergunta "e a favor de quem?" continua respondida por
 * {@link EstadoDeJogo#nosso()}, e e ela que a estrategia consulta. A cor serve
 * para exibir, nao para decidir.
 */
public enum Acao {

    /** Ninguem se mexe. */
    HALT("HALT"),

    /** Jogo interrompido, com limite de velocidade e distancia da bola. */
    STOP("STOP"),

    /** {@code NORMAL_START} e {@code FORCE_START}: bola em jogo. */
    RUNNING("RUNNING"),

    /** {@code PREPARE_KICKOFF_*}: posicionamento para a saida de bola. */
    KICKOFF("KICKOFF"),

    /** {@code PREPARE_PENALTY_*}. */
    PENALTY("PENALTY"),

    /** {@code DIRECT_FREE_*}, e os {@code INDIRECT_FREE_*} que o protocolo aposentou. */
    DIRECT_FREE("DIRECT FREE"),

    /** {@code BALL_PLACEMENT_*}: alguem tem de levar a bola ate a posicao designada. */
    BALL_PLACEMENT("BALL PLACEMENT"),

    /** {@code TIMEOUT_*}. */
    TIMEOUT("TIMEOUT");

    private final String rotulo;

    Acao(String rotulo) { this.rotulo = rotulo; }

    /** Como aparece na tela, ja em caixa alta. */
    public String rotulo() { return rotulo; }

    /** True quando a acao tem dono, ou seja quando cor e {@code nosso} querem dizer algo. */
    public boolean temDono() {
        return this == KICKOFF || this == PENALTY || this == DIRECT_FREE
                || this == BALL_PLACEMENT || this == TIMEOUT;
    }
}
