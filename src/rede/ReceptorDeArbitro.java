package rede;

import jogo.Arbitro;
import proto.gc.SslGcRefereeMessage.Referee;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;

/**
 * Escuta o Game Controller.
 *
 * <p>Segundo multicast, separado do da visao: a visao vem da {@code ssl-vision}
 * (ou do simulador no lugar dela) e o arbitro vem do {@code ssl-game-controller},
 * que sao programas diferentes, em enderecos diferentes. Nada obriga os dois a
 * estarem no ar ao mesmo tempo, e a janela precisa continuar util com so um deles.
 *
 * <p>Mesmo cuidado do {@link ReceptorDeVisao} com as interfaces: o ingresso no
 * grupo e tentado em todas as que suportam multicast, porque a rota padrao quase
 * nunca e a que recebe o trafego.
 */
public final class ReceptorDeArbitro implements AutoCloseable {

    private final MulticastSocket socket;
    private final InetSocketAddress grupo;
    private final Arbitro arbitro;
    private final Thread thread;

    public ReceptorDeArbitro(ConfigRede config, Arbitro arbitro) throws IOException {
        this.arbitro = arbitro;
        this.socket = new MulticastSocket(config.portaArbitro());
        this.socket.setReuseAddress(true);
        this.grupo = new InetSocketAddress(
                InetAddress.getByName(config.grupoArbitro().trim()), config.portaArbitro());

        boolean alguma = false;
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || !ni.supportsMulticast()) continue;
            try { socket.joinGroup(grupo, ni); alguma = true; } catch (IOException ignorado) { }
        }
        if (!alguma) {
            socket.close();
            throw new IOException("nenhuma interface aceitou o grupo do arbitro "
                    + config.grupoArbitro());
        }

        this.thread = new Thread(this::escutar, "ssl-arbitro");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void escutar() {
        byte[] buffer = new byte[ConfigRede.TAMANHO_BUFFER];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p);
                arbitro.daRede(Referee.parseFrom(Arrays.copyOf(p.getData(), p.getLength())));
            } catch (IOException e) {
                if (!socket.isClosed()) System.err.println("arbitro: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        try { socket.leaveGroup(grupo, null); } catch (IOException ignorado) { }
        socket.close();
    }
}
