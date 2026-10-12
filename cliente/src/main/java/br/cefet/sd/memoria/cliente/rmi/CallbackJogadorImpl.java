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
import br.cefet.sd.memoria.comum.remoto.CallbackJogador;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;

/** Implementação do {@link CallbackJogador} exportada pelo Cliente. */
public final class CallbackJogadorImpl implements CallbackJogador {

    private static final System.Logger LOG = System.getLogger(CallbackJogadorImpl.class.getName());

    private final OuvinteCallback ouvinte;
    private final Despachante despachante;
    private volatile boolean desexportado;
    private CallbackJogador stub;

    /** Cria o callback entregando notificações ao ouvinte na EDT. */
    public CallbackJogadorImpl(OuvinteCallback ouvinte) {
        this(ouvinte, Despachante.EDT);
    }

    /** Cria o callback com um despachante específico (injetável em testes sem Swing). */
    public CallbackJogadorImpl(OuvinteCallback ouvinte, Despachante despachante) {
        this.ouvinte = Objects.requireNonNull(ouvinte, "ouvinte");
        this.despachante = Objects.requireNonNull(despachante, "despachante");
    }

    /**
     * Exporta este objeto em porta anônima ({@code UnicastRemoteObject.exportObject(this, 0)}).
     * Chamadas repetidas devolvem o mesmo stub.
     */
    public synchronized CallbackJogador exportar() throws RemoteException {
        if (stub == null) {
            stub = (CallbackJogador) UnicastRemoteObject.exportObject(this, 0);
            desexportado = false;
        }
        return stub;
    }

    /**
     * Remove a exportação (rejeição da entrada ou saída). É idempotente e ignora o caso de o objeto
     * não estar exportado.
     */
    public synchronized void desexportar() {
        desexportado = true;
        stub = null;
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
        }
    }

    @Override
    public void ping() {
    }

    @Override
    public void aoAtualizarSala(SalaDTO sala) {
        encaminhar(() -> ouvinte.aoAtualizarSala(sala));
    }

    @Override
    public void aoDesconectarJogador(DesconexaoDTO desconexao) {
        encaminhar(() -> ouvinte.aoDesconectarJogador(desconexao));
    }

    @Override
    public void aoAtualizarTabuleiro(EstadoPublicoDTO estado) {
        encaminhar(() -> ouvinte.aoAtualizarTabuleiro(estado));
    }

    @Override
    public void aoMudarTurno(MudancaTurnoDTO mudanca) {
        encaminhar(() -> ouvinte.aoMudarTurno(mudanca));
    }

    @Override
    public void aoAtualizarPlacar(PlacarDTO placar) {
        encaminhar(() -> ouvinte.aoAtualizarPlacar(placar));
    }

    @Override
    public void aoFinalizarPartida(ResultadoPartidaDTO resultado) {
        encaminhar(() -> ouvinte.aoFinalizarPartida(resultado));
    }

    @Override
    public void aoPerguntarContinuar(PerguntaContinuarDTO pergunta) {
        encaminhar(() -> ouvinte.aoPerguntarContinuar(pergunta));
    }

    @Override
    public void aoCancelarPartida(CancelamentoPartidaDTO cancelamento) {
        encaminhar(() -> ouvinte.aoCancelarPartida(cancelamento));
    }

    @Override
    public void aoEncerrarSessao(SumarioSessaoDTO sumario) {
        encaminhar(() -> ouvinte.aoEncerrarSessao(sumario));
    }

    /** Agenda a notificação e retorna; falhas do ouvinte não se propagam à thread RMI. */
    private void encaminhar(Runnable notificacao) {
        if (desexportado) {
            return;
        }
        despachante.despachar(() -> {
            try {
                notificacao.run();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Falha ao tratar notificação do Middleware", e);
            }
        });
    }
}
