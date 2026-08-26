package estrategia;


import model.Robo;
import ajuste.Parametro;
import core.Caixa;
import core.Geo;
import core.Vec2;
import jogo.EstadoDeJogo;
import model.Cor;
import model.Geometria;
import mundo.EstadoBola;
import mundo.EstadoRobo;
import mundo.Quadro;

import java.util.List;

/**
 * Tudo que a estrategia pode ler, e nada alem disso.
 *
 * <p>Equivale ao {@code AmbienteCampo} do SSL-Strategy: e o objeto que atravessa
 * todas as camadas, de {@link estrategia.esqueleto.Coach} a
 * {@link estrategia.esqueleto.Skill}. Junta as duas fontes de verdade que
 * existem -- o {@link Quadro} da visao e o {@link EstadoDeJogo} do arbitro --
 * mais a cor que estamos jogando, que e o que transforma "azul" em "nos".
 *
 * <p>E somente leitura de proposito. Nenhuma camada de estrategia deve alcancar
 * rede, Swing ou o {@code Cliente}: quem decide o que os robos fazem tem de poder
 * rodar mil partidas sem abrir janela, que e a condicao para treinar o coach.
 *
 * <p>O tempo aqui e o {@code t_capture} da visao, e nao o relogio de parede. Com
 * o simulador rodando acelerado sao coisas bem diferentes, e e o tempo simulado
 * que faz o timeout de uma play significar a mesma coisa nas duas velocidades.
 *
 * @param margemDaArea folga somada a area de defesa para formar a zona onde so o
 *        goleiro entra, em mm. E o unico numero daqui que NAO vem da visao nem do
 *        arbitro: e escolha nossa, e por isso e ajustavel na interface
 */
public record Ambiente(Quadro quadro, EstadoDeJogo jogo, Cor nossaCor, double margemDaArea) {

    /** Sem margem declarada, vale {@link Parametro#MARGEM_DA_AREA}. */
    public Ambiente(Quadro quadro, EstadoDeJogo jogo, Cor nossaCor) {
        this(quadro, jogo, nossaCor, Parametro.MARGEM_DA_AREA.valor());
    }

    public Geometria geometria() { return quadro.geometria(); }
    public EstadoBola bola()     { return quadro.bola(); }

    /** Segundos no relogio de quem gerou o quadro. */
    public double tempo() { return quadro.tempo(); }

    public Cor corDeles() { return nossaCor.oposta(); }

    public List<EstadoRobo> nossos() { return quadro.robos(nossaCor); }
    public List<EstadoRobo> deles()  { return quadro.robos(corDeles()); }

    public EstadoRobo nosso(int id) { return quadro.robo(nossaCor, id); }

    /**
     * Robo nosso que esta com a bola no sensor, ou {@code null}.
     *
     * <p>Cuidado ao usar: o sensor e inferido por geometria, entao ele responde
     * "a bola esta encostada na boca", que nao e exatamente "a posse e nossa".
     */
    public EstadoRobo nossoComABola() {
        for (EstadoRobo r : nossos()) if (r.bolaNoSensor()) return r;
        return null;
    }

    /** Centro do gol que defendemos. O lado vem do arbitro, nao da visao. */
    public Vec2 nossoGol() { return geometria().centroGol(jogo.nossoLado()); }

    /** Centro do gol que atacamos. */
    public Vec2 golDeles() { return geometria().centroGol(-jogo.nossoLado()); }

    /** True se o ponto esta na metade que defendemos. */
    public boolean noNossoCampo(Vec2 p) { return p.x() * jogo.nossoLado() > 0; }

    /**
     * O retangulo da nossa area de defesa, ja com a folga de seguranca.
     *
     * <p>E zona proibida para todo robo nosso que nao seja o goleiro -- a regra
     * da SSL e essa, e quem a aplica e o {@link estrategia.motor.Executor Executor}, no fim do tique, pelo
     * mesmo motivo que os limites do arbitro moram la: basta uma play esquecer
     * para o time cometer falta.
     */
    public Caixa nossaAreaProibida() {
        return geometria().zonaProibida(jogo.nossoLado(), margemDaArea,
                Parametro.ZONA_PROFUNDIDADE.valor(), Parametro.ZONA_LARGURA.valor());
    }

    /**
     * A area de defesa DELES, ja com a folga de seguranca.
     *
     * <p>Tambem e proibida, e por uma regra diferente da nossa: entrar na area do
     * adversario e falta de ataque, e ali a excecao do goleiro NAO vale -- nem o
     * nosso goleiro pode. Na nossa, o goleiro declarado entra; na deles, ninguem.
     *
     * <p>Mesma forma da nossa, espelhada. Vai ate a parede pelo mesmo motivo: o
     * corredor atras do gol nao tem jogada nenhuma, e um robo que entra la so
     * pode voltar atravessando a area.
     */
    public Caixa areaProibidaDeles() {
        return geometria().zonaProibida(-jogo.nossoLado(), margemDaArea,
                Parametro.ZONA_PROFUNDIDADE.valor(), Parametro.ZONA_LARGURA.valor());
    }

    /**
     * Onde o goleiro pode ficar: a area de defesa mais um raio de robo.
     *
     * <p>Maior que a zona proibida de proposito, e e essa diferenca que faz um
     * goleiro poder ficar EM CIMA da linha da area em vez de so atras dela.
     */
    public Caixa zonaDoGoleiro() {
        return geometria().zonaDoGoleiro(jogo.nossoLado(), Robo.RAIO);
    }

    /** True se este robo nosso pode entrar na area: so o goleiro declarado pode. */
    public boolean podeEntrarNaArea(int id) { return id == jogo.goleiro(); }

    // ------------------------------------------------------------ mira e linha

    /** Os dois postes do gol indicado, do de {@code -y} para o de {@code +y}. */
    public Vec2[] getVec2Postes(int lado) {
        double x = lado * geometria().meioComprimento();
        double meia = geometria().golLargura() / 2.0;
        return new Vec2[]{new Vec2(x, -meia), new Vec2(x, meia)};
    }

    /**
     * Onde a bola cruza a linha do NOSSO gol, se ela estiver indo para la.
     *
     * <p>Semirreta e nao reta: uma bola se afastando tambem cruza a reta do gol,
     * so que atras dela, e um goleiro que acredite nisso se posiciona para
     * defender uma bola que ja passou. E segmento e nao reta do outro lado: fora
     * dos postes a bola nao entra, e nao ha o que defender.
     *
     * @return o ponto entre os postes, ou {@code null} se ela nao vem, se esta
     *         parada ou se passa por fora
     */
    public Vec2 getVec2MiraNoNossoGol() {
        EstadoBola b = bola();
        if (b.perdida() || b.rapidez() < 1e-6) return null;
        Vec2[] postes = getVec2Postes(jogo.nossoLado());
        return Geo.getVec2SemirretaComSegmento(b.posicao(), b.velocidade(), postes[0], postes[1]);
    }

    /** True quando a trajetoria corrente da bola termina dentro do nosso gol. */
    public boolean bBolaVemParaONossoGol() { return getVec2MiraNoNossoGol() != null; }

    /**
     * Onde o goleiro deve ficar para cobrir a bola, no trecho que ele patrulha.
     *
     * <p>Na reta bola -> centro do gol, recuado {@code recuo} da linha, e PRESO
     * ao trecho entre os postes: sair dele para cobrir um angulo impossivel e
     * como nao ter goleiro, porque o gol fica inteiro aberto do outro lado.
     */
    public Vec2 getVec2PostoDoGoleiro(double recuo) {
        Vec2 centro = nossoGol();
        Vec2 alvo = bola().perdida() ? centro : bola().posicao();
        Vec2 dentro = new Vec2(centro.x() - jogo.nossoLado() * recuo, centro.y());

        Vec2[] postes = getVec2Postes(jogo.nossoLado());
        Vec2 p1 = new Vec2(dentro.x(), postes[0].y());
        Vec2 p2 = new Vec2(dentro.x(), postes[1].y());

        Vec2 cruzamento = Geo.getVec2InterseccaoRetaComSegmento(alvo, centro, p1, p2);
        return cruzamento != null ? cruzamento : Geo.getVec2NoSegmento(dentro, p1, p2);
    }

    /**
     * True se nenhum robo em campo corta o trecho, fora quem esta nas pontas.
     *
     * <p>A folga soma o raio do robo ao raio da bola: a linha nao precisa passar
     * pelo centro do adversario para o passe morrer, passar perto ja basta.
     *
     * @param ignorar centros a desconsiderar -- tipicamente quem passa e quem recebe
     */
    public boolean bLinhaLivre(Vec2 de, Vec2 para, double folga, Vec2... ignorar) {
        for (EstadoRobo r : quadro.robos()) {
            boolean pular = false;
            for (Vec2 i : ignorar) {
                if (i != null && i.distancia(r.posicao()) < 1e-6) { pular = true; break; }
            }
            if (pular) continue;
            if (Geo.bSegmentoCortaCirculo(de, para, r.posicao(), folga)) return false;
        }
        return true;
    }

    /** A menor folga que o trecho tem contra os robos DELES. */
    public double dFolgaContraEles(Vec2 de, Vec2 para) {
        return Geo.dFolgaDoSegmento(de, para,
                deles().stream().map(EstadoRobo::posicao).toList());
    }
}
