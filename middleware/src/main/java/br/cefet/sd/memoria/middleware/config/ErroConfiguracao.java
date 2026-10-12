package br.cefet.sd.memoria.middleware.config;

import java.util.Objects;

/**
 * Erro de validação de um parâmetro de configuração.
 *
 * @param parametro parâmetro inválido (por exemplo, {@code registry.porta})
 * @param motivo    motivo da rejeição
 */
public record ErroConfiguracao(String parametro, String motivo) {

    /** Valida que parâmetro e motivo são informados. */
    public ErroConfiguracao {
        Objects.requireNonNull(parametro, "parametro");
        Objects.requireNonNull(motivo, "motivo");
    }

    @Override
    public String toString() {
        return parametro + ": " + motivo;
    }
}
