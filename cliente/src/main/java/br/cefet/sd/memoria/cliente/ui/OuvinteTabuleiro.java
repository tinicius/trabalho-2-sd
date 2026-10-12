package br.cefet.sd.memoria.cliente.ui;

/** Receptor dos cliques do Jogador nas cartas do {@link PainelTabuleiro}. */
@FunctionalInterface
public interface OuvinteTabuleiro {

    /** O Jogador clicou em uma carta habilitada. */
    void aoSelecionar(int posicao);
}
