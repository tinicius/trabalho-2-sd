package br.cefet.sd.memoria.comum.remoto;
import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import java.rmi.Remote;
import java.rmi.RemoteException;
/** Serviço remoto de Sala: entrada, saída e prontidão dos Jogadores. */
public interface ServicoSala extends Remote {
    /** Nome do serviço no Registry. */
    String NOME = NomesServico.SALA;
    /** Registra um Jogador na Sala. */
    EntradaSalaDTO entrar(String nome, CallbackJogador callback)
            throws RemoteException, ExcecaoDominio;
    /** Remove um Jogador da Sala. */
    void sair(String idParticipante) throws RemoteException, ExcecaoDominio;
    /** Marca o Jogador como pronto. */
    void marcarPronto(String idParticipante) throws RemoteException, ExcecaoDominio;
    /** Informa que o Cliente está sendo encerrado; nunca lança exceção de domínio. */
    void notificarEncerramentoCliente(String idParticipante) throws RemoteException;
}
