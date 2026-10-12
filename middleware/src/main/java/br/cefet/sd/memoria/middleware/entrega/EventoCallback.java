package br.cefet.sd.memoria.middleware.entrega;

import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;
import br.cefet.sd.memoria.middleware.nucleo.EventoPartida;
import java.rmi.RemoteException;
import java.util.Objects;

/** Notificação imutável a ser entregue a um {@link CallbackJogador}, com metadados para log. */
public final class EventoCallback {

    /** Valor de {@link #sequencia()} / {@link #versaoSala()} quando não se aplica. */
    public static final long SEM_NUMERO = -1L;

    /** Invocação de um método de {@link CallbackJogador}. */
    @FunctionalInterface
    public interface Invocacao {
        /** Executa a chamada no Callback. */
        void executar(CallbackJogador callback) throws RemoteException;
    }

    private final String descricao;
    private final long sequencia;
    private final long versaoSala;
    private final Invocacao invocacao;

    private EventoCallback(String descricao, long sequencia, long versaoSala, Invocacao invocacao) {
        this.descricao = Objects.requireNonNull(descricao, "descricao");
        this.sequencia = sequencia;
        this.versaoSala = versaoSala;
        this.invocacao = Objects.requireNonNull(invocacao, "invocacao");
    }

    /** Cria um evento genérico com descrição para log, sequência, versão de sala e invocação. */
    public static EventoCallback de(String descricao, long sequencia, long versaoSala, Invocacao invocacao) {
        return new EventoCallback(descricao, sequencia, versaoSala, invocacao);
    }

    /** Cria o evento que chama o método de Callback correspondente a um evento de Partida. */
    public static EventoCallback de(EventoPartida evento) {
        Objects.requireNonNull(evento, "evento");
        long seq = evento.sequencia();
        return switch (evento.tipo()) {
            case TABULEIRO -> new EventoCallback("tabuleiro#" + seq, seq, SEM_NUMERO,
                    cb -> cb.aoAtualizarTabuleiro(evento.estadoPublico()));
            case TURNO -> new EventoCallback("turno#" + seq, seq, SEM_NUMERO,
                    cb -> cb.aoMudarTurno(evento.mudancaTurno()));
            case PLACAR -> new EventoCallback("placar#" + seq, seq, SEM_NUMERO,
                    cb -> cb.aoAtualizarPlacar(evento.placarDto()));
            case RESULTADO -> new EventoCallback("resultado#" + seq, seq, SEM_NUMERO,
                    cb -> cb.aoFinalizarPartida(evento.resultadoPartida()));
            case PERGUNTA -> new EventoCallback("pergunta#" + seq, seq, SEM_NUMERO,
                    cb -> cb.aoPerguntarContinuar(evento.perguntaContinuar()));
            case CANCELAMENTO -> new EventoCallback("cancelamento#" + seq, seq, SEM_NUMERO,
                    cb -> cb.aoCancelarPartida(evento.cancelamentoPartida()));
        };
    }

    /** Cria o evento de atualização da Sala. */
    public static EventoCallback sala(SalaDTO sala) {
        Objects.requireNonNull(sala, "sala");
        return new EventoCallback("sala@v" + sala.versao(), SEM_NUMERO, sala.versao(),
                cb -> cb.aoAtualizarSala(sala));
    }

    /** Cria o evento de desconexão de um Jogador. */
    public static EventoCallback desconexao(DesconexaoDTO desconexao) {
        Objects.requireNonNull(desconexao, "desconexao");
        return new EventoCallback("desconexao@v" + desconexao.versaoSala(), SEM_NUMERO,
                desconexao.versaoSala(), cb -> cb.aoDesconectarJogador(desconexao));
    }

    /** Cria o evento de encerramento da Sessão com o sumário. */
    public static EventoCallback sumario(SumarioSessaoDTO sumario) {
        Objects.requireNonNull(sumario, "sumario");
        return new EventoCallback("sumario#" + sumario.sequencia(), sumario.sequencia(), SEM_NUMERO,
                cb -> cb.aoEncerrarSessao(sumario));
    }

    /** Invoca o método de Callback correspondente a este evento. */
    public void invocar(CallbackJogador callback) throws RemoteException {
        invocacao.executar(callback);
    }

    /** Descrição curta para log. */
    public String descricao() {
        return descricao;
    }

    /** {@code Sequencia_de_Estado} do evento, ou {@link #SEM_NUMERO} para eventos de sala. */
    public long sequencia() {
        return sequencia;
    }

    /** Versão de sala do evento, ou {@link #SEM_NUMERO} para eventos de Partida/Sessão. */
    public long versaoSala() {
        return versaoSala;
    }

    @Override
    public String toString() {
        return "EventoCallback[" + descricao + "]";
    }
}
