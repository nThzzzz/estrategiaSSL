import ajuste.Parametro;
import app.Cliente;
import app.telas.TelaJogo;
import model.Cor;
import rede.ConfigRede;
import view.Estilo;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Software de time da SSL: escuta a visao, mostra o campo, manda comando.
 *
 * <pre>
 *   java Main                          escuta 224.5.23.2:10006, joga de azul
 *   java Main --amarelo                joga de amarelo (manda na porta 10302)
 *   java Main --grupo 224.5.23.2 --porta-visao 10006
 *   java Main --host 192.168.0.10      simulador em outra maquina
 *   java Main --ajuda
 * </pre>
 *
 * <p>Nao ha estrategia escrita ainda: a janela mostra o que a visao entrega e a
 * saida de comandos esta pronta, mas ninguem produz comando. Os robos ficam
 * parados -- e assim que o simulador se comporta quando nenhum time esta
 * conectado.
 *
 * <p>O arbitro e opcional. Sem o {@code ssl-game-controller} no ar a janela
 * funciona igual, mostrando "sem arbitro", e o painel da esquerda permite
 * simular comandos localmente para testar.
 */
public final class Main {

    public static void main(String[] args) {
        Argumentos a;
        try {
            a = Argumentos.parse(args);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            String motivo = e instanceof ArrayIndexOutOfBoundsException
                    ? "falta o valor do ultimo argumento" : e.getMessage();
            System.err.println("erro: " + motivo);
            System.err.println("use --ajuda para ver as opcoes");
            System.exit(2);
            return;
        }

        // Antes do Cliente: ele ja le parametro na construcao, e uma troca que
        // chegasse depois nao valeria para o que ja foi montado.
        if (!bCarregarAjustes(a.ajustes())) { System.exit(2); return; }

        Cliente cliente = new Cliente(a.rede(), a.equipe());
        try {
            cliente.conectar();
        } catch (Exception e) {
            // Nao e motivo para desistir: a janela abre mostrando "sem visao" e a
            // pessoa corrige a porta no painel da esquerda. Fechar aqui obrigaria
            // a reiniciar o programa por causa de um numero errado.
            System.err.println("rede: " + e.getMessage());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(cliente::close));

        SwingUtilities.invokeLater(() -> {
            Estilo.instalar();
            TelaJogo.abrir("Estrategia SSL", cliente);
        });
    }

    /** Arquivo de ajuste procurado quando ninguem passa {@code --ajustes}. */
    private static final String AJUSTES_PADRAO = "ajustes.json";

    /**
     * Carrega o arquivo de ajuste, se houver, e diz o que mudou.
     *
     * <p>O arquivo padrao e procurado sem ninguem pedir, porque numa bancada se
     * edita o arquivo e se roda de novo -- ter de lembrar de uma flag toda vez e
     * como nao ter o arquivo. Mas a carga NUNCA e silenciosa: um valor diferente
     * do de fabrica sem nada dizendo muda o comportamento do time e vira caca ao
     * fantasma.
     *
     * @return false quando o arquivo existe e esta errado; ai e melhor parar do
     *         que subir com metade dos ajustes
     */
    private static boolean bCarregarAjustes(Path pedido) {
        Path arquivo = pedido != null ? pedido : Path.of(AJUSTES_PADRAO);
        if (pedido == null && !Files.isReadable(arquivo)) return true;

        try {
            List<String> mudancas = Parametro.vCarregar(arquivo);
            if (mudancas.isEmpty()) {
                System.out.println("ajustes: " + arquivo + " nao muda nada");
            } else {
                System.out.println("ajustes: " + arquivo);
                for (String m : mudancas) System.out.println("  " + m);
            }
            return true;
        } catch (Exception e) {
            System.err.println("ajustes: " + arquivo + ": " + e.getMessage());
            return false;
        }
    }

    record Argumentos(ConfigRede rede, Cor equipe, Path ajustes) {

        static Argumentos parse(String[] args) {
            ConfigRede rede = ConfigRede.padrao();
            Cor equipe = Cor.AZUL;
            Path ajustes = null;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--azul"           -> equipe = Cor.AZUL;
                    case "--amarelo"        -> equipe = Cor.AMARELO;
                    case "--grupo"          -> rede = rede.comGrupo(args[++i]);
                    case "--porta-visao"    -> rede = rede.comPortaVisao(Integer.parseInt(args[++i]));
                    case "--grupo-arbitro"  -> rede = rede.comGrupoArbitro(args[++i]);
                    case "--porta-arbitro"  -> rede = rede.comPortaArbitro(Integer.parseInt(args[++i]));
                    case "--host"           -> rede = rede.comHost(args[++i]);
                    case "--porta-azul"     -> rede = rede.comPortaAzul(Integer.parseInt(args[++i]));
                    case "--porta-amarelo"  -> rede = rede.comPortaAmarelo(Integer.parseInt(args[++i]));
                    case "--ajustes"        -> ajustes = Path.of(args[++i]);
                    case "--gerar-ajustes"  -> {
                        System.out.print(Parametro.paraJson());
                        System.exit(0);
                    }
                    case "--ajuda", "-h"    -> { ajuda(); System.exit(0); }
                    default -> throw new IllegalArgumentException(
                            "argumento desconhecido: " + args[i] + " (use --ajuda)");
                }
            }

            String problema = rede.problema();
            if (problema != null) throw new IllegalArgumentException("rede: " + problema);
            return new Argumentos(rede, equipe, ajustes);
        }

        static void ajuda() {
            System.out.println("""
                    Estrategia SSL -- software de time

                      java Main                janela, escutando a visao oficial
                      java Main --amarelo      jogar de amarelo

                    Rede
                      visao    <- 224.5.23.2:10006   SSL_WrapperPacket   (multicast)
                      arbitro  <- 224.5.23.1:10003   Referee             (multicast)
                      comandos -> 127.0.0.1:10301    RobotControl azul
                                  127.0.0.1:10302    RobotControl amarelo

                      --grupo <ip>          grupo multicast da visao (padrao 224.5.23.2)
                      --porta-visao <n>     porta da visao           (padrao 10006)
                      --grupo-arbitro <ip>  grupo do Game Controller (padrao 224.5.23.1)
                      --porta-arbitro <n>   porta do arbitro         (padrao 10003)
                      --host <ip>           onde roda o simulador    (padrao 127.0.0.1)
                      --porta-azul <n>      RobotControl azul        (padrao 10301)
                      --porta-amarelo <n>   RobotControl amarelo     (padrao 10302)

                      as mesmas opcoes podem ser mudadas com a janela aberta,
                      no painel da esquerda

                    Equipe
                      --azul | --amarelo    de que cor jogamos (padrao azul)

                    Ajuste
                      tolerancias, distancias e tetos de velocidade ficam em
                      ajuste.Parametro e podem ser trocados por arquivo, sem
                      recompilar. O que o arquivo nao cita fica no padrao.

                      --gerar-ajustes       imprime o modelo com os valores atuais
                      --ajustes <arquivo>   carrega este arquivo
                                            (sem a flag, carrega ./ajustes.json se existir)
                    """);
        }
    }
}
