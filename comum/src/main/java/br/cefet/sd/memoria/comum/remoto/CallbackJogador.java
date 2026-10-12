package br.cefet.sd.memoria.comum.remoto;
import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
/**
 * Callback exportado pelo Cliente para receber notificações do Middleware.
 * Todas as operações devem retornar imediatamente.
 */
public interface CallbackJogador extends Remote {
    /** Verificação de conectividade usada na entrada. */
    void ping() throws RemoteException;
    /** Notifica mudança na Sala. */
    void aoAtualizarSala(SalaDTO sala) throws RemoteException;
    /** Notifica a desconexão de um Jogador. */
    void aoDesconectarJogador(DesconexaoDTO desconexao) throws RemoteException;
    /** Notifica atualização do Tabuleiro (inclui o estado inicial da Partida). */
    void aoAtualizarTabuleiro(EstadoPublicoDTO estado) throws RemoteException;
    /** Notifica mudança de turno. */
    void aoMudarTurno(MudancaTurnoDTO mudanca) throws RemoteException;
    /** Notifica atualização de placar e estatísticas. */
    void aoAtualizarPlacar(PlacarDTO placar) throws RemoteException;
    /** Notifica o resultado de uma Partida finalizada. */
    void aoFinalizarPartida(ResultadoPartidaDTO resultado) throws RemoteException;
    /** Pergunta ao Jogador se deseja continuar a Sessão. */
    void aoPerguntarContinuar(PerguntaContinuarDTO pergunta) throws RemoteException;
    /** Notifica o cancelamento da Partida. */
    void aoCancelarPartida(CancelamentoPartidaDTO cancelamento) throws RemoteException;
    /** Notifica o encerramento da Sessão com o sumário final. */
    void aoEncerrarSessao(SumarioSessaoDTO sumario) throws RemoteException;
}
