package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.excecao.MotivoRejeicao;
import java.util.List;
import java.util.Objects;

/** Resultado de {@link Partida#selecionar}: rejeição ou aceitação com eventos e ação sobre temporizadores. */
public sealed interface ResultadoSelecao {

    /** Seleção rejeitada, sem alterar o estado da Partida. */
    record Rejeitada(MotivoRejeicao motivo) implements ResultadoSelecao {
        /** Construtor canônico. */
        public Rejeitada {
            Objects.requireNonNull(motivo, "motivo");
        }
    }

    /** Seleção aceita, com os eventos criados (em ordem) e a ação sobre os temporizadores. */
    record Aceita(List<EventoPartida> eventos, AcaoTemporizador acao) implements ResultadoSelecao {
        /** Construtor canônico. */
        public Aceita {
            eventos = List.copyOf(Objects.requireNonNull(eventos, "eventos"));
            Objects.requireNonNull(acao, "acao");
        }
    }
}
