package br.cefet.sd.memoria.cliente.modelo;

import br.cefet.sd.memoria.comum.dto.CausaDesconexao;
import br.cefet.sd.memoria.comum.dto.TipoEvento;

/**
 * Mensagens de status textuais exibidas pelo Cliente: acerto, erro, mudança de vez, vitória,
 * empate, reinício, cancelamento e desconexão de Jogador.
 */
public final class MensagensStatus {

    private static final String JOGADOR_DESCONHECIDO = "Jogador";

    private MensagensStatus() {
    }

    /**
     * Mensagem genérica (sem nome de jogador) para cada {@link TipoEvento}. O {@code switch} é
     * exaustivo: adicionar uma constante ao enum sem tratá-la aqui impede a compilação.
     */
    public static String paraEvento(TipoEvento tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("tipo de evento nulo");
        }
        return switch (tipo) {
            case INICIO_PARTIDA -> "A partida começou.";
            case CARTA_REVELADA -> "Carta revelada.";
            case PAR_ENCONTRADO -> "Acerto! Par encontrado.";
            case ERRO -> "Erro! As cartas não formam um par.";
            case VEZ_PASSADA -> "Mudança de vez.";
            case INATIVIDADE -> "Vez encerrada por inatividade.";
            case PARTIDA_CONCLUIDA -> "A partida terminou.";
            case CONSULTA -> "Estado da partida atualizado.";
        };
    }

    /** Acerto do jogador informado. */
    public static String acerto(String jogador) {
        return nome(jogador) + " acertou um par!";
    }

    /** Erro do jogador informado. */
    public static String erro(String jogador) {
        return nome(jogador) + " errou: as cartas não formam um par.";
    }

    /** Mudança de vez para o jogador informado. */
    public static String mudancaDeVez(String jogadorDaVez) {
        return "Vez de " + nome(jogadorDaVez) + ".";
    }

    /** Vitória do jogador informado. */
    public static String vitoria(String vencedor) {
        return nome(vencedor) + " venceu a partida!";
    }

    /** Empate da partida. */
    public static String empate() {
        return "A partida terminou empatada.";
    }

    /** Reinício da partida. */
    public static String reinicio() {
        return "A partida foi reiniciada.";
    }

    /** Cancelamento da partida causado pela saída/falha do jogador informado. */
    public static String cancelamento(String jogador, CausaDesconexao causa) {
        return "Partida cancelada: " + nome(jogador) + " " + descricaoCausa(causa) + ".";
    }

    /** Desconexão do jogador informado. */
    public static String desconexao(String jogador) {
        return nome(jogador) + " foi desconectado.";
    }

    private static String descricaoCausa(CausaDesconexao causa) {
        if (causa == null) {
            return "foi desconectado";
        }
        return switch (causa) {
            case REMOTE_EXCEPTION -> "perdeu a conexão";
            case TIMEOUT -> "não respondeu a tempo";
            case SAIDA_VOLUNTARIA -> "saiu da partida";
        };
    }

    private static String nome(String jogador) {
        if (jogador == null || jogador.isBlank()) {
            return JOGADOR_DESCONHECIDO;
        }
        return jogador.trim();
    }
}
