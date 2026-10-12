package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Participante visivel na Sala. */
public record ParticipanteDTO(String id, String nome, boolean pronto, int ordemEntrada)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public ParticipanteDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nome, "nome");
    }
}
