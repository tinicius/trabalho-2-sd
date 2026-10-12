package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Resultado de uma entrada bem-sucedida na Sala. */
public record EntradaSalaDTO(String idParticipante, CatalogoImagensDTO catalogo, SalaDTO sala)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public EntradaSalaDTO {
        Objects.requireNonNull(idParticipante, "idParticipante");
        Objects.requireNonNull(catalogo, "catalogo");
        Objects.requireNonNull(sala, "sala");
    }
}
