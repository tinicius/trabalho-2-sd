package br.cefet.sd.memoria.cliente.modelo;

import java.time.Duration;
import java.util.Objects;

/**
 * Parâmetros informados pelo Jogador na tela de conexão, já validados por {@link
 * ValidadorFormularioConexao}.
 */
public record ParametrosConexao(String nome, String host, int porta, Duration timeout,
                                String rmiHostname) {

    /** Rejeita valores nulos. */
    public ParametrosConexao {
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(rmiHostname, "rmiHostname");
    }
}
