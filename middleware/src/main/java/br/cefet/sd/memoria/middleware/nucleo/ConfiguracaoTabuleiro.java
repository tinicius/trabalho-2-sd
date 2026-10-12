package br.cefet.sd.memoria.middleware.nucleo;

import java.util.Optional;

/**
 * Configuração imutável das dimensões do tabuleiro; validada por {@link #validar(int, int, int)}.
 *
 * @param linhas  número de linhas do tabuleiro
 * @param colunas número de colunas do tabuleiro
 */
public record ConfiguracaoTabuleiro(int linhas, int colunas) {

    /** Número mínimo de posições aceito em um tabuleiro. */
    public static final int MINIMO_DE_POSICOES = 4;

    /** Configuração padrão: tabuleiro 2x3 (6 posições, 3 pares). */
    public static final ConfiguracaoTabuleiro PADRAO = new ConfiguracaoTabuleiro(2, 3);

    /** Causa de rejeição de uma configuração de tabuleiro. */
    public enum Causa {
        /** O número total de posições é ímpar, menor que 4 ou não positivo. */
        NUMERO_DE_POSICOES_INVALIDO,
        /** O número de pares exigido excede o número de imagens distintas do catálogo. */
        IMAGENS_INSUFICIENTES
    }

    /**
     * Resultado da validação de uma configuração de tabuleiro.
     *
     * @param causa causa da rejeição; vazio quando aceita
     */
    public record Resultado(Optional<Causa> causa) {

        private static final Resultado ACEITO = new Resultado(Optional.empty());

        /** Valida que a causa não é nula (use {@link Optional#empty()} para aceito). */
        public Resultado {
            if (causa == null) {
                throw new IllegalArgumentException("causa não pode ser nula");
            }
        }

        /** Cria o resultado de aceitação. */
        public static Resultado aceito() {
            return ACEITO;
        }

        /** Cria um resultado de rejeição com a causa informada. */
        public static Resultado rejeitado(Causa causa) {
            if (causa == null) {
                throw new IllegalArgumentException("causa não pode ser nula");
            }
            return new Resultado(Optional.of(causa));
        }

        /** Indica se a configuração foi aceita. */
        public boolean foiAceito() {
            return causa.isEmpty();
        }
    }

    /**
     * Valida linhas e colunas: total de posições par e no mínimo 4, com pares não superiores ao
     * número de imagens do catálogo.
     */
    public static Resultado validar(int linhas, int colunas, int numeroDeImagens) {
        if (linhas <= 0 || colunas <= 0) {
            return Resultado.rejeitado(Causa.NUMERO_DE_POSICOES_INVALIDO);
        }
        long posicoes = (long) linhas * (long) colunas;
        if (posicoes < MINIMO_DE_POSICOES || posicoes % 2 != 0) {
            return Resultado.rejeitado(Causa.NUMERO_DE_POSICOES_INVALIDO);
        }
        if (posicoes / 2 > numeroDeImagens) {
            return Resultado.rejeitado(Causa.IMAGENS_INSUFICIENTES);
        }
        return Resultado.aceito();
    }

    /** Número total de posições do tabuleiro ({@code linhas * colunas}). */
    public int posicoes() {
        return linhas * colunas;
    }
}
