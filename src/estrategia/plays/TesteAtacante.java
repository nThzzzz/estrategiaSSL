package estrategia.plays;

import estrategia.Play;
import estrategia.roles.Atacante;

/**
 * Jogada minima de bancada: um robo busca a bola, conduz e chuta.
 *
 * <p>Existe para provar que o encanamento inteiro funciona, do
 * {@code Coach} ate o {@code Comando} no fio: um so papel, sem passe, sem
 * marcacao e sem desvio de obstaculo. Se ela faz gol contra um campo vazio, as
 * camadas estao conversando.
 *
 * <p>A nota e fixa. Nao ha o que aprender com uma jogada so -- a roleta do coach
 * escolhe entre alternativas, e aqui nao existem. Quando houver uma segunda play,
 * {@link #vUpdateScore()} passa a ter sentido: e onde o resultado do episodio
 * vira reforco.
 */
public final class TesteAtacante extends Play {

    private static final int ROLE_ATACANTE = 1;

    private double dScore = 1.0;

    public TesteAtacante(int _id) {
        super(_id);
        bAddRole(new Atacante(ROLE_ATACANTE));
        vSetTempoLimite(30);
    }

    @Override
    public String strName() { return "TesteAtacante"; }

    @Override
    public void vInitialize() { }

    /** Precisa da bola em campo e do arbitro deixando jogar. */
    @Override
    public boolean bCheckPreConditions() {
        return ambiente != null
                && !ambiente.bola().perdida()
                && ambiente.jogo().bolaEmJogo()
                && !ambiente.nossos().isEmpty();
    }

    /**
     * Aborta se a bola sumiu da visao ou se o arbitro parou o jogo.
     *
     * <p>Nao aborta por "a bola escapou do dribbler": perder a bola faz parte da
     * jogada, e o {@link Atacante} volta sozinho para a tatica de buscar.
     */
    @Override
    public boolean bCheckEndConditions() {
        return ambiente == null
                || ambiente.bola().perdida()
                || !ambiente.jogo().bolaEmJogo();
    }

    @Override
    public double dGetScore() { return dScore; }

    @Override
    public void vUpdateScore() { }

    @Override
    public void vSaveScore() { }
}
