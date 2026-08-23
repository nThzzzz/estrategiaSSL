package estrategia;

/**
 * O identificador estavel de cada play.
 *
 * <p>Ver {@link Tatica} para o porque de os ids serem globais. Neste caso o
 * numero e o mais duradouro de todos: e por ele que a nota aprendida de uma
 * jogada e salva e recuperada entre execucoes, entao renumerar nao so confunde o
 * log como faz uma play herdar o aprendizado de outra.
 */
public enum Jogada {

    TESTE_ATACANTE(1);

    private final int id;

    Jogada(int id) { this.id = id; }

    /** O numero que vai nos mapas do framework. */
    public int id() { return id; }
}
