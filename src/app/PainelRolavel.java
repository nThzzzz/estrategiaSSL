package app;

import javax.swing.JPanel;
import javax.swing.Scrollable;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Coluna lateral que acompanha a largura da rolagem em que estiver.
 *
 * <p>Um painel comum dentro de um {@code JScrollPane} fica na largura PREFERIDA
 * dele, e nao na do viewport. Enquanto as colunas tinham largura fixa isso nao
 * aparecia; agora que o divisor e arrastavel, alargar a coluna deixaria o
 * conteudo desenhado na largura antiga, com uma faixa vazia do lado.
 *
 * <p>A altura NAO acompanha, de proposito: e justamente ela ultrapassar o
 * viewport que faz a barra de rolagem aparecer.
 */
public abstract class PainelRolavel extends JPanel implements Scrollable {

    @Override
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

    @Override
    public boolean getScrollableTracksViewportWidth() { return true; }

    @Override
    public boolean getScrollableTracksViewportHeight() { return false; }

    @Override
    public int getScrollableUnitIncrement(Rectangle visivel, int orientacao, int direcao) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visivel, int orientacao, int direcao) {
        return visivel.height;
    }
}
