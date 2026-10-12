package br.cefet.sd.memoria.cliente.ui;

/** Ouvinte da {@link TelaSala}, chamado na EDT. */
@FunctionalInterface
public interface OuvinteTelaSala {

    /**
     * O Jogador clicou em "Pronto". O dono da tela deve enviar a operação ao Middleware em segundo
     * plano (nunca na EDT); em falha, pode chamar {@link TelaSala#reabilitarPronto()}.
     */
    void aoMarcarPronto();
}
