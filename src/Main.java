import app.Cliente;
import app.Janela;
import model.Cor;
import rede.ConfigRede;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

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
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorado) {
                // Aparencia e detalhe: se o sistema nao der, segue com a padrao.
            }
            Janela.abrir("Estrategia SSL", cliente);
        });
    }

    record Argumentos(ConfigRede rede, Cor equipe) {

        static Argumentos parse(String[] args) {
            ConfigRede rede = ConfigRede.padrao();
            Cor equipe = Cor.AZUL;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--azul"           -> equipe = Cor.AZUL;
                    case "--amarelo"        -> equipe = Cor.AMARELO;
                    case "--grupo"          -> rede = rede.comGrupo(args[++i]);
                    case "--porta-visao"    -> rede = rede.comPortaVisao(Integer.parseInt(args[++i]));
                    case "--host"           -> rede = rede.comHost(args[++i]);
                    case "--porta-azul"     -> rede = rede.comPortaAzul(Integer.parseInt(args[++i]));
                    case "--porta-amarelo"  -> rede = rede.comPortaAmarelo(Integer.parseInt(args[++i]));
                    case "--ajuda", "-h"    -> { ajuda(); System.exit(0); }
                    default -> throw new IllegalArgumentException(
                            "argumento desconhecido: " + args[i] + " (use --ajuda)");
                }
            }

            String problema = rede.problema();
            if (problema != null) throw new IllegalArgumentException("rede: " + problema);
            return new Argumentos(rede, equipe);
        }

        static void ajuda() {
            System.out.println("""
                    Estrategia SSL -- software de time

                      java Main                janela, escutando a visao oficial
                      java Main --amarelo      jogar de amarelo

                    Rede
                      visao    <- 224.5.23.2:10006   SSL_WrapperPacket (multicast)
                      comandos -> 127.0.0.1:10301    RobotControl azul
                                  127.0.0.1:10302    RobotControl amarelo

                      --grupo <ip>          grupo multicast da visao (padrao 224.5.23.2)
                      --porta-visao <n>     porta da visao           (padrao 10006)
                      --host <ip>           onde roda o simulador    (padrao 127.0.0.1)
                      --porta-azul <n>      RobotControl azul        (padrao 10301)
                      --porta-amarelo <n>   RobotControl amarelo     (padrao 10302)

                      as mesmas opcoes podem ser mudadas com a janela aberta,
                      no painel da esquerda

                    Equipe
                      --azul | --amarelo    de que cor jogamos (padrao azul)
                    """);
        }
    }
}
