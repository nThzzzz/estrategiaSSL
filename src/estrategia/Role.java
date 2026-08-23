package estrategia;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O papel que UM robo ocupa dentro de uma {@link Play}: goleiro, zagueiro,
 * atacante, recebedor de passe.
 *
 * <p>Toda Role tem {@link Tactic}. A hierarquia inteira e essa:
 *
 * <pre>
 *   Play    -> quem faz o que          (o time todo)
 *   Role    -> o papel de um robo      (um robo)
 *   Tactic  -> conjunto de skills      (um robo)
 *   Skill   -> uma micro habilidade    (um robo)
 * </pre>
 *
 * <p>A Role NAO escolhe qual robo a executa. Ela declara o papel e a
 * {@link Prioridade}, e a Play atribui os robos em
 * {@link Play#vOptimizeRoleAssignment}. E o que faz a mesma play continuar
 * valendo quando um robo leva cartao ou quebra: sem isso, uma play que fixasse
 * "o 3 e o goleiro" pararia de funcionar no instante em que o 3 saisse de campo.
 *
 * <p>Papel sem robo e situacao normal, e nao erro. Ele fica com
 * {@link #player()} nulo, nao roda e nao conta para o fim da play.
 */
public abstract class Role {

    /**
     * Em que ordem os papeis pegam robo quando faltam robos.
     *
     * <p>Uma play declara os papeis que a jogada IDEAL teria, e quase nunca ha
     * robo para todos: cartao, quebra, robo fora do alcance da visao. Sem uma
     * classificacao, quem ficasse de fora seria decidido pela ordem em que
     * alguem escreveu {@code bAddRole}, o que e o mesmo que decidir por acaso.
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

    private final Map<Integer, Tactic> robotTactics = new LinkedHashMap<>();

    protected Ambiente ambiente;
    protected Jogador jogador;

    protected int iID;
    protected boolean bInitialized;
    protected boolean bFinished;
    protected Prioridade prioridade = Prioridade.MEDIA;

    protected Tactic currentTactic;

    protected Role(int _id, Prioridade _prioridade) {
        this.iID = _id;
        this.prioridade = _prioridade;
    }

    // ------------------------------------------------- a implementar

    public abstract String strName();

    public abstract void vInitialize();

    /**
     * A logica do papel em um tique.
     *
     * <p>E aqui que se escolhe a tatica que vale agora, com
     * {@link #bSetTactic(int)}. O executor roda a tatica corrente em seguida.
     */
    protected abstract void vRun();

    /**
     * Custo de colocar este robo neste papel, em mm.
     *
     * <p>Usado pela Play para decidir quem faz o que. Distancia ate o ponto onde
     * o papel comeca costuma bastar; um zagueiro pode preferir quem esta atras da
     * linha da bola, um recebedor de passe quem tem angulo livre.
     *
     * <p>Menor e melhor. Devolva {@link Double#POSITIVE_INFINITY} para dizer que
     * este robo nao serve para este papel de jeito nenhum.
     */
    public abstract double dCusto(Ambiente _ambiente, Jogador _candidato);

    // ------------------------------------------------- ciclo de vida

    /** Roda um tique do papel: decide a tatica e executa a que ficou. */
    public final void vRunRole(Ambiente _ambiente, Jogador _jogador) {
        this.ambiente = _ambiente;
        this.jogador = _jogador;
        if (!bCheckRoleOK()) return;

        if (!bInitialized) {
            vInitialize();
            bInitialized = true;
        }

        vRun();
        if (currentTactic != null) currentTactic.vRunTactic(_ambiente, _jogador);
        vCheckRoleFinished();
    }

    public boolean bCheckRoleOK() {
        return jogador != null && jogador.bEmCampo() && ambiente != null;
    }

    /**
     * Decide se o papel acabou.
     *
     * <p>Por padrao acaba quando as taticas acabaram. Papeis permanentes --
     * goleiro, por exemplo -- devem sobrescrever para nunca terminar.
     */
    public void vCheckRoleFinished() {
        if (bTacticsFinished()) bFinished = true;
    }

    public boolean bTacticsFinished() {
        if (robotTactics.isEmpty()) return false;
        return robotTactics.values().stream().allMatch(Tactic::bIsFinished);
    }

    // ------------------------------------------------- taticas

    public boolean bAddTactic(int _id, Tactic _tactic) {
        if (_tactic == null || robotTactics.containsKey(_id)) return false;
        robotTactics.put(_id, _tactic);
        if (currentTactic == null) currentTactic = _tactic;
        return true;
    }

    public boolean bSetTactic(int _id) {
        Tactic nova = robotTactics.get(_id);
        if (nova == null || nova == currentTactic) return nova != null;
        currentTactic = nova;
        return true;
    }

    public Tactic currentTactic() { return currentTactic; }

    // ------------------------------------------------- atribuicao

    public int id() { return iID; }
    public Prioridade prioridade() { return prioridade; }
    public void vSetPrioridade(Prioridade p) { this.prioridade = p; }

    /** Robo que esta executando este papel, ou {@code null}. */
    public Jogador player() { return jogador; }

    /**
     * Fixa o robo que executa o papel, sem roda-lo.
     *
     * <p>Separado de {@link #vRunRole} de proposito: a atribuicao acontece no
     * comeco do tique, para o time todo, e so depois os papeis executam. Se
     * atribuir ja executasse, um papel reatribuido rodaria duas vezes no mesmo
     * quadro e escreveria dois comandos no mesmo robo.
     */
    public void vSetJogador(Jogador _jogador) { this.jogador = _jogador; }

    public boolean bIsInitialized() { return bInitialized; }
    public boolean bIsFinished()    { return bFinished; }

    protected void vFinalizar() { bFinished = true; }

    public void vResetRole() {
        bInitialized = false;
        bFinished = false;
        jogador = null;
        for (Tactic t : robotTactics.values()) t.vResetTactic();
        currentTactic = robotTactics.isEmpty() ? null
                : robotTactics.values().iterator().next();
    }
}
