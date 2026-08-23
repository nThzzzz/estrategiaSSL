package estrategia.esqueleto;

import estrategia.Ambiente;
import estrategia.Jogador;
import estrategia.ids.Jogada;

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
 * <p>Uma play declara os papeis da jogada IDEAL, e nao os que couberem hoje. Cada
 * papel entra numa {@link Prioridade} -- de {@code MAXIMA} a {@code OPCIONAL} --
 * declarada AQUI, no {@link #bAddRole}, e nao dentro da Role: o mesmo papel vale
 * coisas diferentes em jogadas diferentes. E ela que decide quem fica sem robo
 * quando faltam robos, e e o que permite escrever uma play pensando em seis e ela
 * rodar com cinco: quem sobra e o ultimo {@code OPCIONAL}, e nao o goleiro.
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

    /**
     * Em que ordem os papeis desta play pegam robo quando faltam robos.
     *
     * <p>Uma play declara os papeis que a jogada IDEAL teria, e quase nunca ha
     * robo para todos: cartao, quebra, robo fora do alcance da visao. Sem uma
     * classificacao, quem ficasse de fora seria decidido pela ordem em que alguem
     * escreveu {@code bAddRole}, o que e o mesmo que decidir por acaso.
     *
     * <p>Mora na PLAY e nao na {@link Role} de proposito. O mesmo papel vale
     * coisas diferentes em jogadas diferentes: um marcador e {@code MAXIMA} numa
     * defesa e {@code OPCIONAL} num ataque com o campo livre. Guardar a prioridade
     * na Role obrigaria a escrever duas classes identicas so para dizer isso, ou a
     * mexer no papel de uma jogada e quebrar outra sem perceber.
     *
     * <p>A categoria responde "este papel pode faltar?":
     *
     * <ul>
     *   <li>{@link #MAXIMA} -- nao pode. Sem ele a jogada nao existe: o goleiro
     *       numa defesa, o batedor num penalti. Se nao houver robo para todos os
     *       papeis de prioridade maxima, a play nao e nem escolhida, e aborta se
     *       perder um no meio.
     *   <li>{@link #MEDIA} -- o corpo da jogada. Faltando um, ela perde qualidade
     *       e continua de pe.
     *   <li>{@link #BAIXA} -- apoio. Sai antes do resto quando falta robo.
     *   <li>{@link #OPCIONAL} -- se sobrar robo, otimo; se nao, a jogada nao
     *       sente. E onde entram cobertura e apoio distante.
     * </ul>
     *
     * <p>E isso que permite escrever uma play pensando em seis robos e ela rodar
     * com cinco: com um robo a menos, quem fica sem funcao e o ultimo OPCIONAL, e
     * nao o goleiro. Dentro da mesma categoria vale a ordem de declaracao, porque
     * a ordenacao e estavel -- entao papeis igualmente dispensaveis saem na ordem
     * em que a play os escreveu, que e onde essa decisao fica visivel.
     */
    public enum Prioridade {
        MAXIMA(3),
        MEDIA(2),
        BAIXA(1),
        OPCIONAL(0);

        private final int peso;
        Prioridade(int peso) { this.peso = peso; }

        /** Maior pega robo primeiro. */
        public int peso() { return peso; }

        /** True quando a play nao roda sem este papel. */
        public boolean bEssencial() { return this == MAXIMA; }
    }

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

    /** A prioridade de cada papel NESTA play, indexada pelo id do papel. */
    private final Map<Integer, Prioridade> prioridades = new LinkedHashMap<>();

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

    /** O caminho normal: o id vem da lista global de {@link Jogada}. */
    protected Play(Jogada _id) { this(_id.id()); }

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
        // Perder um papel de prioridade maxima no meio da jogada -- robo que
        // quebrou ou sumiu da visao -- e o mesmo que a jogada deixar de existir.
        // Continuar sem ele seria rodar uma defesa sem goleiro.
        if (!bPapeisEssenciaisAtribuidos()) { vAbortar(); return; }

        vRun();

        for (Role r : playRoles.values()) {
            if (r.player() != null) r.vRunRole(_ambiente, r.player());
        }

        vCheckPlayFinished();
    }

    /**
     * Entrega o mundo a play sem inicia-la.
     *
     * <p>Existe por causa de {@link #bCheckPreConditions()}: o coach pergunta se a
     * play PODE comecar antes de comecar, e a resposta quase sempre depende do
     * mundo -- "so com a bola em jogo", "so no nosso campo de ataque". Sem isto,
     * {@code ambiente} ainda seria nulo na hora da pergunta e toda play que
     * consultasse o mundo responderia que nao pode, para sempre.
     */
    final void vSetAmbiente(Ambiente _ambiente) { this.ambiente = _ambiente; }

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

    /**
     * True quando todo papel COM ROBO ja acabou.
     *
     * <p>Papel que ficou sem robo nao conta. Ele nunca roda, entao nunca termina,
     * e exigi-lo faria uma play de seis papeis rodando com cinco robos ficar
     * pendurada ate o tempo limite -- que e exatamente o caso que as categorias
     * de {@link Role.Prioridade} existem para permitir.
     */
    public boolean bRolesFinished() {
        List<Role> comRobo = playRoles.values().stream()
                .filter(r -> r.player() != null).toList();
        if (comRobo.isEmpty()) return false;
        return comRobo.stream().allMatch(Role::bIsFinished);
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

    /**
     * Declara um papel e o quanto ele importa NESTA jogada.
     *
     * <p>A prioridade e obrigatoria porque e uma decisao de projeto da play, e nao
     * um detalhe com padrao razoavel: e ela que diz quem fica sem robo quando
     * faltam robos. Um padrao silencioso faria toda play nova cair na mesma
     * categoria e o mecanismo inteiro virar enfeite.
     */
    public boolean bAddRole(Role _role, Prioridade _prioridade) {
        if (_role == null || _prioridade == null) return false;
        if (playRoles.containsKey(_role.id())) return false;
        playRoles.put(_role.id(), _role);
        prioridades.put(_role.id(), _prioridade);
        return true;
    }

    /** A prioridade com que este papel foi declarado aqui. */
    public Prioridade prioridadeDe(Role _role) {
        return prioridades.getOrDefault(_role.id(), Prioridade.MEDIA);
    }

    public List<Role> roles() { return List.copyOf(playRoles.values()); }

    /**
     * Quantos robos esta play precisa para existir: um por papel {@code MAXIMA}.
     *
     * <p>E o numero que o {@link Coach} compara com os robos em campo antes de
     * sortear. Uma play que precisa de goleiro e batedor nao deve nem entrar no
     * sorteio com um robo so -- comecar e abortar no primeiro tique gastaria um
     * episodio de aprendizado com um resultado que nao diz nada sobre a jogada.
     */
    public int iRobosMinimos() {
        return (int) playRoles.values().stream()
                .filter(r -> prioridadeDe(r).bEssencial()).count();
    }

    /** True quando todo papel {@code MAXIMA} tem robo. */
    public boolean bPapeisEssenciaisAtribuidos() {
        return playRoles.values().stream()
                .noneMatch(r -> prioridadeDe(r).bEssencial() && r.player() == null);
    }

    public void vResetAllRoles() {
        for (Role r : playRoles.values()) r.vResetRole();
    }

    /**
     * Casa robos com papeis, da categoria mais alta para a mais baixa.
     *
     * <p>Guloso, e nao otimo: para cada papel na ordem de {@link Prioridade},
     * pega o robo de menor custo que ainda esta livre. Uma atribuicao otima (o
     * hungaro, que o SSL-Strategy usa) troca alguns milimetros de custo total por
     * bem mais codigo, e a diferenca so aparece em empates. O que importa de
     * verdade e a histerese: sem ela os robos trocam de papel a cada quadro.
     *
     * <p>A ordenacao e ESTAVEL, entao papeis da mesma categoria disputam na ordem
     * em que a play os declarou. E onde o desempate fica visivel para quem escreve
     * a jogada.
     *
     * <p>Papel que nao acha robo -- porque acabaram, ou porque todo candidato tem
     * custo infinito -- fica explicitamente VAZIO. Deixa-lo com o robo do quadro
     * anterior faria o mesmo robo aparecer em dois papeis ao mesmo tempo, cada um
     * escrevendo um comando por cima do outro.
     *
     * <p>Sobrescreva se a sua play precisar de outro criterio.
     */
    public void vOptimizeRoleAssignment(List<Jogador> _disponiveis) {
        List<Jogador> livres = new ArrayList<>(_disponiveis);

        List<Role> porPrioridade = new ArrayList<>(playRoles.values());
        porPrioridade.sort(Comparator.comparingInt((Role r) -> prioridadeDe(r).peso()).reversed());

        for (Role role : porPrioridade) {
            Jogador atual = role.player();
            Jogador melhor = null;
            double melhorCusto = Double.POSITIVE_INFINITY;

            for (Jogador j : livres) {
                double custo = role.dCusto(ambiente, j);
                if (j == atual) custo *= HISTERESE_DE_ATRIBUICAO;
                if (custo < melhorCusto) { melhorCusto = custo; melhor = j; }
            }

            if (melhor == null || melhorCusto == Double.POSITIVE_INFINITY) {
                if (atual != null) role.vResetRole();
                continue;
            }

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
