package estrategia;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Um conjunto de {@link Skill} que UM robo executa em sequencia.
 *
 * <p>Exemplos: ir ate um ponto E chutar para o gol; ir ate um ponto E esperar um
 * passe; ir ate uma regiao E marcar um adversario. A tatica e quem decide QUANDO
 * trocar de skill; a skill nao sabe que existe uma proxima.
 *
 * <p>Uma tatica de cada vez esta ativa ({@link #currentSkill}), e a troca passa
 * por {@link #bSetSkill(int)}. Rodar duas skills no mesmo tique escreveria duas
 * vezes no mesmo {@link Jogador}, e a segunda venceria sem que ninguem tivesse
 * decidido isso.
 */
public abstract class Tactic {

    private final Map<Integer, Skill> assignedSkills = new LinkedHashMap<>();

    protected Ambiente ambiente;
    protected Jogador jogador;

    protected int iID;
    protected boolean bInitialized;
    protected boolean bFinished;

    protected Skill currentSkill;

    protected Tactic(int _id) { this.iID = _id; }

    // ------------------------------------------------- a implementar

    public abstract String strName();

    public abstract void vInitialize();

    /**
     * A logica da tatica em um tique.
     *
     * <p>E aqui que se decide qual skill vale agora, tipicamente olhando
     * {@code currentSkill.bIsFinished()} e chamando {@link #bSetSkill(int)}. O
     * executor ja roda a skill corrente depois; nao chame {@code vRunSkill}
     * daqui.
     */
    protected abstract void vRun();

    // ------------------------------------------------- ciclo de vida

    /**
     * Roda um tique da tatica: decide a skill e executa a que ficou.
     *
     * <p>A ordem importa. Decidir antes de executar faz o robo obedecer no mesmo
     * quadro em que a tatica mudou de ideia; ao contrario, ele ainda gastaria um
     * quadro com a skill anterior.
     */
    public final void vRunTactic(Ambiente _ambiente, Jogador _jogador) {
        this.ambiente = _ambiente;
        this.jogador = _jogador;
        if (!bCheckTacticOK()) return;

        if (!bInitialized) {
            vInitialize();
            bInitialized = true;
        }

        vRun();
        if (currentSkill != null) currentSkill.vRunSkill(_ambiente, _jogador);
        vCheckTacticFinished();
    }

    public boolean bCheckTacticOK() {
        return jogador != null && jogador.bEmCampo() && ambiente != null;
    }

    /**
     * Decide se a tatica acabou.
     *
     * <p>Por padrao acaba quando todas as skills acabaram. Sobrescreva para
     * mudar o criterio -- uma tatica de marcacao, por exemplo, nao acaba nunca
     * sozinha.
     */
    public void vCheckTacticFinished() {
        if (bSkillsFinished()) bFinished = true;
    }

    public boolean bSkillsFinished() {
        if (assignedSkills.isEmpty()) return false;
        return assignedSkills.values().stream().allMatch(Skill::bIsFinished);
    }

    // ------------------------------------------------- skills

    /** Registra uma skill. Ids repetidos sao recusados. */
    public boolean bAddSkill(int _id, Skill _skill) {
        if (_skill == null || assignedSkills.containsKey(_id)) return false;
        assignedSkills.put(_id, _skill);
        if (currentSkill == null) currentSkill = _skill;
        return true;
    }

    /** Troca a skill ativa. Nao faz nada se ja for a corrente. */
    public boolean bSetSkill(int _id) {
        Skill nova = assignedSkills.get(_id);
        if (nova == null || nova == currentSkill) return nova != null;
        currentSkill = nova;
        return true;
    }

    public Skill currentSkill() { return currentSkill; }

    public int id() { return iID; }
    public boolean bIsInitialized() { return bInitialized; }
    public boolean bIsFinished()    { return bFinished; }

    protected void vFinalizar() { bFinished = true; }

    public void vResetTactic() {
        bInitialized = false;
        bFinished = false;
        for (Skill s : assignedSkills.values()) s.vResetSkill();
        currentSkill = assignedSkills.isEmpty() ? null
                : assignedSkills.values().iterator().next();
    }
}
