package view;

import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * A que taxa a janela redesenha.
 *
 * <p>Era 60 Hz chumbado, e num monitor de 144 ou 165 Hz isso aparece: a bola
 * atravessa o campo aos saltos mesmo com a maquina folgada, porque a tela e que
 * esta segurando, nao a simulacao.
 *
 * <p>Subir a taxa NAO mexe na fisica nem na estrategia, e isso e por construcao
 * dos dois lados. No simulador o relogio da tela so chama {@code ticksPendentes},
 * um acumulador de passo fixo: a fisica roda sempre em {@code dt}, e desenhar
 * mais vezes so faz o acumulador devolver zero mais vezes. Na estrategia o
 * {@code vDecidir} compara {@code frame()} com o do ultimo quadro decidido e sai
 * cedo quando nao mudou -- o time pensa na taxa da VISAO, nunca na da tela.
 *
 * <p>O sistema nem sempre informa a taxa. No macOS {@code getRefreshRate}
 * costuma devolver {@link DisplayMode#REFRESH_RATE_UNKNOWN}, e por isso o padrao
 * existe: sem ele a janela cairia para um intervalo absurdo e queimaria CPU a
 * toa.
 */
public final class TaxaDeTela {

    private TaxaDeTela() {}

    /** Usada quando o sistema nao informa a taxa do monitor. */
    public static final int PADRAO = 60;

    /**
     * Teto de seguranca.
     *
     * <p>Nao e para limitar monitor bom: e para uma taxa absurda vinda de um
     * driver estranho nao virar um Timer de 1 ms que ocupa um nucleo inteiro
     * redesenhando um campo que ninguem consegue ver mudar tao rapido.
     */
    public static final int MAX = 240;

    /**
     * Taxa do monitor em que a janela esta, ou {@link #PADRAO} se nao der para
     * saber. Com {@code null}, a do monitor principal.
     */
    public static int doMonitor(Window janela) {
        try {
            GraphicsConfiguration gc = janela == null ? null : janela.getGraphicsConfiguration();
            GraphicsDevice tela = gc != null
                    ? gc.getDevice()
                    : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            int hz = tela.getDisplayMode().getRefreshRate();
            return hz == DisplayMode.REFRESH_RATE_UNKNOWN || hz <= 0
                    ? PADRAO
                    : Math.min(hz, MAX);
        } catch (Exception ignorado) {
            // Ambiente sem tela, driver que nao responde: desenhar a 60 e melhor
            // que nao abrir a janela.
            return PADRAO;
        }
    }

    /**
     * Taxa a usar: {@code pedida} quando positiva, senao a do monitor.
     *
     * <p>Zero quer dizer "segue o monitor", e nao "nao desenha" -- e o valor de
     * fabrica do ajuste, para quem nunca mexer nele ganhar a taxa certa sozinho.
     */
    public static int escolhida(int pedida, Window janela) {
        return pedida > 0 ? Math.min(pedida, MAX) : doMonitor(janela);
    }

    /** Intervalo de Timer, em ms, para uma taxa em Hz. Nunca menor que 1. */
    public static int intervalo(int hz) {
        return Math.max(1, Math.round(1000f / Math.max(1, hz)));
    }
}
