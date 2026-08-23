package estrategia;

import estrategia.esqueleto.Coach;
import core.Caixa;
import core.Vec2;
import jogo.EstadoDeJogo;
import model.Comando;
import model.Robo;
import mundo.EstadoRobo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O laco que liga a estrategia ao resto do programa.
 *
 * <p>Um tique inteiro: atualiza os robos com o que a visao entregou, roda o
 * {@link Coach}, aplica os limites do arbitro e devolve o mapa de comandos que o
 * {@code Cliente} manda pela rede.
 *
 * <p>E a unica peca do pacote que sabe que existe um mundo fora. Coach, Play,
 * Role, Tactic e Skill so conhecem {@link Ambiente} e {@link Jogador}, e e por
 * isso que da para rodar mil partidas de treino sem abrir janela nem tocar em
 * socket.
 *
 * <h2>Por que os limites do arbitro sao aplicados aqui</h2>
 *
 * <p>Poderiam ficar em cada play, e e assim que se costuma errar. Basta uma play
 * esquecer de checar {@code HALT} para o time andar com o jogo parado, e o custo
 * disso e falta. Aplicando no fim do tique, depois de todo mundo ter opinado,
 * nenhuma play consegue violar a regra nem por engano -- e quem escreve uma play
 * nova nao precisa lembrar.
 *
 * <p>Pelo mesmo motivo mora aqui a area de defesa: so o goleiro declarado entra
 * nela, e isso e regra, nao preferencia. Uma play de defesa que empurrasse um
 * zagueiro para dentro da area por descuido daria penalti.
 */
public final class Executor {

    private final Coach coach;
    private final Diario diario = new Diario();
    private final Map<Integer, Jogador> jogadores = new LinkedHashMap<>();

    public Executor(Coach _coach) { this.coach = _coach; }

    public Coach coach()   { return coach; }
    public Diario diario() { return diario; }

    /**
     * Roda um tique e devolve o comando de cada robo nosso.
     *
     * <p>O mapa sai pronto para {@code Cliente.enviar}.
     */
    public Map<Integer, Comando> vTick(Ambiente _ambiente) {
        List<Jogador> disponiveis = vAtualizarJogadores(_ambiente);

        coach.vRunCoach(_ambiente, disponiveis);
        diario.vObservar(coach, disponiveis, _ambiente.tempo());

        Map<Integer, Comando> saida = new LinkedHashMap<>();
        for (Jogador j : disponiveis) {
            Comando podado = cAplicarLimites(j.comando(), _ambiente.jogo());
            if (!_ambiente.podeEntrarNaArea(j.id())) {
                podado = cManterForaDaArea(podado, j.estado(), _ambiente.nossaAreaProibida());
            }
            // O que o arbitro corta e o que mais confunde quem depura: o robo
            // pediu e nao saiu, e sem esta linha some sem deixar rastro.
            if (j.comando().temChute() && !podado.temChute()) {
                diario.vAnotar(_ambiente.tempo(), Diario.Nivel.ALERTA,
                        "robo " + j.id() + ": chute cortado por " + _ambiente.jogo().descricao());
            }
            saida.put(j.id(), podado);
        }
        return saida;
    }

    /**
     * Sincroniza a lista de jogadores com o que a visao esta vendo.
     *
     * <p>Robo que sumiu continua no mapa, mas sem estado, e fica fora da lista de
     * disponiveis: as trilhas do rastreador ja esquecem quem sumiu por tempo
     * demais, e recriar o {@link Jogador} a cada reaparecimento perderia a
     * identidade que as roles usam para manter a atribuicao estavel.
     */
    private List<Jogador> vAtualizarJogadores(Ambiente _ambiente) {
        for (Jogador j : jogadores.values()) j.vAtualizar(null);

        List<Jogador> disponiveis = new ArrayList<>();
        for (EstadoRobo r : _ambiente.nossos()) {
            Jogador j = jogadores.computeIfAbsent(r.id(), Jogador::new);
            j.vAtualizar(r);
            disponiveis.add(j);
        }
        return disponiveis;
    }

    /**
     * Poda o comando conforme o arbitro permite neste instante.
     *
     * <p>Em {@code HALT} nao sai nada. Fora de jogo corrido, a velocidade e
     * saturada e chute e dribbler sao cortados: chutar com o jogo parado e falta,
     * e o dribbler ligado costuma ser encostar na bola, que tambem e.
     */
    static Comando cAplicarLimites(Comando c, EstadoDeJogo jogo) {
        if (!jogo.podemosMover()) return Comando.PARADO;
        if (jogo.bolaEmJogo()) return c;

        double teto = jogo.limiteDeVelocidade();
        double v = Math.hypot(c.velTangencial(), c.velNormal());
        double fator = v > teto && v > 0 ? teto / v : 1;

        return new Comando(c.velTangencial() * fator, c.velNormal() * fator,
                c.velAngular(), 0, 0, false);
    }

    /**
     * Corta a parte do comando que levaria o robo para dentro da area de defesa.
     *
     * <p>Zerar a componente de entrada quando o robo ja esta encostando nao
     * bastaria: ele desacelera a {@code ACEL_MAX}, entao a 3 m/s ainda entraria
     * 1,5 m depois do comando ir a zero. O que se limita e a velocidade de
     * APROXIMACAO, ao maior valor que ainda deixa o robo parar antes da linha --
     * {@code v = sqrt(2*a*d)}, o mesmo perfil de frenagem que {@code AndarPara}
     * usa para chegar num ponto. De longe nao corta nada; chegando perto, freia na
     * taxa certa.
     *
     * <p>A caixa e dilatada pelo raio do robo porque a regra mede pelo ponto do
     * robo que entra, e nao pelo centro dele.
     *
     * <p>Com o robo JA dentro -- empurrado por outro, ou porque o goleiro mudou --
     * a distancia e zero e sobra so a componente de saida: ele nao consegue
     * afundar mais, e sai pela face mais proxima.
     *
     * <p>So a componente normal e tocada. A tangencial passa inteira, entao o robo
     * continua deslizando ao longo da borda em vez de travar: uma defesa que para
     * de andar ao encostar na area e pior que uma que a contorna.
     */
    static Comando cManterForaDaArea(Comando c, EstadoRobo r, Caixa area) {
        if (r == null) return c;

        Caixa proibida = area.dilatada(Robo.RAIO);
        Vec2 posicao = r.posicao();

        // Normal apontando para FORA da area, e distancia ate ela.
        boolean dentro = proibida.contem(posicao);
        Vec2 normal = dentro
                ? proibida.saidaMaisCurta(posicao)
                : posicao.menos(proibida.pontoMaisProximo(posicao)).normalizado();
        if (normal.norma() < 1e-9) return c;

        double distancia = dentro ? 0 : proibida.distancia(posicao);
        double tetoDeEntrada = Math.sqrt(2 * Robo.ACEL_MAX * distancia);

        Vec2 global = new Vec2(c.velTangencial(), c.velNormal()).paraGlobal(r.theta());
        double entrada = -global.escalar(normal); // positivo quando vai para dentro
        if (entrada <= tetoDeEntrada) return c;

        Vec2 corrigida = global.mais(normal.escala(entrada - tetoDeEntrada));
        Vec2 local = corrigida.paraLocal(r.theta());
        return new Comando(local.x(), local.y(), c.velAngular(),
                c.velChute(), c.anguloChute(), c.dribbler());
    }

    /** Jogadores conhecidos, mesmo os que sumiram da visao. */
    public List<Jogador> jogadores() { return List.copyOf(jogadores.values()); }
}
