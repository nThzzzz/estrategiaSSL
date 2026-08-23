package estrategia.skills;

import core.Angulo;
import core.Vec2;
import estrategia.esqueleto.Skill;
import model.Robo;

/**
 * Gira o robo ate ele encarar um ponto.
 *
 * <p>Escreve so a parte ANGULAR do comando, entao roda junto com
 * {@link SkillAndarPara} sem conflito.
 *
 * <p>O erro passa por {@link Angulo#diferenca}, que devolve o menor giro com
 * sinal. Sem isso, um robo a 170 graus do alvo daria a volta pelo caminho longo,
 * e um que cruzasse 180 graus pareceria girar uma volta inteira.
 *
 * <p>A frenagem segue a mesma ideia da skill de andar: a velocidade angular
 * permitida e a maior que ainda deixa o robo PARAR encarando o alvo, em vez de
 * um proporcional que oscila em torno dele.
 */
public final class SkillOlharPara extends Skill {

    private static final double FATOR_DE_SEGURANCA = 0.8;

    /** Erro angular aceito, em radianos. Cerca de 2 graus. */
    private double dTolerancia = Math.toRadians(2);

    private Vec2 alvo = Vec2.ZERO;

    public SkillOlharPara() { }

    public SkillOlharPara(Vec2 _alvo) { this.alvo = _alvo; }

    public void vSetAlvo(Vec2 _alvo) { this.alvo = _alvo; }

    public void vSetTolerancia(double _radianos) { this.dTolerancia = _radianos; }

    /** Erro angular corrente, em radianos. Util para a tatica decidir se pode chutar. */
    public double dErro() {
        if (jogador == null || !jogador.bEmCampo()) return Math.PI;
        Vec2 para = alvo.menos(jogador.estado().posicao());
        if (para.norma() < 1e-6) return 0;
        return Angulo.diferenca(para.angulo(), jogador.estado().theta());
    }

    @Override
    public String strName() { return "SkillOlharPara"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        double erro = dErro();
        if (Math.abs(erro) <= dTolerancia) {
            jogador.vGirar(0);
            vFinalizar();
            return;
        }

        double omega = Math.min(Robo.OMEGA_MAX,
                Math.sqrt(2 * Robo.ACEL_ANGULAR_MAX * Math.abs(erro)) * FATOR_DE_SEGURANCA);
        jogador.vGirar(Math.signum(erro) * omega);
    }
}
