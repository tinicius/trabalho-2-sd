package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Estatisticas acumuladas de um Jogador na Sessao. */
public record SumarioJogadorDTO(String nome, int vitorias, int pares, int tentativas,
                                BigDecimal aproveitamento, boolean semTentativas, boolean desconectado)
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public SumarioJogadorDTO {
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(aproveitamento, "aproveitamento");
        if (vitorias < 0 || pares < 0 || tentativas < 0) {
            throw new IllegalArgumentException("vitorias, pares e tentativas devem ser >= 0");
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
