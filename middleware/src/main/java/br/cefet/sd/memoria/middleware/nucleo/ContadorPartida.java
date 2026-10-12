package br.cefet.sd.memoria.middleware.nucleo;

/**
 * Contadores de um Jogador em uma Partida, com {@code 0 <= pares <= tentativas}.
 *
 * @param pares      pares encontrados
 * @param tentativas Tentativas realizadas
 */
public record ContadorPartida(int pares, int tentativas) {

    /** Contador zerado. */
    static final ContadorPartida ZERO = new ContadorPartida(0, 0);

    /** Valida os contadores. */
    public ContadorPartida {
        if (pares < 0 || tentativas < 0) {
            throw new IllegalArgumentException("pares e tentativas devem ser >= 0");
        }
        if (pares > tentativas) {
            throw new IllegalArgumentException("pares deve ser <= tentativas");
        }
    }

    /** Valor após uma tentativa que formou par. */
    ContadorPartida comParEncontrado() {
        return new ContadorPartida(pares + 1, tentativas + 1);
    }

    /** Valor após uma tentativa que falhou. */
    ContadorPartida comErro() {
        return new ContadorPartida(pares, tentativas + 1);
    }
}
