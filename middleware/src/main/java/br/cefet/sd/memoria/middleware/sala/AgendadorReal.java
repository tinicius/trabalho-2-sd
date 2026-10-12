package br.cefet.sd.memoria.middleware.sala;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@link Agendador} real, baseado em uma única thread daemon. */
public final class AgendadorReal implements Agendador {

    private static final Logger LOG = Logger.getLogger(AgendadorReal.class.getName());
    private static final Cancelavel INERTE = () -> false;

    private final ScheduledThreadPoolExecutor executor;

    /** Cria o agendador de uma Sala; {@code nomeSala} nomeia a thread. */
    public AgendadorReal(String nomeSala) {
        Objects.requireNonNull(nomeSala, "nomeSala");
        this.executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "temporizadores-" + nomeSala);
            t.setDaemon(true);
            return t;
        });
        this.executor.setRemoveOnCancelPolicy(true);
        this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public Cancelavel agendar(Duration atraso, Runnable tarefa) {
        Objects.requireNonNull(atraso, "atraso");
        Objects.requireNonNull(tarefa, "tarefa");
        long nanos = atraso.isNegative() ? 0L : saturar(atraso);
        try {
            ScheduledFuture<?> futuro = executor.schedule(tarefa, nanos, TimeUnit.NANOSECONDS);
            return () -> futuro.cancel(false);
        } catch (RejectedExecutionException e) {
            LOG.log(Level.FINE, "Agendamento ignorado: agendador encerrado.", e);
            return INERTE;
        }
    }

    /** Encerra a thread e descarta as tarefas pendentes. Idempotente. */
    public void encerrar() {
        executor.shutdownNow();
    }

    private static long saturar(Duration d) {
        try {
            return d.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
