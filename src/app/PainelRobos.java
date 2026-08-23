package app;

import core.Vec2;
import model.Cor;
import mundo.EstadoRobo;
import mundo.Quadro;
import view.Paleta;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Painel da direita: um cartao por robo NOSSO, com o que a estrategia sabe dele.
 *
 * <p>O adversario fica de fora de proposito. Estes cartoes existem para operar os
 * nossos robos -- e sobre eles que se decide, e serao eles que ganharao comando,
 * atribuicao de papel e estado de skill conforme a estrategia crescer. Nada disso
 * existe para um robo do outro time, de quem so se sabe onde esta. Misturar os
 * doze dobraria a lista e empurraria para fora da tela justamente o que se olha.
 * O adversario continua no campo, e clicavel la.
 *
 * <p>Segue {@link Cliente#getEquipe()}: trocar de cor no painel da esquerda troca
 * a lista inteira, sem nada aqui precisar saber que isso aconteceu.
 *
 * <p>Desenhado em um componente so, e nao montado com um {@code JPanel} por
 * robo. A lista muda de tamanho a cada quadro -- robo entra, robo some por
 * oclusao -- e recriar componentes Swing sessenta vezes por segundo custaria
 * mais do que redesenhar tudo, alem de perder foco e selecao no meio.
 *
 * <p>Dos numeros de cada cartao, so a ORIENTACAO vem da rede. Velocidade e bola
 * no sensor sao inferencias.
 *
 * <p>Com a estrategia ligada, cada cartao ganha embaixo a cadeia que o robo esta
 * executando: papel, tatica e skill, em VERDE quando existem e VERMELHO quando
 * faltam. E aqui, e nao na janela de log, porque a pergunta e "o que este robo
 * esta fazendo agora" -- pergunta que se faz olhando o robo, junto da velocidade
 * e do sensor dele. O log responde outra coisa, "o que aconteceu", e essa rola
 * para fora da tela por natureza.
 */
public final class PainelRobos extends PainelRolavel {

    /** Largura inicial da coluna; dai em diante quem manda e o divisor. */
    public static final int LARGURA = 268;

    /** Altura do cartao sem a cadeia de execucao. */
    private static final int ALTURA_SIMPLES = 52;

    /**
     * Altura com papel, tatica e skill embaixo.
     *
     * <p>O cartao cresce so quando a estrategia esta ligada. Com ela desligada
     * essas tres linhas diriam "sem papel" seis vezes, ocupando metade do painel
     * para nao informar nada.
     */
    private static final int ALTURA_COM_CADEIA = 100;

    private static final int ALTURA_TITULO = 30;

    private final Cliente cliente;
    private final BiConsumer<Cor, Integer> aoSelecionar;

    /** Faixas verticais desenhadas no ultimo quadro, para traduzir clique em robo. */
    private final List<Faixa> faixas = new ArrayList<>();

    private Cor corSelecionada;
    private int idSelecionado = -1;

    private record Faixa(int y, int altura, Cor cor, int id) {}

    /**
     * Largura util para desenhar, que e a da coluna e nao mais uma constante.
     *
     * <p>Os cartoes sao pintados a mao, com a borda direita posicionada a partir
     * daqui. Enquanto a coluna tinha largura fixa dava no mesmo usar a constante;
     * agora que o divisor e arrastavel, usar a constante deixaria os cartoes
     * parados na largura antiga enquanto a coluna cresce.
     */
    private int largura() { return Math.max(getWidth(), 160); }

    private int alturaDoCartao() {
        return cliente.isEstrategiaLigada() ? ALTURA_COM_CADEIA : ALTURA_SIMPLES;
    }

    public PainelRobos(Cliente cliente, BiConsumer<Cor, Integer> aoSelecionar) {
        this.cliente = cliente;
        this.aoSelecionar = aoSelecionar;
        setPreferredSize(new Dimension(LARGURA, 100));
        setBackground(Paleta.PAINEL);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (Faixa f : faixas) {
                    if (e.getY() >= f.y() && e.getY() < f.y() + f.altura()) {
                        selecionar(f.cor(), f.id());
                        aoSelecionar.accept(f.cor(), f.id());
                        return;
                    }
                }
                selecionar(null, -1);
                aoSelecionar.accept(null, -1);
            }
        });
    }

    public void selecionar(Cor cor, int id) {
        this.corSelecionada = cor;
        this.idSelecionado = id;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        faixas.clear();

        Quadro q = cliente.quadro();
        int y = secao(g2, q, cliente.getEquipe(), 6);

        if (q.robos(cliente.getEquipe()).isEmpty()) {
            g2.setColor(Paleta.APAGADO);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.drawString(q.robos().isEmpty()
                    ? "nenhum robo na visao"
                    : "nenhum robo nosso na visao", 16, y + 6);
            y += 24;
        }

        y = secaoDeJogadas(g2, y);

        setPreferredSize(new Dimension(LARGURA, Math.max(y + 10, 100)));
        g2.dispose();
    }

    private int secao(Graphics2D g, Quadro q, Cor cor, int y) {
        List<EstadoRobo> robos = q.robos(cor);

        g.setColor(Paleta.de(cor));
        g.fill(new RoundRectangle2D.Double(10, y + 8, 8, 14, 4, 4));
        g.setColor(Paleta.TEXTO);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(cliente.getNome(cor), 26, y + 20);

        g.setColor(Paleta.APAGADO);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        String contagem = robos.size() + (robos.size() == 1 ? " robo" : " robos");
        FontMetrics fm = g.getFontMetrics();
        g.drawString(contagem, largura() - 14 - fm.stringWidth(contagem), y + 20);

        y += ALTURA_TITULO;
        int altura = alturaDoCartao();
        for (EstadoRobo r : robos) {
            desenharCartao(g, r, y, altura);
            faixas.add(new Faixa(y, altura, r.cor(), r.id()));
            y += altura;
        }
        return y + 8;
    }

    /**
     * O repertorio de jogadas, embaixo dos robos.
     *
     * <p>Fica aqui e nao no log porque e estado, e nao acontecimento: a nota de
     * cada jogada e a que esta rodando sao coisas que se consulta olhando, nao
     * rolando um historico.
     *
     * <p>Jogada indisponivel aparece apagada em vez de sumir. Some da lista ela
     * responderia a pergunta errada -- o que se quer saber e por que o coach
     * escolheu aquela, e para isso e preciso ver as que ele NAO podia escolher.
     */
    private int secaoDeJogadas(Graphics2D g, int y) {
        var jogadas = cliente.getExecutor().diario().jogadas();

        g.setColor(Paleta.BORDA);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(10, y + 4, largura() - 10, y + 4));

        g.setColor(Paleta.TEXTO);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("Jogadas", 16, y + 24);

        if (jogadas.isEmpty()) {
            g.setColor(Paleta.APAGADO);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(cliente.isEstrategiaLigada()
                    ? "nenhuma no repertorio" : "estrategia desligada", 16, y + 44);
            return y + 56;
        }

        int linha = y + 32;
        for (var j : jogadas) {
            if (j.atual()) {
                g.setColor(new Color(60, 100, 70));
                g.fill(new RoundRectangle2D.Double(10, linha, largura() - 20, 22, 6, 6));
            }

            Color cor = j.atual() ? Paleta.OK : j.disponivel() ? Paleta.TEXTO : Paleta.APAGADO;
            g.setColor(cor);
            g.setFont(new Font("SansSerif", j.atual() ? Font.BOLD : Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();

            String nome = j.nome();
            while (nome.length() > 1 && fm.stringWidth(nome) > largura() - 110) {
                nome = nome.substring(0, nome.length() - 1);
            }
            g.drawString(nome, 18, linha + 15);

            // Sem pre-condicao a nota nao entra em sorteio nenhum, entao dizer
            // isso vale mais do que repetir o numero.
            String direita = j.disponivel() ? String.format("%.2f", j.nota()) : "fora";
            g.setFont(new Font("Monospaced", j.atual() ? Font.BOLD : Font.PLAIN, 11));
            FontMetrics fmd = g.getFontMetrics();
            g.drawString(direita, largura() - 18 - fmd.stringWidth(direita), linha + 15);

            linha += 24;
        }
        return linha + 8;
    }

    private void desenharCartao(Graphics2D g, EstadoRobo r, int y, int altura) {
        boolean selecionado = r.cor() == corSelecionada && r.id() == idSelecionado;

        g.setColor(selecionado ? new Color(52, 52, 60) : Paleta.PAINEL_ALT);
        g.fill(new RoundRectangle2D.Double(8, y + 2, largura() - 16, altura - 6, 8, 8));
        if (selecionado) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Double(8, y + 2, largura() - 16, altura - 6, 8, 8));
        }

        // Distintivo com o numero, na cor da equipe: e como o robo aparece no campo.
        g.setColor(Paleta.de(r.cor()));
        g.fill(new RoundRectangle2D.Double(16, y + 10, 30, 30, 8, 8));
        g.setColor(Paleta.textoSobre(r.cor()));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        String id = String.valueOf(r.id());
        FontMetrics fmId = g.getFontMetrics();
        g.drawString(id, 31 - fmId.stringWidth(id) / 2, y + 31);

        boolean cru = r.fantasma();
        Color tinta = cru ? Paleta.APAGADO : Paleta.TEXTO;

        // Velocidade, o numero que mais se olha durante um jogo.
        g.setColor(tinta);
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.drawString(String.format("%.2f m/s", r.rapidez() / 1000.0), 56, y + 23);

        // Direcao: seta desenhada no angulo real, mais o valor em graus. A seta
        // sozinha e ambigua em cima de 90 graus; o numero sozinho nao se le de
        // relance. Os dois juntos resolvem.
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(cru ? Paleta.APAGADO : new Color(190, 190, 200));
        g.drawString(String.format("%+.0f°", Math.toDegrees(r.theta())), 76, y + 39);
        seta(g, 62, y + 35, r.theta(), cru);

        if (Math.abs(r.omega()) > 0.15) {
            g.drawString(String.format("%+.1f rad/s", r.omega()), 118, y + 39);
        }

        indicadorDeSensor(g, r, y);

        if (cru) {
            g.setColor(Paleta.ALERTA);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(String.format("sem visao ha %.1fs", r.idade()), 56, y + 50);
        }

        if (altura > ALTURA_SIMPLES) desenharCadeia(g, r, y);
    }

    /**
     * Papel, tatica e skill que este robo esta executando.
     *
     * <p>Verde e o que existe, vermelho e o que falta. Uma tatica CONCLUIDA fica
     * verde com o sufixo "fim": concluir e ter dado certo, mirar e chegar
     * terminam, e pintar isso de vermelho diria que algo falhou quando deu certo.
     * Vermelho fica para robo sem papel, papel sem tatica, tatica sem skill --
     * que sao as tres formas de um robo estar parado sem ninguem perceber.
     */
    private void desenharCadeia(Graphics2D g, EstadoRobo r, int y) {
        var situacao = cliente.getExecutor().diario().situacao(r.id());
        boolean nosso = r.cor() == cliente.getEquipe();

        g.setColor(Paleta.BORDA);
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.Line2D.Double(18, y + 56, largura() - 18, y + 56));

        if (!nosso || situacao == null) {
            g.setColor(Paleta.APAGADO);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(nosso ? "fora da estrategia" : "adversario", 18, y + 72);
            return;
        }

        linhaDaCadeia(g, "role", situacao.role(), situacao.temRole(), y + 70);
        linhaDaCadeia(g, "tactic", situacao.tactic()
                + (situacao.taticaConcluida() ? "  fim" : ""), situacao.temTactic(), y + 83);
        linhaDaCadeia(g, "skill", situacao.skill()
                + (situacao.skillConcluida() ? "  fim" : ""), situacao.temSkill(), y + 96);
    }

    private void linhaDaCadeia(Graphics2D g, String rotulo, String valor,
                               boolean presente, int y) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(Paleta.APAGADO);
        g.drawString(rotulo, 18, y);

        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(presente ? Paleta.OK : Paleta.ERRO);
        FontMetrics fm = g.getFontMetrics();
        String curto = valor;
        while (curto.length() > 1 && fm.stringWidth(curto) > largura() - 90) {
            curto = curto.substring(0, curto.length() - 1);
        }
        g.drawString(curto, 62, y);
    }

    /** Seta apontando para onde o robo olha, no referencial da tela (y invertido). */
    private void seta(Graphics2D g, int cx, int cy, double theta, boolean apagado) {
        double raio = 7;
        Vec2 p = Vec2.dePolar(raio, theta);
        double px = cx + p.x(), py = cy - p.y();

        g.setColor(apagado ? Paleta.APAGADO : new Color(200, 200, 210));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx - p.x(), cy + p.y(), px, py));

        Path2D ponta = new Path2D.Double();
        for (int lado = -1; lado <= 1; lado += 2) {
            Vec2 asa = Vec2.dePolar(4.5, theta + Math.PI - lado * 0.6);
            ponta.moveTo(px, py);
            ponta.lineTo(px + asa.x(), py - asa.y());
        }
        g.draw(ponta);
    }

    /** Selo do sensor de bola: aceso e escrito, apagado e so contorno. */
    private void indicadorDeSensor(Graphics2D g, EstadoRobo r, int y) {
        int larguraDoSelo = 54, altura = 20;
        int x = largura() - larguraDoSelo - 18;
        RoundRectangle2D selo =
                new RoundRectangle2D.Double(x, y + 16, larguraDoSelo, altura, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();

        if (r.bolaNoSensor()) {
            g.setColor(Paleta.SENSOR);
            g.fill(selo);
            g.setColor(new Color(10, 40, 10));
            String texto = "BOLA";
            g.drawString(texto, x + (larguraDoSelo - fm.stringWidth(texto)) / 2, y + 30);
        } else {
            g.setColor(Paleta.BORDA);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(selo);
            g.setColor(Paleta.APAGADO);
            String texto = "sensor";
            g.drawString(texto, x + (larguraDoSelo - fm.stringWidth(texto)) / 2, y + 30);
        }
    }
}
