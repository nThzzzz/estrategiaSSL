package estrategia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma jogada do time inteiro: quem faz o que.
 *
 * <p>A Play define quais {@link Role} existem e quais robos as executam. Ela nao
 * comanda robo nenhum diretamente -- isso desce por Role, Tactic e Skill.
 *
 * <p>Uma play tem inicio e fim declarados, e e isso que permite ao {@link Coach}
 * aprender com ela:
 *
 * <ul>
 *   <li>{@link #bCheckPreConditions()} diz se ela PODE comecar agora
 *   <li>{@link #bCheckEndConditions()} diz se ela tem de ser abortada
 *   <li>{@link #vCheckPlayFinished()} diz se ela se cumpriu
 *   <li>o tempo limite aborta o que travou
 * </ul>
 *
 * <p>Assim que uma play sai de {@link Status#RUNNING}, por qualquer um desses
 * quatro caminhos, o coach escolhe outra. Esse ciclo fechado e o episodio do
 * aprendizado: comeca em uma escolha e termina em um resultado que da para
 * creditar aquela escolha.
 *
 * <h2>Sobre o tempo limite</h2>
 *
 * <p>Contado no relogio da VISAO, e nao no de parede. Com o simulador rodando
 * acelerado os dois divergem muito, e uma play de 10 segundos que virasse 10
 * segundos de tempo real duraria dez vezes menos jogo -- o coach treinado assim
 * aprenderia sobre um jogo que nao existe.
 */
public abstract class Play {

    /** Onde a play esta no proprio ciclo. */
    public enum Status {
        /** Ainda nao comecou. */
        PARADA,
        /** Em andamento. */
        RODANDO,
        /** Cumpriu o que se propunha. */
        CONCLUIDA,
        /** Interrompida pelo tempo ou pela condicao de fim. */
        ABORTADA
    }

    /** Tempo limite padrao de uma jogada, em segundos de jogo. */
    public static final double TEMPO_LIMITE_PADRAO = 120.0;

    /**
     * Vantagem que o robo ja atribuido a um papel leva na reatribuicao.
     *
     * <p>Sem histerese, dois robos a distancias parecidas de um papel ficam
     * trocando de funcao a cada quadro, e nenhum dos dois chega a lugar nenhum.
     * O valor multiplica o custo do candidato atual, entao 0,7 quer dizer "so
     * troque se o outro for pelo menos 30% melhor".
     */
    public static final double HISTERESE_DE_ATRIBUICAO = 0.7;

    private final Map<Integer, Role> playRoles = new LinkedHashMap<>();

    protected Ambiente ambiente;
    protected Status status = Status.PARADA;
    protected boolean bInitialized;

    protected int iID;
    protected double dTempoLimite = TEMPO_LIMITE_PADRAO;

    /** Instante em que a play comecou, no relogio da visao. */
    private double dInicio = Double.NaN;

    /** Retrato do mundo no comeco da play, para {@link #vUpdateScore()} comparar. */
    protected Ambiente ambienteInicial;

    protected Play(int _id) { this.iID = _id; }

    // ------------------------------------------------- a implementar

    public abstract String strName();

    public abstract void vInitialize();

    /**
     * As condicoes para esta play poder COMECAR.
     *
     * <p>O coach so considera plays que respondem true aqui. E onde se escreve
     * "so vale no nosso kickoff" ou "so com a bola no campo de ataque".
     */
    public abstract boolean bCheckPreConditions();

    /**
     * As condicoes que ABORTAM a play em andamento.
     *
     * <p>Checada a cada tique, antes de rodar as roles. Tipicamente o oposto das
     * pre-condicoes: perdemos a bola, o arbitro parou o jogo, o adversario
     * tomou a posicao que a jogada precisava.
     */
    public abstract boolean bCheckEndConditions();

    /**
     * A nota atual da play, usada pelo coach na escolha.
     *
     * <p>Maior e melhor, e nunca negativa: a selecao por roleta reparte
     * probabilidade proporcional a nota, e nota negativa nao tem sentido nela.
     */
    public abstract double dGetScore();

    /**
     * Atualiza a nota com o que aconteceu.
     *
     * <p>Chamado UMA vez, quando a play sai de {@link Status#RODANDO}. E o
     * momento do reforco: compare {@link #ambienteInicial} com {@link #ambiente}
     * -- gol saiu, posse mudou, a bola avancou -- e mova a nota conforme.
     * {@link #status} diz por qual dos caminhos ela terminou, e concluida e
     * abortada merecem reforcos diferentes.
     */
    public abstract void vUpdateScore();

    /** Persiste a nota, para o aprendizado sobreviver ao fim do processo. */
    public abstract void vSaveScore();

    /**
     * A logica extra da play em um tique, se houver.
     *
     * <p>Roda depois das roles ja atribuidas e antes delas executarem. A maioria
     * das plays nao precisa de nada aqui: elas so declaram roles.
     */
    protected void vRun() { }

    // ------------------------------------------------- ciclo de vida

    /**
     * Roda um tique da play.
     *
     * <p>A ordem e deliberada: tempo, condicao de fim, atribuicao, logica
     * propria, roles, conclusao. Checar o fim ANTES de mover os robos evita
     * mandar um comando por uma jogada que ja devia ter parado -- em
     * {@code STOP} do arbitro isso e a diferenca entre obedecer e levar falta.
     */
    public final void vRunPlay(Ambiente _ambiente, List<Jogador> _disponiveis) {
        this.ambiente = _ambiente;
        if (status != Status.RODANDO) return;

        if (dTempoDecorrido() > dTempoLimite) { vAbortar(); return; }
        if (bCheckEndConditions())            { vAbortar(); return; }

        vOptimizeRoleAssignment(_disponiveis);
        vRun();

        for (Role r : playRoles.values()) {
            if (r.player() != null) r.vRunRole(_ambiente, r.player());
        }

        vCheckPlayFinished();
    }

    /** Coloca a play em execucao. Chamado pelo coach. */
    public final void vStart(Ambiente _ambiente) {
        this.ambiente = _ambiente;
        this.ambienteInicial = _ambiente;
        this.dInicio = _ambiente.tempo();
        vResetAllRoles();
        if (!bInitialized) {
            vInitialize();
            bInitialized = true;
        }
        status = Status.RODANDO;
    }

    /**
     * Decide se a play se cumpriu.
     *
     * <p>Por padrao acaba quando todas as roles acabaram. Sobrescreva quando a
     * jogada tiver um criterio proprio -- "a bola entrou", "o passe chegou".
     */
    public void vCheckPlayFinished() {
        if (bRolesFinished()) status = Status.CONCLUIDA;
    }

    private void vAbortar() { status = Status.ABORTADA; }

    public boolean bRolesFinished() {
        if (playRoles.isEmpty()) return false;
        return playRoles.values().stream().allMatch(Role::bIsFinished);
    }

    /** True enquanto a play deve continuar rodando. */
    public boolean bRodando() { return status == Status.RODANDO; }

    public Status status() { return status; }
    public int id() { return iID; }

    public double dTempoDecorrido() {
        return Double.isNaN(dInicio) || ambiente == null ? 0 : ambiente.tempo() - dInicio;
    }

    public double dTempoRestante() { return Math.max(0, dTempoLimite - dTempoDecorrido()); }

    public void vSetTempoLimite(double _segundos) { this.dTempoLimite = _segundos; }

    public void vReset() {
        status = Status.PARADA;
        dInicio = Double.NaN;
        vResetAllRoles();
    }

    // ------------------------------------------------- roles

    public boolean bAddRole(Role _role) {
        if (_role == null || playRoles.containsKey(_role.id())) return false;
        playRoles.put(_role.id(), _role);
        return true;
    }

    public List<Role> roles() { return List.copyOf(playRoles.values()); }

    public void vResetAllRoles() {
        for (Role r : playRoles.values()) r.vResetRole();
    }

    /**
     * Casa robos com papeis, do papel mais prioritario para o menos.
     *
     * <p>Guloso, e nao otimo: para cada papel na ordem de prioridade, pega o robo
     * de menor custo que ainda esta livre. Uma atribuicao otima (o hungaro, que o
     * SSL-Strategy usa) troca alguns milimetros de custo total por bem mais
     * codigo, e a diferenca so aparece em empates. O que importa de verdade e a
     * histerese: sem ela os robos trocam de papel a cada quadro.
     *
     * <p>Sobrescreva se a sua play precisar de outro criterio.
     */
    public void vOptimizeRoleAssignment(List<Jogador> _disponiveis) {
        List<Jogador> livres = new ArrayList<>(_disponiveis);

        List<Role> porPrioridade = new ArrayList<>(playRoles.values());
        porPrioridade.sort(Comparator.comparingInt((Role r) -> r.prioridade().peso()).reversed());

        for (Role role : porPrioridade) {
            Jogador atual = role.player();
            Jogador melhor = null;
            double melhorCusto = Double.POSITIVE_INFINITY;

            for (Jogador j : livres) {
                double custo = role.dCusto(ambiente, j);
                if (j == atual) custo *= HISTERESE_DE_ATRIBUICAO;
                if (custo < melhorCusto) { melhorCusto = custo; melhor = j; }
            }

            if (melhor != null && melhorCusto < Double.POSITIVE_INFINITY) {
                livres.remove(melhor);
                // Trocar de robo zera o papel: as taticas e skills guardam estado
                // do robo anterior, e herda-lo faria o novo continuar de onde o
                // outro parou, em outra posicao do campo.
                if (melhor != atual) {
                    role.vResetRole();
                    role.vSetJogador(melhor);
                } else {
                    role.vSetJogador(atual);
                }
            }
        }
    }
}
