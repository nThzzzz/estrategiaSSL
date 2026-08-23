package estrategia.skills;

import ajuste.Parametro;
import core.Vec2;
import estrategia.esqueleto.Skill;
import model.Robo;

/**
 * Leva o robo ate um ponto.
 *
 * <p>Escreve so a parte LINEAR do comando. Quem cuida da orientacao e
 * {@link SkillOlharPara}, e as duas rodam no mesmo tique sem se atrapalhar: um robo
 * omnidirecional anda para um lado enquanto olha para outro, e separar as duas
 * skills e o que permite escrever isso naturalmente.
 *
 * <h2>Por que nao e um proporcional puro</h2>
 *
 * <p>Um {@code v = k * erro} ou chega devagar demais de longe, ou passa do ponto
 * de perto. O que se usa aqui e o perfil de frenagem: a velocidade permitida e a
 * maior que ainda deixa o robo PARAR em cima do alvo,
 * {@code v = sqrt(2 * a * distancia)}, saturada no teto do robo. De longe ele vai
 * no maximo; chegando perto, a propria formula freia na taxa certa.
 *
 * <p>O fator de seguranca desconta o atraso entre decidir e obedecer. Sem ele o
 * robo pede exatamente a velocidade limite de frenagem, e como o comando chega
 * alguns milissegundos depois, ele passa do ponto e volta -- o famoso robo que
 * fica bailando em cima do alvo.
 */
public final class SkillAndarPara extends Skill {

    /**
     * Tolerancia pedida pela tatica, em mm; {@code null} usa
     * {@link Parametro#TOLERANCIA_DE_CHEGADA}.
     *
     * <p>Guardar o valor do parametro num campo no construtor faria uma troca em
     * tempo de execucao valer so para as skills criadas depois dela.
     */
    private Double dTolerancia;

    private Vec2 destino = Vec2.ZERO;
    private double dVelMax = Robo.VEL_MAX;

    public SkillAndarPara() { }

    public SkillAndarPara(Vec2 _destino) { this.destino = _destino; }

    /** Troca o destino. A tatica chama isto a cada tique quando o alvo se move. */
    public void vSetDestino(Vec2 _destino) { this.destino = _destino; }

    public void vSetTolerancia(double _mm) { this.dTolerancia = _mm; }

    /** A tolerancia que vale agora: a pedida, ou a do ajuste global. */
    private double dTolerancia() {
        return dTolerancia != null ? dTolerancia : Parametro.TOLERANCIA_DE_CHEGADA.valor();
    }

    /** Limita a velocidade desta skill, para conducao ou aproximacao delicada. */
    public void vSetVelMax(double _mmPorSegundo) { this.dVelMax = _mmPorSegundo; }

    public Vec2 destino() { return destino; }

    @Override
    public String strName() { return "SkillAndarPara"; }

    @Override
    public void vInitialize() { }

    @Override
    protected void vRun() {
        Vec2 posicao = jogador.estado().posicao();
        Vec2 erro = destino.menos(posicao);
        double distancia = erro.norma();

        if (distancia <= dTolerancia()) {
            jogador.vMover(0, 0);
            vFinalizar();
            return;
        }

        double velocidade = Math.min(dVelMax,
                Math.sqrt(2 * Robo.ACEL_MAX * distancia) * Parametro.FRENAGEM_LINEAR.valor());

        // O comando vai no referencial do robo, que e onde o firmware o executa.
        Vec2 global = erro.comNorma(velocidade);
        Vec2 local = global.paraLocal(jogador.estado().theta());
        jogador.vMover(local.x(), local.y());
    }

    @Override
    public void vResetSkill() {
        super.vResetSkill();
        dVelMax = Robo.VEL_MAX;
        dTolerancia = null;
    }
}
