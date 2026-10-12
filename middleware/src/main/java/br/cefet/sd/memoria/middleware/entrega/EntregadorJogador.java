package br.cefet.sd.memoria.middleware.entrega;

import br.cefet.sd.memoria.comum.dto.CausaDesconexao;
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entrega ordenada de notificações a um único Jogador, com fila e worker próprios; em caso de
 * falha ou timeout notifica o {@link OuvinteFalhaEntrega} uma vez.
 */
public final class EntregadorJogador implements AutoCloseable {

    private enum Estado {
        /** Aceita novos eventos. */
        ABERTO,
        /** Não aceita novos eventos, mas entrega o que já está na fila. */
        ESVAZIANDO,
        /** Um Callback falhou; fila descartada e ouvinte notificado. */
        FALHOU,
        /** Fechado por {@link #fecharAgora()}; fila descartada, sem notificar o ouvinte. */
        FECHADO
    }

    /** Sentinela (pílula de veneno) que encerra o worker após esvaziar a fila. */
    private static final EventoCallback FIM = EventoCallback.de("FIM", EventoCallback.SEM_NUMERO,
            EventoCallback.SEM_NUMERO, cb -> { });

    private static final AtomicInteger CONTADOR_POOL = new AtomicInteger();

    private final String idJogador;
    private final CallbackJogador callback;
    private final Duration timeout;
    private final ExecutorService poolCallbacks;
    private final OuvinteFalhaEntrega ouvinte;

    private final BlockingQueue<EventoCallback> fila = new LinkedBlockingQueue<>();
    private final AtomicReference<Estado> estado = new AtomicReference<>(Estado.ABERTO);
    private final Thread worker;

    /** Cria o entregador e inicia o worker do Jogador. */
    public EntregadorJogador(String idJogador, CallbackJogador callback, Duration timeout,
                             ExecutorService poolCallbacks, OuvinteFalhaEntrega ouvinte) {
        this.idJogador = Objects.requireNonNull(idJogador, "idJogador");
        this.callback = Objects.requireNonNull(callback, "callback");
        this.timeout = exigirPositivo(timeout);
        this.poolCallbacks = Objects.requireNonNull(poolCallbacks, "poolCallbacks");
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        this.worker = new Thread(this::executar, "entregador-" + idJogador);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Cria o pool compartilhado de chamadas de Callback (threads daemon). */
    public static ExecutorService novoPoolCallbacks() {
        return Executors.newCachedThreadPool(tarefa -> {
            Thread t = new Thread(tarefa, "callback-" + CONTADOR_POOL.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Identificador do Jogador atendido por este entregador. */
    public String idJogador() {
        return idJogador;
    }

    /** Enfileira um evento para entrega, sem bloquear; ignorado se o entregador não está aberto. */
    public void enfileirar(EventoCallback evento) {
        Objects.requireNonNull(evento, "evento");
        if (estado.get() == Estado.ABERTO) {
            fila.offer(evento);
        }
    }

    /** Para de aceitar eventos, entrega o que já está na fila e encerra o worker. */
    public void fecharAposEsvaziar() {
        if (estado.compareAndSet(Estado.ABERTO, Estado.ESVAZIANDO)) {
            fila.offer(FIM);
        }
    }

    /** Para imediatamente: descarta a fila e encerra o worker, sem notificar o ouvinte. Idempotente. */
    public void fecharAgora() {
        Estado anterior = estado.getAndUpdate(e -> e == Estado.FALHOU ? e : Estado.FECHADO);
        if (anterior == Estado.FALHOU || anterior == Estado.FECHADO) {
            return;
        }
        fila.clear();
        fila.offer(FIM);
        worker.interrupt();
    }

    /** Equivalente a {@link #fecharAgora()}. */
    @Override
    public void close() {
        fecharAgora();
    }

    /** Chama {@link CallbackJogador#ping()} com timeout; {@code false} se falhar ou expirar. */
    public boolean pingSincrono(Duration timeoutPing) {
        return pingar(callback, timeoutPing, poolCallbacks);
    }

    /** Versão estática de {@link #pingSincrono(Duration)}, para Jogadores ainda sem entregador. */
    public static boolean pingar(CallbackJogador callback, Duration timeoutPing, ExecutorService poolCallbacks) {
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(poolCallbacks, "poolCallbacks");
        Duration limite = exigirPositivo(timeoutPing);
        Future<?> futuro;
        try {
            futuro = poolCallbacks.submit(() -> {
                callback.ping();
                return null;
            });
        } catch (RuntimeException e) {
            return false;
        }
        try {
            futuro.get(limite.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException | ExecutionException | RuntimeException e) {
            futuro.cancel(true);
            return false;
        }
    }

    private void executar() {
        try {
            while (true) {
                EventoCallback evento = fila.take();
                if (evento == FIM) {
                    return;
                }
                Estado atual = estado.get();
                if (atual == Estado.FALHOU || atual == Estado.FECHADO) {
                    return;
                }
                if (!entregar(evento)) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Entrega um evento e aguarda o resultado; {@code false} se o worker deve parar. */
    private boolean entregar(EventoCallback evento) {
        Future<?> futuro;
        try {
            futuro = poolCallbacks.submit(() -> {
                evento.invocar(callback);
                return null;
            });
        } catch (RuntimeException e) {
            falhar(CausaDesconexao.REMOTE_EXCEPTION);
            return false;
        }
        try {
            futuro.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException e) {
            futuro.cancel(true);
            falhar(CausaDesconexao.TIMEOUT);
            return false;
        } catch (ExecutionException e) {
            falhar(CausaDesconexao.REMOTE_EXCEPTION);
            return false;
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            futuro.cancel(true);
            falhar(CausaDesconexao.REMOTE_EXCEPTION);
            return false;
        }
    }

    /** Marca falha, descarta a fila e notifica o ouvinte uma única vez. */
    private void falhar(CausaDesconexao causa) {
        Estado anterior = estado.getAndUpdate(e -> e == Estado.ABERTO || e == Estado.ESVAZIANDO ? Estado.FALHOU : e);
        if (anterior != Estado.ABERTO && anterior != Estado.ESVAZIANDO) {
            return;
        }
        fila.clear();
        try {
            ouvinte.aoFalhar(idJogador, causa);
        } catch (RuntimeException e) {
            System.err.println("Ouvinte de falha de entrega lançou exceção para " + idJogador + ": " + e);
        }
    }

    private static Duration exigirPositivo(Duration d) {
        Objects.requireNonNull(d, "timeout");
        if (d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException("timeout deve ser positivo");
        }
        return d;
    }
}
