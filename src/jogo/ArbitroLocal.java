package jogo;

import proto.gc.SslGcRefereeMessage.Referee;

/**
 * Fabrica mensagens de arbitro para testar sem subir o Game Controller.
 *
 * <p>Monta um {@link Referee} de verdade, e nao um atalho que injeta o estado ja
 * traduzido. E o mesmo objeto que chegaria pela rede, entao o caminho de
 * traducao exercitado no teste e exatamente o que vai rodar no jogo: se um
 * comando estiver mapeado errado, aparece aqui.
 *
 * <p>Nao publica nada na rede. Um segundo publicador no multicast oficial
 * brigaria com o Game Controller de verdade e confundiria qualquer outro time na
 * mesma bancada.
 */
public final class ArbitroLocal {

    private int contador;
    private int golsAzul, golsAmarelo;
    private boolean azulNoLadoPositivo = true;
    private String nomeAzul = "Azul";
    private String nomeAmarelo = "Amarelo";
    private int goleiroAzul = 0, goleiroAmarelo = 0;

    /**
     * Ultimo comando pedido, para reemitir.
     *
     * <p>Goleiro e lado do campo nao sao comandos: sao declaracoes que viajam
     * DENTRO de toda mensagem de arbitro. Trocar um deles so chega em quem
     * escuta na proxima mensagem, e sem guardar o comando corrente a unica forma
     * de reemitir seria inventar um STOP -- o que interromperia o jogo por causa
     * de uma mudanca de configuracao.
     */
    private Referee.Command ultimo = Referee.Command.HALT;

    public void setNomes(String azul, String amarelo) {
        this.nomeAzul = azul;
        this.nomeAmarelo = amarelo;
    }

    public void setAzulNoLadoPositivo(boolean b) { this.azulNoLadoPositivo = b; }
    public boolean isAzulNoLadoPositivo()        { return azulNoLadoPositivo; }

    /** Numero do goleiro declarado por uma das equipes. */
    public void setGoleiro(model.Cor cor, int id) {
        if (cor == model.Cor.AZUL) goleiroAzul = id; else goleiroAmarelo = id;
    }

    public int goleiro(model.Cor cor) {
        return cor == model.Cor.AZUL ? goleiroAzul : goleiroAmarelo;
    }

    /** Repete o ultimo comando, para uma declaracao trocada entrar em vigor. */
    public Referee reemitir() { return comando(ultimo); }

    public void marcarGol(model.Cor cor) {
        if (cor == model.Cor.AZUL) golsAzul++; else golsAmarelo++;
    }

    public void zerarPlacar() { golsAzul = 0; golsAmarelo = 0; }

    /**
     * Monta a mensagem do comando pedido.
     *
     * <p>O {@code command_counter} anda a cada chamada, como no arbitro de
     * verdade: e assim que quem recebe distingue "mandou STOP de novo" de "o
     * STOP de antes continua valendo".
     */
    public Referee comando(Referee.Command comando) {
        this.ultimo = comando;
        long agora = System.currentTimeMillis() * 1000; // o protocolo usa microssegundos
        return Referee.newBuilder()
                .setPacketTimestamp(agora)
                .setStage(Referee.Stage.NORMAL_FIRST_HALF)
                .setStageTimeLeft(300_000_000L) // 5 min, so para a barra ter o que mostrar
                .setCommand(comando)
                .setCommandCounter(++contador)
                .setCommandTimestamp(agora)
                .setBlueTeamOnPositiveHalf(azulNoLadoPositivo)
                .setBlue(time(nomeAzul, golsAzul, goleiroAzul))
                .setYellow(time(nomeAmarelo, golsAmarelo, goleiroAmarelo))
                .build();
    }

    /** Todos os campos de {@code TeamInfo} sao required no proto2; nenhum pode faltar. */
    private static Referee.TeamInfo time(String nome, int gols, int goleiro) {
        return Referee.TeamInfo.newBuilder()
                .setName(nome)
                .setScore(gols)
                .setRedCards(0)
                .setYellowCards(0)
                .setTimeouts(4)
                .setTimeoutTime(300_000_000)
                .setGoalkeeper(goleiro)
                .build();
    }
}
