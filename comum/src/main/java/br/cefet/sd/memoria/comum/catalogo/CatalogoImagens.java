package br.cefet.sd.memoria.comum.catalogo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Catalogo imutavel que associa cada {@code idPar} ao nome do recurso de imagem, lido de {@code /images/catalogo.txt}. */
public final class CatalogoImagens {

    /** Diretorio de recursos no classpath (com barras inicial e final). */
    public static final String DIRETORIO_CLASSPATH = "/images/";

    /** Recurso do catalogo no classpath. */
    public static final String RECURSO_CATALOGO = DIRETORIO_CLASSPATH + "catalogo.txt";

    /** Nome do arquivo da face oculta das cartas. */
    public static final String ARQUIVO_VERSO = "verso.png";

    private final Map<Integer, String> recursoPorPar;

    private CatalogoImagens(Map<Integer, String> recursoPorPar) {
        this.recursoPorPar = Collections.unmodifiableMap(new TreeMap<>(recursoPorPar));
    }

    /** Carrega o catalogo padrao do classpath ({@value #RECURSO_CATALOGO}). */
    public static CatalogoImagens carregar() {
        try (InputStream in = CatalogoImagens.class.getResourceAsStream(RECURSO_CATALOGO)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Catalogo de imagens nao encontrado no classpath: " + RECURSO_CATALOGO);
            }
            return carregar(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + RECURSO_CATALOGO, e);
        }
    }

    /** Interpreta o conteudo de um catalogo ({@code id=arquivo} por linha). */
    public static CatalogoImagens interpretar(String conteudo) {
        try {
            return carregar(new StringReader(conteudo));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Interpreta o catalogo a partir de um {@link Reader} (nao o fecha). */
    public static CatalogoImagens carregar(Reader origem) throws IOException {
        BufferedReader leitor = origem instanceof BufferedReader
                ? (BufferedReader) origem : new BufferedReader(origem);
        Map<Integer, String> mapa = new TreeMap<>();
        Set<String> arquivos = new HashSet<>();
        String linha;
        int numero = 0;
        while ((linha = leitor.readLine()) != null) {
            numero++;
            String texto = linha.strip();
            if (numero == 1 && texto.startsWith("\uFEFF")) {
                texto = texto.substring(1).strip();
            }
            if (texto.isEmpty() || texto.startsWith("#")) {
                continue;
            }
            int igual = texto.indexOf('=');
            if (igual <= 0 || igual == texto.length() - 1) {
                throw new IllegalArgumentException(
                        "Linha " + numero + " do catalogo invalida (esperado id=arquivo): " + texto);
            }
            int id;
            try {
                id = Integer.parseInt(texto.substring(0, igual).strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Linha " + numero + " do catalogo com id nao numerico: " + texto, e);
            }
            String arquivo = texto.substring(igual + 1).strip();
            if (id < 1) {
                throw new IllegalArgumentException(
                        "Linha " + numero + " do catalogo: id deve ser >= 1: " + id);
            }
            if (arquivo.isEmpty() || arquivo.contains("/") || arquivo.contains("\\")
                    || arquivo.contains("..")) {
                throw new IllegalArgumentException(
                        "Linha " + numero + " do catalogo: nome de arquivo invalido: " + arquivo);
            }
            if (mapa.putIfAbsent(id, arquivo) != null) {
                throw new IllegalArgumentException(
                        "Linha " + numero + " do catalogo: id repetido: " + id);
            }
            if (!arquivos.add(arquivo)) {
                throw new IllegalArgumentException(
                        "Linha " + numero + " do catalogo: arquivo repetido: " + arquivo);
            }
        }
        if (mapa.isEmpty()) {
            throw new IllegalArgumentException("Catalogo de imagens vazio");
        }
        return new CatalogoImagens(mapa);
    }

    /** Numero de imagens distintas disponiveis. */
    public int quantidade() {
        return recursoPorPar.size();
    }

    /** Mapa imutavel e ordenado {@code idPar -> nome do recurso}. */
    public Map<Integer, String> recursos() {
        return recursoPorPar;
    }

    /** Mapa imutavel {@code idPar -> nome do recurso} restrito aos pares {@code 1..pares}. */
    public Map<Integer, String> recursosParaPares(int pares) {
        if (pares < 1 || pares > quantidade()) {
            throw new IllegalArgumentException(
                    "Pares fora do intervalo 1.." + quantidade() + ": " + pares);
        }
        Map<Integer, String> parcial = new TreeMap<>();
        int copiados = 0;
        for (Map.Entry<Integer, String> e : recursoPorPar.entrySet()) {
            if (copiados++ == pares) {
                break;
            }
            parcial.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(parcial);
    }

    /** Indica se existe imagem para o par informado. */
    public boolean contem(int idPar) {
        return recursoPorPar.containsKey(idPar);
    }

    /** Nome do recurso (ex.: {@code "par-03.png"}) do par informado. */
    public String recursoDoPar(int idPar) {
        String recurso = recursoPorPar.get(idPar);
        if (recurso == null) {
            throw new NoSuchElementException("Par inexistente no catalogo: " + idPar);
        }
        return recurso;
    }

    /** Caminho absoluto no classpath do par (ex.: {@code "/images/par-03.png"}). */
    public String caminhoDoPar(int idPar) {
        return caminhoDoRecurso(recursoDoPar(idPar));
    }

    /** URL no classpath da imagem do par, se o arquivo existir. */
    public Optional<URL> urlDoPar(int idPar) {
        return urlDoRecurso(recursoDoPar(idPar));
    }

    /** Bytes da imagem do par, lidos do classpath. */
    public byte[] bytesDoPar(int idPar) {
        return lerBytes(recursoDoPar(idPar));
    }

    /** Caminho absoluto no classpath de um nome de recurso. */
    public static String caminhoDoRecurso(String nomeRecurso) {
        return DIRETORIO_CLASSPATH + nomeRecurso;
    }

    /** URL no classpath de um recurso em {@code /images/}, se existir. */
    public static Optional<URL> urlDoRecurso(String nomeRecurso) {
        return Optional.ofNullable(CatalogoImagens.class.getResource(caminhoDoRecurso(nomeRecurso)));
    }

    /** URL da imagem de verso (carta virada para baixo), se existir. */
    public static Optional<URL> urlDoVerso() {
        return urlDoRecurso(ARQUIVO_VERSO);
    }

    /** Bytes da imagem de verso, lidos do classpath. */
    public static byte[] bytesDoVerso() {
        return lerBytes(ARQUIVO_VERSO);
    }

    /** Le os bytes de um recurso em {@code /images/}. */
    public static byte[] lerBytes(String nomeRecurso) {
        String caminho = caminhoDoRecurso(nomeRecurso);
        try (InputStream in = CatalogoImagens.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Imagem nao encontrada no classpath: " + caminho);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + caminho, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CatalogoImagens outro && recursoPorPar.equals(outro.recursoPorPar);
    }

    @Override
    public int hashCode() {
        return recursoPorPar.hashCode();
    }

    @Override
    public String toString() {
        return "CatalogoImagens" + recursoPorPar;
    }
}
