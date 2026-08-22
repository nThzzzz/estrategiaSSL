package app;

import model.Comando;
import model.Cor;
import mundo.Quadro;
import percepcao.Rastreador;
import rede.ConfigRede;
import rede.EmissorDeComandos;
import rede.ReceptorDeVisao;

import java.io.IOException;
import java.util.Map;

/**
 * Presenca da estrategia na rede: escuta a visao, manda comando.
 *
 * <p>Uma entrada e uma saida, ao contrario do simulador, que tem uma saida e
 * tres entradas. E a mesma conversa vista do outro lado.
 *
 * <p>Concentra o estado que a janela inteira compartilha -- configuracao, cor da
 * equipe, nomes -- para que os paineis nao precisem se conhecer. O painel da
 * esquerda troca a configuracao, o de cima le os nomes e o da direita le o
 * quadro, e nenhum dos tres tem referencia para os outros.
 *
 * <p>Os nomes das equipes ficam aqui e nao chegam pela rede porque o protocolo de
 * visao nao os carrega: {@code SSL_DetectionFrame} tem robos azuis e robos
 * amarelos, sem nome nenhum. Em competicao os nomes vem do Game Controller, que
 * o simulador ainda nao publica; ate la, sao configuracao local.
 */
public final class Cliente implements AutoCloseable {

    private final Rastreador rastreador = new Rastreador();

    private ConfigRede config;
    private Cor equipe;
    private String nomeAzul = "RoboFEI";
    private String nomeAmarelo = "Adversario";

    private ReceptorDeVisao receptor;
    private EmissorDeComandos emissor;
    private String falha;

    public Cliente(ConfigRede config, Cor equipe) {
        this.config = config;
        this.equipe = equipe;
    }

    // ------------------------------------------------------------- estado

    public ConfigRede getConfig()   { return config; }
    public Cor getEquipe()          { return equipe; }
    public Cor getAdversario()      { return equipe.oposta(); }
    public String getNomeAzul()     { return nomeAzul; }
    public String getNomeAmarelo()  { return nomeAmarelo; }
    public String getNome(Cor c)    { return c == Cor.AZUL ? nomeAzul : nomeAmarelo; }

    public void setNomes(String azul, String amarelo) {
        this.nomeAzul = azul == null || azul.isBlank() ? "Azul" : azul.trim();
        this.nomeAmarelo = amarelo == null || amarelo.isBlank() ? "Amarelo" : amarelo.trim();
    }

    /** Ultima falha de rede, ou {@code null} se esta tudo bem. */
    public String getFalha() { return falha; }

    public Quadro quadro() {
        return receptor != null ? receptor.ultimoQuadro() : rastreador.atual();
    }

    public boolean recebendo()      { return receptor != null && receptor.recebendo(); }
    public double silencio()        { return receptor == null ? Double.POSITIVE_INFINITY : receptor.silencio(); }
    public long pacotesRecebidos()  { return receptor == null ? 0 : receptor.getPacotesRecebidos(); }
    public long pacotesGeometria()  { return receptor == null ? 0 : receptor.getPacotesGeometria(); }
    public long pacotesEnviados()   { return emissor == null ? 0 : emissor.getPacotesEnviados(); }
    public double taxaHz()          { return receptor == null ? 0 : receptor.taxaHz(); }

    public String destinoDeComandos() {
        return emissor == null ? "--"
                : emissor.getDestino().getHostString() + ":" + emissor.getDestino().getPort();
    }

    // -------------------------------------------------------------- rede

    /** Abre a rede com a configuracao atual. */
    public void conectar() throws IOException {
        abrir(config, equipe);
    }

    /**
     * Troca configuracao e/ou cor da equipe com a janela aberta.
     *
     * <p>Se a nova configuracao falhar -- porta ocupada por outro processo, que e
     * o caso comum quando ja existe uma instancia rodando -- a anterior e
     * restaurada. Deixar a estrategia surda porque alguem digitou uma porta
     * errada seria pior do que ignorar a troca.
     */
    public synchronized void reconfigurar(ConfigRede nova, Cor novaEquipe) throws IOException {
        String problema = nova.problema();
        if (problema != null) throw new IOException(problema);
        if (nova.equals(config) && novaEquipe == equipe) return;

        ConfigRede anterior = config;
        Cor equipeAnterior = equipe;
        fechar();
        try {
            abrir(nova, novaEquipe);
        } catch (IOException erro) {
            try {
                abrir(anterior, equipeAnterior);
                throw new IOException(erro.getMessage()
                        + ". A configuracao anterior foi mantida.", erro);
            } catch (IOException perdida) {
                throw new IOException(erro.getMessage()
                        + ". E a anterior tambem nao voltou: " + perdida.getMessage(), erro);
            }
        }
    }

    private void abrir(ConfigRede c, Cor cor) throws IOException {
        try {
            receptor = new ReceptorDeVisao(c, rastreador);
            emissor = new EmissorDeComandos(c, cor);
            this.config = c;
            this.equipe = cor;
            this.falha = null;
        } catch (IOException e) {
            fechar();
            this.falha = e.getMessage();
            throw e;
        }
    }

    /**
     * Manda um ciclo de comandos para a nossa equipe.
     *
     * <p>Nao existe ainda quem produza esses comandos: sem estrategia escrita, o
     * mapa chega vazio e nada e enviado -- os robos ficam parados, do mesmo jeito
     * que ficam no simulador quando ninguem esta conectado.
     */
    public void enviar(Map<Integer, Comando> comandos) {
        if (emissor == null) return;
        try {
            emissor.enviar(comandos);
            falha = null;
        } catch (IOException e) {
            falha = "envio: " + e.getMessage();
        }
    }

    private void fechar() {
        if (receptor != null) { receptor.close(); receptor = null; }
        if (emissor != null)  { emissor.close();  emissor = null; }
    }

    @Override
    public void close() { fechar(); }
}
