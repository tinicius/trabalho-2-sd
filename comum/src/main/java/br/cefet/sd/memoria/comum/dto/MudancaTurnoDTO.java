package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Notificacao de mudanca da vez. */
public record MudancaTurnoDTO(long idPartida, long sequencia, String idJogadorDaVez, String nomeJogadorDaVez)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public MudancaTurnoDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        Objects.requireNonNull(idJogadorDaVez, "idJogadorDaVez");
        Objects.requireNonNull(nomeJogadorDaVez, "nomeJogadorDaVez");
    }
}
