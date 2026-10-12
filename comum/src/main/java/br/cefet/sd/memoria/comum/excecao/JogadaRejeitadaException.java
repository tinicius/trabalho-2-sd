package br.cefet.sd.memoria.comum.excecao;

import java.util.Objects;

/** Jogada rejeitada pelo Middleware; carrega o {@link MotivoRejeicao}. */
public class JogadaRejeitadaException extends ExcecaoDominio {

    private static final long serialVersionUID = 1L;

    private final MotivoRejeicao motivo;

    public JogadaRejeitadaException(MotivoRejeicao motivo) {
        super(CodigoErro.JOGADA_REJEITADA,
                "Jogada rejeitada: " + Objects.requireNonNull(motivo, "motivo").getMensagem());
        this.motivo = motivo;
    }

    public MotivoRejeicao getMotivo() {
        return motivo;
    }
}
