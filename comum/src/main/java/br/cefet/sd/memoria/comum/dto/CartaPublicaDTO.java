package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Carta como vista pelos Jogadores: {@code idPar != null} se e somente se o estado nao e FECHADA. */
public record CartaPublicaDTO(EstadoCartaDTO estado, Integer idPar) implements Serializable {

    private static final long serialVersionUID = 1L;

    public CartaPublicaDTO {
        Objects.requireNonNull(estado, "estado");
        if ((estado == EstadoCartaDTO.FECHADA) != (idPar == null)) {
            throw new IllegalArgumentException("idPar deve ser nulo se e somente se a carta esta FECHADA");
        }
        if (idPar != null && idPar < 1) {
            throw new IllegalArgumentException("idPar deve ser >= 1");
        }
    }
}
