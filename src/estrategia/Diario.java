package estrategia;

import model.Comando;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O que a estrategia esta fazendo, em texto e por robo.
 *
 * <p>Nasceu de uma depuracao: o robo parava a um palmo da bola e nada na tela
 * dizia por que. A resposta estava em "a skill de andar se declarou concluida
 * enquanto o sensor ainda nao via a bola" -- uma frase que so aparece quando se
 * consegue ver, ao mesmo tempo, o papel, a tatica, a skill e o comando que saiu.
 *
 * <h2>Observa em vez de ser chamado</h2>
 *
 * <p>Ninguem escreve no diario. Ele olha a arvore do coach a cada tique e anota
 * o que MUDOU. Poderia ser o contrario, com cada skill chamando um logger, e seria
 * pior: obrigaria a passar o diario por todas as camadas, sujaria a assinatura de
 * quem so quer decidir um comando, e quem escrevesse uma skill nova teria de
 * lembrar de registrar. Diferenca nao esquece.
 *
 * <p>O preco e nao ver o que nao aparece no estado: o motivo de uma escolha fica
 * de fora, so o resultado dela entra. Para o que se depura aqui, basta.
 */
public final class Diario {

    /** Quantas linhas ficam guardadas. Passou disso, a mais antiga cai. */
    private static final int CAPACIDADE = 400;

    public enum Nivel {
        /** Andamento normal. */
        INFO,
        /** Algo aconteceu no mundo: chute, posse ganha ou perdida. */
        ACAO,
        /** Algo que costuma explicar um robo travado. */
        ALERTA
    }

    public record Linha(double tempo, Nivel nivel, String texto) {}

    /**
     * O que cada robo esta executando agora.
     *
     * <p>Presenca e conclusao sao coisas diferentes, e a distincao importa na
     * hora de pintar. Uma skill CONCLUIDA cumpriu o que se propunha -- mirar e
     * chegar -- e continua rodando; pinta-la de vermelho diria que algo falhou
     * quando na verdade deu certo. Vermelho fica para o que esta FALTANDO: sem
     * papel, sem tatica, sem skill.
     *
     * <p>{@code comandado} e a resposta a pergunta que se faz de verdade olhando
     * um robo parado: esta saindo alguma coisa para ele? Um robo com a cadeia
     * inteira montada e comando zerado esta travado, e e o unico jeito de ver isso.
     */
    public record Situacao(
            int id,
            String role, String tactic, String skill,
            boolean temRole, boolean temTactic, boolean temSkill,
            boolean taticaConcluida, boolean skillConcluida,
            boolean comandado, boolean comBola, double velocidade) {

        /** True quando a cadeia esta montada e algo esta saindo para o robo. */
        public boolean executando() { return temRole && temTactic && temSkill && comandado; }
    }

    private final Deque<Linha> linhas = new ArrayDeque<>();
    private final Map<Integer, Situacao> situacoes = new LinkedHashMap<>();

    // Ultimo estado visto, para saber o que mudou.
    private String ultimaPlay = "";
    private final Map<Integer, String> ultimoDeRobo = new HashMap<>();
    private final Map<Integer, Boolean> ultimaPosse = new HashMap<>();

    public List<Linha> linhas()        { return List.copyOf(linhas); }
    public List<Situacao> situacoes()  { return List.copyOf(situacoes.values()); }

    public void vLimpar() {
        linhas.clear();
        situacoes.clear();
        ultimoDeRobo.clear();
        ultimaPosse.clear();
        ultimaPlay = "";
    }

    /** Anota uma linha a mao. Usado pelo executor para o que nao esta na arvore. */
    public void vAnotar(double tempo, Nivel nivel, String texto) {
        linhas.addLast(new Linha(tempo, nivel, texto));
        while (linhas.size() > CAPACIDADE) linhas.removeFirst();
    }

    /** Olha a arvore e anota as mudancas. Chamado uma vez por tique. */
    public void vObservar(Coach coach, List<Jogador> jogadores, double tempo) {
        Play play = coach.currentPlay();
        String agoraPlay = play == null ? "nenhuma play"
                : play.strName() + " (" + play.status() + ")";
        if (!agoraPlay.equals(ultimaPlay)) {
            Nivel n = play == null ? Nivel.ALERTA : Nivel.INFO;
            vAnotar(tempo, n, "play: " + agoraPlay);
            ultimaPlay = agoraPlay;
        }

        situacoes.clear();
        Map<Integer, Role> papelDe = new HashMap<>();
        if (play != null) {
            for (Role r : play.roles()) {
                if (r.player() != null) papelDe.put(r.player().id(), r);
            }
        }

        for (Jogador j : jogadores) {
            Role role = papelDe.get(j.id());
            Tactic tactic = role == null ? null : role.currentTactic();
            Skill skill = tactic == null ? null : tactic.currentSkill();

            Comando cmd = j.comando();
            boolean comandado = !cmd.equals(Comando.PARADO);

            Situacao s = new Situacao(j.id(),
                    role == null ? "sem papel" : role.strName(),
                    tactic == null ? "-" : tactic.strName(),
                    skill == null ? "-" : skill.strName(),
                    role != null, tactic != null, skill != null,
                    tactic != null && tactic.bIsFinished(),
                    skill != null && skill.bIsFinished(),
                    comandado, j.bComABola(),
                    j.estado() == null ? 0 : j.estado().rapidez());
            situacoes.put(j.id(), s);

            // O log so anota a cadeia, sem o comando: velocidade muda todo quadro
            // e encheria o diario de linhas que nao dizem nada de novo.
            String resumo = s.role() + " / " + s.tactic() + " / " + s.skill();
            if (!resumo.equals(ultimoDeRobo.get(j.id()))) {
                boolean completa = s.temRole() && s.temTactic() && s.temSkill();
                vAnotar(tempo, completa ? Nivel.INFO : Nivel.ALERTA,
                        "robo " + j.id() + ": " + resumo);
                ultimoDeRobo.put(j.id(), resumo);
            }

            boolean antes = ultimaPosse.getOrDefault(j.id(), false);
            if (j.bComABola() != antes) {
                vAnotar(tempo, Nivel.ACAO, "robo " + j.id()
                        + (j.bComABola() ? ": bola no sensor" : ": perdeu a bola"));
                ultimaPosse.put(j.id(), j.bComABola());
            }

            if (cmd.temChute()) {
                vAnotar(tempo, Nivel.ACAO, String.format("robo %d: %s a %.1f m/s",
                        j.id(), cmd.ehChip() ? "chip" : "chute", cmd.velChute() / 1000));
            }
        }

        // Robo que sumiu da visao sai da lista, mas o log tem de dizer.
        List<Integer> presentes = new ArrayList<>();
        for (Jogador j : jogadores) presentes.add(j.id());
        ultimoDeRobo.keySet().removeIf(id -> {
            if (presentes.contains(id)) return false;
            vAnotar(tempo, Nivel.ALERTA, "robo " + id + ": sumiu da visao");
            return true;
        });
    }
}
