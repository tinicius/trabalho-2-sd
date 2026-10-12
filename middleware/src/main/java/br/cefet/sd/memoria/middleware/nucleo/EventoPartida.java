package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import java.io.Serializable;
import java.util.Objects;

/**
 * Evento de Partida pronto para publicação: sequência, tipo de Callback e o DTO correspondente.
 *
 * @param sequencia número da sequência da Partida (&gt;= 1)
 * @param tipo      Callback ao qual o evento corresponde
 * @param payload   DTO a entregar
 */
public record EventoPartida(long sequencia, Tipo tipo, Serializable payload) {

    /** Callback correspondente ao evento. */
    public enum Tipo {
        /** {@code aoAtualizarTabuleiro(EstadoPublicoDTO)}. */
        TABULEIRO,
        /** {@code aoMudarTurno(MudancaTurnoDTO)}. */
        TURNO,
        /** {@code aoAtualizarPlacar(PlacarDTO)}. */
        PLACAR,
        /** {@code aoFinalizarPartida(ResultadoPartidaDTO)}. */
        RESULTADO,
        /** {@code aoPerguntarContinuar(PerguntaContinuarDTO)}. */
        PERGUNTA,
        /** {@code aoCancelarPartida(CancelamentoPartidaDTO)}. */
        CANCELAMENTO
    }

    /** Valida que o payload corresponde ao tipo e à sequência. */
    public EventoPartida {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(payload, "payload");
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        long dtoSequencia = switch (tipo) {
            case TABULEIRO -> exigir(payload, EstadoPublicoDTO.class).sequencia();
            case TURNO -> exigir(payload, MudancaTurnoDTO.class).sequencia();
            case PLACAR -> exigir(payload, PlacarDTO.class).sequencia();
            case RESULTADO -> exigir(payload, ResultadoPartidaDTO.class).sequencia();
            case PERGUNTA -> exigir(payload, PerguntaContinuarDTO.class).sequencia();
            case CANCELAMENTO -> exigir(payload, CancelamentoPartidaDTO.class).sequencia();
        };
        if (dtoSequencia != sequencia) {
            throw new IllegalArgumentException("sequencia do evento difere da sequencia do DTO");
        }
    }

    private static <T> T exigir(Serializable payload, Class<T> tipo) {
        if (!tipo.isInstance(payload)) {
            throw new IllegalArgumentException("payload deve ser " + tipo.getSimpleName());
        }
        return tipo.cast(payload);
    }

    /** Cria evento de tabuleiro. */
    public static EventoPartida tabuleiro(EstadoPublicoDTO dto) {
        return new EventoPartida(dto.sequencia(), Tipo.TABULEIRO, dto);
    }

    /** Cria evento de mudança de turno. */
    public static EventoPartida turno(MudancaTurnoDTO dto) {
        return new EventoPartida(dto.sequencia(), Tipo.TURNO, dto);
    }

    /** Cria evento de placar. */
    public static EventoPartida placar(PlacarDTO dto) {
        return new EventoPartida(dto.sequencia(), Tipo.PLACAR, dto);
    }

    /** Cria evento de resultado. */
    public static EventoPartida resultado(ResultadoPartidaDTO dto) {
        return new EventoPartida(dto.sequencia(), Tipo.RESULTADO, dto);
    }

    /** Cria evento de pergunta "deseja continuar?". */
    public static EventoPartida pergunta(PerguntaContinuarDTO dto) {
        return new EventoPartida(dto.sequencia(), Tipo.PERGUNTA, dto);
    }

    /** Cria evento de cancelamento. */
    public static EventoPartida cancelamento(CancelamentoPartidaDTO dto) {
        return new EventoPartida(dto.sequencia(), Tipo.CANCELAMENTO, dto);
    }

    /** Payload como {@link EstadoPublicoDTO}. @throws ClassCastException se o tipo não for TABULEIRO */
    public EstadoPublicoDTO estadoPublico() {
        return (EstadoPublicoDTO) payload;
    }

    /** Payload como {@link MudancaTurnoDTO}. @throws ClassCastException se o tipo não for TURNO */
    public MudancaTurnoDTO mudancaTurno() {
        return (MudancaTurnoDTO) payload;
    }

    /** Payload como {@link PlacarDTO}. @throws ClassCastException se o tipo não for PLACAR */
    public PlacarDTO placarDto() {
        return (PlacarDTO) payload;
    }

    /** Payload como {@link ResultadoPartidaDTO}. @throws ClassCastException se o tipo não for RESULTADO */
    public ResultadoPartidaDTO resultadoPartida() {
        return (ResultadoPartidaDTO) payload;
    }

    /** Payload como {@link PerguntaContinuarDTO}. @throws ClassCastException se o tipo não for PERGUNTA */
    public PerguntaContinuarDTO perguntaContinuar() {
        return (PerguntaContinuarDTO) payload;
    }

    /** Payload como {@link CancelamentoPartidaDTO}. @throws ClassCastException se o tipo não for CANCELAMENTO */
    public CancelamentoPartidaDTO cancelamentoPartida() {
        return (CancelamentoPartidaDTO) payload;
    }
}
