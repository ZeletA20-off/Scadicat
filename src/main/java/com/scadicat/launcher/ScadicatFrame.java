package com.scadicat.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScadicatFrame extends JFrame {

    private final ScadicatLauncher launcher = new ScadicatLauncher(this::setStatus);
    private final ModpackSync sync = new ModpackSync(this::setStatus);

    private static final Color BG_COLOR = new Color(0x4A5BE8);
    private static final Color PLAY_BG = new Color(0xF5B041);
    private static final Color PLAY_HOVER = new Color(0xFFC35E);
    private static final Color UPDATE_BG = new Color(0x28B463);
    private static final Color UPDATE_HOVER = new Color(0x2ECC71);
    private static final Color NICK_BG = new Color(0xCCCCCC);
    private static final Color SIDE_BG = new Color(0x3B4BD0);
    private static final Color SIDE_HOVER = new Color(0x5061FF);
    private static final Color SIDE_ACTIVE = new Color(0x7B8AFF);

    private static final String FORGE_MC_VERSION = "1.20.1";
    private static final String FORGE_VERSION = "47.4.23";

    private String packName = "ScadicatPack";
    private String mcVersion = "1.20.1";
    private BufferedImage backgroundImage;

    private final JPanel versionsPanel = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextField usernameField;

    private final RoundedButton playBtn = new RoundedButton("Играть", PLAY_BG, PLAY_HOVER, Color.WHITE);
    private final RoundedButton updateBtn = new RoundedButton("Обновить", UPDATE_BG, UPDATE_HOVER, Color.WHITE);

    private String selectedVersion = null;

    public ScadicatFrame() {
        super("Scadicat Launcher");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 650);
        setMinimumSize(new Dimension(900, 540));
        setLocationRelativeTo(null);

        Settings savedSettings = Settings.load();
        usernameField = new RoundedTextField(savedSettings.username != null ? savedSettings.username : "Player");

        loadBackground();

        JPanel root = new JPanel(new BorderLayout(20, 10));
        root.setBackground(BG_COLOR);
        root.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));
        setContentPane(root);

        // ========== ВЕРХ ==========
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Scadicat Launcher");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        RoundedButton settingsBtn = new RoundedButton("Настройки ⚙", SIDE_BG, SIDE_HOVER, Color.WHITE);
        settingsBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        settingsBtn.setPreferredSize(new Dimension(140, 35));
        settingsBtn.addActionListener(e -> openSettingsDialog());

        JPanel settingsWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        settingsWrapper.setOpaque(false);
        settingsWrapper.add(settingsBtn);
        headerPanel.add(settingsWrapper, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

        // ========== ЦЕНТР ==========
        JPanel centerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                if (backgroundImage != null) {
                    int w = getWidth();
                    int h = getHeight();
                    int iw = backgroundImage.getWidth();
                    int ih = backgroundImage.getHeight();
                    double scale = Math.max((double) w / iw, (double) h / ih);
                    int dw = (int) (iw * scale);
                    int dh = (int) (ih * scale);
                    Shape clip = new RoundRectangle2D.Double(0, 0, w, h, 20, 20);
                    g2.setClip(clip);
                    g2.drawImage(backgroundImage, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                }
                g2.dispose();
            }
        };
        centerPanel.setOpaque(false);

        JPanel centerContainer = new JPanel(new BorderLayout(15, 0));
        centerContainer.setOpaque(false);
        centerContainer.add(centerPanel, BorderLayout.CENTER);

        // ========== СПРАВА ==========
        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(250, 0));

        JLabel versionsLabel = new JLabel("версии");
        versionsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        versionsLabel.setForeground(Color.WHITE);
        versionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(versionsLabel);
        rightPanel.add(Box.createVerticalStrut(10));

        versionsPanel.setOpaque(false);
        versionsPanel.setLayout(new BoxLayout(versionsPanel, BoxLayout.Y_AXIS));
        versionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(versionsPanel);
        rightPanel.add(Box.createVerticalGlue());

        centerContainer.add(rightPanel, BorderLayout.EAST);
        root.add(centerContainer, BorderLayout.CENTER);

        // ========== НИЗ ==========
        JPanel bottomWrapper = new JPanel(new BorderLayout(0, 5));
        bottomWrapper.setOpaque(false);
        bottomWrapper.setPreferredSize(new Dimension(0, 90));

        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomWrapper.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new GridBagLayout());
        buttonsPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 5, 0, 5);
        gbc.weighty = 1.0;

        playBtn.setPreferredSize(new Dimension(0, 50));
        playBtn.addActionListener(e -> playAsync());
        gbc.gridx = 0; gbc.weightx = 0.5;
        buttonsPanel.add(playBtn, gbc);

        usernameField.setPreferredSize(new Dimension(0, 50));
        usernameField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { saveUsername(); }
        });
        usernameField.addActionListener(e -> saveUsername());
        gbc.gridx = 1; gbc.weightx = 0.25;
        buttonsPanel.add(usernameField, gbc);

        updateBtn.setPreferredSize(new Dimension(0, 50));
        updateBtn.addActionListener(e -> manualUpdateAsync());
        gbc.gridx = 2; gbc.weightx = 0.25;
        buttonsPanel.add(updateBtn, gbc);

        bottomWrapper.add(buttonsPanel, BorderLayout.CENTER);
        root.add(bottomWrapper, BorderLayout.SOUTH);

        updateVersionPanel();
        SwingUtilities.invokeLater(this::initAsync);
    }

    // ==================== КОМПОНЕНТЫ ====================
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
            setFont(new Font("Segoe UI", Font.BOLD, 18));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { currentColor = hoverColor; repaint(); }
                @Override public void mouseExited(MouseEvent e) { currentColor = bgColor; repaint(); }
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

    class RoundedTextField extends JTextField {
        public RoundedTextField(String text) {
            super(text);
            setHorizontalAlignment(JTextField.CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 18));
            setForeground(Color.BLACK);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
            setCaretColor(Color.BLACK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(NICK_BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==================== ЛОГИКА ====================
    private void saveUsername() {
        try {
            String name = usernameField.getText().trim();
            if (name.isEmpty()) name = "Player";
            Settings s = Settings.load();
            if (!name.equals(s.username)) {
                s.username = name;
                s.save();
            }
        } catch (Exception ignored) {}
    }

    private void openSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, Settings.load());
        dialog.setVisible(true);
        Settings updated = Settings.load();
        if (updated.username != null && !updated.username.equals(usernameField.getText())) {
            usernameField.setText(updated.username);
        }
    }

    private JButton createVersionButton(String displayName, String versionId) {
        RoundedButton btn = new RoundedButton(displayName, SIDE_BG, SIDE_HOVER, Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        btn.setPreferredSize(new Dimension(220, 50));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);

        btn.addActionListener(e -> {
            selectedVersion = versionId;
            mcVersion = versionId;
            for (Component c : versionsPanel.getComponents()) {
                if (c instanceof RoundedButton rb) {
                    rb.bgColor = SIDE_BG;
                    rb.currentColor = SIDE_BG;
                    rb.repaint();
                }
            }
            btn.bgColor = SIDE_ACTIVE;
            btn.currentColor = SIDE_ACTIVE;
            btn.repaint();
            setStatus("Выбрана версия: " + displayName);
        });
        return btn;
    }

    private void loadBackground() {
        String[] names = {"background.png", "background.jpg", "background.jpeg"};
        Path externalDir = ModpackSync.ROOT;
        if (Files.isDirectory(externalDir)) {
            for (String name : names) {
                Path p = externalDir.resolve(name);
                if (Files.exists(p)) {
                    try {
                        BufferedImage img = ImageIO.read(p.toFile());
                        if (img != null) { backgroundImage = img; return; }
                    } catch (IOException ignored) {}
                }
            }
        }
        for (String name : names) {
            try (InputStream is = getClass().getResourceAsStream("/" + name)) {
                if (is != null) {
                    BufferedImage img = ImageIO.read(is);
                    if (img != null) { backgroundImage = img; return; }
                }
            } catch (IOException ignored) {}
        }
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) statusLabel.setText(text);
        });
    }

    private void ensureForgeInstalled() {
        String forgeDirName = FORGE_MC_VERSION + "-forge-" + FORGE_VERSION;
        Path forgeDir = ModpackSync.VERSIONS_DIR.resolve(forgeDirName);
        Path forgeJson = forgeDir.resolve(forgeDirName + ".json");

        if (Files.exists(forgeJson)) {
            log("✓ Forge " + FORGE_VERSION + " уже установлен");
            return;
        }

        log("⚠ Forge не найден, устанавливаю...");
        try {
            launcher.installForge(FORGE_MC_VERSION, FORGE_VERSION);
            log("✓ Forge установлен!");
        } catch (Exception ex) {
            log("❌ Ошибка установки Forge: " + ex.getMessage());
        }
    }

    private void log(String msg) {
        System.out.println("[Scadicat] " + msg);
        setStatus(msg);
    }

    private void updateVersionPanel() {
        SwingUtilities.invokeLater(() -> {
            versionsPanel.removeAll();
            String forgeVer = FORGE_MC_VERSION + "-forge-" + FORGE_VERSION;
            Path forgeDir = ModpackSync.VERSIONS_DIR.resolve(forgeVer);
            boolean hasForge = Files.isDirectory(forgeDir);

            String targetVersion = hasForge ? forgeVer : mcVersion;
            JButton packBtn = createVersionButton(packName, targetVersion);
            versionsPanel.add(packBtn);
            versionsPanel.add(Box.createVerticalStrut(10));

            if (hasForge) {
                JButton forgeBtn = createVersionButton(forgeVer, forgeVer);
                ((RoundedButton)forgeBtn).bgColor = SIDE_ACTIVE;
                ((RoundedButton)forgeBtn).currentColor = SIDE_ACTIVE;
                selectedVersion = forgeVer;
                versionsPanel.add(forgeBtn);
            } else {
                selectedVersion = mcVersion;
                ((RoundedButton)packBtn).bgColor = SIDE_ACTIVE;
                ((RoundedButton)packBtn).currentColor = SIDE_ACTIVE;
            }

            versionsPanel.revalidate();
            versionsPanel.repaint();
        });
    }

    private void initAsync() {
        new Thread(() -> {
            try {
                // ⭐ 1. Сначала проверяем и скачиваем Java
                setStatus("Проверка встроенной Java...");
                launcher.installJavaIfNeeded();

                // 2. Проверяем Forge
                setStatus("Проверка Forge...");
                ensureForgeInstalled();

                // 3. Синхронизация модпака
                setStatus("Проверка обновлений модпака...");
                ModpackSync.SyncResult result = sync.sync();

                this.packName = result.packName;
                if (result.minecraftVersion != null) this.mcVersion = result.minecraftVersion;

                if (result.updated) {
                    setStatus("Модпак обновлён до " + result.remoteVersion);
                } else {
                    setStatus("Модпак актуален");
                }

                updateVersionPanel();
            } catch (Exception ex) {
                setStatus("Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        }).start();
    }

    private void manualUpdateAsync() {
        updateBtn.setEnabled(false);
        updateBtn.setText("Обновление...");
        new Thread(() -> {
            try {
                setStatus("Проверка встроенной Java...");
                launcher.installJavaIfNeeded();

                setStatus("Проверка Forge...");
                ensureForgeInstalled();

                setStatus("Скачивание обновления модпака...");
                ModpackSync.SyncResult result = sync.sync();

                if (result.updated) {
                    setStatus("Обновлено до " + result.remoteVersion);
                    JOptionPane.showMessageDialog(this, "Модпак обновлён до " + result.remoteVersion, "Обновление", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    setStatus("Обновлений нет");
                    JOptionPane.showMessageDialog(this, "Установлена последняя версия", "Обновление", JOptionPane.INFORMATION_MESSAGE);
                }
                updateVersionPanel();
            } catch (Exception ex) {
                setStatus("Ошибка: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Не удалось обновить:\n" + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Обновить");
                });
            }
        }).start();
    }

    private void playAsync() {
        saveUsername();
        String username = usernameField.getText().trim();
        if (username.isEmpty()) username = "Player";
        final String user = username;
        final String ver = (selectedVersion != null) ? selectedVersion : mcVersion;

        new Thread(() -> {
            try {
                setStatus("Запуск " + ver + "...");
                launcher.launch(ver, packName, user);
                setStatus("Игра запущена");
            } catch (Exception ex) {
                setStatus("Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        }).start();
    }
}