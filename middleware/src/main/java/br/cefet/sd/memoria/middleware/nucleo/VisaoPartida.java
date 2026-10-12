package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.dto.StatusPartida;
import java.util.OptionalLong;

/** Visão somente leitura do estado necessária ao {@link ValidadorJogada}. */
public interface VisaoPartida {

    /** {@code true} se o id pertence a um participante conhecido e presente na Sala. */
    boolean participanteConhecido(String idParticipante);

    /** Identificador da Partida corrente, ou vazio se não houver Partida corrente. */
    OptionalLong idPartidaCorrente();

    /** Status da Partida corrente (só consultado se houver Partida corrente). */
    StatusPartida status();

    /** Id do Jogador da vez (só consultado se houver Partida corrente). */
    String idJogadorDaVez();

    /** Sub-estado da tentativa corrente. */
    SubEstado subEstado();

    /** Número total de cartas (n) do tabuleiro. */
    int tamanho();

    /** Posição da primeira carta da tentativa corrente, ou {@code -1} se não houver. */
    int primeiraPosicao();

    /** Estado da carta na posição dada, com {@code 0 <= posicao < tamanho()}. */
    EstadoCarta estadoCarta(int posicao);
}
