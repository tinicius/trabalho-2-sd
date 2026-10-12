package br.cefet.sd.memoria.middleware.servico;

import br.cefet.sd.memoria.comum.dto.EntradaSalaDTO;
import br.cefet.sd.memoria.comum.excecao.ExcecaoDominio;
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;
import br.cefet.sd.memoria.comum.remoto.ServicoSala;
import br.cefet.sd.memoria.middleware.sala.Sala;

import java.rmi.RemoteException;
import java.util.Objects;

/** Implementação do serviço remoto {@link ServicoSala}: delega à {@link Sala}. */
public class ServicoSalaImpl implements ServicoSala {

    private final Sala sala;

    /** Cria o serviço sobre a Sala informada. */
    public ServicoSalaImpl(Sala sala) {
        this.sala = Objects.requireNonNull(sala, "sala");
    }

    @Override
    public EntradaSalaDTO entrar(String nome, CallbackJogador callback)
            throws RemoteException, ExcecaoDominio {
        try {
            return sala.entrar(nome, callback);
        } catch (RuntimeException e) {
            throw TradutorErrosServico.traduzir("ServicoSala.entrar", e);
        }
    }

    @Override
    public void sair(String idParticipante) throws RemoteException, ExcecaoDominio {
        try {
            sala.sair(idParticipante);
        } catch (RuntimeException e) {
            throw TradutorErrosServico.traduzir("ServicoSala.sair", e);
        }
    }

    @Override
    public void marcarPronto(String idParticipante) throws RemoteException, ExcecaoDominio {
        try {
            sala.marcarPronto(idParticipante);
        } catch (RuntimeException e) {
            throw TradutorErrosServico.traduzir("ServicoSala.marcarPronto", e);
        }
    }

    /** {@inheritDoc} Nunca lança exceção; falhas internas são apenas registradas no log. */
    @Override
    public void notificarEncerramentoCliente(String idParticipante) throws RemoteException {
        try {
            sala.notificarEncerramentoCliente(idParticipante);
        } catch (RuntimeException e) {
            TradutorErrosServico.registrar("ServicoSala.notificarEncerramentoCliente", e);
        }
    }
}
