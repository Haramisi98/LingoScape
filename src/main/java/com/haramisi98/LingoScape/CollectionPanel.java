package com.haramisi98.LingoScape;

import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.BorderLayout;

final class CollectionPanel extends PluginPanel {
    private final JLabel status = new JLabel("Collection off");
    private final JTextArea message = new JTextArea("Exports contain local game text. Review before sharing.");
    CollectionPanel(Runnable export) {
        setLayout(new BorderLayout(0, 12));
        add(status, BorderLayout.NORTH);
        message.setEditable(false); message.setLineWrap(true); message.setWrapStyleWord(true);
        add(message, BorderLayout.CENTER);
        JButton button = new JButton("Export missing text");
        button.addActionListener(event -> export.run());
        add(button, BorderLayout.SOUTH);
    }
    void status(String value) { SwingUtilities.invokeLater(() -> status.setText(value)); }
    void message(String value) { SwingUtilities.invokeLater(() -> message.setText(value)); }
}
