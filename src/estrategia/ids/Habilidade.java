package estrategia.ids;

/**
 * O identificador estavel de cada skill.
 *
 * <p>Mesma historia de {@link Tatica}: a numeracao era local de cada
 * {@link Tactic}, entao {@code AndarPara} era 1 em {@code BuscarBola} e 1 em
 * {@code ConduzirAoGol} por coincidencia, e teria sido 2 se alguem tivesse
 * escrito na outra ordem. O numero passa a ser da skill.
 *
 * <p><b>Nunca renumere.</b> Ver {@link Tatica}.
 */
public enum Habilidade {

    ANDAR_PARA(1),
    OLHAR_PARA(2),
    CHUTAR(3);

    private final int id;

    Habilidade(int id) { this.id = id; }

    /** O numero que vai nos mapas do framework. */
    public int id() { return id; }
}
