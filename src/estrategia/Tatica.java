package estrategia;

/**
 * O identificador estavel de cada tatica.
 *
 * <p>Antes cada {@link Role} numerava as taticas por conta propria, com um
 * {@code private static final int TATICA_BUSCAR = 1} repetido em cada arquivo.
 * Funcionava enquanto havia um papel so, e quebrava do jeito mais chato assim que
 * aparecia o segundo: o mesmo {@code BuscarBola} era 1 num papel e 2 em outro, e
 * nada impedia dois papeis de discordarem. O numero acabava sendo propriedade de
 * QUEM REGISTROU, e nao da tatica.
 *
 * <p>Aqui ele vira propriedade da tatica. {@code BuscarBola} e
 * {@link #BUSCAR_BOLA} em qualquer papel, para sempre, e um papel novo nao precisa
 * inventar numeracao nenhuma -- so escolher da lista.
 *
 * <p><b>Nunca renumere.</b> O id vai para o log e para a nota persistida das
 * plays; trocar um numero faz o historico passar a falar de outra coisa sem que
 * nada acuse. Tatica que sai de uso deixa o numero dela queimado.
 */
public enum Tatica {

    BUSCAR_BOLA(1),
    CONDUZIR_AO_GOL(2),
    CHUTAR_AO_GOL(3),

    /** Ainda um esqueleto vazio; o numero fica reservado. */
    CHUTAR_PARA(4);

    private final int id;

    Tatica(int id) { this.id = id; }

    /** O numero que vai nos mapas do framework. */
    public int id() { return id; }
}
