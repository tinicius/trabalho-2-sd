package br.cefet.sd.memoria.comum.remoto;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import java.rmi.Remote;
import java.rmi.RemoteException;
/** Serviço remoto de Sessão: continuidade da Sessão. */
public interface ServicoSessao extends Remote {
    /** Nome do serviço no Registry. */
    String NOME = NomesServico.SESSAO;
    /** Registra a resposta do Jogador à pergunta sobre continuar a Sessão. */
    void responderContinuar(String idParticipante, Boolean continuar)
            throws RemoteException, ExcecaoDominio;
}
