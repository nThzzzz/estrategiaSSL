package estrategia.tactics;

import estrategia.ids.Habilidade;
import estrategia.esqueleto.Tactic;
import estrategia.ids.Tatica;
import estrategia.skills.Chutar;
import estrategia.skills.OlharPara;

/**
 * Mira o gol e chuta.
 *
 * <p>Duas skills em sequencia de verdade, e nao em paralelo: mirar e chutar sao
 * a mesma coisa acontecendo em ordem. Chutar antes de estar alinhado manda a bola
 * para a lateral, e o custo de esperar mais alguns quadros e baixo perto disso.
 *
 * <p>A tolerancia de mira e apertada porque a distancia amplifica o erro: a
 * 2,5 m do gol, dois graus de desvio ja deslocam a bola quase 9 cm no fundo da
 * rede, e o gol tem 1 m de largura.
 */
public final class ChutarAoGol extends Tactic {


    /** Erro de mira aceito antes de acionar o chutador. */
    public static final double TOLERANCIA_DE_MIRA = Math.toRadians(2);

    /**
     * Quanto tempo a bola precisa ficar no sensor antes de valer o chute.
     *
     * <p>Sem esta espera o robo chuta no instante em que a bola ENCOSTA, e isso
     * da errado de duas maneiras. Uma bola que so passa raspando pela boca aciona
     * o sensor por um quadro e leva um chute para lugar nenhum. E mesmo quando a
     * bola e nossa, ela chega a boca ainda quicando: chutar antes de o rolete
     * assentar manda a bola para qualquer lado menos o mirado.
     *
     * <p>Contado no relogio da visao, entao vale igual com o simulador acelerado.
     */
    public static final double POSSE_MINIMA = 0.15; // s

    /**
     * Acima desta velocidade a bola nao e nossa, em mm/s.
     *
     * <p>Bola que a gente conduz anda junto com o robo, devagar. Bola a 6 m/s
     * passando pela boca acabou de ser chutada -- inclusive por nos mesmos.
     *
     * <p>Sem esta condicao acontece um chute duplo: o chute sai, a play se
     * conclui, o coach reinicia a mesma play no quadro seguinte, e o dribbler
     * reengata na bola ANTES de ela escapar da boca. Ela e segurada de volta e
     * chutada outra vez, 0,18 s depois da primeira. Nao da para ver isso sem log,
     * porque na tela parece um chute so.
     */
    public static final double VEL_MAX_CONTROLE = 1500; // mm/s

    private final OlharPara olhar = new OlharPara();
    private final Chutar chutar = new Chutar();

    /** Instante em que a bola entrou no sensor, ou NaN se ela nao esta la. */
    private double dPosseDesde = Double.NaN;

    /**
     * O id vem de {@link Tatica}, e nao de quem registra a tatica.
     *
     * <p>O construtor que recebe {@code int} continua existindo so enquanto os
     * papeis ainda numeram por conta propria; ele pode sair assim que o ultimo
     * deles passar a usar este.
     */
    public ChutarAoGol() { this(Tatica.CHUTAR_AO_GOL.id()); }

    public ChutarAoGol(int _id) {
        super(_id);
        olhar.vSetTolerancia(TOLERANCIA_DE_MIRA);
        bAddSkill(Habilidade.OLHAR_PARA, olhar);
        bAddSkill(Habilidade.CHUTAR, chutar);
    }

    /** Velocidade do chute, em mm/s. */
    public void vSetVelocidade(double _mmPorSegundo) { chutar.vSetVelocidade(_mmPorSegundo); }

    @Override
    public String strName() { return "ChutarAoGol"; }

    @Override
    public void vInitialize() {
        bSetSkill(Habilidade.OLHAR_PARA);
        dPosseDesde = Double.NaN;
    }

    @Override
    protected void vRun() {
        olhar.vSetAlvo(ambiente.golDeles());

        boolean controlavel = ambiente.bola().rapidez() < VEL_MAX_CONTROLE;
        jogador.vDribbler(controlavel);

        if (!jogador.bComABola() || !controlavel) dPosseDesde = Double.NaN;
        else if (Double.isNaN(dPosseDesde)) dPosseDesde = ambiente.tempo();

        boolean mirado = Math.abs(olhar.dErro()) <= TOLERANCIA_DE_MIRA;
        boolean assentada = !Double.isNaN(dPosseDesde)
                && ambiente.tempo() - dPosseDesde >= POSSE_MINIMA;

        bSetSkill(mirado && assentada ? Habilidade.CHUTAR : Habilidade.OLHAR_PARA);
    }

    /** Segundos com a bola no sensor, ou zero. Util para o log. */
    public double dTempoDePosse() {
        if (Double.isNaN(dPosseDesde) || ambiente == null) return 0;
        return ambiente.tempo() - dPosseDesde;
    }

    /** Acaba quando o chute saiu, ou se a bola escapou antes disso. */
    @Override
    public void vCheckTacticFinished() {
        if (chutar.bIsFinished()) vFinalizar();
    }
}
