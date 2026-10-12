package br.cefet.sd.memoria.middleware.nucleo;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de {@link Partida#fecharAposErro} e {@link Partida#expirarInatividade}.
 *
 * @param aplicado {@code true} se a Partida mudou de estado
 * @param eventos  eventos criados, em ordem
 * @param acao     ação sobre os temporizadores
 */
public record ResultadoTemporizador(boolean aplicado, List<EventoPartida> eventos, AcaoTemporizador acao) {

    /** Resultado de uma chamada ignorada. */
    public static final ResultadoTemporizador IGNORADO =
            new ResultadoTemporizador(false, List.of(), AcaoTemporizador.NENHUMA);

    /** Construtor canônico. */
    public ResultadoTemporizador {
        eventos = List.copyOf(Objects.requireNonNull(eventos, "eventos"));
        Objects.requireNonNull(acao, "acao");
    }
}
