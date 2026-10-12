package br.cefet.sd.memoria.cliente.rmi;

import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;

/** Ouvinte das notificações enviadas pelo Middleware via {@code CallbackJogador}. */
public interface OuvinteCallback {

    /** A Sala foi atualizada. */
    void aoAtualizarSala(SalaDTO sala);

    /** Um Jogador foi desconectado. */
    void aoDesconectarJogador(DesconexaoDTO desconexao);

    /** O Tabuleiro foi atualizado. */
    void aoAtualizarTabuleiro(EstadoPublicoDTO estado);

    /** O turno mudou. */
    void aoMudarTurno(MudancaTurnoDTO mudanca);

    /** O placar foi atualizado. */
    void aoAtualizarPlacar(PlacarDTO placar);

    /** A Partida terminou. */
    void aoFinalizarPartida(ResultadoPartidaDTO resultado);

    /** O Jogador deve responder se deseja continuar a Sessão. */
    void aoPerguntarContinuar(PerguntaContinuarDTO pergunta);

    /** A Partida foi cancelada. */
    void aoCancelarPartida(CancelamentoPartidaDTO cancelamento);

    /** A Sessão foi encerrada. */
    void aoEncerrarSessao(SumarioSessaoDTO sumario);
}
