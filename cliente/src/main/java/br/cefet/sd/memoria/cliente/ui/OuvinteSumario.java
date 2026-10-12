package br.cefet.sd.memoria.cliente.ui;

/** Ouvinte da {@link TelaSumario}, chamado na EDT. */
@FunctionalInterface
public interface OuvinteSumario {

    /** O Jogador pediu para voltar à tela de conexão. */
    void aoFechar();
}
