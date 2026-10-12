package br.cefet.sd.memoria.cliente.ui;

import br.cefet.sd.memoria.cliente.modelo.ParametrosConexao;

/** Ouvinte da {@link TelaConexao}, chamado na EDT somente com dados já validados. */
@FunctionalInterface
public interface OuvinteTelaConexao {

    /**
     * O Jogador confirmou um formulário válido. O dono da tela deve conectar em segundo plano
     * (nunca na EDT) e, em caso de falha, chamar {@link TelaConexao#mostrarFalha(String, String,
     * int)}.
     */
    void aoConectar(ParametrosConexao parametros);
}
