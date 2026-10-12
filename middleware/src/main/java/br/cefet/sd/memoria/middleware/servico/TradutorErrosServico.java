package br.cefet.sd.memoria.middleware.servico;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registra falhas inesperadas dos serviços e as converte em {@link RemoteException} genérica. */
final class TradutorErrosServico {

    /** Mensagem genérica enviada ao Cliente em caso de erro interno. */
    static final String MENSAGEM_ERRO_INTERNO = "Erro interno do servidor";

    private static final Logger LOG = Logger.getLogger(TradutorErrosServico.class.getName());

    private TradutorErrosServico() {
    }

    /** Registra o erro no log do servidor e devolve a exceção genérica a lançar ao Cliente. */
    static RemoteException traduzir(String operacao, RuntimeException causa) {
        registrar(operacao, causa);
        return new RemoteException(MENSAGEM_ERRO_INTERNO);
    }

    /** Registra uma falha inesperada no log do servidor, com o host do Cliente. */
    static void registrar(String operacao, Throwable causa) {
        try {
            LOG.log(Level.SEVERE, "Erro interno em " + operacao + " (cliente=" + clienteRemoto() + ")", causa);
        } catch (RuntimeException e) {
        }
    }

    private static String clienteRemoto() {
        try {
            return RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            return "local";
        }
    }
}
