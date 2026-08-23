package estrategia.tactics;

import core.Vec2;
import estrategia.esqueleto.Tactic;
import estrategia.ids.Tatica;

public final class TacticAndarPara extends Tactic {
    private Vec2 posicao;

    public TacticAndarPara(int _id){
        super(_id);

    }

    @Override
    public String strName() {
        return "Andar Para";
    }

    @Override
    public void vInitialize() {

    }

    @Override
    protected void vRun() {

    }
}
