package estrategia.skills;

import estrategia.Skill;
import model.Robo;

/**
 * Aciona o chutador.
 *
 * <p>So chuta com a bola no sensor. Mandar chutar sem bola gasta a carga do
 * capacitor e nao produz nada, e como o comando de chute vai em todo quadro
 * enquanto a skill estiver ativa, um chute solto viraria uma sequencia de chutes
 * a vazio.
 *
 * <p>Ela nao mira. Mirar e girar o robo, ou seja {@link OlharPara}, e quem decide
 * que a mira ja esta boa o suficiente e a tatica. Uma skill que fizesse as duas
 * coisas nao poderia ser usada para tocar a bola de leve sem mirar.
 */
public final class Chutar extends Skill {

    private double dVelocidade = Robo.VEL_CHUTE_MAX;
    private boolean bChip;

    public Chutar() { }

    public Chutar(double _velocidade) { this.dVelocidade = _velocidade; }

    /** Velocidade de saida da bola, em mm/s. O teto da regra e 6500. */
    public void vSetVelocidade(double _mmPorSegundo) {
        this.dVelocidade = Math.min(_mmPorSegundo, Robo.VEL_CHUTE_MAX);
    }

    /** Liga o chute alto. */
    public void vSetChip(boolean _chip) { this.bChip = _chip; }

    @Override
    public String strName() { return "Chutar"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        if (!jogador.bComABola()) return; // espera a bola encostar

        if (bChip) jogador.vChutarAlto(dVelocidade);
        else jogador.vChutar(dVelocidade);

        // O dribbler tem de largar, senao ele segura a bola que o chutador
        // acabou de mandar embora.
        jogador.vDribbler(false);
        vFinalizar();
    }
}
