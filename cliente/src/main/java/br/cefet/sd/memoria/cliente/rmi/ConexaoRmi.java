package br.cefet.sd.memoria.cliente.rmi;

import br.cefet.sd.memoria.comum.remoto.NomesServico;
import br.cefet.sd.memoria.comum.remoto.ServicoPartida;
import br.cefet.sd.memoria.comum.remoto.ServicoSala;
import br.cefet.sd.memoria.comum.remoto.ServicoSessao;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Localiza os três serviços do Middleware no RMI Registry. */
public final class ConexaoRmi {

    /** Timeout padrão da conexão, em segundos. */
    public static final long TIMEOUT_PADRAO_SEGUNDOS = 10L;

    /** Timeout padrão da conexão. */
    public static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(TIMEOUT_PADRAO_SEGUNDOS);

    private ConexaoRmi() {
    }

    /**
     * Estratégia de busca de um objeto remoto pelo nome; por padrão, um {@link Registry}. Existe
     * para permitir testes sem Registry real.
     */
    @FunctionalInterface
    public interface Localizador {
        /** Busca o objeto remoto registrado com o nome informado. */
        Remote buscar(String nome) throws RemoteException, NotBoundException;
    }

    /** Conecta ao Registry com o timeout padrão de 10 s. */
    public static ServicosRemotos conectar(String host, int porta) throws ConexaoRmiException {
        return conectar(host, porta, TIMEOUT_PADRAO);
    }

    /**
     * Conecta ao Registry e localiza {@code memoria/Sala}, {@code memoria/Partida} e {@code
     * memoria/Sessao}.
     */
    public static ServicosRemotos conectar(String host, int porta, Duration timeout)
            throws ConexaoRmiException {
        return conectar(host, porta, timeout, () -> {
            Registry registry = LocateRegistry.getRegistry(host, porta);
            return registry::lookup;
        });
    }

    /**
     * Fornece o {@link Localizador} a usar; pode lançar {@link RemoteException}. Executado na
     * thread de conexão, junto com os {@code lookup}.
     */
    @FunctionalInterface
    public interface FabricaLocalizador {
        /** Cria o localizador. */
        Localizador criar() throws RemoteException;
    }

    /** Variante com {@link Localizador} injetável, usada em testes. */
    public static ServicosRemotos conectar(String host, int porta, Duration timeout,
                                           FabricaLocalizador fabrica) throws ConexaoRmiException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(fabrica, "fabrica");

        ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
            Thread thread = new Thread(tarefa, "rmi-conexao-" + host + ":" + porta);
            thread.setDaemon(true);
            return thread;
        });
        Future<ServicosRemotos> futuro = executor.submit(() -> localizar(host, porta, fabrica));
        try {
            return futuro.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            throw new ConexaoRmiException(host, porta,
                    "tempo esgotado após " + timeout.toSeconds() + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            futuro.cancel(true);
            throw new ConexaoRmiException(host, porta, "conexão interrompida", e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            if (causa instanceof ConexaoRmiException conexao) {
                throw conexao;
            }
            throw new ConexaoRmiException(host, porta, descrever(causa), causa);
        } finally {
            executor.shutdownNow();
        }
    }

    private static ServicosRemotos localizar(String host, int porta, FabricaLocalizador fabrica)
            throws ConexaoRmiException {
        try {
            Localizador localizador = fabrica.criar();
            ServicoSala sala = (ServicoSala) localizador.buscar(NomesServico.SALA);
            ServicoPartida partida = (ServicoPartida) localizador.buscar(NomesServico.PARTIDA);
            ServicoSessao sessao = (ServicoSessao) localizador.buscar(NomesServico.SESSAO);
            return new ServicosRemotos(host, porta, sala, partida, sessao);
        } catch (NotBoundException e) {
            throw new ConexaoRmiException(host, porta,
                    "serviço não registrado (" + e.getMessage() + ")", e);
        } catch (RemoteException | ClassCastException | NullPointerException e) {
            throw new ConexaoRmiException(host, porta, descrever(e), e);
        }
    }

    private static String descrever(Throwable erro) {
        String mensagem = erro.getMessage();
        return mensagem == null || mensagem.isBlank()
                ? erro.getClass().getSimpleName()
                : erro.getClass().getSimpleName() + ": " + mensagem;
    }
}
