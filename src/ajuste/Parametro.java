package ajuste;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Os numeros que se ajusta numa bancada, num lugar so e trocaveis por arquivo.
 *
 * <p>Estavam espalhados como {@code static final} dentro da skill, da tatica ou
 * da play que os usava. Cada um no seu lugar parecia arrumado, e escondia dois
 * problemas. O primeiro e que ajustar exigia RECOMPILAR: numa bancada, decidir se
 * a tolerancia de chegada deve ser 40 ou 25 mm e coisa de tentar seis valores em
 * dois minutos, e nao de editar, compilar e reabrir a janela seis vezes. O
 * segundo e que ninguem sabia quantos eram nem onde estavam -- e o que nao da
 * para listar nao da para ajustar com metodo.
 *
 * <p>O que entra aqui e o que se AJUSTA, e nao o que se sabe. Continuam fora:
 *
 * <ul>
 *   <li>regra da liga -- o teto de 1,5 m/s no {@code STOP}, os 500 mm de
 *       distancia da bola. Mudar isso nao e ajustar, e jogar errado.
 *   <li>dimensao fisica -- raio do robo, face do dribbler. Sao medidas, nao
 *       escolhas.
 *   <li>geometria do campo -- vem da rede, e chumbar quebraria em Divisao A.
 * </ul>
 *
 * <p>O valor e lido a cada uso, e nao guardado em campo {@code final}, para que
 * uma troca em tempo de execucao valha no tique seguinte. E o custo disso e uma
 * leitura de campo por tique, o que nao aparece em lugar nenhum.
 *
 * <p><b>Unidades:</b> mm e segundos, como no resto do programa, com UMA excecao:
 * angulo aqui vai em GRAUS. Nao e inconsistencia, e a mesma regra que ja vale
 * para a rede -- a conversao acontece na borda, e um arquivo de ajuste e uma
 * borda: quem digita "2" para tolerancia de mira esta pensando em dois graus, e
 * {@code 0.03490658503988659} num campo de texto nao e um numero que alguem
 * revise. Quem le o parametro converte com {@code Math.toRadians}, e sao dois
 * lugares.
 */
public enum Parametro {

    // ------------------------------------------------------------- movimento

    /** Quao perto do destino o robo considera que chegou, em mm. */
    TOLERANCIA_DE_CHEGADA(40, "mm"),

    /**
     * Quanto da frenagem teorica se pede de fato, entre 0 e 1.
     *
     * <p>Desconta o atraso entre decidir e obedecer. Em 1,0 o robo pede
     * exatamente a velocidade limite de frenagem e, como o comando chega alguns
     * milissegundos depois, passa do ponto e volta -- e o robo que fica bailando
     * em cima do alvo.
     */
    FRENAGEM_LINEAR(0.8, ""),

    /** Erro de orientacao aceito antes de parar de girar, em graus. */
    TOLERANCIA_DE_ANGULO(2, "graus"),

    /** Ver {@link #FRENAGEM_LINEAR}, para o giro. */
    FRENAGEM_ANGULAR(0.8, ""),

    // ---------------------------------------------------------------- taticas

    /**
     * Tolerancia de chegada ao ir buscar a bola, em mm.
     *
     * <p>Menor que a folga do sensor de proposito: parar com a tolerancia padrao
     * deixa o robo a um palmo da bola, sem nunca captura-la.
     */
    TOLERANCIA_NA_BOLA(30, "mm"),

    /** Teto de velocidade com a bola presa no dribbler, em mm/s. */
    VEL_CONDUCAO(1200, "mm/s"),

    /** Tolerancia de chegada durante a conducao, em mm. */
    TOLERANCIA_DE_CONDUCAO(120, "mm"),

    /**
     * A que distancia do gol o atacante passa a mirar em vez de conduzir, em mm.
     *
     * <p>Ver {@link #DISTANCIA_DE_CONDUCAO}: os dois numeros nao podem ser iguais.
     */
    DISTANCIA_DE_CHUTE(2500, "mm"),

    /**
     * Ate onde a conducao leva a bola, em mm do gol.
     *
     * <p>Menor que {@link #DISTANCIA_DE_CHUTE} de proposito, e isso NAO e ajuste
     * fino: com os dois iguais, o robo para exatamente em cima do limiar que
     * decide entre conduzir e chutar, e a tolerancia de chegada faz ele parar ora
     * um pouco dentro, ora um pouco fora. Quando para fora, fica parado com a bola
     * achando que ainda tem de conduzir, e a jogada morre ali. Com o destino
     * 500 mm dentro da zona de chute, a troca acontece durante a aproximacao e o
     * robo ja chega mirando.
     *
     * <p>Quem ajustar um dos dois tem de olhar o outro.
     */
    DISTANCIA_DE_CONDUCAO(2000, "mm"),

    /** Erro de mira aceito antes de acionar o chutador, em graus. */
    TOLERANCIA_DE_MIRA(2, "graus"),

    /**
     * Quanto tempo a bola precisa ficar no sensor antes de valer o chute, em s.
     *
     * <p>Sem a espera o robo chuta no instante em que a bola ENCOSTA, e isso da
     * errado de duas maneiras. Uma bola que so passa raspando pela boca aciona o
     * sensor por um quadro e leva um chute para lugar nenhum. E mesmo quando a
     * bola e nossa, ela chega a boca ainda quicando: chutar antes de o rolete
     * assentar manda a bola para qualquer lado menos o mirado.
     *
     * <p>Contado no relogio da visao, entao vale igual com o simulador acelerado.
     */
    POSSE_MINIMA(0.15, "s"),

    /**
     * Acima desta velocidade a bola nao e nossa, em mm/s.
     *
     * <p>Bola que a gente conduz anda junto com o robo, devagar. Bola a 6 m/s
     * passando pela boca acabou de ser chutada -- inclusive por nos mesmos.
     *
     * <p>Sem esta condicao acontece um chute duplo: o chute sai, a play se
     * conclui, o coach reinicia a mesma play no quadro seguinte, e o dribbler
     * reengata na bola ANTES de ela escapar da boca. Ela e segurada de volta e
     * chutada outra vez, 0,18 s depois da primeira. Nao da para ver isso sem log,
     * porque na tela parece um chute so.
     */
    VEL_MAX_CONTROLE(1500, "mm/s"),

    // ------------------------------------------------------------ play e area

    /**
     * Vantagem do robo ja atribuido a um papel, entre 0 e 1.
     *
     * <p>Multiplica o custo do candidato atual, entao 0,7 quer dizer "so troque
     * se o outro for pelo menos 30% melhor". Sem histerese, dois robos a
     * distancias parecidas trocam de funcao a cada quadro e nenhum chega a lugar
     * nenhum.
     */
    HISTERESE_DE_ATRIBUICAO(0.7, ""),

    /** Tempo limite de uma jogada, em segundos do relogio da VISAO. */
    TEMPO_LIMITE_DA_PLAY(120, "s"),

    /**
     * Folga somada a area da visao para formar a zona proibida, em mm.
     *
     * <p>So vale quando {@link #ZONA_PROFUNDIDADE} e {@link #ZONA_LARGURA} estao
     * em zero, que e o padrao. Meio raio de robo: a regra mede pelo ponto do robo
     * que entra, entao encostar a casca na linha ja e falta.
     */
    MARGEM_DA_AREA(45, "mm"),

    /**
     * Com que velocidade um robo de linha sai da area, em mm/s.
     *
     * <p>Existe porque o limitador so sabia REMOVER a componente de entrada: com
     * a play mandando zero nao havia o que remover, e o robo ficava parado dentro
     * da area cometendo falta ate alguem mandar ele andar. Acontece de verdade --
     * empurrao de adversario, ou o goleiro declarado mudar no meio da partida e o
     * antigo virar robo de linha ja dentro.
     *
     * <p>Moderada de proposito. Sair e obrigatorio, mas sair a toda faz o robo
     * atravessar meio campo por causa de um encostao, e atrapalhar mais do que a
     * falta que evitou. Passar do limite para fora nao custa nada: fora e legal.
     */
    VEL_DE_SAIDA_DA_AREA(800, "mm/s"),

    /**
     * Quanto a zona proibida avanca da linha de fundo para o meio, em mm.
     *
     * <p>Zero quer dizer "usar a area que a VISAO informou, mais
     * {@link #MARGEM_DA_AREA}" -- e o padrao, e e o que mantem a zona certa em
     * Divisao A. Um numero aqui manda nela, e ai e responsabilidade de quem
     * digitou conferir a divisao.
     */
    ZONA_PROFUNDIDADE(0, "mm, 0 = da visao"),

    /** Extensao total da zona proibida no eixo Y. Ver {@link #ZONA_PROFUNDIDADE}. */
    ZONA_LARGURA(0, "mm, 0 = da visao"),

    // -------------------------------------------------------------- percepcao

    /** Folga do sensor de bola sobre a boca do dribbler, em mm. */
    TOLERANCIA_DO_SENSOR(15, "mm"),

    /** Quanto tempo uma trilha sobrevive sem ser vista, em s. */
    ESQUECIMENTO_DA_TRILHA(1.5, "s");

    private final double padrao;
    private final String unidade;

    /** Volatil porque a Swing escreve e a thread de decisao le. */
    private volatile double valor;

    Parametro(double padrao, String unidade) {
        this.padrao = padrao;
        this.unidade = unidade;
        this.valor = padrao;
    }

    public double valor()    { return valor; }
    public double padrao()   { return padrao; }
    public String unidade()  { return unidade; }

    public void vSetValor(double novo) { this.valor = novo; }

    /** True quando o valor corrente nao e mais o de fabrica. */
    public boolean bAjustado() { return valor != padrao; }

    // ------------------------------------------------------------- arquivo

    /**
     * Le um arquivo de ajuste e aplica o que ele traz.
     *
     * <p>Chave ausente fica no padrao: o arquivo diz o que MUDA, e nao o estado
     * inteiro. Chave desconhecida e ERRO, e nao aviso -- um nome digitado errado
     * que passa em silencio faz procurar o problema no lugar errado, e e
     * exatamente o que acontece quando se ajusta com pressa.
     *
     * @return o que mudou, em linhas legiveis, para quem chamou registrar
     */
    public static List<String> vCarregar(Path arquivo) throws IOException {
        Map<String, Double> lido = Json.objetoDeNumeros(Files.readString(arquivo));

        List<String> desconhecidas = new ArrayList<>();
        for (String chave : lido.keySet()) {
            if (de(chave) == null) desconhecidas.add(chave);
        }
        if (!desconhecidas.isEmpty()) {
            throw new IllegalArgumentException("parametro desconhecido: "
                    + String.join(", ", desconhecidas)
                    + " (os nomes validos estao em ajuste.Parametro)");
        }

        List<String> mudancas = new ArrayList<>();
        for (Map.Entry<String, Double> e : lido.entrySet()) {
            Parametro p = de(e.getKey());
            if (p.valor == e.getValue()) continue;
            mudancas.add(String.format("%s: %s -> %s %s",
                    p.name(), curto(p.valor), curto(e.getValue()), p.unidade).trim());
            p.vSetValor(e.getValue());
        }
        return mudancas;
    }

    /** Devolve todo mundo ao valor de fabrica. */
    public static void vRestaurarPadroes() {
        for (Parametro p : values()) p.valor = p.padrao;
    }

    private static Parametro de(String nome) {
        for (Parametro p : values()) if (p.name().equals(nome)) return p;
        return null;
    }

    /**
     * O estado corrente como arquivo de ajuste, pronto para editar.
     *
     * <p>Existe para nao ser preciso saber os nomes de cor: gera-se o modelo,
     * apaga-se o que nao interessa e mexe-se no resto.
     */
    public static String paraJson() {
        StringBuilder sb = new StringBuilder("{\n");
        Parametro[] todos = values();
        for (int i = 0; i < todos.length; i++) {
            Parametro p = todos[i];
            sb.append("  \"").append(p.name()).append("\": ").append(curto(p.valor));
            if (i < todos.length - 1) sb.append(',');
            if (!p.unidade.isEmpty()) sb.append("   // ").append(p.unidade);
            sb.append('\n');
        }
        return sb.append("}\n").toString();
    }

    /** Sem casa decimal quando o numero e inteiro; o arquivo fica legivel. */
    private static String curto(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v)
                ? String.valueOf((long) v)
                : String.valueOf(v);
    }
}
