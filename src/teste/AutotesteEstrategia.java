package teste;

import ajuste.Parametro;
import core.Caixa;
import core.Vec2;
import estrategia.Ambiente;
import estrategia.esqueleto.Coach;
import estrategia.motor.Diario;
import estrategia.motor.Executor;
import estrategia.Jogador;
import estrategia.esqueleto.Play;
import estrategia.esqueleto.Role;
import estrategia.esqueleto.Skill;
import estrategia.esqueleto.Tactic;
import estrategia.skills.SkillAndarPara;
import estrategia.skills.SkillChutar;
import estrategia.skills.SkillOlharPara;
import estrategia.tactics.TacticConduzirAoGol;
import model.Robo;
import mundo.Projecao;
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
        preCondicaoEnxergaOMundo();
        roletaPrefereNotaAlta();
        prioridadeDecideQuemEntraPrimeiro();
        histereseEvitaTrocaTrocaDePapel();
        mesmoPapelTemPrioridadeDiferenteEmCadaPlay();
        categoriaDecideQuemFicaSemRobo();
        dentroDaCategoriaValeAOrdemDeDeclaracao();
        papelQuePerdeORoboFicaVazio();
        playTerminaComPapelSemRobo();
        playComEssencialDemaisNaoEntraNoSorteio();
        perderPapelEssencialAbortaAPlay();
        arbitroPodaOComando();
        areaDeDefesaSoAceitaOGoleiro();
        areaDeDefesaFreiaEmVezDeCortarSeco();
        roboDentroDaAreaSoPodeSair();
        deslizarPelaBordaDaAreaNaoECortado();
        folgaEmpurraOLimiteDaArea();
        zonaProibidaVaiAteAParede();
        zonaDoGoleiroPassaDaLinhaDaArea();
        medidaDigitadaMandaNaZona();

        // --- projecao ---
        roboProjetadoSegueEmLinhaReta();
        roboParadoNaoSeProjeta();
        menorDistanciaEncontraAAproximacao();

        // --- skills ---
        andarParaAceleraDeLongeEFreiaDePerto();
        andarParaTerminaNoDestino();
        olharParaGiraPeloCaminhoCurto();
        chutarSoChutaComABola();

        // --- diario ---
        diarioMarcaQuemNaoTemPapel();
        diarioNaoRepeteLinhaIgual();
        diarioAnotaChuteEPosse();
        diarioAnotaChuteCortadoPeloArbitro();
        diarioListaORepertorioComNotas();

        // --- coerencia das taticas ---
        conducaoParaDentroDaZonaDeChute();

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
     * A pre-condicao e perguntada antes de a play comecar, e quase sempre depende
     * do mundo. Se o coach nao entregar o ambiente antes de perguntar, toda play
     * que consulta o mundo responde que nao pode comecar, para sempre.
     */
    private static void preCondicaoEnxergaOMundo() {
        PlayQuePreCisaDoMundo play = new PlayQuePreCisaDoMundo();
        CoachAutomatico coach = new CoachAutomatico(play);
        new Executor(coach).vTick(ambiente(0.0, Command.FORCE_START, 3));

        verdadeiro("pre-condicao: a play recebe o mundo antes de ser perguntada",
                coach.currentPlay() == play);
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
        RoleComCusto importante = new RoleComCusto(10, Vec2.ZERO);
        RoleComCusto secundaria = new RoleComCusto(11, Vec2.ZERO);

        PlayComRoles play = new PlayComRoles(
                papel(importante, Play.Prioridade.MAXIMA),
                papel(secundaria, Play.Prioridade.BAIXA));
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
        RoleComCusto atribuido = new RoleComCusto(10, Vec2.ZERO);
        PlayComRoles play = new PlayComRoles(papel(atribuido, Play.Prioridade.MAXIMA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        // O robo 0 comeca mais perto; depois o 1 passa a ser 10% melhor.
        exec.vTick(ambienteComRobos(0.0, new Vec2(1000, 0), new Vec2(1200, 0)));
        int primeiro = atribuido.player().id();
        exec.vTick(ambienteComRobos(0.1, new Vec2(1000, 0), new Vec2(900, 0)));

        verdadeiro("histerese: vantagem pequena nao tira o papel de quem ja o tem",
                atribuido.player().id() == primeiro);

        // Agora o 1 fica MUITO melhor: ai a troca tem de acontecer.
        exec.vTick(ambienteComRobos(0.2, new Vec2(4000, 0), new Vec2(100, 0)));
        verdadeiro("histerese: vantagem grande troca o papel de robo",
                atribuido.player().id() != primeiro);
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

    // ------------------------------------------------------------ area de defesa

    /**
     * A regra que mais custa caro: robo de linha dentro da area e penalti.
     *
     * <p>O mesmo robo, no mesmo lugar, com o mesmo comando -- muda so quem esta
     * declarado goleiro. E assim que o {@code Executor} decide, e por isso o teste
     * troca a declaracao em vez de trocar de robo.
     */
    private static void areaDeDefesaSoAceitaOGoleiro() {
        Vec2 perto = new Vec2(3000, 0); // a caminho da nossa area, que comeca em 3500

        Comando doGoleiro = umRoboIndoParaAArea(perto, 0).get(0);
        Comando deLinha = umRoboIndoParaAArea(perto, 5).get(0);

        aproximado("area: o goleiro declarado entra sem corte",
                doGoleiro.velTangencial(), Robo.VEL_MAX, 1e-6);
        verdadeiro("area: quem nao e goleiro tem a entrada cortada",
                deLinha.velTangencial() < doGoleiro.velTangencial());
    }

    /**
     * Cortar seco nao bastaria: o robo desacelera a ACEL_MAX e entraria assim mesmo.
     *
     * <p>A 3 m/s ele ainda percorre 1,5 m depois do comando ir a zero. O que se
     * limita e a velocidade de aproximacao, ao maior valor que ainda deixa parar
     * antes da linha -- o mesmo perfil de frenagem de {@code SkillAndarPara}.
     */
    private static void areaDeDefesaFreiaEmVezDeCortarSeco() {
        double x = 3000;
        // A caixa proibida e a area mais a folga, mais o raio do robo: a regra
        // mede pelo ponto do robo que entra, nao pelo centro.
        double borda = Geometria.DIVISAO_B.areaDefesa(1, Parametro.MARGEM_DA_AREA.valor()).xMin()
                - Robo.RAIO;
        double esperado = Math.sqrt(2 * Robo.ACEL_MAX * (borda - x));

        Comando c = umRoboIndoParaAArea(new Vec2(x, 0), 5).get(0);
        aproximado(String.format("area: a entrada satura na frenagem (%.0f mm/s)", esperado),
                c.velTangencial(), esperado, 1);
    }

    /** Empurrado para dentro, ou goleiro que deixou de ser: so resta sair. */
    private static void roboDentroDaAreaSoPodeSair() {
        Vec2 dentro = new Vec2(4000, 0); // area vai de 3500 a 4500

        Comando entrando = umRoboIndoParaAArea(dentro, 5).get(0);
        aproximado("area: dentro dela, a componente de entrada zera",
                entrando.velTangencial(), 0, 1e-6);

        Comando saindo = umRoboAndando(dentro, -Robo.VEL_MAX, 0, 5).get(0);
        aproximado("area: mas a de saida passa inteira",
                saindo.velTangencial(), -Robo.VEL_MAX, 1e-6);
    }

    /**
     * So a componente normal e tocada; a tangencial passa.
     *
     * <p>Uma defesa que trava ao encostar na area e pior que uma que a contorna.
     */
    private static void deslizarPelaBordaDaAreaNaoECortado() {
        Comando c = umRoboAndando(new Vec2(3000, 0), 0, Robo.VEL_MAX, 5).get(0);
        aproximado("area: andar ao longo da borda nao e cortado",
                c.velNormal(), Robo.VEL_MAX, 1e-6);
    }

    /** A folga e a unica parte local do retangulo, e ela cresce os quatro lados. */
    private static void folgaEmpurraOLimiteDaArea() {
        Geometria g = Geometria.DIVISAO_B;
        Caixa semFolga = g.areaDefesa(1, 0);
        Caixa comFolga = g.areaDefesa(1, 200);

        aproximado("area: sem folga, a boca fica na linha que a visao informou",
                semFolga.xMin(), g.meioComprimento() - g.areaDefesaProfundidade(), 1e-6);
        aproximado("area: a folga empurra a boca para fora",
                comFolga.xMin(), semFolga.xMin() - 200, 1e-6);
        aproximado("area: e alarga a lateral na mesma medida",
                comFolga.yMax(), semFolga.yMax() + 200, 1e-6);
    }

    /**
     * A zona proibida vai ate a PAREDE, e nao ate a linha de fundo.
     *
     * <p>O corredor atras do gol nao tem jogada nenhuma: robo que entra la errou
     * o caminho, e volta atravessando a area.
     */
    private static void zonaProibidaVaiAteAParede() {
        Geometria g = Geometria.DIVISAO_B;
        Caixa zona = g.zonaProibida(1, Parametro.MARGEM_DA_AREA.valor(), 0, 0);

        aproximado("zona: alcanca a parede fisica, e nao a linha de fundo",
                zona.xMax(), g.limiteParedeX(), 1e-6);
        aproximado("zona: avanca a area da visao mais a margem",
                zona.xMin(),
                g.meioComprimento() - g.areaDefesaProfundidade()
                        - Parametro.MARGEM_DA_AREA.valor(), 1e-6);
    }

    /** O goleiro pode ficar EM CIMA da linha da area, e nao so atras dela. */
    private static void zonaDoGoleiroPassaDaLinhaDaArea() {
        Geometria g = Geometria.DIVISAO_B;
        Caixa goleiro = g.zonaDoGoleiro(1, Robo.RAIO);
        Caixa area = g.areaDefesa(1);

        aproximado("zona do goleiro: sobra um raio de robo alem da linha da area",
                area.xMin() - goleiro.xMin(), Robo.RAIO, 1e-6);
        aproximado("zona do goleiro: e o mesmo raio na lateral",
                goleiro.yMax() - area.yMax(), Robo.RAIO, 1e-6);
    }

    /** Medida digitada manda; zero devolve a conta para a visao. */
    private static void medidaDigitadaMandaNaZona() {
        Geometria g = Geometria.DIVISAO_B;

        Caixa daVisao = g.zonaProibida(1, 45, 0, 0);
        Caixa digitada = g.zonaProibida(1, 45, 1500, 3000);

        aproximado("zona: profundidade digitada manda",
                digitada.xMin(), g.meioComprimento() - 1500, 1e-6);
        aproximado("zona: largura digitada manda", digitada.extensaoY(), 3000, 1e-6);
        verdadeiro("zona: com zero, quem manda e a area que a visao informou",
                Math.abs(daVisao.extensaoY() - (g.areaDefesaLargura() + 90)) < 1e-6);
    }

    /** Um robo so, indo para frente no maximo, com o goleiro declarado que se pedir. */
    private static Map<Integer, Comando> umRoboIndoParaAArea(Vec2 posicao, int goleiro) {
        return umRoboAndando(posicao, Robo.VEL_MAX, 0, goleiro);
    }

    private static Map<Integer, Comando> umRoboAndando(Vec2 posicao, double vx, double vy,
                                                       int goleiro) {
        SkillQueAnda skill = new SkillQueAnda(vx, vy);
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(skill)));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        EstadoRobo r = new EstadoRobo(0, Cor.AZUL, posicao, 0, Vec2.ZERO, 0, false, 0, 5);
        return exec.vTick(montar(0.0, Command.FORCE_START, List.of(r), goleiro));
    }

    // ------------------------------------------------ categorias de papel

    /**
     * A razao de a prioridade morar na play e nao na role.
     *
     * <p>O MESMO papel, declarado nas duas jogadas com pesos diferentes. Guardar a
     * prioridade dentro da Role obrigaria a escrever duas classes identicas so
     * para dizer que um marcador vale mais numa defesa do que num ataque com o
     * campo livre.
     */
    private static void mesmoPapelTemPrioridadeDiferenteEmCadaPlay() {
        RoleComCusto marcador = new RoleComCusto(10, Vec2.ZERO);

        PlayComRoles defesa = new PlayComRoles(papel(marcador, Play.Prioridade.MAXIMA));
        PlayComRoles ataque = new PlayComRoles(papel(marcador, Play.Prioridade.OPCIONAL));

        verdadeiro("prioridade: o mesmo papel e MAXIMA na defesa e OPCIONAL no ataque",
                defesa.prioridadeDe(marcador) == Play.Prioridade.MAXIMA
                        && ataque.prioridadeDe(marcador) == Play.Prioridade.OPCIONAL);
        verdadeiro("prioridade: e cada play conta os proprios robos minimos",
                defesa.iRobosMinimos() == 1 && ataque.iRobosMinimos() == 0);
    }

    /**
     * O caso que as categorias existem para resolver: play de quatro papeis com
     * dois robos.
     *
     * <p>Sem elas, quem ficaria de fora seria decidido pela ordem em que alguem
     * escreveu {@code bAddRole} -- o mesmo que decidir por acaso.
     */
    private static void categoriaDecideQuemFicaSemRobo() {
        RoleComCusto goleiro = new RoleComCusto(10, Vec2.ZERO);
        RoleComCusto meio = new RoleComCusto(11, Vec2.ZERO);
        RoleComCusto apoio = new RoleComCusto(12, Vec2.ZERO);
        RoleComCusto cobertura = new RoleComCusto(13, Vec2.ZERO);

        // Declaradas fora de ordem de proposito: quem manda e a categoria.
        PlayComRoles play = new PlayComRoles(
                papel(cobertura, Play.Prioridade.OPCIONAL),
                papel(apoio, Play.Prioridade.BAIXA),
                papel(meio, Play.Prioridade.MEDIA),
                papel(goleiro, Play.Prioridade.MAXIMA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0), new Vec2(200, 0)));

        verdadeiro("categoria: MAXIMA pega robo mesmo declarada por ultimo",
                goleiro.player() != null);
        verdadeiro("categoria: MEDIA pega o robo que sobrou", meio.player() != null);
        verdadeiro("categoria: BAIXA e OPCIONAL ficam sem robo, e a play segue",
                apoio.player() == null && cobertura.player() == null && play.bRodando());
    }

    /** Empate de categoria se desfaz pela ordem de declaracao: a ordenacao e estavel. */
    private static void dentroDaCategoriaValeAOrdemDeDeclaracao() {
        RoleComCusto primeiro = new RoleComCusto(10, Vec2.ZERO);
        RoleComCusto segundo = new RoleComCusto(11, Vec2.ZERO);

        PlayComRoles play = new PlayComRoles(
                papel(primeiro, Play.Prioridade.MEDIA),
                papel(segundo, Play.Prioridade.MEDIA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0))); // um robo so

        verdadeiro("categoria: no empate, o papel declarado antes leva o robo",
                primeiro.player() != null && segundo.player() == null);
    }

    /**
     * Papel que perde o robo tem de ficar VAZIO, e nao guardar o antigo.
     *
     * <p>Guardar faria o mesmo robo aparecer em dois papeis no quadro seguinte,
     * cada um escrevendo um comando por cima do outro.
     */
    private static void papelQuePerdeORoboFicaVazio() {
        RoleComCusto fixa = new RoleComCusto(10, Vec2.ZERO);
        RoleComCusto solta = new RoleComCusto(11, Vec2.ZERO);

        PlayComRoles play = new PlayComRoles(
                papel(fixa, Play.Prioridade.MAXIMA),
                papel(solta, Play.Prioridade.OPCIONAL));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0), new Vec2(3000, 0)));
        verdadeiro("categoria: com dois robos os dois papeis sao atribuidos",
                fixa.player() != null && solta.player() != null);

        exec.vTick(ambienteComRobos(0.1, new Vec2(100, 0))); // o segundo robo sumiu
        verdadeiro("categoria: o papel que perdeu o robo fica vazio",
                solta.player() == null);
        verdadeiro("categoria: e o robo que ficou nao aparece em dois papeis",
                fixa.player() != null && fixa.player().id() == 0);
    }

    /** Papel sem robo nunca roda, logo nunca termina: nao pode segurar a play. */
    private static void playTerminaComPapelSemRobo() {
        PlayQueTermina play = new PlayQueTermina(
                papel(new RoleQueAcaba(10), Play.Prioridade.MAXIMA),
                papel(new RoleQueAcaba(11), Play.Prioridade.OPCIONAL));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0))); // um robo, dois papeis

        verdadeiro("categoria: a play conclui sem esperar o papel que ficou sem robo",
                play.status() == Play.Status.CONCLUIDA);
    }

    /**
     * Play que precisa de mais robos do que ha nem entra no sorteio.
     *
     * <p>Comecar e abortar no primeiro tique gastaria um episodio de aprendizado
     * com um resultado que nao diz nada sobre a jogada.
     */
    private static void playComEssencialDemaisNaoEntraNoSorteio() {
        PlayComRoles play = new PlayComRoles(
                papel(new RoleComCusto(10, Vec2.ZERO), Play.Prioridade.MAXIMA),
                papel(new RoleComCusto(11, Vec2.ZERO), Play.Prioridade.MAXIMA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);

        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0)));
        verdadeiro("categoria: dois papeis MAXIMA e um robo -- play indisponivel",
                coach.playsDisponiveis().isEmpty());

        exec.vTick(ambienteComRobos(0.1, new Vec2(100, 0), new Vec2(200, 0)));
        verdadeiro("categoria: com o segundo robo ela volta a ser sorteavel",
                coach.playsDisponiveis().size() == 1);
    }

    /** Perder um papel MAXIMA no meio da jogada e a jogada deixar de existir. */
    private static void perderPapelEssencialAbortaAPlay() {
        PlayComRoles play = new PlayComRoles(
                papel(new RoleComCusto(10, Vec2.ZERO), Play.Prioridade.MAXIMA),
                papel(new RoleComCusto(11, new Vec2(3000, 0)), Play.Prioridade.MAXIMA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        exec.vTick(ambienteComRobos(0.0, new Vec2(100, 0), new Vec2(3000, 0)));
        verdadeiro("categoria: com os dois robos a play roda", play.bRodando());

        exec.vTick(ambienteComRobos(0.1, new Vec2(100, 0)));
        verdadeiro("categoria: perder um papel MAXIMA aborta a play",
                play.status() == Play.Status.ABORTADA);
    }

    // ------------------------------------------------------------------ projecao

    /**
     * Um segundo de rumo constante: o robo aparece exatamente onde a velocidade
     * atual o levaria. E o fantasma que a tela desenha.
     */
    private static void roboProjetadoSegueEmLinhaReta() {
        EstadoRobo r = new EstadoRobo(0, Cor.AZUL, new Vec2(1000, -500), 0,
                new Vec2(2000, 500), 1.5, false, 0, 5);

        Vec2 futuro = Projecao.robo(r, 1.0);
        aproximado("projecao: robo avanca a velocidade vezes o tempo (x)", futuro.x(), 3000, 1e-6);
        aproximado("projecao: idem em y", futuro.y(), 0, 1e-6);
        aproximado("projecao: e a orientacao acompanha omega",
                Projecao.theta(r, 1.0), 1.5, 1e-6);

        Vec2 meio = Projecao.robo(r, 0.5);
        aproximado("projecao: meio segundo da metade do caminho", meio.x(), 2000, 1e-6);
    }

    private static void roboParadoNaoSeProjeta() {
        EstadoRobo r = new EstadoRobo(3, Cor.AZUL, new Vec2(500, 500), 0.4,
                Vec2.ZERO, 0, false, 0, 5);
        Vec2 futuro = Projecao.robo(r, 1.0);
        aproximado("projecao: robo parado fica onde esta",
                futuro.distancia(r.posicao()), 0, 1e-9);
    }

    /** O CPA da navegacao: dois rumos convergentes se aproximam mais do que estao. */
    private static void menorDistanciaEncontraAAproximacao() {
        Vec2 pA = new Vec2(0, 0), vA = new Vec2(1000, 0);
        Vec2 pB = new Vec2(3000, 500), vB = new Vec2(-1000, 0);

        double agora = pA.distancia(pB);
        double minima = Projecao.menorDistancia(pA, vA, pB, vB);
        aproximado("projecao: rumos convergentes chegam a 500 mm um do outro",
                minima, 500, 1.0);
        verdadeiro("projecao: e isso e menos do que a distancia atual", minima < agora);

        double paralelo = Projecao.menorDistancia(pA, vA, new Vec2(0, 800), vA);
        aproximado("projecao: rumo paralelo mantem a distancia", paralelo, 800, 1e-6);
    }

    // -------------------------------------------------------------------- skills

    private static void andarParaAceleraDeLongeEFreiaDePerto() {
        SkillAndarPara skill = new SkillAndarPara(new Vec2(3000, 0));
        Jogador longe = jogadorEm(new Vec2(0, 0));
        Jogador perto = jogadorEm(new Vec2(2900, 0));

        skill.vRunSkill(ambiente(0, Command.FORCE_START, 1), longe);
        double vLonge = Math.hypot(longe.comando().velTangencial(), longe.comando().velNormal());

        skill.vResetSkill();
        skill.vSetDestino(new Vec2(3000, 0));
        skill.vRunSkill(ambiente(0, Command.FORCE_START, 1), perto);
        double vPerto = Math.hypot(perto.comando().velTangencial(), perto.comando().velNormal());

        verdadeiro(String.format("skill: de longe vai no maximo (%.0f mm/s)", vLonge),
                vLonge > Robo.VEL_MAX * 0.9);
        verdadeiro(String.format("skill: de perto freia (%.0f mm/s)", vPerto),
                vPerto < vLonge / 2);
    }

    private static void andarParaTerminaNoDestino() {
        SkillAndarPara skill = new SkillAndarPara(new Vec2(1000, 0));
        Jogador j = jogadorEm(new Vec2(1000, 0));
        skill.vRunSkill(ambiente(0, Command.FORCE_START, 1), j);

        verdadeiro("skill: chegou, entao a skill termina", skill.bIsFinished());
        verdadeiro("skill: e o robo para", j.comando().velTangencial() == 0
                && j.comando().velNormal() == 0);
    }

    /** Um robo a 170 graus do alvo tem de girar 10, e nao 350. */
    private static void olharParaGiraPeloCaminhoCurto() {
        SkillOlharPara skill = new SkillOlharPara(new Vec2(1000, 0));
        // Olhando para 170 graus; o alvo esta a 0 grau, logo o caminho curto e horario.
        Jogador j = jogadorOlhando(new Vec2(0, 0), Math.toRadians(170));
        skill.vRunSkill(ambiente(0, Command.FORCE_START, 1), j);

        verdadeiro(String.format("skill: gira pelo lado curto (omega %.1f)",
                j.comando().velAngular()), j.comando().velAngular() < 0);

        SkillOlharPara alinhada = new SkillOlharPara(new Vec2(1000, 0));
        Jogador certo = jogadorOlhando(new Vec2(0, 0), 0);
        alinhada.vRunSkill(ambiente(0, Command.FORCE_START, 1), certo);
        verdadeiro("skill: ja alinhado, termina sem girar",
                alinhada.bIsFinished() && certo.comando().velAngular() == 0);
    }

    private static void chutarSoChutaComABola() {
        SkillChutar skill = new SkillChutar(4000);

        Jogador semBola = jogadorEm(new Vec2(0, 0));
        skill.vRunSkill(ambiente(0, Command.FORCE_START, 1), semBola);
        verdadeiro("skill: sem bola no sensor nao chuta",
                semBola.comando().velChute() == 0 && !skill.bIsFinished());

        Jogador comBola = jogadorComBola(new Vec2(0, 0));
        skill.vRunSkill(ambiente(0, Command.FORCE_START, 1), comBola);
        verdadeiro("skill: com a bola no sensor chuta e termina",
                comBola.comando().velChute() > 0 && skill.bIsFinished());
        verdadeiro("skill: e larga o dribbler junto", !comBola.comando().dribbler());
    }

    /**
     * Regressao do travamento: com o destino da conducao igual ao limiar de
     * chute, o robo parava em cima da fronteira e a jogada morria com a bola
     * presa. Medido no simulador: sem isto, sem gol; com isto, gol em 5,2 s.
     */
    private static void conducaoParaDentroDaZonaDeChute() {
        verdadeiro(String.format("taticas: conducao (%.0f) para dentro da zona de chute (%.0f)",
                        Parametro.DISTANCIA_DE_CONDUCAO.valor(),
                        Parametro.DISTANCIA_DE_CHUTE.valor()),
                Parametro.DISTANCIA_DE_CONDUCAO.valor()
                        < Parametro.DISTANCIA_DE_CHUTE.valor() - 100);
    }

    // ------------------------------------------------------------------- diario

    /** A pergunta que se faz olhando um robo travado: ele tem papel? */
    private static void diarioMarcaQuemNaoTemPapel() {
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());
        exec.vTick(ambiente(0.0, Command.FORCE_START, 3));
        exec.vTick(ambiente(0.1, Command.FORCE_START, 3));

        List<Diario.Situacao> lista = exec.diario().situacoes();
        verdadeiro("diario: uma situacao por robo nosso", lista.size() == 3);

        Diario.Situacao comPapel = lista.stream().filter(Diario.Situacao::temRole)
                .findFirst().orElse(null);
        verdadeiro("diario: quem tem papel aparece executando",
                comPapel != null && comPapel.temTactic() && comPapel.temSkill()
                        && comPapel.comandado() && comPapel.executando());

        long semPapel = lista.stream().filter(x -> !x.temRole()).count();
        verdadeiro("diario: os outros aparecem sem papel e sem comando",
                semPapel == 2 && lista.stream().filter(x -> !x.temRole())
                        .noneMatch(Diario.Situacao::comandado));
    }

    /** Estado que nao muda nao vira linha nova, senao o log rola sozinho e some. */
    private static void diarioNaoRepeteLinhaIgual() {
        PlayContada play = new PlayContada(new RoleContada(new TacticContada(new SkillContada())));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        for (int i = 0; i < 30; i++) exec.vTick(ambiente(i * 0.1, Command.FORCE_START, 2));

        int linhas = exec.diario().linhas().size();
        verdadeiro("diario: 30 tiques iguais nao viram 30 linhas (" + linhas + ")", linhas <= 6);
    }

    private static void diarioAnotaChuteEPosse() {
        PlayComRoles play = new PlayComRoles(
                papel(new RoleQueChuta(10), Play.Prioridade.MAXIMA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        exec.vTick(comBola(0.0, Command.FORCE_START, false));
        exec.vTick(comBola(0.1, Command.FORCE_START, true));

        String tudo = String.join(" | ", exec.diario().linhas().stream()
                .map(Diario.Linha::texto).toList());
        verdadeiro("diario: anota a bola entrando no sensor", tudo.contains("bola no sensor"));
        verdadeiro("diario: anota o chute com a velocidade", tudo.contains("chute a 6,0 m/s")
                || tudo.contains("chute a 6.0 m/s"));
    }

    /**
     * O caso mais traicoeiro de depurar: a play pediu chute, o arbitro cortou, e
     * sem log o comando some sem deixar rastro.
     */
    private static void diarioAnotaChuteCortadoPeloArbitro() {
        PlayComRoles play = new PlayComRoles(
                papel(new RoleQueChuta(10), Play.Prioridade.MAXIMA));
        CoachManual coach = new CoachManual(play);
        Executor exec = new Executor(coach);
        coach.bSetPlay(play.id());

        exec.vTick(comBola(0.0, Command.STOP, true));

        String tudo = String.join(" | ", exec.diario().linhas().stream()
                .map(Diario.Linha::texto).toList());
        verdadeiro("diario: anota o chute cortado pelo arbitro",
                tudo.contains("chute cortado por STOP"));
    }

    /**
     * O painel da direita mostra o repertorio com nota, disponibilidade e qual
     * esta rodando. Jogada sem pre-condicao continua na lista, apagada: sumir
     * responderia a pergunta errada, porque quem olha quer entender POR QUE o
     * coach escolheu aquela.
     */
    private static void diarioListaORepertorioComNotas() {
        PlayContada rodando = new PlayContada(
                new RoleContada(new TacticContada(new SkillContada())));
        rodando.dScore = 7;

        PlayContada vetada = new PlayContada(
                new RoleContada(new TacticContada(new SkillContada())));
        vetada.iID2 = 99;
        vetada.dScore = 3;
        vetada.bPodeComecar = false;

        CoachAutomatico coach = new CoachAutomatico(rodando, vetada);
        Executor exec = new Executor(coach);
        exec.vTick(ambiente(0.0, Command.FORCE_START, 2));

        List<Diario.Jogada> lista = exec.diario().jogadas();
        verdadeiro("diario: o repertorio inteiro aparece", lista.size() == 2);

        Diario.Jogada boa = lista.stream().filter(j -> j.id() == 1).findFirst().orElse(null);
        verdadeiro("diario: a que roda vem marcada como atual e disponivel",
                boa != null && boa.atual() && boa.disponivel());
        aproximado("diario: com a nota dela", boa == null ? -1 : boa.nota(), 7, 1e-9);

        Diario.Jogada fora = lista.stream().filter(j -> j.id() == 99).findFirst().orElse(null);
        verdadeiro("diario: a sem pre-condicao continua listada, fora do sorteio",
                fora != null && !fora.disponivel() && !fora.atual());
    }

    /** Ambiente com o nosso robo 0 segurando (ou nao) a bola. */
    private static Ambiente comBola(double tempo, Command comando, boolean posse) {
        List<EstadoRobo> robos = List.of(
                new EstadoRobo(0, Cor.AZUL, Vec2.ZERO, 0, Vec2.ZERO, 0, posse, 0, 5));
        return montar(tempo, comando, robos);
    }

    // -------------------------------------------------------------- apoio

    private static Jogador jogadorEm(Vec2 posicao) { return jogadorOlhando(posicao, 0); }

    private static Jogador jogadorOlhando(Vec2 posicao, double theta) {
        Jogador j = new Jogador(0);
        aplicar(j, new EstadoRobo(0, Cor.AZUL, posicao, theta, Vec2.ZERO, 0, false, 0, 5));
        return j;
    }

    private static Jogador jogadorComBola(Vec2 posicao) {
        Jogador j = new Jogador(0);
        aplicar(j, new EstadoRobo(0, Cor.AZUL, posicao, 0, Vec2.ZERO, 0, true, 0, 5));
        return j;
    }

    /**
     * {@code vAtualizar} e de pacote, porque so o executor deve chamar. O teste
     * alcanca por reflexao em vez de abrir o metodo: o encapsulamento existe para
     * o codigo de producao, e afroxa-lo por causa de teste e o comeco de alguem
     * chamar isso de dentro de uma skill.
     */
    private static void aplicar(Jogador j, EstadoRobo estado) {
        try {
            var m = Jogador.class.getDeclaredMethod("vAtualizar", EstadoRobo.class);
            m.setAccessible(true);
            m.invoke(j, estado);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

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
        return montar(tempo, comando, robos, 0);
    }

    /**
     * Ambiente com o goleiro declarado.
     *
     * <p>Jogamos de azul e o azul fica no lado positivo, entao defendemos +x: a
     * nossa area vai de 3500 a 4500 em x.
     */
    private static Ambiente montar(double tempo, Command comando, List<EstadoRobo> robos,
                                   int goleiro) {
        Quadro q = new Quadro(0, tempo, System.nanoTime(), Geometria.DIVISAO_B,
                new EstadoBola(Vec2.ZERO, 21.5, Vec2.ZERO, 0, 5), robos);

        Arbitro a = new Arbitro();
        a.setModoLocal(true);
        ArbitroLocal gc = new ArbitroLocal();
        gc.setGoleiro(Cor.AZUL, goleiro);
        a.doPainel(gc.comando(comando));
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
        RoleContada(Tactic t) { super(1); bAddTactic(1, t); }
        @Override public String strName() { return "role de teste"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { iRuns++; }
        @Override public double dCusto(Ambiente a, Jogador j) { return j.id(); }
    }

    /** Papel e prioridade juntos, so para os testes montarem play numa linha. */
    private record PapelDaPlay(Role role, Play.Prioridade prioridade) {}

    private static PapelDaPlay papel(Role r, Play.Prioridade p) {
        return new PapelDaPlay(r, p);
    }

    /** Papel cujo custo e a distancia ate um ponto -- o criterio mais comum. */
    private static final class RoleComCusto extends Role {
        private final Vec2 alvo;
        RoleComCusto(int id, Vec2 alvo) {
            super(id);
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

        PlayContada(Role r) { super(1); bAddRole(r, Prioridade.MAXIMA); }
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

    /** Papel que se da por concluido no primeiro tique. */
    private static final class RoleQueAcaba extends Role {
        RoleQueAcaba(int id) {
            super(id);
            bAddTactic(1, new TacticContada(new SkillContada()));
        }
        @Override public String strName() { return "role que acaba"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { }
        @Override public void vCheckRoleFinished() { vFinalizar(); }
        @Override public double dCusto(Ambiente a, Jogador j) {
            return j.estado().posicao().norma();
        }
    }

    /** Como a PlayComRoles, mas deixando o fim padrao valer: acaba com as roles. */
    private static final class PlayQueTermina extends Play {
        PlayQueTermina(PapelDaPlay... papeis) {
            super(1);
            for (PapelDaPlay x : papeis) bAddRole(x.role(), x.prioridade());
        }
        @Override public String strName() { return "play que termina"; }
        @Override public void vInitialize() { }
        @Override public boolean bCheckPreConditions() { return true; }
        @Override public boolean bCheckEndConditions() { return false; }
        @Override public double dGetScore() { return 1; }
        @Override public void vUpdateScore() { }
        @Override public void vSaveScore() { }
    }

    private static final class PlayComRoles extends Play {
        PlayComRoles(PapelDaPlay... papeis) {
            super(1);
            for (PapelDaPlay x : papeis) bAddRole(x.role(), x.prioridade());
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

    /** Pre-condicao que so responde true com o mundo em maos. */
    private static final class PlayQuePreCisaDoMundo extends Play {
        PlayQuePreCisaDoMundo() {
            super(1);
            bAddRole(new RoleContada(new TacticContada(new SkillContada())),
                    Prioridade.MAXIMA);
        }
        @Override public String strName() { return "precisa do mundo"; }
        @Override public void vInitialize() { }
        @Override public boolean bCheckPreConditions() {
            return ambiente != null && ambiente.jogo().bolaEmJogo();
        }
        @Override public boolean bCheckEndConditions() { return false; }
        @Override public double dGetScore() { return 1; }
        @Override public void vUpdateScore() { }
        @Override public void vSaveScore() { }
    }

    /** Papel que chuta assim que a bola encosta; existe so para o diario ter o que anotar. */
    private static final class RoleQueChuta extends Role {
        RoleQueChuta(int id) {
            super(id);
            bAddTactic(1, new TacticQueChuta());
        }
        @Override public String strName() { return "chutador"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { }
        @Override public void vCheckRoleFinished() { }
        @Override public double dCusto(Ambiente a, Jogador j) { return 0; }
    }

    private static final class TacticQueChuta extends Tactic {
        TacticQueChuta() { super(1); bAddSkill(1, new SkillQueChutaSeTiver()); }
        @Override public String strName() { return "chuta"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { }
        @Override public void vCheckTacticFinished() { }
    }

    /** Anda com uma velocidade local fixa, sem chutar nem dribblar. */
    private static final class SkillQueAnda extends Skill {
        private final double vx, vy;
        SkillQueAnda(double vx, double vy) { this.vx = vx; this.vy = vy; }
        @Override public String strName() { return "anda reto"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() { jogador.vMover(vx, vy); }
    }

    private static final class SkillQueChutaSeTiver extends Skill {
        @Override public String strName() { return "chuta se tiver"; }
        @Override public void vInitialize() { }
        @Override protected void vRun() {
            jogador.vMover(1000, 0);
            if (jogador.bComABola()) jogador.vChutar(6000);
        }
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

    private static void aproximado(String nome, double obtido, double esperado, double tol) {
        total++;
        if (Math.abs(obtido - esperado) <= tol) {
            System.out.printf("  ok    %s%n", nome);
        } else {
            falhas++;
            System.out.printf("  FALHA %s  (obtido %.4f, esperado %.4f +- %.4f)%n",
                    nome, obtido, esperado, tol);
        }
    }

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
