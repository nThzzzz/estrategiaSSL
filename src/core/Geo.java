package core;

import java.util.List;

/**
 * Geometria de retas, segmentos e circulos, sem saber o que e campo.
 *
 * <p>Separado de {@link Vec2} de proposito. Vec2 e um valor: soma, gira, mede.
 * Aqui moram as perguntas que envolvem DOIS objetos -- onde duas retas se
 * cruzam, se um segmento passa por dentro de um circulo -- e que nao pertencem a
 * nenhum dos dois. Enfiar isso em Vec2 faria o tipo mais usado do programa
 * crescer sem parar.
 *
 * <p>Nao conhece campo, gol nem robo. Quem sabe disso e {@code Ambiente}, que
 * chama daqui: assim a mesma conta serve para mira, para passe e para desvio,
 * sem tres versoes quase iguais.
 *
 * <h2>Reta ou segmento, e por que a diferenca importa</h2>
 *
 * <p>Sao perguntas diferentes e confundi-las da erro silencioso. A trajetoria de
 * uma bola e uma SEMIRRETA que segue adiante; a linha do gol e um SEGMENTO que
 * acaba nos postes. Perguntar "onde essas duas RETAS se cruzam" sempre responde
 * um ponto, inclusive quando a bola vai para o outro lado ou passa longe do
 * poste -- e um goleiro que acredite nessa resposta corre para fora do gol.
 *
 * <h2>Nome com o tipo</h2>
 *
 * <p>Quem devolve {@link Vec2} comeca com {@code getVec2}; booleano com
 * {@code b}, double com {@code d}, como no resto do projeto. Numa cadeia de
 * contas geometricas o tipo do resultado e o que mais se erra, e ele aparecer no
 * nome resolve antes de compilar.
 *
 * <p>Todo metodo que pode nao ter resposta devolve {@code null}, e nunca um
 * ponto inventado. "As retas sao paralelas" e "o cruzamento fica fora do
 * trecho" sao respostas legitimas, e o chamador precisa poder distinguir isso de
 * um ponto de verdade.
 */
public final class Geo {

    /** Abaixo disto, duas direcoes sao a mesma e um segmento e um ponto. */
    private static final double EPS = 1e-9;

    private Geo() {}

    // --------------------------------------------------------------- basico

    /**
     * Produto vetorial 2D, {@code a.x*b.y - a.y*b.x}.
     *
     * <p>E o sinal dele que diz de que LADO um ponto esta de uma reta, e e disso
     * que sai praticamente toda interseccao daqui.
     */
    public static double dCruz(Vec2 a, Vec2 b) {
        return a.x() * b.y() - a.y() * b.x();
    }

    /**
     * De que lado de {@code a -> b} esta o ponto: +1 esquerda, -1 direita, 0 em cima.
     */
    public static int iLado(Vec2 a, Vec2 b, Vec2 p) {
        double c = dCruz(b.menos(a), p.menos(a));
        if (c > EPS) return 1;
        if (c < -EPS) return -1;
        return 0;
    }

    // ------------------------------------------------------- retas e segmentos

    /**
     * Onde as RETAS infinitas {@code a1a2} e {@code b1b2} se cruzam.
     *
     * @return o ponto, ou {@code null} se sao paralelas (inclusive coincidentes:
     *         "cruzam em toda parte" nao e um ponto)
     */
    public static Vec2 getVec2InterseccaoDeRetas(Vec2 a1, Vec2 a2, Vec2 b1, Vec2 b2) {
        Vec2 dirA = a2.menos(a1);
        Vec2 dirB = b2.menos(b1);
        double den = dCruz(dirA, dirB);
        if (Math.abs(den) < EPS) return null;

        double t = dCruz(b1.menos(a1), dirB) / den;
        return a1.mais(dirA.escala(t));
    }

    /**
     * Onde os SEGMENTOS se cruzam, dentro dos dois trechos.
     *
     * @return o ponto, ou {@code null} se as retas nao se cruzam ou se cruzam
     *         fora de um dos dois trechos
     */
    public static Vec2 getVec2InterseccaoDeSegmentos(Vec2 a1, Vec2 a2, Vec2 b1, Vec2 b2) {
        Vec2 dirA = a2.menos(a1);
        Vec2 dirB = b2.menos(b1);
        double den = dCruz(dirA, dirB);
        if (Math.abs(den) < EPS) return null;

        Vec2 entre = b1.menos(a1);
        double t = dCruz(entre, dirB) / den;
        double u = dCruz(entre, dirA) / den;
        if (t < 0 || t > 1 || u < 0 || u > 1) return null;
        return a1.mais(dirA.escala(t));
    }

    /**
     * Onde a RETA {@code a1a2} cruza o SEGMENTO {@code p1p2}.
     *
     * <p>E o caso do goleiro: a reta e por onde a bola vai, o segmento e o trecho
     * que ele patrulha, e a resposta so vale se cair ENTRE os dois pontos. Fora
     * dali a bola nao passa por onde ele consegue chegar, e fingir um ponto o
     * mandaria para fora do gol.
     *
     * @return o ponto dentro do trecho, ou {@code null}
     */
    public static Vec2 getVec2InterseccaoRetaComSegmento(Vec2 a1, Vec2 a2, Vec2 p1, Vec2 p2) {
        Vec2 dirA = a2.menos(a1);
        Vec2 dirP = p2.menos(p1);
        double den = dCruz(dirA, dirP);
        if (Math.abs(den) < EPS) return null;

        double u = dCruz(p1.menos(a1), dirA) / den;
        if (u < 0 || u > 1) return null;
        return p1.mais(dirP.escala(u));
    }

    /**
     * Onde a SEMIRRETA que sai de {@code origem} na direcao {@code direcao} cruza
     * o segmento.
     *
     * <p>Semirreta, e nao reta: uma bola indo embora do gol tambem cruza a reta
     * do gol, so que ATRAS dela. Sem esta distincao o goleiro se posiciona para
     * defender uma bola que esta se afastando.
     *
     * @return o ponto, ou {@code null} se a direcao e nula, se sao paralelas, se
     *         o cruzamento fica para tras ou fora do trecho
     */
    public static Vec2 getVec2SemirretaComSegmento(Vec2 origem, Vec2 direcao,
                                                   Vec2 p1, Vec2 p2) {
        if (direcao.norma() < EPS) return null;
        Vec2 dirP = p2.menos(p1);
        double den = dCruz(direcao, dirP);
        if (Math.abs(den) < EPS) return null;

        Vec2 entre = p1.menos(origem);
        double t = dCruz(entre, dirP) / den;   // quanto se anda na semirreta
        double u = dCruz(entre, direcao) / den; // onde cai no trecho
        if (t < 0 || u < 0 || u > 1) return null;
        return origem.mais(direcao.escala(t));
    }

    public static boolean bSegmentosCruzam(Vec2 a1, Vec2 a2, Vec2 b1, Vec2 b2) {
        return getVec2InterseccaoDeSegmentos(a1, a2, b1, b2) != null;
    }

    // ----------------------------------------------------------- projecoes

    /**
     * Ponto do SEGMENTO {@code a -> b} mais proximo de {@code p}.
     *
     * <p>Preso ao trecho: se a projecao cai antes de {@code a} ou depois de
     * {@code b}, a resposta e a propria ponta. E o que se quer quando o segmento
     * e um lugar onde da para estar, e nao uma reta imaginaria.
     */
    public static Vec2 getVec2NoSegmento(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.menos(a);
        double comprimento = ab.normaQuad();
        if (comprimento < EPS) return a;

        double t = p.menos(a).escalar(ab) / comprimento;
        t = Math.max(0, Math.min(1, t));
        return a.mais(ab.escala(t));
    }

    /** Ponto da RETA {@code a -> b} mais proximo de {@code p}, sem prender no trecho. */
    public static Vec2 getVec2NaReta(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.menos(a);
        double comprimento = ab.normaQuad();
        if (comprimento < EPS) return a;
        return a.mais(ab.escala(p.menos(a).escalar(ab) / comprimento));
    }

    public static double dDistanciaAoSegmento(Vec2 p, Vec2 a, Vec2 b) {
        return p.distancia(getVec2NoSegmento(p, a, b));
    }

    public static double dDistanciaAReta(Vec2 p, Vec2 a, Vec2 b) {
        return p.distancia(getVec2NaReta(p, a, b));
    }

    // ------------------------------------------------------------- circulos

    /**
     * True se o segmento passa a menos de {@code raio} do centro.
     *
     * <p>E a pergunta do passe: o robo e um circulo, e a linha nao precisa passar
     * pelo centro dele para bater -- passar perto ja basta. Somar o raio da bola
     * ao raio do robo antes de chamar e o que transforma "a linha toca" em "a
     * bola nao passa".
     */
    public static boolean bSegmentoCortaCirculo(Vec2 a, Vec2 b, Vec2 centro, double raio) {
        return dDistanciaAoSegmento(centro, a, b) <= raio;
    }

    /**
     * A menor folga entre o segmento e um conjunto de centros.
     *
     * <p>Serve para ESCOLHER, e nao so para recusar: entre dois passes possiveis,
     * o de maior folga e o que menos depende do adversario ficar parado. Um
     * booleano so responde "da ou nao da", e obriga quem chama a inventar um
     * criterio de desempate.
     *
     * @return {@link Double#POSITIVE_INFINITY} quando nao ha ninguem no caminho
     */
    public static double dFolgaDoSegmento(Vec2 a, Vec2 b, List<Vec2> centros) {
        double menor = Double.POSITIVE_INFINITY;
        for (Vec2 c : centros) menor = Math.min(menor, dDistanciaAoSegmento(c, a, b));
        return menor;
    }
}
