package br.cefet.sd.memoria.middleware.servico;

import br.cefet.sd.memoria.comum.dto.VisaoGeralDTO;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import br.cefet.sd.memoria.comum.remoto.ServicoPartida;
import br.cefet.sd.memoria.middleware.sala.Sala;

import java.rmi.RemoteException;
import java.util.Objects;

/** Implementação do serviço remoto {@link ServicoPartida}: delega à {@link Sala}. */
public class ServicoPartidaImpl implements ServicoPartida {

    private final Sala sala;

    /** Cria o serviço sobre a Sala informada. */
    public ServicoPartidaImpl(Sala sala) {
        this.sala = Objects.requireNonNull(sala, "sala");
    }

    @Override
    public void selecionarCarta(String idParticipante, Long idPartida, Integer posicao)
            throws RemoteException, ExcecaoDominio {
        try {
            sala.selecionarCarta(idParticipante, idPartida, posicao);
        } catch (RuntimeException e) {
            throw TradutorErrosServico.traduzir("ServicoPartida.selecionarCarta", e);
        }
    }

    @Override
    public VisaoGeralDTO consultarEstado(String idParticipante)
            throws RemoteException, ExcecaoDominio {
        try {
            return sala.consultarEstado(idParticipante);
        } catch (RuntimeException e) {
            throw TradutorErrosServico.traduzir("ServicoPartida.consultarEstado", e);
        }
    }
}
