package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Notificacao de atualizacao do placar. */
public record PlacarDTO(long idPartida, long sequencia, List<PlacarJogadorDTO> placar)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public PlacarDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        placar = List.copyOf(Objects.requireNonNull(placar, "placar"));
    }
}
