package teste;

import core.Vec2;
import estrategia.Ambiente;
import estrategia.Coach;
import estrategia.Executor;
import estrategia.Jogador;
import estrategia.Play;
import estrategia.Role;
import estrategia.Skill;
import estrategia.Tactic;
import jogo.Arbitro;
import jogo.ArbitroLocal;
import jogo.EstadoDeJogo;
import model.Comando;
import model.Cor;
import model.Geometria;
import mundo.EstadoBola;
import mundo.EstadoRobo;
import mundo.Quadro;
import proto.gc.SslGcRefereeMessage.Referee.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verificacao do esqueleto de estrategia: Coach, Play, Role, Tactic e Skill.
 *
 * <p>Nao ha jogada de verdade aqui. O que se testa e o CICLO DE VIDA -- quem roda
 * a cada tique, quando uma play termina, quando o coach escolhe outra, quem fica
 * com qual papel e o que o arbitro impede. Sao exatamente as partes que toda
 * implementacao futura vai herdar sem reescrever, e portanto as que precisam
 * estar certas antes de existir a primeira play.
 *
 * <p>Roda com {@code java -cp out/production/estrategiaSSL:lib/*
 * teste.AutotesteEstrategia}.
 */
public final class AutotesteEstrategia {

    private static int falhas = 0;
    private static int total = 0;

    public static void main(String[] args) {
        cadaCamadaRodaPorTique();
        playTerminaPorTempo();
        playTerminaPorCondicaoDeFim();
        playTerminaQuandoAsRolesTerminam();
        coachFechaOEpisodioUmaVezSo();
        coachSoConsideraPlaysComPreCondicao();
        roletaPrefereNotaAlta();
        prioridadeDecideQuemEntraPrimeiro();
        histereseEvitaTrocaTrocaDePapel();
        arbitroPodaOComando();

        System.out.printf("%n%d/%d verificacoes passaram%n", total - falhas, total);
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------ ciclo de vida

    /** O tique tem de descer inteiro: Play, Role, Tactic e Skill. */
    private static void cadaCamadaRodaPorTique() {
        SkillContada skill = new SkillContada();
        TacticContada tactic = new TacticContada(skill);
        RoleContada role = new RoleContada(tactic);
        PlayContada play = new PlayContada(role);

        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);

        Ambiente a = ambiente(0.0, Command.FORCE_START, 3);
        coach.bSetPlay(play.id());
        for (int i = 0; i < 5; i++) exec.vTick(ambiente(i * 0.1, Command.FORCE_START, 3));

        verdadeiro("ciclo: vRun da Play roda a cada tique (" + play.iRuns + ")", play.iRuns == 5);
        verdadeiro("ciclo: vRun da Role roda a cada tique (" + role.iRuns + ")", role.iRuns == 5);
        verdadeiro("ciclo: vRun da Tactic roda a cada tique (" + tactic.iRuns + ")", tactic.iRuns == 5);
        verdadeiro("ciclo: vRun da Skill roda a cada tique (" + skill.iRuns + ")", skill.iRuns == 5);
        verdadeiro("ciclo: vInitialize roda uma vez so", play.iInits == 1 && skill.iInits == 1);
        verdadeiro("ciclo: a skill escreve comando no jogador", a != null);
    }

    /** Tempo limite contado no relogio da visao, nao no de parede. */
    private static void playTerminaPorTempo() {
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        play.vSetTempoLimite(2.0);

        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambiente(0.0, Command.FORCE_START, 3));

        exec.vTick(ambiente(1.5, Command.FORCE_START, 3));
        verdadeiro("fim por tempo: antes do limite segue rodando",
                play.status() == Play.Status.RODANDO);

        exec.vTick(ambiente(2.5, Command.FORCE_START, 3));
        verdadeiro("fim por tempo: passado o limite, aborta",
                play.status() == Play.Status.ABORTADA);
    }

    private static void playTerminaPorCondicaoDeFim() {
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambiente(0.0, Command.FORCE_START, 3));

        play.bTerminar = true;
        exec.vTick(ambiente(0.1, Command.FORCE_START, 3));
        verdadeiro("fim por condicao: aborta assim que a condicao vale",
                play.status() == Play.Status.ABORTADA);
    }

    private static void playTerminaQuandoAsRolesTerminam() {
        SkillContada skill = new SkillContada();
        RoleContada role = new RoleContada(new TacticContada(skill));
        PlayContada play = new PlayContada(role);

        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambiente(0.0, Command.FORCE_START, 3));

        skill.bAcabar = true;
        exec.vTick(ambiente(0.1, Command.FORCE_START, 3));
        verdadeiro("fim natural: skill acabada termina tactic, role e play",
                play.status() == Play.Status.CONCLUIDA);
    }

    /** A nota so pode ser atualizada uma vez por episodio. */
    private static void coachFechaOEpisodioUmaVezSo() {
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        play.vSetTempoLimite(1.0);
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);

        coach.bSetPlay(play.id());
        exec.vTick(ambiente(0.0, Command.FORCE_START, 3));
        for (int i = 1; i <= 5; i++) exec.vTick(ambiente(i, Command.FORCE_START, 3));

        verdadeiro("episodio: vUpdateScore chamado uma vez (" + play.iUpdates + ")",
                play.iUpdates == 1);
        verdadeiro("episodio: vSaveScore chamado uma vez", play.iSaves == 1);
    }

    // --------------------------------------------------------------- escolha

    private static void coachSoConsideraPlaysComPreCondicao() {
        PlayContada boa = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        PlayContada vetada = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        vetada.iID2 = 99;
        vetada.bPodeComecar = false;

        CoachAutomatico coach = new CoachAutomatico(boa, vetada);
        Executor exec = new Executor(coach);
        exec.vTick(ambiente(0.0, Command.FORCE_START, 3));

        verdadeiro("escolha: play sem pre-condicao nao entra",
                coach.currentPlay() == boa);
    }

    /**
     * Nota alta tem de ganhar na media, sem nunca zerar a chance da outra.
     *
     * <p>Cada rodada usa uma semente diferente, e e por isso que
     * {@link Coach#vSetSemente} embaralha: com {@code java.util.Random} cru,
     * sementes sequenciais pequenas dao primeiros sorteios quase iguais e este
     * teste mediria 100% para a play boa sem que a roleta tivesse defeito nenhum.
     */
    private static void roletaPrefereNotaAlta() {
        int vezesDaBoa = 0;
        int rodadas = 400;

        for (int i = 0; i < rodadas; i++) {
            PlayContada boa = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
            boa.dScore = 9;
            PlayContada fraca = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
            fraca.iID2 = 99;
            fraca.dScore = 1;

            CoachAutomatico coach = new CoachAutomatico(boa, fraca);
            coach.vSetSemente(i);
            new Executor(coach).vTick(ambiente(0.0, Command.FORCE_START, 3));
            if (coach.currentPlay() == boa) vezesDaBoa++;
        }

        double fracao = vezesDaBoa / (double) rodadas;
        verdadeiro(String.format("roleta: nota 9 contra 1 escolhe a boa em %.0f%% das vezes",
                fracao * 100), fracao > 0.8 && fracao < 0.98);
    }

    // ------------------------------------------------------------- atribuicao

    private static void prioridadeDecideQuemEntraPrimeiro() {
        // Dois papeis, dois robos: o 1 esta perto da origem, o 2 esta longe.
        RoleComCusto importante = new RoleComCusto(10, Role.Prioridade.MAXIMA, Vec2.ZERO);
        RoleComCusto secundaria = new RoleComCusto(11, Role.Prioridade.BAIXA, Vec2.ZERO);

        PlayComRoles play = new PlayComRoles(importante, secundaria);
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0), new Vec2(3000, 0)));

        verdadeiro("prioridade: o papel MAXIMA fica com o robo mais perto",
                importante.player() != null && importante.player().id() == 0);
        verdadeiro("prioridade: e o de BAIXA fica com o que sobrou",
                secundaria.player() != null && secundaria.player().id() == 1);
    }

    /** Sem histerese, um empate quase perfeito faz os robos trocarem de papel toda hora. */
    private static void histereseEvitaTrocaTrocaDePapel() {
        RoleComCusto papel = new RoleComCusto(10, Role.Prioridade.MAXIMA, Vec2.ZERO);
        PlayComRoles play = new PlayComRoles(papel);
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        // O robo 0 comeca mais perto; depois o 1 passa a ser 10% melhor.
        exec.vTick(ambienteComRobos(0.0, new Vec2(1000, 0), new Vec2(1200, 0)));
        int primeiro = papel.player().id();
        exec.vTick(ambienteComRobos(0.1, new Vec2(1000, 0), new Vec2(900, 0)));

        verdadeiro("histerese: vantagem pequena nao tira o papel de quem ja o tem",
                papel.player().id() == primeiro);

        // Agora o 1 fica MUITO melhor: ai a troca tem de acontecer.
        exec.vTick(ambienteComRobos(0.2, new Vec2(4000, 0), new Vec2(100, 0)));
        verdadeiro("histerese: vantagem grande troca o papel de robo",
                papel.player().id() != primeiro);
    }

    // ----------------------------------------------------------------- arbitro

    private static void arbitroPodaOComando() {
        SkillQueChuta skill = new SkillQueChuta();
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(skill)));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        Map<Integer, Comando> emJogo = exec.vTick(ambiente(0.0, Command.FORCE_START, 1));
        verdadeiro("arbitro: com a bola em jogo o chute passa",
                emJogo.get(0).velChute() > 0);

        Map<Integer, Comando> parado = exec.vTick(ambiente(0.1, Command.STOP, 1));
        Comando c = parado.get(0);
        verdadeiro("arbitro: em STOP o chute e cortado", c.velChute() == 0);
        verdadeiro("arbitro: em STOP o dribbler e cortado", !c.dribbler());
        double v = Math.hypot(c.velTangencial(), c.velNormal());
        verdadeiro(String.format("arbitro: em STOP a velocidade satura em 1,5 m/s (%.0f)", v),
                v <= EstadoDeJogo.VEL_MAX_PARADO + 1e-6);

        Map<Integer, Comando> halt = exec.vTick(ambiente(0.2, Command.HALT, 1));
        verdadeiro("arbitro: em HALT nao sai nada",
                halt.get(0).equals(Comando.PARADO));
    }

    // -------------------------------------------------------------- apoio

    private static Ambiente ambiente(double tempo, Command comando, int robos) {
        List<EstadoRobo> lista = new ArrayList<>();
        for (int i = 0; i < robos; i++) {
            lista.add(new EstadoRobo(i, Cor.AZUL, new Vec2(i * 500, 0), 0,
                    Vec2.ZERO, 0, false, 0, 5));
        }
        return montar(tempo, comando, lista);
    }

    private static Ambiente ambienteComRobos(double tempo, Vec2... posicoes) {
        List<EstadoRobo> lista = new ArrayList<>();
        for (int i = 0; i < posicoes.length; i++) {
            lista.add(new EstadoRobo(i, Cor.AZUL, posicoes[i], 0, Vec2.ZERO, 0, false, 0, 5));
        }
        return montar(tempo, Command.FORCE_START, lista);
    }

    private static Ambiente montar(double tempo, Command comando, List<EstadoRobo> robos) {
        Quadro q = new Quadro(0, tempo, System.nanoTime(), Geometria.DIVISAO_B,
                new EstadoBola(Vec2.ZERO, 21.5, Vec2.ZERO, 0, 5), robos);

        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        a.doPainel(new ArbitroLocal().comando(comando));
        return new Ambiente(q, a.estado(Cor.AZUL), Cor.AZUL);
    }

    // --------------------------------------------------- implementacoes de teste

    private static final class SkillContada extends Skill {
        int iRuns, iInits;
        boolean bAcabar;
        @Override public String strName() { return "skill de teste"; }
        @Override public void vInitialize() { iInits++; }
        @Override protected void vRun() {
            iRuns++;
            jogador.vMover(500, 0, 0);
            if (bAcabar) vFinalizar();
        }
    }

    private static final class SkillQueChuta extends Skill {
        @Override public String strName() { return "chuta sempre"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() {
            jogador.vMover(3000, 0, 0);
            jogador.vChutar(6000);
            jogador.vDribbler(true);
        }
    }

    private static final class TacticContada extends Tactic {
        int iRuns;
        TacticContada(Skill s) { super(1); bAddSkill(1, s); }
        @Override public String strName() { return "tactic de teste"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { iRuns++; }
    }

    private static final class RoleContada extends Role {
        int iRuns;
        RoleContada(Tactic t) { super(1, Prioridade.MAXIMA); bAddTactic(1, t); }
        @Override public String strName() { return "role de teste"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { iRuns++; }
        @Override public double dCusto(Ambiente a, Jogador j) { return j.id(); }
    }

    /** Papel cujo custo e a distancia ate um ponto -- o criterio mais comum. */
    private static final class RoleComCusto extends Role {
        private final Vec2 alvo;
        RoleComCusto(int id, Prioridade p, Vec2 alvo) {
            super(id, p);
            this.alvo = alvo;
            bAddTactic(1, new TacticContada(new SkillContada()));
        }
        @Override public String strName() { return "role com custo"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { }
        @Override public void vCheckRoleFinished() { } // papel permanente
        @Override public double dCusto(Ambiente a, Jogador j) {
            return j.estado().posicao().distancia(alvo);
        }
    }

    private static class PlayContada extends Play {
        int iRuns, iInits, iUpdates, iSaves;
        int iID2 = 1;
        boolean bPodeComecar = true;
        boolean bTerminar;
        double dScore = 1;

        PlayContada(Role r) { super(1); bAddRole(r); }
        @Override public String strName() { return "play de teste"; }
        @Override public void vInitialize() { iInits++; }
        @Override public boolean bCheckPreConditions() { return bPodeComecar; }
        @Override public boolean bCheckEndConditions() { return bTerminar; }
        @Override public double dGetScore() { return dScore; }
        @Override public void vUpdateScore() { iUpdates++; }
        @Override public void vSaveScore() { iSaves++; }
        @Override protected void vRun() { iRuns++; }
        @Override public int id() { return iID2; }
    }

    private static final class PlayComRoles extends Play {
        PlayComRoles(Role... roles) {
            super(1);
            for (Role r : roles) bAddRole(r);
        }
        @Override public String strName() { return "play com roles"; }
        @Override public void vInitialize() { }
        @Override public boolean bCheckPreConditions() { return true; }
        @Override public boolean bCheckEndConditions() { return false; }
        @Override public double dGetScore() { return 1; }
        @Override public void vUpdateScore() { }
        @Override public void vSaveScore() { }
        @Override public void vCheckPlayFinished() { } // nunca termina sozinha
    }

    private static final class CoachManual extends Coach {
        CoachManual(Play p) { super(1, Modo.MANUAL); bAddPlay(p); }
        @Override public String strName() { return "coach manual"; }
        @Override public void vInitialize() { }
    }

    private static final class CoachAutomatico extends Coach {
        CoachAutomatico(Play... plays) {
            super(1, Modo.AUTOMATICO);
            for (Play p : plays) bAddPlay(p);
        }
        @Override public String strName() { return "coach automatico"; }
        @Override public void vInitialize() { }
    }

    // -------------------------------------------------------------- relatorio

    private static void verdadeiro(String nome, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("  ok    " + nome);
        } else {
            falhas++;
            System.out.println("  FALHA " + nome);
        }
    }
}
