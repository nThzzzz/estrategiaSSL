package ajuste;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leitor de um objeto JSON plano de numeros.
 *
 * <p>Escrito a mao, e nao trazido de uma biblioteca, pelo mesmo motivo que o
 * resto do projeto compila offline: a unica dependencia versionada aqui e o
 * {@code protobuf-java}, e nada justifica uma segunda so para ler dezessete
 * numeros.
 *
 * <p>Aceita {@code {"CHAVE": numero, ...}} e mais nada: sem aninhamento, sem
 * texto como valor, sem lista. A recusa e proposital -- um arquivo de ajuste que
 * aceita mais do que sabe usar cria a duvida de para onde foi o que se escreveu
 * nele.
 *
 * <p>A excecao e o comentario de linha, {@code //}, que JSON nao tem. Este
 * arquivo e escrito por gente numa bancada, e um numero sem a unidade ao lado e
 * como se acaba escrevendo grau onde se esperava radiano. Sem comentario, o
 * modelo gerado por {@code Parametro.paraJson()} nao poderia dizer a unidade.
 *
 * <p>Erro sempre diz a POSICAO. "esperava ':' na posicao 42" resolve em um
 * segundo o que "JSON invalido" faz procurar por minutos.
 */
final class Json {

    private Json() {}

    static Map<String, Double> objetoDeNumeros(String texto) {
        Cursor c = new Cursor(texto);
        Map<String, Double> saida = new LinkedHashMap<>();

        c.exigir('{');
        if (c.aceitar('}')) return saida;

        do {
            String chave = c.texto();
            c.exigir(':');
            double valor = c.numero();
            if (saida.put(chave, valor) != null) {
                throw new IllegalArgumentException("chave repetida: \"" + chave + "\"");
            }
        } while (c.aceitar(','));

        c.exigir('}');
        c.exigirFim();
        return saida;
    }

    /** Percorre o texto guardando a posicao, que e o que faz o erro ser util. */
    private static final class Cursor {

        private final String s;
        private int i;

        Cursor(String s) { this.s = s; }

        /** Come espaco e comentario de linha, que aqui contam como espaco. */
        private void pular() {
            while (i < s.length()) {
                if (Character.isWhitespace(s.charAt(i))) { i++; continue; }
                if (s.charAt(i) == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    while (i < s.length() && s.charAt(i) != '\n') i++;
                    continue;
                }
                return;
            }
        }

        boolean aceitar(char esperado) {
            pular();
            if (i < s.length() && s.charAt(i) == esperado) { i++; return true; }
            return false;
        }

        void exigir(char esperado) {
            pular();
            if (i >= s.length()) {
                throw new IllegalArgumentException(
                        "esperava '" + esperado + "' mas o arquivo acabou");
            }
            if (s.charAt(i) != esperado) {
                throw new IllegalArgumentException("esperava '" + esperado + "' na posicao "
                        + i + ", achei '" + s.charAt(i) + "'");
            }
            i++;
        }

        void exigirFim() {
            pular();
            if (i < s.length()) {
                throw new IllegalArgumentException("sobrou texto depois do objeto, na posicao " + i);
            }
        }

        String texto() {
            exigir('"');
            int inicio = i;
            while (i < s.length() && s.charAt(i) != '"') {
                if (s.charAt(i) == '\\') {
                    throw new IllegalArgumentException(
                            "escape nao e aceito em chave, na posicao " + i);
                }
                i++;
            }
            if (i >= s.length()) {
                throw new IllegalArgumentException("texto sem fecha-aspas, aberto na posicao "
                        + (inicio - 1));
            }
            String saida = s.substring(inicio, i);
            i++; // consome a aspa final
            return saida;
        }

        double numero() {
            pular();
            int inicio = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i))
                    || s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E'
                    || ((s.charAt(i) == '-' || s.charAt(i) == '+')
                        && (s.charAt(i - 1) == 'e' || s.charAt(i - 1) == 'E')))) {
                i++;
            }
            if (inicio == i) {
                throw new IllegalArgumentException("esperava um numero na posicao " + inicio);
            }
            try {
                return Double.parseDouble(s.substring(inicio, i));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + s.substring(inicio, i)
                        + "\" nao e um numero, na posicao " + inicio);
            }
        }
    }
}
