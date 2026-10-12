package br.cefet.sd.memoria.middleware.nucleo;

import java.util.Objects;

/**
 * Resultado de um Jogador em uma Partida concluída, entrada de
 * {@link Sessao#registrarConcluida(java.util.List)}.
 *
 * @param idJogador  identificador estável do participante
 * @param nome       nome de exibição do Jogador
 * @param pares      pares encontrados; {@code 0 <= pares <= tentativas}
 * @param tentativas tentativas realizadas
 */
public record ResultadoJogadorPartida(String idJogador, String nome, int pares, int tentativas) {

    /** Valida os dados do resultado. */
    public ResultadoJogadorPartida {
        Objects.requireNonNull(idJogador, "idJogador");
        Objects.requireNonNull(nome, "nome");
        if (pares < 0 || tentativas < 0) {
            throw new IllegalArgumentException("pares e tentativas devem ser >= 0");
        }
        if (pares > tentativas) {
            throw new IllegalArgumentException(
                    "pares (" + pares + ") não pode exceder tentativas (" + tentativas + ")");
        }
    }
}
