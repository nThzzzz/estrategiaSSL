package estrategia.roles;

import estrategia.Ambiente;
import estrategia.Jogador;
import estrategia.Papel;
import estrategia.Role;
import estrategia.Tatica;
import estrategia.tactics.BuscarBola;
import estrategia.tactics.ChutarAoGol;
import estrategia.tactics.ConduzirAoGol;

/**
 * O robo que vai atras da bola e tenta o gol.
 *
 * <p>Tres taticas em sequencia, e a escolha entre elas nao depende de lembrar em
 * que fase a jogada esta: depende do MUNDO. Sem a bola, buscar; com a bola longe
 * do gol, conduzir; com a bola perto, chutar. Uma maquina de estados com memoria
 * ficaria presa em "conduzindo" no instante em que a bola escapasse, e o robo
 * continuaria empurrando o ar ate alguem perceber.
 *
 * <p>O custo de atribuicao e a distancia ate a bola, sem previsao nenhuma. E
 * grosseiro de proposito: quando alguem esta em movimento, o robo mais proximo
 * pode estar indo para o outro lado, e o custo certo seria tempo ate um ponto de
 * encontro. Fica para quando a previsao de bola existir.
 */
public final class Atacante extends Role {

    private final BuscarBola buscar = new BuscarBola();
    private final ConduzirAoGol conduzir = new ConduzirAoGol();
    private final ChutarAoGol chutar = new ChutarAoGol();

    public Atacante(Papel _id) {
        super(_id);
        bAddTactic(Tatica.BUSCAR_BOLA, buscar);
        bAddTactic(Tatica.CONDUZIR_AO_GOL, conduzir);
        bAddTactic(Tatica.CHUTAR_AO_GOL, chutar);
    }

    @Override
    public String strName() { return "Atacante"; }

    @Override
    public void vInitialize() { bSetTactic(Tatica.BUSCAR_BOLA); }

    @Override
    protected void vRun() {
        if (!jogador.bComABola()) {
            bSetTactic(Tatica.BUSCAR_BOLA);
            return;
        }

        double aoGol = jogador.estado().posicao().distancia(ambiente.golDeles());
        bSetTactic(aoGol <= ConduzirAoGol.DISTANCIA_DE_CHUTE
                ? Tatica.CHUTAR_AO_GOL : Tatica.CONDUZIR_AO_GOL);
    }

    @Override
    public double dCusto(Ambiente _ambiente, Jogador _candidato) {
        if (_candidato == null || !_candidato.bEmCampo()) return Double.POSITIVE_INFINITY;

        // Quem ja esta com a bola e o atacante obvio, custe o que custar em
        // distancia: tirar o papel de quem tem a posse largaria a bola.
        if (_candidato.bComABola()) return 0;

        return _candidato.estado().posicao().distancia(_ambiente.bola().posicao());
    }

    /** O papel se cumpre quando o chute sai. */
    @Override
    public void vCheckRoleFinished() {
        if (chutar.bIsFinished()) vFinalizar();
    }
}
