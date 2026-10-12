package br.cefet.sd.memoria.comum.dto;

import java.io.Serializable;

/** Pergunta "deseja continuar?" ao fim de uma Partida. */
public record PerguntaContinuarDTO(long idPartida, long sequencia, int prazoSeg) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PerguntaContinuarDTO {
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        if (prazoSeg < 0) {
            throw new IllegalArgumentException("prazoSeg deve ser >= 0");
        }
    }
}
