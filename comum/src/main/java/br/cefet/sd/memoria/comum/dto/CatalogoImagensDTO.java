package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Catalogo de imagens: idPar (1..N) -> nome do recurso distinto. */
public record CatalogoImagensDTO(Map<Integer, String> recursoPorPar) implements Serializable {

    private static final long serialVersionUID = 1L;

    public CatalogoImagensDTO {
        recursoPorPar = Map.copyOf(Objects.requireNonNull(recursoPorPar, "recursoPorPar"));
        for (int id = 1; id <= recursoPorPar.size(); id++) {
            if (!recursoPorPar.containsKey(id)) {
                throw new IllegalArgumentException("chaves devem ser 1.." + recursoPorPar.size());
            }
        }
        if (new HashSet<>(recursoPorPar.values()).size() != recursoPorPar.size()) {
            throw new IllegalArgumentException("recursos devem ser distintos");
        }
    }
}
