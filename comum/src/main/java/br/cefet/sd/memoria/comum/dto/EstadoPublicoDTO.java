package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Snapshot publico da Partida; {@code idJogadorDaVez}, {@code nomeJogadorEvento} e {@code catalogo} podem ser nulos. */
public record EstadoPublicoDTO(long idPartida, long sequencia, int linhas, int colunas,
                               List<CartaPublicaDTO> cartas,
                               List<String> ordemJogadores, String idJogadorDaVez,
                               List<PlacarJogadorDTO> placar, StatusPartida status,
                               TipoEvento ultimoEvento, String nomeJogadorEvento,
                               CatalogoImagensDTO catalogo)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public EstadoPublicoDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        if (linhas < 1 || colunas < 1) {
            throw new IllegalArgumentException("linhas e colunas devem ser >= 1");
        }
        cartas = List.copyOf(Objects.requireNonNull(cartas, "cartas"));
        if (cartas.size() != (long) linhas * colunas) {
            throw new IllegalArgumentException("cartas.size() deve ser linhas*colunas");
        }
        ordemJogadores = List.copyOf(Objects.requireNonNull(ordemJogadores, "ordemJogadores"));
        placar = List.copyOf(Objects.requireNonNull(placar, "placar"));
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ultimoEvento, "ultimoEvento");
    }
}
