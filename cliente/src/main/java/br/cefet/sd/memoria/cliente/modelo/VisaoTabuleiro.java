package br.cefet.sd.memoria.cliente.modelo;

import br.cefet.sd.memoria.comum.dto.CartaPublicaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoCartaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.StatusPartida;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Visão imutável do tabuleiro, derivada de forma pura de um {@link EstadoPublicoDTO}. */
public record VisaoTabuleiro(int linhas, int colunas, List<Celula> celulas,
                             boolean podeSelecionar, boolean minhaVez, String idJogadorDaVez) {

    /** Célula do tabuleiro. */
    public record Celula(EstadoCartaDTO estado, Integer idPar, boolean habilitada) {
    }

    /** Valida e torna imutável a lista de células. */
    public VisaoTabuleiro {
        Objects.requireNonNull(celulas, "celulas");
        celulas = List.copyOf(celulas);
        if (celulas.size() != (long) linhas * colunas) {
            throw new IllegalArgumentException("celulas.size() deve ser linhas*colunas");
        }
    }

    /** Obtém a célula de uma posição. */
    public Celula celula(int linha, int coluna) {
        if (linha < 0 || linha >= linhas || coluna < 0 || coluna >= colunas) {
            throw new IndexOutOfBoundsException("posição fora do tabuleiro: " + linha + "," + coluna);
        }
        return celulas.get(linha * colunas + coluna);
    }

    /** Mapeia o estado público para a visão, assumindo que não há falha de sincronização. */
    public static VisaoTabuleiro de(EstadoPublicoDTO estado, String meuId, boolean conexaoPerdida) {
        return de(estado, meuId, conexaoPerdida, false);
    }

    /** Mapeia o estado público para a visão do tabuleiro. */
    public static VisaoTabuleiro de(EstadoPublicoDTO estado, String meuId,
                                    boolean conexaoPerdida, boolean falhaSincronizacao) {
        Objects.requireNonNull(estado, "estado não pode ser nulo");
        boolean minhaVez = meuId != null && meuId.equals(estado.idJogadorDaVez());
        boolean jogoLivre = estado.status() == StatusPartida.EM_ANDAMENTO
                && !conexaoPerdida && !falhaSincronizacao;
        boolean podeJogar = minhaVez && jogoLivre;

        List<Celula> celulas = new ArrayList<>(estado.cartas().size());
        boolean algumaHabilitada = false;
        for (CartaPublicaDTO carta : estado.cartas()) {
            boolean habilitada = podeJogar && carta.estado() == EstadoCartaDTO.FECHADA;
            algumaHabilitada |= habilitada;
            celulas.add(new Celula(carta.estado(), carta.idPar(), habilitada));
        }
        return new VisaoTabuleiro(estado.linhas(), estado.colunas(), celulas,
                algumaHabilitada, minhaVez, estado.idJogadorDaVez());
    }
}
