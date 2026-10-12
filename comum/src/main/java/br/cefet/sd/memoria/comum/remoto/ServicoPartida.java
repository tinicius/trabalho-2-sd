package br.cefet.sd.memoria.comum.remoto;
import br.cefet.sd.memoria.comum.dto.VisaoGeralDTO;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import java.rmi.Remote;
import java.rmi.RemoteException;
/** Serviço remoto de Partida: jogadas e consulta de estado. */
public interface ServicoPartida extends Remote {
    /** Nome do serviço no Registry. */
    String NOME = NomesServico.PARTIDA;
    /** Seleciona uma carta do Tabuleiro; os parâmetros são boxed para permitir validar {@code null}. */
    void selecionarCarta(String idParticipante, Long idPartida, Integer posicao)
            throws RemoteException, ExcecaoDominio;
    /** Consulta o estado geral atual da Sala, Partida e Sessão. */
    VisaoGeralDTO consultarEstado(String idParticipante)
            throws RemoteException, ExcecaoDominio;
}
