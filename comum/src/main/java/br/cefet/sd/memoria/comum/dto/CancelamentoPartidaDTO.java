package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Cancelamento da Partida por desconexao de um Jogador. */
public record CancelamentoPartidaDTO(long idPartida, long sequencia, String nomeJogadorDesconectado,
                                     CausaDesconexao causa)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public CancelamentoPartidaDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        Objects.requireNonNull(nomeJogadorDesconectado, "nomeJogadorDesconectado");
        Objects.requireNonNull(causa, "causa");
    }
}
