package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Resultado de uma Partida concluida: 1 vencedor, ou >= 2 em caso de empate. */
public record ResultadoPartidaDTO(long idPartida, long sequencia, boolean empate,
                                  List<String> nomesVencedores,
                                  List<PlacarJogadorDTO> placarFinal, int prazoContinuarSeg)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public ResultadoPartidaDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        nomesVencedores = List.copyOf(Objects.requireNonNull(nomesVencedores, "nomesVencedores"));
        placarFinal = List.copyOf(Objects.requireNonNull(placarFinal, "placarFinal"));
        if (nomesVencedores.isEmpty()) {
            throw new IllegalArgumentException("nomesVencedores nao pode ser vazio");
        }
        if (empate != (nomesVencedores.size() >= 2)) {
            throw new IllegalArgumentException("empate deve ser verdadeiro se e somente se houver >= 2 vencedores");
        }
        if (prazoContinuarSeg < 0) {
            throw new IllegalArgumentException("prazoContinuarSeg deve ser >= 0");
        }
    }
}
