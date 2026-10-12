package br.cefet.sd.memoria.cliente;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

/** Ponto de entrada do Cliente (GUI Swing) do Jogo da Memória. */
public final class Principal {

    private Principal() {
    }

    /** Inicia o Cliente. */
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("O Cliente do Jogo da Memória precisa de um ambiente gráfico.");
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> new AplicacaoCliente().iniciar());
    }
}
