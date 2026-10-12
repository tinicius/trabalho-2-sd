package br.cefet.sd.memoria.middleware.servico;

import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import br.cefet.sd.memoria.comum.remoto.ServicoSessao;
import br.cefet.sd.memoria.middleware.sala.Sala;

import java.rmi.RemoteException;
import java.util.Objects;

/** Implementação do serviço remoto {@link ServicoSessao}: delega à {@link Sala}. */
public class ServicoSessaoImpl implements ServicoSessao {

    private final Sala sala;

    /** Cria o serviço sobre a Sala informada. */
    public ServicoSessaoImpl(Sala sala) {
        this.sala = Objects.requireNonNull(sala, "sala");
    }

    @Override
    public void responderContinuar(String idParticipante, Boolean continuar)
            throws RemoteException, ExcecaoDominio {
        try {
            sala.responderContinuar(idParticipante, continuar);
        } catch (RuntimeException e) {
            throw TradutorErrosServico.traduzir("ServicoSessao.responderContinuar", e);
        }
    }
}
