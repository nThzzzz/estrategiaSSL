package estrategia.esqueleto;

import estrategia.Ambiente;
import estrategia.Jogador;

/**
 * Uma micro habilidade de UM robo: a menor unidade de comportamento.
 *
 * <p>Exemplos: andar ate um ponto, olhar para um ponto, contornar um obstaculo,
 * chutar para o gol. Se descrever a coisa exige um "e depois", ela nao e uma
 * Skill: e uma {@link Tactic}.
 *
 * <p>Quem implementa escreve {@link #vRun()}, e so. O ciclo de vida em volta --
 * inicializar na primeira vez, nao rodar sem robo atribuido, marcar como
 * terminada -- fica em {@link #vRunSkill()}, que e final justamente para que
 * nenhuma implementacao possa esquecer um pedaco dele.
 *
 * <p>{@code vRun()} roda uma vez por tique e NAO bloqueia. Uma skill de "andar
 * ate o ponto" nao anda: ela calcula a velocidade deste tique e escreve no
 * {@link Jogador}. Bloquear ate chegar congelaria o time inteiro e impediria
 * reagir a uma bola que mudou de rumo no meio do caminho.
 */
public abstract class Skill {

    protected Ambiente ambiente;
    protected Jogador jogador;

    protected boolean bInitialized;
    protected boolean bFinished;

    // ------------------------------------------------- a implementar

    /** Nome da skill, para log e para a interface. */
    public abstract String strName();

    /** Preparacao feita uma vez, quando a skill entra em uso. */
    public abstract void vInitialize();

    /**
     * O que a skill faz em um tique.
     *
     * <p>Le {@link #ambiente} e escreve em {@link #jogador}. Quando o objetivo
     * estiver cumprido, chame {@link #vFinalizar()} -- e assim que a
     * {@link Tactic} sabe que pode passar para a proxima.
     */
    protected abstract void vRun();

    // ------------------------------------------------- ciclo de vida

    /**
     * Roda um tique da skill.
     *
     * <p>Final de proposito: e o contrato de execucao, nao um comportamento a
     * ser personalizado. Quem quer mudar o que acontece, muda {@link #vRun()}.
     */
    public final void vRunSkill(Ambiente _ambiente, Jogador _jogador) {
        this.ambiente = _ambiente;
        this.jogador = _jogador;
        if (!bCheckSkillOK()) return;

        if (!bInitialized) {
            vInitialize();
            bInitialized = true;
        }
        vRun();
    }

    /** Uma skill sem robo em campo nao tem o que fazer. */
    public boolean bCheckSkillOK() {
        return jogador != null && jogador.bEmCampo() && ambiente != null;
    }

    public boolean bIsInitialized() { return bInitialized; }
    public boolean bIsFinished()    { return bFinished; }

    /** Declara a skill cumprida. */
    protected void vFinalizar() { bFinished = true; }

    /**
     * Devolve a skill ao estado inicial.
     *
     * <p>Chamado quando a skill volta a ser usada depois de ter saido de cena.
     * Sem isso, uma skill que terminou uma vez ja nasceria terminada na proxima.
     */
    public void vResetSkill() {
        bInitialized = false;
        bFinished = false;
    }
}
