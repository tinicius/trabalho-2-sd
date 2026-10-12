package br.cefet.sd.memoria.middleware.config;

import br.cefet.sd.memoria.middleware.nucleo.ConfiguracaoTabuleiro;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Carrega e valida a configuração do Middleware (padrões &lt; arquivo {@code .properties} &lt;
 * argumentos {@code --chave=valor}), coletando os erros em {@link ResultadoCarregamento}.
 */
public final class CarregadorConfiguracao {

    /** Chave: opção {@code --config} (somente linha de comando). */
    public static final String CONFIG = "config";
    /** Chave: porta do RMI Registry. */
    public static final String REGISTRY_PORTA = "registry.porta";
    /** Chave: host/IP anunciado nos stubs. */
    public static final String RMI_HOSTNAME = "rmi.hostname";
    /** Chave: porta de exportação dos objetos remotos. */
    public static final String RMI_PORTA_OBJETOS = "rmi.portaObjetos";
    /** Chave: capacidade da Sala. */
    public static final String SALA_CAPACIDADE = "sala.capacidade";
    /** Chave: linhas do tabuleiro. */
    public static final String TABULEIRO_LINHAS = "tabuleiro.linhas";
    /** Chave: colunas do tabuleiro. */
    public static final String TABULEIRO_COLUNAS = "tabuleiro.colunas";
    /** Chave: Intervalo_de_Erro em milissegundos. */
    public static final String INTERVALO_ERRO_MS = "partida.intervaloErroMs";
    /** Chave: inatividade da vez em segundos. */
    public static final String INATIVIDADE_SEG = "turno.inatividadeSeg";
    /** Chave: prazo de "continuar" em segundos. */
    public static final String PRAZO_CONTINUAR_SEG = "sessao.prazoContinuarSeg";
    /** Chave: timeout de Callback em segundos. */
    public static final String CALLBACK_TIMEOUT_SEG = "callback.timeoutSeg";

    /** Nome usado nos erros do tabuleiro combinado (linhas x colunas). */
    private static final String TABULEIRO = TABULEIRO_LINHAS + "/" + TABULEIRO_COLUNAS;
    private static final int CALLBACK_MIN_SEG = 1;
    private static final int CALLBACK_MAX_SEG = 60;
    private static final Pattern HOSTNAME = Pattern.compile("[A-Za-z0-9._:%\\[\\]-]*");
    private static final Pattern COMENTARIO_FINAL = Pattern.compile("\\s+#.*$");

    private static final Set<String> CHAVES_CONHECIDAS = Set.of(REGISTRY_PORTA, RMI_HOSTNAME,
            RMI_PORTA_OBJETOS, SALA_CAPACIDADE, TABULEIRO_LINHAS, TABULEIRO_COLUNAS, INTERVALO_ERRO_MS,
            INATIVIDADE_SEG, PRAZO_CONTINUAR_SEG, CALLBACK_TIMEOUT_SEG);

    private CarregadorConfiguracao() {
    }

    /** Indica se o texto é um host/IP aceitável para {@code rmi.hostname}. */
    public static boolean hostValido(String host) {
        return host != null && !host.isEmpty() && HOSTNAME.matcher(host).matches();
    }

    /**
     * Carrega a configuração a partir de argumentos de linha de comando e de um arquivo.
     *
     * @param arquivoPadrao arquivo usado quando não há {@code --config}; {@code null} ou
     *                      inexistente significa "só padrões"
     */
    public static ResultadoCarregamento carregar(String[] args, Path arquivoPadrao, int numeroDeImagens) {
        List<ErroConfiguracao> erros = new ArrayList<>();
        List<Aviso> avisos = new ArrayList<>();
        Map<String, String> cli = new LinkedHashMap<>();
        if (args != null) {
            for (String a : args) {
                if (a == null) {
                    continue;
                }
                int igual = a.indexOf('=');
                if (!a.startsWith("--") || igual < 3) {
                    erros.add(new ErroConfiguracao(a, "argumento inválido; use --chave=valor"));
                    continue;
                }
                cli.put(a.substring(2, igual).trim(), normalizar(a.substring(igual + 1)));
            }
        }

        String explicito = cli.remove(CONFIG);
        Path arquivo = arquivoPadrao;
        boolean arquivoExplicito = false;
        if (explicito != null && !explicito.isEmpty()) {
            arquivoExplicito = true;
            try {
                arquivo = Paths.get(explicito);
            } catch (InvalidPathException e) {
                erros.add(new ErroConfiguracao(CONFIG, "caminho inválido: " + explicito));
                arquivo = null;
            }
        }

        Map<String, String> efetivo = new LinkedHashMap<>();
        if (arquivo != null) {
            lerArquivo(arquivo, arquivoExplicito, efetivo, erros);
        }
        efetivo.putAll(cli);
        return montar(efetivo, numeroDeImagens, erros, avisos);
    }

    /** Carrega a configuração a partir de um mapa {@code chave -> valor}, sem ler arquivos. */
    public static ResultadoCarregamento carregar(Map<String, String> valores, int numeroDeImagens) {
        Map<String, String> efetivo = new LinkedHashMap<>();
        if (valores != null) {
            valores.forEach((k, v) -> efetivo.put(k.trim(), normalizar(v)));
        }
        return montar(efetivo, numeroDeImagens, new ArrayList<>(), new ArrayList<>());
    }

    /** Carrega a configuração a partir de um texto {@code .properties}. */
    public static ResultadoCarregamento carregarDeTexto(String texto, int numeroDeImagens) {
        List<ErroConfiguracao> erros = new ArrayList<>();
        Map<String, String> efetivo = new LinkedHashMap<>();
        try (Reader leitor = new StringReader(texto == null ? "" : texto)) {
            lerPropriedades(leitor, efetivo);
        } catch (IOException | IllegalArgumentException e) {
            erros.add(new ErroConfiguracao(CONFIG, "texto de propriedades inválido: " + e.getMessage()));
        }
        return montar(efetivo, numeroDeImagens, erros, new ArrayList<>());
    }

    private static void lerArquivo(Path arquivo, boolean explicito, Map<String, String> destino,
            List<ErroConfiguracao> erros) {
        if (!Files.exists(arquivo)) {
            if (explicito) {
                erros.add(new ErroConfiguracao(CONFIG, "arquivo não encontrado: " + arquivo));
            }
            return;
        }
        if (!Files.isRegularFile(arquivo)) {
            erros.add(new ErroConfiguracao(CONFIG, "não é um arquivo regular: " + arquivo));
            return;
        }
        try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            lerPropriedades(leitor, destino);
        } catch (IOException | IllegalArgumentException e) {
            erros.add(new ErroConfiguracao(CONFIG, "não foi possível ler " + arquivo + ": " + e.getMessage()));
        }
    }

    private static void lerPropriedades(Reader leitor, Map<String, String> destino) throws IOException {
        Properties p = new Properties();
        p.load(leitor);
        for (String chave : new java.util.TreeSet<>(p.stringPropertyNames())) {
            destino.put(chave.trim(), normalizar(p.getProperty(chave)));
        }
    }

    /** Remove espaços e comentário final ({@code  # ...}); valor iniciado por {@code #} fica vazio. */
    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String v = valor.trim();
        if (v.startsWith("#")) {
            return "";
        }
        return COMENTARIO_FINAL.matcher(v).replaceFirst("").trim();
    }

    private static ResultadoCarregamento montar(Map<String, String> m, int numeroDeImagens,
            List<ErroConfiguracao> erros, List<Aviso> avisos) {
        Set<String> desconhecidas = new LinkedHashSet<>(m.keySet());
        desconhecidas.removeAll(CHAVES_CONHECIDAS);
        for (String chave : desconhecidas) {
            avisos.add(new Aviso(chave, "chave desconhecida, ignorada"));
        }

        ConfiguracaoMiddleware d = ConfiguracaoMiddleware.padrao();
        Integer porta = lerInteiro(m, REGISTRY_PORTA, d.registryPorta(), 1, 65535, erros);
        String hostname = valor(m, RMI_HOSTNAME);
        if (!HOSTNAME.matcher(hostname).matches()) {
            erros.add(new ErroConfiguracao(RMI_HOSTNAME, "host/IP inválido: '" + hostname + "'"));
        }
        Integer portaObjetos = lerInteiro(m, RMI_PORTA_OBJETOS, d.rmiPortaObjetos(), 0, 65535, erros);
        Integer capacidade = lerInteiro(m, SALA_CAPACIDADE, d.salaCapacidade(), 2, 8, erros);
        Integer linhas = lerInteiro(m, TABULEIRO_LINHAS, d.linhas(), 1, Integer.MAX_VALUE, erros);
        Integer colunas = lerInteiro(m, TABULEIRO_COLUNAS, d.colunas(), 1, Integer.MAX_VALUE, erros);
        Integer intervalo = lerInteiro(m, INTERVALO_ERRO_MS, d.intervaloErroMs(), 500, 10_000, erros);
        Integer inatividade = lerInteiro(m, INATIVIDADE_SEG, d.inatividadeSeg(), 1, Integer.MAX_VALUE, erros);
        Integer prazo = lerInteiro(m, PRAZO_CONTINUAR_SEG, d.prazoContinuarSeg(), 1, Integer.MAX_VALUE, erros);
        int callback = lerCallback(m, avisos);

        if (linhas != null && colunas != null) {
            ConfiguracaoTabuleiro.Resultado r = ConfiguracaoTabuleiro.validar(linhas, colunas, numeroDeImagens);
            if (!r.foiAceito() && r.causa().get() == ConfiguracaoTabuleiro.Causa.NUMERO_DE_POSICOES_INVALIDO) {
                erros.add(new ErroConfiguracao(TABULEIRO, "linhas x colunas = " + ((long) linhas * colunas)
                        + " deve ser par e >= " + ConfiguracaoTabuleiro.MINIMO_DE_POSICOES));
            } else if (!r.foiAceito()) {
                erros.add(new ErroConfiguracao(TABULEIRO, "o tabuleiro exige " + ((long) linhas * colunas / 2)
                        + " pares, mas o catálogo tem apenas " + numeroDeImagens + " imagens"));
            }
        }

        if (!erros.isEmpty()) {
            return new ResultadoCarregamento(null, erros, avisos);
        }
        return new ResultadoCarregamento(new ConfiguracaoMiddleware(porta, hostname, portaObjetos, capacidade,
                linhas, colunas, intervalo, inatividade, prazo, callback), erros, avisos);
    }

    private static String valor(Map<String, String> m, String chave) {
        String v = m.get(chave);
        return v == null ? "" : v;
    }

    /** Lê inteiro em {@code [min, max]}; vazio = padrão; em erro, registra e devolve {@code null}. */
    private static Integer lerInteiro(Map<String, String> m, String chave, int padrao, int min, int max,
            List<ErroConfiguracao> erros) {
        String v = valor(m, chave);
        if (v.isEmpty()) {
            return padrao;
        }
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            erros.add(new ErroConfiguracao(chave, "deve ser um número inteiro, mas foi '" + v + "'"));
            return null;
        }
        if (n < min || n > max) {
            String faixa = max == Integer.MAX_VALUE
                    ? "deve ser um inteiro positivo (>= " + min + ")"
                    : "deve estar entre " + min + " e " + max;
            erros.add(new ErroConfiguracao(chave, faixa + ", mas foi " + n));
            return null;
        }
        return n;
    }

    /** Timeout de Callback: valor inválido vira o padrão com aviso; nunca é erro. */
    private static int lerCallback(Map<String, String> m, List<Aviso> avisos) {
        String v = valor(m, CALLBACK_TIMEOUT_SEG);
        if (v.isEmpty()) {
            return ConfiguracaoMiddleware.CALLBACK_TIMEOUT_PADRAO_SEG;
        }
        try {
            int n = Integer.parseInt(v);
            if (n >= CALLBACK_MIN_SEG && n <= CALLBACK_MAX_SEG) {
                return n;
            }
        } catch (NumberFormatException e) {
            // cai no aviso abaixo
        }
        avisos.add(new Aviso(CALLBACK_TIMEOUT_SEG, "valor inválido '" + v + "' (esperado inteiro de "
                + CALLBACK_MIN_SEG + " a " + CALLBACK_MAX_SEG + "); usando "
                + ConfiguracaoMiddleware.CALLBACK_TIMEOUT_PADRAO_SEG + " s"));
        return ConfiguracaoMiddleware.CALLBACK_TIMEOUT_PADRAO_SEG;
    }

}
