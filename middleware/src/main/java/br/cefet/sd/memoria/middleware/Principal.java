package br.cefet.sd.memoria.middleware;

import br.cefet.sd.memoria.comum.catalogo.CatalogoImagens;
import br.cefet.sd.memoria.comum.remoto.NomesServico;
import br.cefet.sd.memoria.middleware.config.Aviso;
import br.cefet.sd.memoria.middleware.config.CarregadorConfiguracao;
import br.cefet.sd.memoria.middleware.config.ConfiguracaoMiddleware;
import br.cefet.sd.memoria.middleware.config.ErroConfiguracao;
import br.cefet.sd.memoria.middleware.config.ResultadoCarregamento;
import br.cefet.sd.memoria.middleware.entrega.EntregadorJogador;
import br.cefet.sd.memoria.middleware.log.RegistroEventos;
import br.cefet.sd.memoria.middleware.nucleo.ConfiguracaoTabuleiro;
import br.cefet.sd.memoria.middleware.sala.AgendadorReal;
import br.cefet.sd.memoria.middleware.sala.RegistroEventosSala;
import br.cefet.sd.memoria.middleware.sala.RelogioSistema;
import br.cefet.sd.memoria.middleware.sala.Sala;
import br.cefet.sd.memoria.middleware.servico.PublicacaoRmiException;
import br.cefet.sd.memoria.middleware.servico.PublicadorRmi;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;

/** Ponto de entrada do Middleware (servidor RMI do Jogo da Memória). */
public final class Principal {

    /** Código de saída para configuração inválida. */
    public static final int SAIDA_CONFIGURACAO_INVALIDA = 2;
    /** Código de saída para falha ao publicar o Registry. */
    public static final int SAIDA_FALHA_PUBLICACAO = 1;
    /** Propriedade de sistema com o caminho do arquivo de log (opcional). */
    public static final String PROPRIEDADE_LOG = "memoria.log";

    private Principal() {
    }

    /** Resultado da inicialização: código de saída ({@code 0} em sucesso) e o servidor ativo. */
    public record Inicializacao(int codigo, Servidor servidor) {
    }

    /** Servidor em execução; {@link #close()} desfaz tudo o que foi iniciado. */
    public static final class Servidor implements AutoCloseable {
        private final PublicadorRmi.Publicacao publicacao;
        private final Sala sala;
        private final ExecutorService pool;
        private final AgendadorReal agendador;
        private final RegistroEventos registro;
        private boolean fechado;

        private Servidor(PublicadorRmi.Publicacao publicacao, Sala sala, ExecutorService pool,
                AgendadorReal agendador, RegistroEventos registro) {
            this.publicacao = publicacao;
            this.sala = sala;
            this.pool = pool;
            this.agendador = agendador;
            this.registro = registro;
        }

        /** Porta do Registry em uso. */
        public int porta() {
            return publicacao.porta();
        }

        /** Sala atendida pelo servidor. */
        public Sala sala() {
            return sala;
        }

        /** Remove a publicação RMI, desliga a Sala e encerra pool, agendador e log. Idempotente. */
        @Override
        public synchronized void close() {
            if (fechado) {
                return;
            }
            fechado = true;
            publicacao.close();
            sala.desligar();
            agendador.encerrar();
            pool.shutdownNow();
            registro.close();
        }
    }

    /** Inicia o Middleware sem encerrar a JVM, retornando o código de saída e o servidor. */
    public static Inicializacao iniciar(String[] args, Path arquivoPadrao, PrintStream saida,
            PrintStream erro, boolean registrarHook) {
        CatalogoImagens catalogo = CatalogoImagens.carregar();
        ResultadoCarregamento resultado =
                CarregadorConfiguracao.carregar(args, arquivoPadrao, catalogo.quantidade());
        for (Aviso aviso : resultado.avisos()) {
            erro.println("Aviso: " + aviso.parametro() + ": " + aviso.motivo());
        }
        if (!resultado.ok()) {
            for (ErroConfiguracao e : resultado.erros()) {
                erro.println(e.parametro() + ": " + e.motivo());
            }
            return new Inicializacao(SAIDA_CONFIGURACAO_INVALIDA, null);
        }
        ConfiguracaoMiddleware config = resultado.config();

        String arquivoLog = System.getProperty(PROPRIEDADE_LOG);
        RegistroEventos registro = RegistroEventos.criar(
                arquivoLog == null || arquivoLog.isBlank() ? null : Paths.get(arquivoLog));
        ExecutorService pool = EntregadorJogador.novoPoolCallbacks();
        AgendadorReal agendador = new AgendadorReal("sala");
        Sala sala = new Sala(config.paraSala(catalogo), pool, agendador, RelogioSistema.INSTANCIA, registro);
        registro.registrar(RegistroEventosSala.Tipo.CONFIG, null, "OK",
                "host=" + (config.rmiHostname().isEmpty() ? "(automatico)" : config.rmiHostname())
                        + " porta=" + config.registryPorta()
                        + " capacidade=" + config.salaCapacidade()
                        + " tabuleiro=" + config.linhas() + "x" + config.colunas());

        PublicadorRmi.Publicacao publicacao;
        try {
            publicacao = PublicadorRmi.publicar(config, sala);
        } catch (PublicacaoRmiException e) {
            erro.println("Erro: não foi possível publicar o Registry na porta " + e.porta() + ": "
                    + e.motivo());
            sala.desligar();
            agendador.encerrar();
            pool.shutdownNow();
            registro.close();
            return new Inicializacao(SAIDA_FALHA_PUBLICACAO, null);
        }

        Servidor servidor = new Servidor(publicacao, sala, pool, agendador, registro);
        if (registrarHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(servidor::close, "middleware-desligamento"));
        }
        saida.println("Middleware do Jogo da Memória pronto.");
        saida.println("  Host: " + (config.rmiHostname().isEmpty() ? "(automático)" : config.rmiHostname()));
        saida.println("  Porta do Registry: " + config.registryPorta());
        saida.println("  Serviços: " + NomesServico.SALA + ", " + NomesServico.PARTIDA + ", "
                + NomesServico.SESSAO);
        return new Inicializacao(0, servidor);
    }

    /** Inicia o Middleware lendo host, porta e tabuleiro do terminal; encerra a JVM em caso de falha. */
    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            System.err.println("Aviso: argumentos ignorados; o IP é informado no terminal.");
        }
        String host = null;
        Integer porta = null;
        ConfiguracaoTabuleiro tabuleiro = null;
        try {
            BufferedReader entrada =
                    new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
            host = EntradaHost.solicitar(entrada, System.out, EntradaHost.detectarEnderecoLocal())
                    .orElse(null);
            if (host != null) {
                porta = EntradaHost.solicitarPorta(entrada, System.out,
                        ConfiguracaoMiddleware.REGISTRY_PORTA_PADRAO).orElse(null);
            }
            if (host != null && porta != null) {
                tabuleiro = EntradaHost.solicitarTabuleiro(entrada, System.out,
                        ConfiguracaoTabuleiro.PADRAO, CatalogoImagens.carregar().quantidade()).orElse(null);
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler do terminal: " + e.getMessage());
        }
        if (host == null || porta == null || tabuleiro == null) {
            System.err.println("Erro: IP, porta e tamanho do tabuleiro são obrigatórios.");
            System.exit(SAIDA_CONFIGURACAO_INVALIDA);
            return;
        }
        Inicializacao inicio = iniciar(new String[] {
                "--" + CarregadorConfiguracao.RMI_HOSTNAME + "=" + host,
                "--" + CarregadorConfiguracao.REGISTRY_PORTA + "=" + porta,
                "--" + CarregadorConfiguracao.TABULEIRO_LINHAS + "=" + tabuleiro.linhas(),
                "--" + CarregadorConfiguracao.TABULEIRO_COLUNAS + "=" + tabuleiro.colunas()},
                null, System.out, System.err, true);
        if (inicio.codigo() != 0) {
            System.exit(inicio.codigo());
        }
    }
}
