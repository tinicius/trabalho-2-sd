package br.cefet.sd.memoria.middleware.servico;

import br.cefet.sd.memoria.comum.remoto.NomesServico;
import br.cefet.sd.memoria.middleware.config.ConfiguracaoMiddleware;
import br.cefet.sd.memoria.middleware.sala.Sala;
import java.io.ObjectInputFilter;
import java.lang.reflect.Proxy;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Publica o RMI Registry e os três serviços remotos do Middleware. */
public final class PublicadorRmi {

    /** Propriedade de sistema consultada pelo RMI para montar o endereço dos stubs. */
    public static final String PROPRIEDADE_HOSTNAME = "java.rmi.server.hostname";

    private static final String[] PREFIXOS_PERMITIDOS = {
        "br.cefet.sd.memoria.comum.", "java.lang.", "java.util.", "java.math.", "java.rmi.", "sun.rmi."
    };

    private static final ObjectInputFilter FILTRO = PublicadorRmi::filtrar;

    private PublicadorRmi() {
    }

    /** Filtro de desserialização (allowlist) aplicado aos argumentos recebidos pelos serviços. */
    public static ObjectInputFilter filtroDesserializacao() {
        return FILTRO;
    }

    private static ObjectInputFilter.Status filtrar(ObjectInputFilter.FilterInfo info) {
        Class<?> classe = info.serialClass();
        if (classe == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        while (classe.isArray()) {
            classe = classe.getComponentType();
        }
        if (classe.isPrimitive()) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        if (Proxy.isProxyClass(classe)) {
            for (Class<?> interf : classe.getInterfaces()) {
                if (!Remote.class.isAssignableFrom(interf) || !nomePermitido(interf.getName())) {
                    return ObjectInputFilter.Status.REJECTED;
                }
            }
            return ObjectInputFilter.Status.ALLOWED;
        }
        return nomePermitido(classe.getName()) ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
    }

    private static boolean nomePermitido(String nome) {
        if (nome.startsWith("java.math.")) {
            return nome.indexOf('.', "java.math.".length()) < 0;
        }
        for (String prefixo : PREFIXOS_PERMITIDOS) {
            if (nome.startsWith(prefixo)) {
                return true;
            }
        }
        return false;
    }

    /** Publica o Registry e os serviços da {@code sala}; feche a publicação para desfazê-la. */
    public static Publicacao publicar(ConfiguracaoMiddleware config, Sala sala)
            throws PublicacaoRmiException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(sala, "sala");
        int porta = config.registryPorta();
        if (porta < 1 || porta > 65535) {
            throw new PublicacaoRmiException(porta, "porta do Registry fora do intervalo 1 a 65535", null);
        }
        int portaObjetos = config.rmiPortaObjetos();
        if (portaObjetos < 0 || portaObjetos > 65535) {
            throw new PublicacaoRmiException(porta,
                    "rmi.portaObjetos " + portaObjetos + " fora do intervalo 0 a 65535", null);
        }
        if (!config.rmiHostname().isEmpty()) {
            System.setProperty(PROPRIEDADE_HOSTNAME, config.rmiHostname());
        }

        Registry registry;
        boolean criou = true;
        try {
            registry = LocateRegistry.createRegistry(porta);
        } catch (ExportException e) {
            criou = false;
            registry = reaproveitar(porta, e);
        } catch (RemoteException | RuntimeException e) {
            throw new PublicacaoRmiException(porta, descrever(e), e);
        }

        Publicacao publicacao = new Publicacao(porta, registry, criou);
        try {
            publicacao.exportar(NomesServico.SALA, new ServicoSalaImpl(sala), portaObjetos);
            publicacao.exportar(NomesServico.PARTIDA, new ServicoPartidaImpl(sala), portaObjetos);
            publicacao.exportar(NomesServico.SESSAO, new ServicoSessaoImpl(sala), portaObjetos);
        } catch (RemoteException | RuntimeException e) {
            publicacao.close();
            throw new PublicacaoRmiException(porta, "falha ao exportar/registrar serviços: " + descrever(e), e);
        }
        return publicacao;
    }

    private static Registry reaproveitar(int porta, ExportException original)
            throws PublicacaoRmiException {
        try {
            Registry existente = LocateRegistry.getRegistry(porta);
            existente.list();
            return existente;
        } catch (RemoteException | RuntimeException e) {
            throw new PublicacaoRmiException(porta,
                    "porta em uso por outro processo que não é um RMI Registry (" + descrever(original) + ")",
                    original);
        }
    }

    private static String descrever(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    /** Publicação ativa, com referências ao Registry e às implementações exportadas. */
    public static final class Publicacao implements AutoCloseable {

        private final int porta;
        private final Registry registry;
        private final boolean registryCriado;
        private final List<String> nomes = new ArrayList<>();
        private final List<Remote> implementacoes = new ArrayList<>();
        private boolean fechada;

        private Publicacao(int porta, Registry registry, boolean registryCriado) {
            this.porta = porta;
            this.registry = registry;
            this.registryCriado = registryCriado;
        }

        private void exportar(String nome, Remote impl, int portaObjetos) throws RemoteException {
            implementacoes.add(impl);
            Remote stub = UnicastRemoteObject.exportObject(impl, portaObjetos, null, null, FILTRO);
            registry.rebind(nome, stub);
            nomes.add(nome);
        }

        /** Porta do Registry. */
        public int porta() {
            return porta;
        }

        /** Registry em uso (criado por este Middleware ou reaproveitado). */
        public Registry registry() {
            return registry;
        }

        /** Nomes registrados, na ordem de registro. */
        public List<String> nomes() {
            return List.copyOf(nomes);
        }

        /** Remove os nomes do Registry e desexporta os objetos. Idempotente. */
        @Override
        public synchronized void close() {
            if (fechada) {
                return;
            }
            fechada = true;
            for (String nome : nomes) {
                try {
                    registry.unbind(nome);
                } catch (RemoteException | NotBoundException | RuntimeException e) {
                }
            }
            for (Remote impl : implementacoes) {
                try {
                    UnicastRemoteObject.unexportObject(impl, true);
                } catch (RemoteException | RuntimeException e) {
                }
            }
            if (registryCriado) {
                try {
                    UnicastRemoteObject.unexportObject(registry, true);
                } catch (RemoteException | RuntimeException e) {
                }
            }
        }
    }
}
