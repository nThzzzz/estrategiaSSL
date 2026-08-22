package estrategia;

import model.Comando;
import mundo.EstadoRobo;

/**
 * Um robo nosso, do ponto de vista de quem o comanda.
 *
 * <p>Equivale ao {@code Robo} que o SSL-Strategy passa para Skill, Tactic e Role.
 * Junta as duas metades que estavam separadas: o que a visao diz dele
 * ({@link EstadoRobo}, imutavel e vindo do Kalman) e o {@link Comando} que ele
 * vai receber neste tique.
 *
 * <p>O comando e acumulado aqui e nao devolvido por {@code vRun} de proposito.
 * Uma Skill costuma escrever so uma parte dele -- andar sem mexer no chute,
 * ligar o dribbler sem mexer no movimento -- e uma cadeia de skills precisa
 * conseguir compor sem que a ultima apague o que as anteriores pediram. Por isso
 * existem {@link #vMover}, {@link #vChutar} e {@link #vDribbler} separados, em
 * vez de um unico setter do comando inteiro.
 *
 * <p>O comando e zerado no comeco de cada tique. Sem isso, uma skill que parou de
 * pedir chute continuaria chutando para sempre.
 */
public final class Jogador {

    private final int id;
    private EstadoRobo estado;
    private Comando comando = Comando.PARADO;

    public Jogador(int id) { this.id = id; }

    public int id() { return id; }

    /** O que a visao sabe dele, ou {@code null} se sumiu do campo. */
    public EstadoRobo estado() { return estado; }

    public boolean bEmCampo() { return estado != null; }

    /** True quando a bola esta cortando o feixe da boca deste robo. */
    public boolean bComABola() { return estado != null && estado.bolaNoSensor(); }

    public Comando comando() { return comando; }

    // ------------------------------------------------------ escrita por tique

    /** Chamado pelo executor no inicio do tique. */
    void vAtualizar(EstadoRobo novo) {
        this.estado = novo;
        this.comando = Comando.PARADO;
    }

    /** Velocidades no referencial local do robo: frente, esquerda, giro. */
    public void vMover(double velTangencial, double velNormal, double velAngular) {
        comando = new Comando(velTangencial, velNormal, velAngular,
                comando.velChute(), comando.anguloChute(), comando.dribbler());
    }

    public void vParar() { vMover(0, 0, 0); }

    /** Chute rasteiro, em mm/s. */
    public void vChutar(double velocidade) { comando = comando.comChute(velocidade); }

    /** Chute alto, em mm/s, na elevacao padrao de chip. */
    public void vChutarAlto(double velocidade) { comando = comando.comChip(velocidade); }

    public void vDribbler(boolean ligado) { comando = comando.comDribbler(ligado); }

    /** Substitui o comando inteiro. Use os metodos especificos quando puder. */
    public void vSetComando(Comando novo) { this.comando = novo; }
}
