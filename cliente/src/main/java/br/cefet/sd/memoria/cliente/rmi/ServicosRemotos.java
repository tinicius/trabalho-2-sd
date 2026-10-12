package br.cefet.sd.memoria.cliente.rmi;

import br.cefet.sd.memoria.comum.remoto.ServicoPartida;
import br.cefet.sd.memoria.comum.remoto.ServicoSala;
import br.cefet.sd.memoria.comum.remoto.ServicoSessao;

import java.util.Objects;

/**
 * Conjunto imutável dos stubs dos três serviços do Middleware, obtido por {@link
 * ConexaoRmi#conectar(String, int)}.
 */
public record ServicosRemotos(String host, int porta, ServicoSala sala,
                              ServicoPartida partida, ServicoSessao sessao) {

    /** Valida que nenhum componente é nulo. */
    public ServicosRemotos {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(sala, "sala");
        Objects.requireNonNull(partida, "partida");
        Objects.requireNonNull(sessao, "sessao");
    }
}
