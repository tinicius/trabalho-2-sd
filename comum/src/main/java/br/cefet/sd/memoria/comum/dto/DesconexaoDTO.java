package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;
import java.util.Objects;

/** Evento de sala: um Jogador foi desconectado. */
public record DesconexaoDTO(long versaoSala, String nomeJogador, CausaDesconexao causa)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public DesconexaoDTO {
        if (versaoSala < 0) {
            throw new IllegalArgumentException("versaoSala deve ser >= 0");
        }
        Objects.requireNonNull(nomeJogador, "nomeJogador");
        Objects.requireNonNull(causa, "causa");
    }
}
