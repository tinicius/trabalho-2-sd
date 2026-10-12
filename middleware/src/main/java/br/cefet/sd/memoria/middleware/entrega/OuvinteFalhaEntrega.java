package br.cefet.sd.memoria.middleware.entrega;

import br.cefet.sd.memoria.comum.dto.CausaDesconexao;

/** Ouvinte notificado quando a entrega de um Callback a um Jogador falha. */
@FunctionalInterface
public interface OuvinteFalhaEntrega {

    /** Informa que o Callback do Jogador falhou, com a causa da falha. */
    void aoFalhar(String idJogador, CausaDesconexao causa);
}
