package br.cefet.sd.memoria.cliente.ui;

/** Ouvinte do {@link PainelResultado}, chamado na EDT uma única vez por pergunta de continuar. */
@FunctionalInterface
public interface OuvinteResultado {

    /**
     * O Jogador escolheu uma opção. O dono do painel deve enviar a resposta ao Middleware em
     * segundo plano (nunca na EDT), pois os botões já foram desabilitados.
     */
    void aoResponder(boolean continuar);
}
