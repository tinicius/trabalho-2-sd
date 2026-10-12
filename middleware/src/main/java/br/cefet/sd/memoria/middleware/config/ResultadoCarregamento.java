package br.cefet.sd.memoria.middleware.config;

import java.util.List;
import java.util.Optional;

/**
 * Resultado do carregamento da configuração do Middleware.
 *
 * @param config configuração válida, ou {@code null} se houver erros
 * @param erros  erros encontrados
 * @param avisos avisos não fatais
 */
public record ResultadoCarregamento(
        ConfiguracaoMiddleware config, List<ErroConfiguracao> erros, List<Aviso> avisos) {

    /** Copia as listas e garante a coerência entre {@code config} e {@code erros}. */
    public ResultadoCarregamento {
        erros = List.copyOf(erros);
        avisos = List.copyOf(avisos);
        if (erros.isEmpty() == (config == null)) {
            throw new IllegalArgumentException("config deve existir se e somente se não houver erros");
        }
    }

    /** Indica se o carregamento foi bem-sucedido (sem erros). */
    public boolean ok() {
        return erros.isEmpty();
    }

    /** Configuração carregada, ou vazio se houve erros. */
    public Optional<ConfiguracaoMiddleware> configuracao() {
        return Optional.ofNullable(config);
    }
}
