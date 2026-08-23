package estrategia.esqueleto;

import estrategia.Ambiente;
import estrategia.Jogador;
import estrategia.ids.Papel;
import estrategia.ids.Tatica;

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
 * <p>A Role NAO escolhe qual robo a executa, e tambem NAO diz o quanto ela
 * importa. Ela declara o que o papel faz e o {@link #dCusto} de cada candidato; a
 * {@link Play} e que atribui os robos, e e la que mora a
 * {@link Play.Prioridade} deste papel NAQUELA jogada. O mesmo papel vale coisas
 * diferentes em jogadas diferentes -- um marcador e essencial numa defesa e
 * dispensavel num ataque com o campo livre -- e uma prioridade gravada na Role
 * obrigaria a escrever duas classes iguais so para dizer isso.
 *
 * <p>Nao escolher o robo e o que faz a mesma play continuar valendo quando um
 * robo leva cartao ou quebra: uma play que fixasse "o 3 e o goleiro" pararia de
 * funcionar no instante em que o 3 saisse de campo.
 *
 * <p>Papel sem robo e situacao normal, e nao erro. Ele fica com
 * {@link #player()} nulo, nao roda e nao conta para o fim da play.
 */
public abstract class Role {

    private final Map<Integer, Tactic> robotTactics = new LinkedHashMap<>();

    protected Ambiente ambiente;
    protected Jogador jogador;

    protected int iID;
    protected boolean bInitialized;
    protected boolean bFinished;

    protected Tactic currentTactic;

    protected Role(int _id) { this.iID = _id; }

    /** O caminho normal: o id vem da lista global de {@link Papel}. */
    protected Role(Papel _id) { this(_id.id()); }

    // ------------------------------------------------- a implementar

    public abstract String strName();

    public abstract void vInitialize();

    /**
     * A logica do papel em um tique.
     *
     * <p>E aqui que se escolhe a tatica que vale agora, com
     * {@link #bSetTactic(Tatica)}. O executor roda a tatica corrente em seguida.
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

    /** Registra a tatica sob o id global dela. */
    public boolean bAddTactic(Tatica _id, Tactic _tactic) {
        return bAddTactic(_id.id(), _tactic);
    }

    /** Troca a tatica corrente pela do id global indicado. */
    public boolean bSetTactic(Tatica _id) { return bSetTactic(_id.id()); }

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
