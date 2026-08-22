package jogo;

import core.Vec2;

/**
 * O estado de jogo do nosso ponto de vista.
 *
 * <p>Retrato imutavel, como o {@link mundo.Quadro}: a thread do arbitro escreve,
 * a Swing e a estrategia leem.
 *
 * <p>Tudo aqui ja esta traduzido para "nos" e "eles". A cor nao aparece em campo
 * nenhum de proposito -- quem consome nao deveria precisar saber de que cor
 * jogamos para decidir se a falta e a favor.
 *
 * @param acao        o que fazer
 * @param nosso       a acao e a nosso favor; so tem sentido quando {@link Acao#temDono()}
 * @param defendemosLadoPositivo em qual metade fica o NOSSO gol, do
 *        {@code blue_team_on_positive_half}. A visao nao diz isto, e sem ele nao
 *        da para saber para que lado atacar
 * @param estagio     nome do {@code Stage} corrente, para exibir
 * @param tempoRestante segundos restantes no estagio, ou negativo se o protocolo nao informou
 * @param nomeNosso   nome do nosso time, como o arbitro conhece
 * @param nomeDeles   idem, do adversario
 * @param golsNossos  placar a nosso favor
 * @param golsDeles   placar contra
 * @param goleiro     id do robo que declaramos como goleiro
 * @param posicaoDesignada alvo do ball placement, ou {@code null}
 * @param contador    {@code command_counter}: muda a cada comando novo, mesmo que o comando repita
 * @param daRede      true se veio do Game Controller, false se do painel de teste
 */
public record EstadoDeJogo(
        Acao acao,
        boolean nosso,
        boolean defendemosLadoPositivo,
        String estagio,
        double tempoRestante,
        String nomeNosso,
        String nomeDeles,
        int golsNossos,
        int golsDeles,
        int goleiro,
        Vec2 posicaoDesignada,
        long contador,
        boolean daRede
) {
    /**
     * Antes de qualquer mensagem chegar.
     *
     * <p>Comeca em {@link Acao#PARAR} de proposito: enquanto nao se sabe o estado
     * do jogo, o certo e nao mover robo nenhum. Um padrao otimista faria a equipe
     * entrar em campo andando durante um HALT que ela ainda nao ouviu.
     */
    public static final EstadoDeJogo SEM_ARBITRO = new EstadoDeJogo(
            Acao.PARAR, false, false, "sem arbitro", -1,
            null, null, 0, 0, -1, null, -1, false);

    /** Limite de velocidade durante {@code STOP}, em mm/s. */
    public static final double VEL_MAX_PARADO = 1500;

    /** Distancia minima da bola quando o jogo esta parado ou a reposicao e deles, em mm. */
    public static final double DISTANCIA_MINIMA_DA_BOLA = 500;

    public boolean semArbitro() { return contador < 0; }

    /** True quando a bola esta em jogo e vale disputar. */
    public boolean bolaEmJogo() { return acao == Acao.JOGAR; }

    /** True quando podemos mover os robos. */
    public boolean podemosMover() { return acao != Acao.PARAR; }

    /**
     * Teto de velocidade imposto pelas regras neste instante, em mm/s.
     *
     * <p>Infinito quando nao ha limite. Nao e uma preferencia da estrategia: e
     * regra, e por isso mora aqui e nao no planejador.
     */
    public double limiteDeVelocidade() {
        if (acao == Acao.PARAR) return 0;
        if (acao == Acao.JOGAR) return Double.POSITIVE_INFINITY;
        return VEL_MAX_PARADO;
    }

    /**
     * Quanto temos de nos afastar da bola, em mm.
     *
     * <p>Zero quando a bola esta em jogo, ou quando a reposicao e nossa -- nesse
     * caso e o adversario que tem de dar espaco.
     */
    public double afastamentoDaBola() {
        if (acao == Acao.JOGAR || acao == Acao.PARAR) return 0;
        if (acao.temDono() && nosso) return 0;
        return DISTANCIA_MINIMA_DA_BOLA;
    }

    /** Sinal do nosso lado do campo: -1 ou +1 em x. */
    public int nossoLado() { return defendemosLadoPositivo ? 1 : -1; }

    /** Descricao curta para a barra do topo. */
    public String descricao() {
        if (semArbitro()) return "sem arbitro";
        if (!acao.temDono()) return acao.rotulo();
        return acao.rotulo() + (nosso ? " nosso" : " deles");
    }
}
