package rede;

import model.Comando;
import model.Cor;
import proto.sim.SslSimulationRobotControl.MoveLocalVelocity;
import proto.sim.SslSimulationRobotControl.RobotControl;
import proto.sim.SslSimulationRobotControl.RobotMoveCommand;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Manda {@code RobotControl} para o simulador -- a unica saida de atuacao.
 *
 * <p>Um pacote por ciclo com TODOS os robos dentro, e nao um pacote por robo: e
 * assim que a mensagem foi desenhada ({@code repeated robot_commands}) e evita
 * que metade da equipe ande com um quadro de atraso em relacao a outra metade.
 *
 * <h2>Onde as unidades trocam</h2>
 *
 * <p>Aqui, e so aqui. Dentro do programa tudo e mm, radianos e segundos, como a
 * visao entrega; o {@code ssl_simulation_protocol} fala metros e graus. Errar
 * essa conversao da mil vezes de diferenca sem estourar excecao em lugar
 * nenhum -- o robo simplesmente sai voando ou nao sai do lugar.
 *
 * <p>O dribbler tambem muda de tipo: booleano aqui, RPM no protocolo. O valor
 * enviado quando ligado e uma velocidade plausivel de rolete, ja que o
 * simulador so olha se e maior que zero.
 */
public final class EmissorDeComandos implements AutoCloseable {

    private static final double MM_POR_M = 1000.0;

    /** RPM mandado quando o dribbler esta ligado. */
    private static final float DRIBBLER_RPM = 1000f;

    private final DatagramSocket socket;
    private final InetSocketAddress destino;
    private volatile long pacotesEnviados;

    public EmissorDeComandos(ConfigRede config, Cor equipe) throws IOException {
        this.socket = new DatagramSocket();
        this.destino = new InetSocketAddress(
                InetAddress.getByName(config.hostComandos().trim()), config.portaDe(equipe));
    }

    public long getPacotesEnviados() { return pacotesEnviados; }
    public InetSocketAddress getDestino() { return destino; }

    /** Envia um ciclo de comandos, indexados por id de robo. */
    public void enviar(Map<Integer, Comando> comandos) throws IOException {
        if (comandos.isEmpty()) return;

        RobotControl.Builder rc = RobotControl.newBuilder();
        for (Map.Entry<Integer, Comando> e : comandos.entrySet()) {
            rc.addRobotCommands(traduzir(e.getKey(), e.getValue()));
        }

        byte[] dados = rc.build().toByteArray();
        socket.send(new DatagramPacket(dados, dados.length, destino));
        pacotesEnviados++;
    }

    private static proto.sim.SslSimulationRobotControl.RobotCommand traduzir(int id, Comando c) {
        var b = proto.sim.SslSimulationRobotControl.RobotCommand.newBuilder()
                .setId(id)
                .setMoveCommand(RobotMoveCommand.newBuilder()
                        .setLocalVelocity(MoveLocalVelocity.newBuilder()
                                .setForward((float) (c.velTangencial() / MM_POR_M))
                                .setLeft((float) (c.velNormal() / MM_POR_M))
                                .setAngular((float) c.velAngular())));

        if (c.temChute()) {
            b.setKickSpeed((float) (c.velChute() / MM_POR_M));
            b.setKickAngle((float) Math.toDegrees(c.anguloChute()));
        }
        if (c.dribbler()) b.setDribblerSpeed(DRIBBLER_RPM);
        return b.build();
    }

    @Override
    public void close() { socket.close(); }
}
