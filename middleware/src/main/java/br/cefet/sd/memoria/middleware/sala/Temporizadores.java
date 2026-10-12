package br.cefet.sd.memoria.middleware.sala;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Temporizadores de uma Sala: fim do Intervalo_de_Erro, inatividade da vez e prazo de resposta
 * de "continuar". Cada agendamento repassa a época da Sala ao consumidor quando dispara.
 */
public final class Temporizadores {

    private static final Logger LOG = Logger.getLogger(Temporizadores.class.getName());

    private final Agendador agendador;
    private final Duration inatividade;
    private final Duration prazoContinuar;
    private volatile Supplier<Duration> intervaloErro;

    private Cancelavel slotErro;
    private Cancelavel slotInatividade;
    private Cancelavel slotPrazoContinuar;

    /** Cria os temporizadores com Intervalo_de_Erro fixo (alterável por {@link #definirIntervaloErro}). */
    public Temporizadores(Agendador agendador, Duration intervaloErro, Duration inatividade,
            Duration prazoContinuar) {
        this(agendador, constante(intervaloErro), inatividade, prazoContinuar);
    }

    /** Cria os temporizadores com Intervalo_de_Erro lido de um {@link Supplier} a cada agendamento. */
    public Temporizadores(Agendador agendador, Supplier<Duration> intervaloErro, Duration inatividade,
            Duration prazoContinuar) {
        this.agendador = Objects.requireNonNull(agendador, "agendador");
        this.intervaloErro = Objects.requireNonNull(intervaloErro, "intervaloErro");
        this.inatividade = validar(inatividade, "inatividade");
        this.prazoContinuar = validar(prazoContinuar, "prazoContinuar");
    }

    /** Altera a duração do Intervalo_de_Erro para os próximos agendamentos. */
    public void definirIntervaloErro(Duration novo) {
        this.intervaloErro = constante(novo);
    }

    /** Agenda o fim do Intervalo_de_Erro, cancelando o agendamento anterior deste tipo. */
    public synchronized void agendarFimDoErro(long epoca, LongConsumer tarefa) {
        Duration atraso = validar(intervaloErro.get(), "intervaloErro");
        cancelar(slotErro);
        slotErro = agendar(atraso, epoca, tarefa, "fim do erro");
    }

    /** Agenda o limite de inatividade da vez, cancelando o agendamento anterior deste tipo. */
    public synchronized void agendarInatividade(long epoca, LongConsumer tarefa) {
        cancelar(slotInatividade);
        slotInatividade = agendar(inatividade, epoca, tarefa, "inatividade");
    }

    /** Agenda o prazo de resposta de "continuar", cancelando o agendamento anterior deste tipo. */
    public synchronized void agendarPrazoContinuar(long epoca, LongConsumer tarefa) {
        cancelar(slotPrazoContinuar);
        slotPrazoContinuar = agendar(prazoContinuar, epoca, tarefa, "prazo de continuar");
    }

    /** Cancela o temporizador do Intervalo_de_Erro, se houver. */
    public synchronized void cancelarFimDoErro() {
        cancelar(slotErro);
        slotErro = null;
    }

    /** Cancela o temporizador de inatividade, se houver. */
    public synchronized void cancelarInatividade() {
        cancelar(slotInatividade);
        slotInatividade = null;
    }

    /** Cancela o temporizador do prazo de continuar, se houver. */
    public synchronized void cancelarPrazoContinuar() {
        cancelar(slotPrazoContinuar);
        slotPrazoContinuar = null;
    }

    /** Cancela todos os temporizadores pendentes (os três tipos). */
    public synchronized void cancelarTudo() {
        cancelarFimDoErro();
        cancelarInatividade();
        cancelarPrazoContinuar();
    }

    private Cancelavel agendar(Duration atraso, long epoca, LongConsumer tarefa, String nome) {
        Objects.requireNonNull(tarefa, "tarefa");
        return agendador.agendar(atraso, () -> {
            try {
                tarefa.accept(epoca);
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Falha na tarefa de temporizador (" + nome + ", época " + epoca + ")", t);
            }
        });
    }

    private static void cancelar(Cancelavel c) {
        if (c != null) {
            c.cancel();
        }
    }

    private static Supplier<Duration> constante(Duration d) {
        validar(d, "intervaloErro");
        return () -> d;
    }

    private static Duration validar(Duration d, String nome) {
        Objects.requireNonNull(d, nome);
        if (d.isNegative()) {
            throw new IllegalArgumentException(nome + " negativo: " + d);
        }
        return d;
    }
}
