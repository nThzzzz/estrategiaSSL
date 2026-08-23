package jogo;

import core.Vec2;
import model.Cor;
import proto.gc.SslGcRefereeMessage.Referee;

/**
 * Guarda a ultima mensagem do arbitro e a traduz para o nosso ponto de vista.
 *
 * <h2>Por que guardar a mensagem crua</h2>
 *
 * <p>A traducao acontece na LEITURA, e nao na chegada. A mensagem do arbitro
 * nomeia cores, e a nossa cor pode mudar com a janela aberta -- trocar de azul
 * para amarelo no painel tem de virar "falta deles" na hora, sem esperar o
 * proximo pacote do Game Controller, que em {@code HALT} pode demorar. Guardar o
 * cru e traduzir sob demanda resolve isso sem nenhum codigo de invalidacao.
 *
 * <h2>Rede e painel de teste</h2>
 *
 * <p>As duas fontes escrevem aqui, e uma exclui a outra: em modo local, pacote da
 * rede e ignorado. Sem essa exclusao, um Game Controller rodando na mesma rede
 * sobrescreveria o comando de teste no quadro seguinte, e o painel pareceria
 * quebrado.
 */
public final class Arbitro {

    private volatile Referee ultima;
    private volatile boolean modoLocal;
    private volatile long recebidoEm;
    private volatile long pacotes;

    public boolean isModoLocal()          { return modoLocal; }
    public long getPacotesRecebidos()     { return pacotes; }

    /**
     * Troca entre o arbitro da rede e o painel de teste.
     *
     * <p>O estado corrente e descartado na troca, e nao mantido: herdar o comando
     * da fonte anterior faria a estrategia agir por uma ordem que a fonte atual
     * nunca deu.
     */
    /**
     * Troca a fonte do arbitro, descartando o que a fonte anterior tinha dito.
     *
     * <p>Descartar e o certo numa troca de verdade: o ultimo comando do Game
     * Controller nao vale mais depois de passar para o arbitro local, e vice
     * versa. Mas so numa troca de VERDADE -- chamar com o modo que ja esta
     * valendo apagava o comando corrente, e quem reencostava nisso era a
     * interface, ao sincronizar o seletor com o estado real.
     */
    public void setModoLocal(boolean local) {
        if (local == modoLocal) return;
        this.modoLocal = local;
        this.ultima = null;
        this.recebidoEm = 0;
    }

    /** Mensagem vinda do Game Controller. Ignorada em modo local. */
    public void daRede(Referee r) {
        if (modoLocal) return;
        pacotes++;
        aceitar(r);
    }

    /** Mensagem fabricada pelo painel de teste. Ignorada quando a rede manda. */
    public void doPainel(Referee r) {
        if (!modoLocal) return;
        aceitar(r);
    }

    private void aceitar(Referee r) {
        this.ultima = r;
        this.recebidoEm = System.nanoTime();
    }

    /** Segundos desde a ultima mensagem, ou infinito se nunca chegou nenhuma. */
    public double silencio() {
        long t = recebidoEm;
        return t == 0 ? Double.POSITIVE_INFINITY : (System.nanoTime() - t) / 1e9;
    }

    /**
     * O estado de jogo visto por quem joga de {@code nossaCor}.
     *
     * <p>Sem mensagem nenhuma devolve {@link EstadoDeJogo#SEM_ARBITRO}, que manda
     * parar. E a escolha conservadora: nao saber o estado do jogo nao autoriza a
     * jogar.
     */
    public EstadoDeJogo estado(Cor nossaCor) {
        Referee r = ultima;
        if (r == null) return EstadoDeJogo.SEM_ARBITRO;

        boolean somosAzul = nossaCor == Cor.AZUL;
        Referee.TeamInfo nos = somosAzul ? r.getBlue() : r.getYellow();
        Referee.TeamInfo eles = somosAzul ? r.getYellow() : r.getBlue();

        Acao acao = acaoDe(r.getCommand());
        boolean nosso = ehNosso(r.getCommand(), somosAzul);

        // blue_team_on_positive_half fala do AZUL; para nos, depende da cor.
        boolean azulNoPositivo = r.hasBlueTeamOnPositiveHalf() && r.getBlueTeamOnPositiveHalf();
        boolean nossoLadoPositivo = somosAzul == azulNoPositivo;

        Vec2 designada = r.hasDesignatedPosition()
                ? new Vec2(r.getDesignatedPosition().getX(), r.getDesignatedPosition().getY())
                : null;

        // stage_time_left vem em MICROssegundos, e pode ser negativo quando o
        // estagio estourou o tempo -- o protocolo usa sint64 exatamente por isso.
        double restante = r.hasStageTimeLeft() ? r.getStageTimeLeft() / 1e6 : -1;

        return new EstadoDeJogo(acao, nosso, corDe(r.getCommand()), nossoLadoPositivo,
                r.getStage().name(), restante,
                nos.getName(), eles.getName(),
                nos.getScore(), eles.getScore(),
                nos.hasGoalkeeper() ? nos.getGoalkeeper() : -1,
                designada, r.getCommandCounter(), !modoLocal);
    }

    static Acao acaoDe(Referee.Command c) {
        return switch (c) {
            case HALT -> Acao.HALT;
            case STOP -> Acao.STOP;
            case NORMAL_START, FORCE_START -> Acao.RUNNING;
            case PREPARE_KICKOFF_BLUE, PREPARE_KICKOFF_YELLOW -> Acao.KICKOFF;
            case PREPARE_PENALTY_BLUE, PREPARE_PENALTY_YELLOW -> Acao.PENALTY;
            case DIRECT_FREE_BLUE, DIRECT_FREE_YELLOW,
                 INDIRECT_FREE_BLUE, INDIRECT_FREE_YELLOW -> Acao.DIRECT_FREE;
            case BALL_PLACEMENT_BLUE, BALL_PLACEMENT_YELLOW -> Acao.BALL_PLACEMENT;
            case TIMEOUT_BLUE, TIMEOUT_YELLOW -> Acao.TIMEOUT;
            // GOAL_* estao aposentados no protocolo: o placar vem no TeamInfo, e o
            // jogo segue por STOP. Tratar como parada e o que o arbitro faria.
            case GOAL_BLUE, GOAL_YELLOW -> Acao.STOP;
        };
    }

    /** De que cor e o comando, ou {@code null} se ele nao nomeia nenhuma. */
    static Cor corDe(Referee.Command c) {
        return switch (c) {
            case PREPARE_KICKOFF_BLUE, PREPARE_PENALTY_BLUE, DIRECT_FREE_BLUE,
                 INDIRECT_FREE_BLUE, TIMEOUT_BLUE, BALL_PLACEMENT_BLUE, GOAL_BLUE -> Cor.AZUL;
            case PREPARE_KICKOFF_YELLOW, PREPARE_PENALTY_YELLOW, DIRECT_FREE_YELLOW,
                 INDIRECT_FREE_YELLOW, TIMEOUT_YELLOW, BALL_PLACEMENT_YELLOW,
                 GOAL_YELLOW -> Cor.AMARELO;
            default -> null;
        };
    }

    /** True se o comando nomeia a NOSSA cor. Para comandos sem dono, sempre false. */
    static boolean ehNosso(Referee.Command c, boolean somosAzul) {
        boolean doAzul = switch (c) {
            case PREPARE_KICKOFF_BLUE, PREPARE_PENALTY_BLUE, DIRECT_FREE_BLUE,
                 INDIRECT_FREE_BLUE, TIMEOUT_BLUE, BALL_PLACEMENT_BLUE, GOAL_BLUE -> true;
            default -> false;
        };
        boolean doAmarelo = switch (c) {
            case PREPARE_KICKOFF_YELLOW, PREPARE_PENALTY_YELLOW, DIRECT_FREE_YELLOW,
                 INDIRECT_FREE_YELLOW, TIMEOUT_YELLOW, BALL_PLACEMENT_YELLOW,
                 GOAL_YELLOW -> true;
            default -> false;
        };
        if (!doAzul && !doAmarelo) return false;
        return somosAzul ? doAzul : doAmarelo;
    }
}
