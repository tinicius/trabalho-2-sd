package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Visao geral para consulta de estado; {@code partida} e nulo quando nao ha Partida. */
public record VisaoGeralDTO(SalaDTO sala, EstadoPublicoDTO partida) implements Serializable {

    private static final long serialVersionUID = 1L;

    public VisaoGeralDTO {
        Objects.requireNonNull(sala, "sala");
    }
}
