package br.cefet.sd.memoria.cliente.ui;

import java.awt.Window;
import java.util.Objects;
import javax.swing.JDialog;
import javax.swing.WindowConstants;

/**
 * Fábrica do diálogo que hospeda o {@link PainelResultado}. Mantida separada do painel para que a
 * lógica de exibição seja testável sem criar janelas.
 */
public final class DialogoResultado {

    private DialogoResultado() {
    }

    /**
     * Cria (sem exibir) um diálogo não modal com o painel. O fechamento pela janela é ignorado: o
     * diálogo só fecha por {@link PainelResultado#fechar()}, para não deixar a pergunta sem
     * resposta.
     */
    public static JDialog criar(Window dono, PainelResultado painel) {
        Objects.requireNonNull(painel, "painel");
        JDialog dialogo = new JDialog(dono, "Resultado da partida", java.awt.Dialog.ModalityType.MODELESS);
        dialogo.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialogo.setContentPane(painel);
        dialogo.pack();
        dialogo.setLocationRelativeTo(dono);
        return dialogo;
    }
}
