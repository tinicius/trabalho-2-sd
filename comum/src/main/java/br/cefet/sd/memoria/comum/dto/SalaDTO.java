package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Estado publico da Sala, versionado por {@code versao}. */
public record SalaDTO(long versao, List<ParticipanteDTO> participantes, int capacidade, FaseSala fase)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public SalaDTO {
        if (versao < 0) {
            throw new IllegalArgumentException("versao deve ser >= 0");
        }
        participantes = List.copyOf(Objects.requireNonNull(participantes, "participantes"));
        if (capacidade < 1) {
            throw new IllegalArgumentException("capacidade deve ser >= 1");
        }
        if (participantes.size() > capacidade) {
            throw new IllegalArgumentException("participantes excede a capacidade");
        }
        Objects.requireNonNull(fase, "fase");
    }
}
