package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Sumario final da Sessao. */
public record SumarioSessaoDTO(long idPartida, long sequencia, int partidasConcluidas, int partidasCanceladas,
                               List<List<String>> empates, List<SumarioJogadorDTO> jogadores,
                               List<String> campeoes)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public SumarioSessaoDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        if (partidasConcluidas < 0 || partidasCanceladas < 0) {
            throw new IllegalArgumentException("contagens de partidas devem ser >= 0");
        }
        Objects.requireNonNull(empates, "empates");
        empates = empates.stream().map(e -> List.copyOf(Objects.requireNonNull(e, "empate"))).toList();
        jogadores = List.copyOf(Objects.requireNonNull(jogadores, "jogadores"));
        campeoes = List.copyOf(Objects.requireNonNull(campeoes, "campeoes"));
    }
}
