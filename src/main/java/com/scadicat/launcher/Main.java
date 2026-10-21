package com.scadicat.launcher;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Устанавливаем тёмную тему для Swing (Nimbus/FlatLaf — по желанию)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ScadicatFrame frame = new ScadicatFrame();
            frame.setVisible(true);
        });
    }
}