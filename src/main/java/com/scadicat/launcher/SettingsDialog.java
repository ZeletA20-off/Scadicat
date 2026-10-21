package com.scadicat.launcher;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SettingsDialog extends JDialog {

    private static final Color BG_DARK = new Color(0x1E1E2E);
    private static final Color BG_PANEL = new Color(0x2A2A3E);
    private static final Color ACCENT = new Color(0x4A5BE8);
    private static final Color TEXT_MAIN = Color.WHITE;
    private static final Color TEXT_DIM = new Color(0xAAAAc0);

    private final Settings settings;

    private final JSlider ramSlider;
    private final JLabel ramLabel = new JLabel();
    private final JLabel ramInfoLabel = new JLabel();
    private final JComboBox<String> javaCombo = new JComboBox<>();
    private final JTextField javaPathField = new JTextField();

    public SettingsDialog(JFrame parent, Settings settings) {
        super(parent, "Настройки", true);
        this.settings = settings;

        // ⭐ ДЕЛАЕМ ОКНО БЕЗ РАМОК И С ПРОЗРАЧНЫМ ФОНОМ ДЛЯ ЗАКРУГЛЕНИЙ
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Прозрачный фон (чтобы углы были ровными)
        setSize(650, 620);
        setLocationRelativeTo(parent);

        // ⭐ ЗАКРУГЛЯЕМ САМО ОКНО
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));

        // Панель с закругленным фоном
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_DARK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(0x3B4BD0));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2)); // Отступ для рамки
        setContentPane(root);

        // ======== ШАПКА ========
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(15, 20, 10, 20));

        JLabel title = new JLabel("Настройки");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT_MAIN);
        header.add(title, BorderLayout.WEST);

        // ⭐ КРАСИВАЯ КНОПКА ЗАКРЫТИЯ (Крестик)
        RoundedButton closeBtn = new RoundedButton("", new Color(0xE03030), new Color(0xFF4444), Color.WHITE);
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        closeBtn.setPreferredSize(new Dimension(36, 36));
        closeBtn.addActionListener(e -> dispose());

        JPanel closeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrapper.setOpaque(false);
        closeWrapper.add(closeBtn);
        header.add(closeWrapper, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // ======== КОНТЕНТ ========
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 20, 20, 20));

        // ---------- RAM ----------
        content.add(sectionTitle("Оперативная память"));

        long totalMb = Settings.getTotalRamMb();
        long maxMb   = Settings.getMaxRamForMcMb();
        long recMb   = Settings.getRecommendedRamMb();

        ramInfoLabel.setText(String.format(
                "Всего в системе: %.1f ГБ  •  Доступно для игры: %.1f ГБ",
                totalMb / 1024.0, maxMb / 1024.0));
        ramInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ramInfoLabel.setForeground(TEXT_DIM);
        ramInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(ramInfoLabel);
        content.add(Box.createVerticalStrut(15));

        int maxInt = (int) Math.max(1024, maxMb);
        int initVal = Math.min(settings.ramMb, maxInt);
        if (initVal < 1024) initVal = (int) recMb;

        ramSlider = new JSlider(1024, maxInt, initVal);
        ramSlider.setOpaque(false);
        ramSlider.putClientProperty(FlatClientProperties.STYLE,
                "trackColor: #2A2A3E; thumbColor: #4A5BE8; hoverThumbColor: #5E6FFF;");
        ramSlider.setMajorTickSpacing(4096);
        ramSlider.setMinorTickSpacing(1024);
        ramSlider.setPaintTicks(true);
        ramSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        ramSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        ramSlider.addChangeListener(e -> {
            int v = ramSlider.getValue();
            v = (v / 512) * 512;
            ramLabel.setText(String.format("%.1f ГБ", v / 1024.0));
        });

        content.add(ramSlider);
        content.add(Box.createVerticalStrut(10));

        JPanel ramBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ramBottom.setOpaque(false);
        ramBottom.setAlignmentX(Component.LEFT_ALIGNMENT);

        ramLabel.setText(String.format("%.1f ГБ", initVal / 1024.0));
        ramLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        ramLabel.setForeground(ACCENT);
        ramBottom.add(ramLabel);

        ramBottom.add(Box.createHorizontalStrut(20));

        RoundedButton recBtn = new RoundedButton("Рекомендуется: " + String.format("%.1f ГБ", recMb / 1024.0), BG_PANEL, new Color(0x3A3A5E), ACCENT);
        recBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        recBtn.setPreferredSize(new Dimension(200, 35));
        recBtn.addActionListener(e -> ramSlider.setValue((int) recMb));
        ramBottom.add(recBtn);

        content.add(ramBottom);
        content.add(Box.createVerticalStrut(30));

        // ---------- JAVA ----------
        content.add(sectionTitle("Java"));
        JLabel javaHint = new JLabel("Для 1.17+ нужна Java 17, для 1.20.5+ — Java 21");
        javaHint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        javaHint.setForeground(TEXT_DIM);
        javaHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(javaHint);
        content.add(Box.createVerticalStrut(10));

        List<JavaInstall> javas = findJavaInstalls();

        javaCombo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        javaCombo.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: #2A2A3E; foreground: #ffffff; borderColor: #3B4BD0; focusColor: #4A5BE8;");
        javaCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        javaCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        javaCombo.addItem("Автоматически (JAVA_HOME / PATH)");
        for (JavaInstall j : javas) javaCombo.addItem(j.label);
        javaCombo.addItem("— Указать вручную —");

        if (settings.javaPath != null) {
            for (int i = 0; i < javas.size(); i++) {
                if (javas.get(i).path.equals(settings.javaPath)) {
                    javaCombo.setSelectedIndex(i + 1);
                    break;
                }
            }
        }

        content.add(javaCombo);
        content.add(Box.createVerticalStrut(10));

        javaPathField.setText(settings.javaPath != null ? settings.javaPath : "");
        javaPathField.setFont(new Font("Consolas", Font.PLAIN, 13));
        javaPathField.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: #2A2A3E; foreground: #ffffff; borderColor: #3B4BD0; focusColor: #4A5BE8; margin: 0, 10, 0, 10;");
        javaPathField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        javaPathField.setAlignmentX(Component.LEFT_ALIGNMENT);

        javaCombo.addActionListener(e -> {
            int idx = javaCombo.getSelectedIndex();
            if (idx == 0) {
                javaPathField.setText("");
                javaPathField.setEnabled(false);
            } else if (idx == javaCombo.getItemCount() - 1) {
                javaPathField.setEnabled(true);
                javaPathField.requestFocus();
            } else {
                JavaInstall j = javas.get(idx - 1);
                javaPathField.setText(j.path);
                javaPathField.setEnabled(false);
            }
        });

        if (settings.javaPath == null) javaPathField.setEnabled(false);

        content.add(javaPathField);
        content.add(Box.createVerticalStrut(10));

        RoundedButton browseJavaBtn = new RoundedButton("Обзор...", BG_PANEL, new Color(0x3A3A5E), Color.WHITE);
        browseJavaBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        browseJavaBtn.setPreferredSize(new Dimension(120, 35));
        browseJavaBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        browseJavaBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите java.exe");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                if (f != null) {
                    javaPathField.setText(f.getAbsolutePath());
                    javaCombo.setSelectedIndex(javaCombo.getItemCount() - 1);
                    javaPathField.setEnabled(true);
                }
            }
        });
        content.add(browseJavaBtn);
        content.add(Box.createVerticalStrut(30));

        // ---------- ПАПКА ----------
        content.add(sectionTitle("Игровая папка"));
        JLabel folderLabel = new JLabel(ModpackSync.ROOT.toString());
        folderLabel.setFont(new Font("Consolas", Font.PLAIN, 13));
        folderLabel.setForeground(TEXT_DIM);
        folderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(folderLabel);
        content.add(Box.createVerticalStrut(10));

        RoundedButton openFolderBtn = new RoundedButton("📁  Открыть папку", ACCENT, new Color(0x5E6FFF), Color.WHITE);
        openFolderBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        openFolderBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        openFolderBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        openFolderBtn.addActionListener(e -> {
            try {
                Files.createDirectories(ModpackSync.ROOT);
                Desktop.getDesktop().open(ModpackSync.ROOT.toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Не удалось открыть: " + ex.getMessage());
            }
        });
        content.add(openFolderBtn);

        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        // ======== ПОДВАЛ ========
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 20, 20, 20));

        RoundedButton saveBtn = new RoundedButton("Сохранить", ACCENT, new Color(0x5E6FFF), Color.WHITE);
        saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        saveBtn.setPreferredSize(new Dimension(180, 45));
        saveBtn.addActionListener(e -> {
            int v = ramSlider.getValue();
            v = (v / 512) * 512;
            settings.ramMb = v;

            int idx = javaCombo.getSelectedIndex();
            if (idx == 0) {
                settings.javaPath = null;
            } else if (idx == javaCombo.getItemCount() - 1) {
                String p = javaPathField.getText().trim();
                settings.javaPath = p.isEmpty() ? null : p;
            } else {
                settings.javaPath = javas.get(idx - 1).path;
            }

            settings.save();
            dispose();
        });

        JPanel saveWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        saveWrapper.setOpaque(false);
        saveWrapper.add(saveBtn);
        footer.add(saveWrapper, BorderLayout.EAST);

        root.add(footer, BorderLayout.SOUTH);
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        l.setForeground(ACCENT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // ==================== КАСТОМНАЯ КНОПКА-КАПСУЛА ====================
    class RoundedButton extends JButton {
        private Color bgColor;
        private Color hoverColor;
        private Color currentColor;

        public RoundedButton(String text, Color bg, Color hover, Color fg) {
            super(text);
            this.bgColor = bg;
            this.hoverColor = hover;
            this.currentColor = bg;

            setForeground(fg);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    currentColor = hoverColor;
                    repaint();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    currentColor = bgColor;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(currentColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==================== ПОИСК JAVA ====================
    private static class JavaInstall {
        String label;
        String path;
        JavaInstall(String l, String p) { label = l; path = p; }
    }

    private List<JavaInstall> findJavaInstalls() {
        List<JavaInstall> found = new ArrayList<>();

        String jh = System.getenv("JAVA_HOME");
        if (jh != null) {
            Path p = Paths.get(jh, "bin", isWindows() ? "java.exe" : "java");
            if (Files.exists(p)) {
                found.add(new JavaInstall("JAVA_HOME — " + jh, p.toString()));
            }
        }

        if (isWindows()) {
            Path[] roots = {
                    Paths.get("C:\\Program Files\\Java"),
                    Paths.get("C:\\Program Files\\Eclipse Adoptium"),
                    Paths.get("C:\\Program Files\\Microsoft"),
                    Paths.get("C:\\Program Files\\Amazon Corretto"),
                    Paths.get("C:\\Program Files\\BellSoft"),
                    Paths.get("C:\\Program Files\\Zulu"),
                    Paths.get(System.getenv("LOCALAPPDATA"), "Programs", "Eclipse Adoptium"),
                    Paths.get("C:\\Program Files (x86)\\Minecraft Launcher\\runtime")
            };

            for (Path root : roots) {
                if (root == null || !Files.isDirectory(root)) continue;
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory).forEach(dir -> {
                        Path javaExe = dir.resolve("bin").resolve("java.exe");
                        if (Files.exists(javaExe)) {
                            found.add(new JavaInstall(dir.getFileName().toString(), javaExe.toString()));
                        }
                    });
                } catch (Exception ignored) {}
            }
        } else {
            Path[] roots = {
                    Paths.get("/usr/lib/jvm"),
                    Paths.get("/Library/Java/JavaVirtualMachines"),
                    Paths.get(System.getProperty("user.home"), ".sdkman", "candidates", "java")
            };
            for (Path root : roots) {
                if (root == null || !Files.isDirectory(root)) continue;
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory).forEach(dir -> {
                        Path javaExe = dir.resolve("bin").resolve("java");
                        if (Files.exists(javaExe)) {
                            found.add(new JavaInstall(dir.getFileName().toString(), javaExe.toString()));
                        }
                    });
                } catch (Exception ignored) {}
            }
        }

        try {
            String path = System.getenv("PATH");
            if (path != null) {
                for (String part : path.split(File.pathSeparator)) {
                    Path javaExe = Paths.get(part, isWindows() ? "java.exe" : "java");
                    if (Files.exists(javaExe)) {
                        boolean exists = found.stream().anyMatch(j -> j.path.equals(javaExe.toString()));
                        if (!exists) {
                            found.add(new JavaInstall("PATH — " + javaExe, javaExe.toString()));
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        return found;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}