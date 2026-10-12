package br.cefet.sd.memoria.cliente.rmi;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool de threads usado pelo Cliente para executar as chamadas RMI ao Middleware fora da thread de
 * interface (EDT).
 */
public final class ExecutorRmi {

    /** Número mínimo de threads do pool. */
    public static final int MINIMO_THREADS = 2;

    /** Número máximo de threads do pool. */
    public static final int MAXIMO_THREADS = 4;

    /** Capacidade da fila de tarefas pendentes antes de rejeitar novas submissões. */
    private static final int CAPACIDADE_FILA = 256;

    private static final long OCIOSIDADE_SEGUNDOS = 30L;

    private final ThreadPoolExecutor pool;

    /** Cria o pool com o máximo de {@link #MAXIMO_THREADS} threads. */
    public ExecutorRmi() {
        this(MAXIMO_THREADS);
    }

    /** Cria o pool com o máximo de threads informado. */
    public ExecutorRmi(int maximoThreads) {
        if (maximoThreads < MINIMO_THREADS || maximoThreads > MAXIMO_THREADS) {
            throw new IllegalArgumentException("maximoThreads deve estar entre " + MINIMO_THREADS
                    + " e " + MAXIMO_THREADS + ": " + maximoThreads);
        }
        // Com fila ilimitada o pool nunca passaria do núcleo; por isso a fila é limitada
        // e as threads além do núcleo só surgem quando a fila enche.
        this.pool = new ThreadPoolExecutor(MINIMO_THREADS, maximoThreads,
                OCIOSIDADE_SEGUNDOS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(CAPACIDADE_FILA),
                new FabricaThreadsDaemon());
    }

    /** Submete uma tarefa que produz resultado. */
    public <T> Future<T> submit(Callable<T> tarefa) {
        return pool.submit(tarefa);
    }

    /** Submete uma tarefa sem resultado. */
    public Future<?> submit(Runnable tarefa) {
        return pool.submit(tarefa);
    }

    /** Encerra o pool imediatamente, interrompendo as tarefas em execução. */
    public void shutdown() {
        pool.shutdownNow();
    }

    /** Informa se o pool já foi encerrado. */
    public boolean estaEncerrado() {
        return pool.isShutdown();
    }

    /** Fábrica de threads daemon nomeadas {@code rmi-cliente-N}. */
    private static final class FabricaThreadsDaemon implements ThreadFactory {
        private final AtomicInteger contador = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable tarefa) {
            Thread thread = new Thread(tarefa, "rmi-cliente-" + contador.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
