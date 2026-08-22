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
 * <p>A Role NAO escolhe qual robo a executa. Ela declara o papel e a prioridade,
 * e a Play atribui os robos em {@link Play#vOptimizeRoleAssignment()}. E o que
 * faz a mesma play continuar valendo quando um robo leva cartao ou quebra: sem
 * isso, uma play que fixasse "o 3 e o goleiro" pararia de funcionar no instante
 * em que o 3 saisse de campo.
 */
public abstract class Role {

    /**
     * Quem entra primeiro quando ha mais roles do que robos.
     *
     * <p>Faltar goleiro e diferente de faltar um segundo atacante, e a
     * prioridade e o que deixa isso dito no codigo em vez de implicito na ordem
     * em que as roles foram adicionadas.
     */
    public enum Prioridade {
        /** Tem de ser atribuida. Sem ela a play nao roda. */
        MAXIMA(3),
        /** A falta gera aviso, mas a play segue. */
        MEDIA(2),
        BAIXA(1),
        /** Se sobrar robo, otimo; se nao, a jogada nao sofre. */
        OPCIONAL(0);

        private final int peso;
        Prioridade(int peso) { this.peso = peso; }
        public int peso() { return peso; }
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
