package rede;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * De onde a estrategia escuta e para onde ela manda.
 *
 * <p>Os padroes sao os oficiais da liga, os mesmos que a {@code ssl-vision} e o
 * {@code grSim} usam -- com eles a estrategia conecta no simulador sem
 * configurar nada.
 *
 * <p>Note a assimetria: a visao chega por MULTICAST, porque e uma transmissao
 * para quem quiser ouvir, e os comandos saem por UNICAST direto para o host do
 * simulador, porque tem um destinatario so. Por isso a entrada tem grupo e a
 * saida tem host.
 *
 * @param grupoVisao   grupo multicast onde a visao e publicada
 * @param portaVisao   porta da visao
 * @param hostComandos host que roda o simulador (ou o robo real, em campo)
 * @param portaAzul    porta de {@code RobotControl} da equipe azul
 * @param portaAmarelo porta de {@code RobotControl} da equipe amarela
 */
public record ConfigRede(String grupoVisao, int portaVisao,
                         String hostComandos, int portaAzul, int portaAmarelo) {

    public static final String VISAO_GRUPO = "224.5.23.2";
    public static final int VISAO_PORTA = 10006;
    public static final String HOST_PADRAO = "127.0.0.1";
    public static final int CONTROLE_AZUL_PORTA = 10301;
    public static final int CONTROLE_AMARELO_PORTA = 10302;

    /** Datagrama maximo tratado; um quadro de visao cabe folgado. */
    public static final int TAMANHO_BUFFER = 8192;

    public static ConfigRede padrao() {
        return new ConfigRede(VISAO_GRUPO, VISAO_PORTA, HOST_PADRAO,
                CONTROLE_AZUL_PORTA, CONTROLE_AMARELO_PORTA);
    }

    public ConfigRede comGrupo(String g)      { return new ConfigRede(g, portaVisao, hostComandos, portaAzul, portaAmarelo); }
    public ConfigRede comPortaVisao(int p)    { return new ConfigRede(grupoVisao, p, hostComandos, portaAzul, portaAmarelo); }
    public ConfigRede comHost(String h)       { return new ConfigRede(grupoVisao, portaVisao, h, portaAzul, portaAmarelo); }
    public ConfigRede comPortaAzul(int p)     { return new ConfigRede(grupoVisao, portaVisao, hostComandos, p, portaAmarelo); }
    public ConfigRede comPortaAmarelo(int p)  { return new ConfigRede(grupoVisao, portaVisao, hostComandos, portaAzul, p); }

    /** Porta de comando da equipe indicada. */
    public int portaDe(model.Cor cor) {
        return cor == model.Cor.AZUL ? portaAzul : portaAmarelo;
    }

    /**
     * Explica por que a configuracao e invalida, ou {@code null} se estiver boa.
     *
     * <p>Mesma divisao de trabalho que o simulador faz, e pelo mesmo motivo: o
     * que da para saber sem tocar no sistema operacional e rejeitado aqui, na
     * hora em que a pessoa digita. Um endereco fora da faixa multicast nao daria
     * erro nenhum ao abrir socket -- simplesmente nunca chegaria pacote, e o
     * sintoma seria uma tela parada sem explicacao. Ja "porta ocupada" so aparece
     * no bind, e e tratada la.
     */
    public String problema() {
        if (grupoVisao == null || grupoVisao.isBlank()) return "informe o grupo multicast da visao";
        try {
            if (!InetAddress.getByName(grupoVisao.trim()).isMulticastAddress()) {
                return grupoVisao + " nao e multicast (faixa 224.0.0.0 a 239.255.255.255)";
            }
        } catch (UnknownHostException e) {
            return "endereco de visao invalido: " + grupoVisao;
        }

        if (hostComandos == null || hostComandos.isBlank()) return "informe o host do simulador";
        try {
            InetAddress.getByName(hostComandos.trim());
        } catch (UnknownHostException e) {
            return "host de comandos desconhecido: " + hostComandos;
        }

        String[] nomes = {"visao", "robos azul", "robos amarelo"};
        int[] portas = {portaVisao, portaAzul, portaAmarelo};
        for (int i = 0; i < portas.length; i++) {
            if (portas[i] < 1024 || portas[i] > 65535) {
                return "porta de " + nomes[i] + " fora da faixa 1024-65535: " + portas[i];
            }
        }
        if (portaAzul == portaAmarelo) {
            return "azul e amarelo nao podem mandar na mesma porta (" + portaAzul + ")";
        }
        return null;
    }
}
