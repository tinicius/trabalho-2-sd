package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Placar de um Jogador; aproveitamento em [0, 100]. */
public record PlacarJogadorDTO(String idParticipante, String nome, int pares, int tentativas,
                               BigDecimal aproveitamento, boolean semTentativas)
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public PlacarJogadorDTO {
        Objects.requireNonNull(idParticipante, "idParticipante");
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(aproveitamento, "aproveitamento");
        if (pares < 0 || tentativas < 0) {
            throw new IllegalArgumentException("pares e tentativas devem ser >= 0");
        }
        if (pares > tentativas) {
            throw new IllegalArgumentException("pares deve ser <= tentativas");
        }
        if (aproveitamento.signum() < 0 || aproveitamento.compareTo(CEM) > 0) {
            throw new IllegalArgumentException("aproveitamento deve estar entre 0 e 100");
        }
        if (semTentativas != (tentativas == 0)) {
            throw new IllegalArgumentException("semTentativas deve ser verdadeiro se e somente se tentativas == 0");
        }
    }
}
