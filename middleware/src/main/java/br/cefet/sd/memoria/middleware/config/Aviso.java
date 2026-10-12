package br.cefet.sd.memoria.middleware.config;

import java.util.Objects;

/**
 * Aviso não fatal emitido durante o carregamento da configuração.
 *
 * @param parametro parâmetro a que o aviso se refere
 * @param motivo    descrição do aviso
 */
public record Aviso(String parametro, String motivo) {

    /** Valida que parâmetro e motivo são informados. */
    public Aviso {
        Objects.requireNonNull(parametro, "parametro");
        Objects.requireNonNull(motivo, "motivo");
    }

    @Override
    public String toString() {
        return parametro + ": " + motivo;
    }
}
