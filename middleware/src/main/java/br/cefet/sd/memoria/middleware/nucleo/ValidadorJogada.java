package br.cefet.sd.memoria.middleware.nucleo;

import br.cefet.sd.memoria.comum.dto.StatusPartida;
import br.cefet.sd.memoria.comum.excecao.MotivoRejeicao;
import java.util.Optional;
import java.util.OptionalLong;

/** Validação pura de uma seleção de carta; para na primeira condição que falhar. */
public final class ValidadorJogada {

    private ValidadorJogada() {
    }

    /**
     * Valida a seleção; retorna vazio se válida, ou o motivo da primeira falha.
     *
     * @param disputouLock se {@code true}, a seleção repetida é reportada como conflitante
     */
    public static Optional<MotivoRejeicao> validar(VisaoPartida visao, String idParticipante,
            Long idPartida, Integer posicao, boolean disputouLock) {
        if (idParticipante == null || !visao.participanteConhecido(idParticipante)) {
            return Optional.of(MotivoRejeicao.PARTICIPANTE_INVALIDO);
        }

        OptionalLong corrente = visao.idPartidaCorrente();
        if (corrente.isEmpty() || idPartida == null || idPartida.longValue() != corrente.getAsLong()) {
            return Optional.of(MotivoRejeicao.PARTIDA_ANTERIOR_OU_INEXISTENTE);
        }

        if (visao.status() != StatusPartida.EM_ANDAMENTO) {
            return Optional.of(MotivoRejeicao.PARTIDA_ENCERRADA);
        }

        if (!idParticipante.equals(visao.idJogadorDaVez())) {
            return Optional.of(MotivoRejeicao.FORA_DA_VEZ);
        }

        if (visao.subEstado() == SubEstado.INTERVALO_ERRO) {
            return Optional.of(MotivoRejeicao.INTERVALO_DE_ERRO);
        }

        if (posicao == null || posicao < 0 || posicao >= visao.tamanho()) {
            return Optional.of(MotivoRejeicao.POSICAO_INVALIDA);
        }

        if (posicao == visao.primeiraPosicao()) {
            return Optional.of(disputouLock
                    ? MotivoRejeicao.SELECAO_CONFLITANTE
                    : MotivoRejeicao.SELECAO_REPETIDA);
        }

        if (visao.estadoCarta(posicao) == EstadoCarta.CONQUISTADA) {
            return Optional.of(MotivoRejeicao.CARTA_INDISPONIVEL);
        }

        return Optional.empty();
    }
}
