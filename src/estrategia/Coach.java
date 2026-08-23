package estrategia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Quem escolhe a {@link Play} que o time executa.
 *
 * <p>Uma play de cada vez. O coach a mantem rodando ate ela sair de
 * {@link Play.Status#RODANDO} -- por conclusao, por condicao de fim ou por tempo
 * -- e nesse instante fecha o episodio: manda a play atualizar e salvar a nota, e
 * escolhe a proxima. E esse ciclo que da ao aprendizado um comeco e um fim para
 * creditar.
 *
 * <h2>Como a escolha acontece</h2>
 *
 * <p>Em {@link Modo#AUTOMATICO}, entre as plays cujas pre-condicoes valem agora,
 * a escolha e por ROLETA: a chance de cada uma e proporcional a sua nota. Nao e
 * pegar a melhor, e de proposito -- sempre pegar a melhor nunca experimenta as
 * outras, e uma play com nota baixa por azar nas primeiras tentativas jamais
 * teria a chance de se provar. A roleta e a forma mais simples de manter alguma
 * exploracao viva sem parametro de temperatura para ajustar.
 *
 * <p>Em {@link Modo#MANUAL}, quem escolhe e o {@link #vRun()} de quem implementa,
 * chamando {@link #bSetPlay(int)}. Serve para teste e para jogadas ensaiadas, em
 * que a decisao e do humano.
 */
public abstract class Coach {

    public enum Modo {
        /** As plays sao escolhidas em {@link Coach#vRun()}, na mao. */
        MANUAL,
        /** O coach escolhe pelas pre-condicoes e pela nota. */
        AUTOMATICO
    }

    private final Map<Integer, Play> assignedPlays = new LinkedHashMap<>();
    private final Random sorteio = new Random();

    protected Ambiente ambiente;

    /**
     * Quantos robos nossos a visao esta vendo no tique corrente.
     *
     * <p>Guardado em vez de recebido por parametro porque quem pergunta por
     * {@link #playsDisponiveis()} nem sempre esta no tique: a interface lista o
     * repertorio para exibir, e teria de inventar um numero para conseguir
     * perguntar. Comeca em zero, entao antes do primeiro tique nenhuma play com
     * papel essencial aparece como disponivel -- que e a verdade.
     */
    private int iRobosEmCampo;
    protected Play currentPlay;

    /**
     * Play escolhida antes de existir ambiente, esperando o primeiro tique.
     *
     * <p>Montar o coach e ja dizer qual play comeca e o uso mais natural, e nesse
     * instante ainda nao chegou quadro nenhum da visao. Guardar a escolha e
     * inicia-la no primeiro tique evita obrigar quem monta a saber que
     * {@code bSetPlay} so pode ser chamado de dentro de {@link #vRun()}.
     */
    private Play playPendente;
    protected int iID;
    protected boolean bInitialized;

    private Modo modo = Modo.AUTOMATICO;

    protected Coach(int _id, Modo _modo) {
        this.iID = _id;
        this.modo = _modo;
    }

    // ------------------------------------------------- a implementar

    public abstract String strName();

    public abstract void vInitialize();

    /**
     * A logica propria do coach em um tique.
     *
     * <p>Em {@link Modo#MANUAL} e aqui que a play e escolhida. Em
     * {@link Modo#AUTOMATICO} costuma ficar vazio, e existe para casos em que se
     * quer intervir -- forcar uma play em situacao de emergencia, por exemplo.
     */
    protected void vRun() { }

    // ------------------------------------------------- ciclo de vida

    /**
     * Roda um tique do coach.
     *
     * <p>Fecha o episodio anterior antes de abrir o proximo: a nota tem de ser
     * atualizada com o mundo como ele ficou no fim da jogada, e nao depois que a
     * jogada seguinte ja mexeu nele.
     */
    public final void vRunCoach(Ambiente _ambiente, List<Jogador> _disponiveis) {
        this.ambiente = _ambiente;
        this.iRobosEmCampo = _disponiveis.size();

        if (!bInitialized) {
            vInitialize();
            bInitialized = true;
        }

        if (playPendente != null) {
            vIniciarPlay(playPendente);
            playPendente = null;
        }

        if (currentPlay != null && !currentPlay.bRodando()) vFecharEpisodio();

        vRun();

        if (currentPlay == null && modo == Modo.AUTOMATICO) vEscolherPlay();
        if (currentPlay != null) currentPlay.vRunPlay(_ambiente, _disponiveis);
    }

    /**
     * Encerra a play corrente e recolhe o aprendizado.
     *
     * <p>A ordem importa: atualizar antes de salvar, e so entao soltar a play. Se
     * a referencia fosse solta antes, a nota do episodio se perderia.
     */
    private void vFecharEpisodio() {
        currentPlay.vUpdateScore();
        currentPlay.vSaveScore();
        currentPlay.vReset();
        currentPlay = null;
    }

    /** Sorteia entre as plays disponiveis, com peso proporcional a nota. */
    private void vEscolherPlay() {
        List<Play> candidatas = playsDisponiveis();
        if (candidatas.isEmpty()) return;

        double total = 0;
        for (Play p : candidatas) total += Math.max(0, p.dGetScore());

        Play escolhida;
        if (total <= 0) {
            // Todas zeradas: sem informacao nenhuma, sorteio uniforme. E o caso
            // do primeiro treino, quando ainda nao ha o que preferir.
            escolhida = candidatas.get(sorteio.nextInt(candidatas.size()));
        } else {
            double alvo = sorteio.nextDouble() * total;
            escolhida = candidatas.get(candidatas.size() - 1);
            double acumulado = 0;
            for (Play p : candidatas) {
                acumulado += Math.max(0, p.dGetScore());
                if (acumulado >= alvo) { escolhida = p; break; }
            }
        }
        vIniciarPlay(escolhida);
    }

    /**
     * Plays que podem comecar neste instante.
     *
     * <p>Duas perguntas, e as duas precisam de contexto. Cada play recebe o mundo
     * ANTES de ser perguntada pelas pre-condicoes: "voce pode comecar agora?" so
     * tem resposta com o mundo em maos, e a play ainda nao comecou -- logo
     * ninguem mais teria entregado isso a ela.
     *
     * <p>A segunda e quantos robos ha em campo. Uma play cujos papeis de
     * prioridade {@code MAXIMA} nao cabem nos robos disponiveis fica de fora do
     * sorteio, em vez de ser escolhida e abortar no primeiro tique: um episodio
     * que morre antes de comecar gastaria uma rodada de aprendizado com um
     * resultado que nao diz nada sobre a jogada. As outras categorias nao entram
     * na conta, porque a play roda sem elas -- e essa a razao de existirem.
     */
    public List<Play> playsDisponiveis() {
        List<Play> saida = new ArrayList<>();
        for (Play p : assignedPlays.values()) {
            p.vSetAmbiente(ambiente);
            if (p.iRobosMinimos() > iRobosEmCampo) continue;
            if (p.bCheckPreConditions()) saida.add(p);
        }
        return saida;
    }

    /** Forca uma play, ignorando nota e sorteio. Usado no modo manual. */
    public boolean bSetPlay(int _id) {
        Play nova = assignedPlays.get(_id);
        if (nova == null || nova == currentPlay) return nova != null;

        if (ambiente == null) { // ainda nao houve tique; comeca no proximo
            playPendente = nova;
            return true;
        }
        if (currentPlay != null) vFecharEpisodio();
        vIniciarPlay(nova);
        return true;
    }

    private void vIniciarPlay(Play _play) {
        currentPlay = _play;
        _play.vStart(ambiente);
    }

    // ------------------------------------------------- plays

    public boolean bAddPlay(Play _play) {
        if (_play == null || assignedPlays.containsKey(_play.id())) return false;
        assignedPlays.put(_play.id(), _play);
        return true;
    }

    public List<Play> plays()   { return List.copyOf(assignedPlays.values()); }
    public Play currentPlay()   { return currentPlay; }
    public Modo modo()          { return modo; }
    public void vSetModo(Modo m) { this.modo = m; }
    public int id()             { return iID; }

    /**
     * Semente fixa, para uma corrida de treino sair reproduzivel.
     *
     * <p>A semente e embaralhada antes de ir para o gerador, e isso NAO e
     * preciosismo. O uso obvio desta API e {@code vSetSemente(numeroDaCorrida)},
     * com 0, 1, 2, 3..., e {@code java.util.Random} tem um defeito conhecido
     * justamente ai: com sementes pequenas e sequenciais, o PRIMEIRO
     * {@code nextDouble()} de cada uma cai sempre na mesma faixa estreita.
     * Medido, para as sementes de 0 a 399, todos os primeiros valores ficaram
     * entre 0,721 e 0,766.
     *
     * <p>O efeito pratico seria uma roleta viciada: como o coach sorteia uma vez
     * no comeco do episodio, mil corridas de treino escolheriam sempre a mesma
     * play, e o treino inteiro sairia enviesado sem nenhum sintoma visivel.
     *
     * <p>O embaralhamento e o finalizador do SplitMix64, que espalha bits de
     * entrada proximos por todo o intervalo.
     */
    public void vSetSemente(long semente) {
        long z = semente + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        sorteio.setSeed(z ^ (z >>> 31));
    }
}
