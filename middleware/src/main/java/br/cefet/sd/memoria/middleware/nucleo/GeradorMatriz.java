package br.cefet.sd.memoria.middleware.nucleo;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Gerador da matriz secreta de identificadores do jogo da memória (cada id de 1 a n/2 duas vezes). */
public final class GeradorMatriz {

    /** Número mínimo de posições do tabuleiro. */
    private static final int MINIMO_POSICOES = 4;

    private GeradorMatriz() {
    }

    /** Gera uma matriz embaralhada (Fisher-Yates) de {@code n} posições, par e maior ou igual a 4. */
    public static int[] gerar(int n, RandomGenerator rng) {
        validarN(n);
        Objects.requireNonNull(rng, "rng");
        int[] m = new int[n];
        for (int i = 0; i < n; i++) {
            m[i] = i / 2 + 1;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = m[i];
            m[i] = m[j];
            m[j] = t;
        }
        return m;
    }

    /** Gera uma matriz usando uma instância nova de {@link SecureRandom}. */
    public static int[] gerarProducao(int n) {
        return gerar(n, new SecureRandom());
    }

    /** Valida uma disposição fornecida e devolve uma cópia dela. */
    public static int[] validarDisposicao(int[] ids, int n) {
        validarN(n);
        if (ids == null) {
            throw new IllegalArgumentException("Disposição ausente.");
        }
        if (ids.length != n) {
            throw new IllegalArgumentException(
                    "Disposição com tamanho " + ids.length + ", esperado " + n + ".");
        }
        int[] ocorrencias = new int[n / 2 + 1];
        for (int id : ids) {
            if (id < 1 || id > n / 2) {
                throw new IllegalArgumentException("Identificador fora do intervalo 1.." + n / 2 + ": " + id);
            }
            if (++ocorrencias[id] > 2) {
                throw new IllegalArgumentException("Identificador repetido mais de duas vezes: " + id);
            }
        }
        return ids.clone();
    }

    private static void validarN(int n) {
        if (n < MINIMO_POSICOES || n % 2 != 0) {
            throw new IllegalArgumentException(
                    "Número de posições deve ser par e >= " + MINIMO_POSICOES + ": " + n);
        }
    }
}
