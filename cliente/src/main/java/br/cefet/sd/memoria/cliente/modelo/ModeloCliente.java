package br.cefet.sd.memoria.cliente.modelo;

import br.cefet.sd.memoria.comum.dto.CancelamentoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.DesconexaoDTO;
import br.cefet.sd.memoria.comum.dto.EstadoPublicoDTO;
import br.cefet.sd.memoria.comum.dto.MudancaTurnoDTO;
import br.cefet.sd.memoria.comum.dto.PerguntaContinuarDTO;
import br.cefet.sd.memoria.comum.dto.PlacarDTO;
import br.cefet.sd.memoria.comum.dto.ResultadoPartidaDTO;
import br.cefet.sd.memoria.comum.dto.SalaDTO;
import br.cefet.sd.memoria.comum.dto.SumarioSessaoDTO;
import java.util.Objects;
import java.util.Optional;

/** Estado aplicado pelo Cliente a partir das notificações (Callbacks) do Middleware. */
public final class ModeloCliente {

    /** Valor inicial de {@code versaoSala}: menor que qualquer versão válida (&gt;= 0). */
    private static final long VERSAO_SALA_INICIAL = -1L;

    private boolean partidaAplicada;
    private long ultimoIdPartida;
    private long ultimaSequencia;

    private long versaoSala = VERSAO_SALA_INICIAL;

    private EstadoPublicoDTO estadoPublico;
    private MudancaTurnoDTO ultimaMudancaTurno;
    private PlacarDTO ultimoPlacar;
    private ResultadoPartidaDTO ultimoResultado;
    private PerguntaContinuarDTO ultimaPergunta;
    private CancelamentoPartidaDTO ultimoCancelamento;
    private SumarioSessaoDTO ultimoSumario;

    private SalaDTO sala;
    private DesconexaoDTO ultimaDesconexao;

    private boolean conexaoPerdida;
    private boolean falhaSincronizacao;

    /** Aplica o snapshot público do tabuleiro, se for mais novo que o último aplicado. */
    public boolean aplicar(EstadoPublicoDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        estadoPublico = dto;
        return true;
    }

    /** Aplica uma mudança de turno, se for mais nova que a última aplicada. */
    public boolean aplicar(MudancaTurnoDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        ultimaMudancaTurno = dto;
        return true;
    }

    /** Aplica uma atualização de placar, se for mais nova que a última aplicada. */
    public boolean aplicar(PlacarDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        ultimoPlacar = dto;
        return true;
    }

    /** Aplica o resultado de uma Partida, se for mais novo que o último aplicado. */
    public boolean aplicar(ResultadoPartidaDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        ultimoResultado = dto;
        return true;
    }

    /** Aplica a pergunta "continuar?", se for mais nova que a última aplicada. */
    public boolean aplicar(PerguntaContinuarDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        ultimaPergunta = dto;
        return true;
    }

    /** Aplica o cancelamento de uma Partida, se for mais novo que o último aplicado. */
    public boolean aplicar(CancelamentoPartidaDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        ultimoCancelamento = dto;
        return true;
    }

    /** Aplica o sumário de encerramento da Sessão, se for mais novo que o último aplicado. */
    public boolean aplicar(SumarioSessaoDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (!aceitar(dto.idPartida(), dto.sequencia())) {
            return false;
        }
        ultimoSumario = dto;
        return true;
    }

    /** Aplica a visão da Sala, se sua versão for estritamente maior que a última aplicada. */
    public boolean aplicarSala(SalaDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (dto.versao() <= versaoSala) {
            return false;
        }
        versaoSala = dto.versao();
        sala = dto;
        return true;
    }

    /**
     * Aplica uma notificação de desconexão de Jogador na Sala, que compartilha o contador {@code
     * versaoSala} com {@link #aplicarSala(SalaDTO)}. Descarta versão menor ou igual à última.
     */
    public boolean aplicarDesconexao(DesconexaoDTO dto) {
        Objects.requireNonNull(dto, "dto");
        if (dto.versaoSala() <= versaoSala) {
            return false;
        }
        versaoSala = dto.versaoSala();
        ultimaDesconexao = dto;
        return true;
    }

    /** Define se a conexão com o Middleware foi perdida. */
    public void setConexaoPerdida(boolean conexaoPerdida) {
        this.conexaoPerdida = conexaoPerdida;
    }

    /**
     * Define se houve falha ao sincronizar o estado (por exemplo, ao consultar o estado atual).
     */
    public void setFalhaSincronizacao(boolean falhaSincronizacao) {
        this.falhaSincronizacao = falhaSincronizacao;
    }

    public boolean isConexaoPerdida() {
        return conexaoPerdida;
    }

    public boolean isFalhaSincronizacao() {
        return falhaSincronizacao;
    }

    public boolean temPartidaAplicada() {
        return partidaAplicada;
    }

    public long getUltimoIdPartida() {
        verificarPartidaAplicada();
        return ultimoIdPartida;
    }

    public long getUltimaSequencia() {
        verificarPartidaAplicada();
        return ultimaSequencia;
    }

    public long getVersaoSala() {
        return versaoSala;
    }

    public Optional<EstadoPublicoDTO> getEstadoPublico() {
        return Optional.ofNullable(estadoPublico);
    }

    public Optional<MudancaTurnoDTO> getUltimaMudancaTurno() {
        return Optional.ofNullable(ultimaMudancaTurno);
    }

    public Optional<PlacarDTO> getUltimoPlacar() {
        return Optional.ofNullable(ultimoPlacar);
    }

    public Optional<ResultadoPartidaDTO> getUltimoResultado() {
        return Optional.ofNullable(ultimoResultado);
    }

    public Optional<PerguntaContinuarDTO> getUltimaPergunta() {
        return Optional.ofNullable(ultimaPergunta);
    }

    public Optional<CancelamentoPartidaDTO> getUltimoCancelamento() {
        return Optional.ofNullable(ultimoCancelamento);
    }

    public Optional<SumarioSessaoDTO> getUltimoSumario() {
        return Optional.ofNullable(ultimoSumario);
    }

    public Optional<SalaDTO> getSala() {
        return Optional.ofNullable(sala);
    }

    public Optional<DesconexaoDTO> getUltimaDesconexao() {
        return Optional.ofNullable(ultimaDesconexao);
    }

    /**
     * Decide se {@code (idPartida, sequencia)} é estritamente mais novo que o último aplicado e, em
     * caso positivo, registra-o. Quando o {@code idPartida} avança, descarta os dados da Partida
     * anterior.
     */
    private boolean aceitar(long idPartida, long sequencia) {
        if (partidaAplicada) {
            if (idPartida < ultimoIdPartida) {
                return false;
            }
            if (idPartida == ultimoIdPartida && sequencia <= ultimaSequencia) {
                return false;
            }
            if (idPartida > ultimoIdPartida) {
                esquecerDadosDePartida();
            }
        }
        partidaAplicada = true;
        ultimoIdPartida = idPartida;
        ultimaSequencia = sequencia;
        return true;
    }

    private void esquecerDadosDePartida() {
        estadoPublico = null;
        ultimaMudancaTurno = null;
        ultimoPlacar = null;
        ultimoResultado = null;
        ultimaPergunta = null;
        ultimoCancelamento = null;
        ultimoSumario = null;
    }

    private void verificarPartidaAplicada() {
        if (!partidaAplicada) {
            throw new IllegalStateException("nenhuma notificacao de Partida foi aplicada");
        }
    }
}
