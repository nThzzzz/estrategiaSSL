package estrategia;

/**
 * O identificador estavel de cada papel.
 *
 * <p>Ver {@link Tatica} para o porque de os ids serem globais. Aqui vale a mesma
 * coisa com uma consequencia a mais: {@link Play#bAddRole} usa o id do papel como
 * chave, entao dois papeis com o mesmo numero na mesma play fariam o segundo ser
 * silenciosamente recusado. Com a lista num lugar so, isso deixa de ser possivel
 * por descuido.
 *
 * <p>Nao confunda com {@link Play.Prioridade}: o id diz QUAL papel e, a prioridade
 * diz o quanto ele importa naquela jogada. O mesmo papel entra com prioridades
 * diferentes em plays diferentes, e e por isso que uma coisa mora aqui e a outra
 * na play.
 *
 * <p><b>Nunca renumere.</b> Ver {@link Tatica}.
 */
public enum Papel {

    ATACANTE(1),
    GOLEIRO(2);

    private final int id;

    Papel(int id) { this.id = id; }

    /** O numero que vai nos mapas do framework. */
    public int id() { return id; }
}
