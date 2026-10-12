package br.cefet.sd.memoria.cliente.modelo;

import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarJogadorDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import java.util.List;

/**
 * Contrato entre o {@link ControladorCliente} e a interface do Cliente (telas Swing ou, em testes,
 * um dublê).
 */
public interface OuvinteControlador {

    /**
     * Mensagem informativa para a barra de status (acerto, erro, mudança de vez, vitória, empate,
     * reinício, cancelamento, desconexão, falha de envio etc.). A tela deve mantê-la até a próxima.
     */
    default void mostrarStatus(String mensagem) {
    }

    /**
     * Erro a destacar para o Jogador: motivo de rejeição de uma jogada ou operação, falha de
     * sincronização ou perda de conexão. É entregue assim que a rejeição chega ao Cliente, bem
     * antes do limite de 1 s.
     */
    default void mostrarErro(String mensagem) {
    }

    /** A entrada na Sala foi aceita pelo Middleware. */
    default void entradaAceita(EntradaSalaDTO entrada) {
    }

    /** A visão da Sala (lista de Jogadores e prontidão) foi atualizada. */
    default void atualizarSala(SalaDTO sala) {
    }

    /** O tabuleiro deve ser redesenhado. */
    default void atualizarVisao(VisaoTabuleiro visao, EstadoPublicoDTO estado) {
    }

    /** O placar foi atualizado (por estado público ou por notificação de placar). */
    default void atualizarPlacar(List<PlacarJogadorDTO> placar) {
    }

    /** A vez mudou. */
    default void mudouTurno(MudancaTurnoDTO mudanca, boolean minhaVez) {
    }

    /** A Partida terminou com vencedor ou empate. */
    default void mostrarResultado(ResultadoPartidaDTO resultado) {
    }

    /** O Jogador deve responder se deseja continuar a Sessão. */
    default void mostrarPergunta(PerguntaContinuarDTO pergunta) {
    }

    /**
     * A resposta de continuar foi enviada (ou não é mais aceita): as opções devem ser
     * desabilitadas.
     */
    default void perguntaRespondida() {
    }

    /** A Partida foi cancelada pela desconexão de um Jogador. */
    default void partidaCancelada(CancelamentoPartidaDTO cancelamento) {
    }

    /** A Sessão foi encerrada. */
    default void mostrarSumario(SumarioSessaoDTO sumario) {
    }

    /**
     * A conexão com o Middleware foi perdida: todas as ações de jogo devem ficar desabilitadas até
     * o Jogador reiniciar o Cliente. Chamado uma única vez por controlador.
     */
    default void conexaoPerdida() {
    }

    /** O Jogador saiu da Sala voluntariamente e o Callback foi desexportado. */
    default void saiu() {
    }
}
