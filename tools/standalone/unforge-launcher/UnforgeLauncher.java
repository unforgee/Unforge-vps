package org.unforge.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Modern UnForge launcher with verified client updates and a ready-to-play UI. */
public final class UnforgeLauncher {
    private static final String APP_VERSION = "2.0.0";
    private static final String CLIENT_RESOURCE = "/bundled/client.jar";
    private static final String WORLD_LIST_RESOURCE = "/bundled/world_list.ws";
    private static final String BACKGROUND_RESOURCE = "/bundled/launcher-bg.png";
    private static final String GAMEPACK_NAME = "gamepack_2506588_public.jar";
    private static final String DEFAULT_SERVER_HOST = "94.237.118.174";
    private static final int DEFAULT_GAME_PORT = 43594;
    private static final String DEFAULT_MANIFEST_URL =
            "https://rspsunforge.online/downloads/client-manifest.json";
    private static final Color RED = new Color(238, 38, 29);
    private static final Color ORANGE = new Color(255, 133, 57);
    private static final Color WHITE = new Color(245, 245, 241);
    private static final Color MUTED = new Color(169, 174, 174);
    private static final Color PANEL = new Color(7, 10, 13, 226);
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Options options;
    private final JFrame frame = new JFrame();
    private final JButton updateButton = actionButton("UPDATE", true);
    private final JButton playButton = actionButton("PLAY", true);
    private final JLabel statusLabel = new JLabel("Checking for the latest client...");
    private final JLabel versionLabel = new JLabel("CLIENT STATUS  /  CONNECTING");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea updateLog = logArea();
    private final JTextArea releaseNotes = notesArea();
    private final ExecutorService workers = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "unforge-launcher-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean updateRunning = new AtomicBoolean();
    private final Path clientDirectory = Path.of(System.getProperty("user.home"), ".unforge", "client");
    private final Path clientPath = clientDirectory.resolve("unforge-client.jar");
    private final Path statePath = clientDirectory.resolve("client.properties");
    private final Path updateLogPath = clientDirectory.resolve("update.log");
    private volatile String host;
    private volatile int port;
    private volatile Path readyClient;
    private volatile LocalConfigServer configServer;
    private int dragX;
    private int dragY;

    private UnforgeLauncher(Options options) {
        this.options = options;
        this.host = validateHost(firstNonBlank(options.host, DEFAULT_SERVER_HOST));
        this.port = options.port > 0 ? options.port : DEFAULT_GAME_PORT;
        if (this.port != DEFAULT_GAME_PORT) {
            throw new IllegalArgumentException("This client uses game server port " + DEFAULT_GAME_PORT + ".");
        }
        buildWindow();
        loadSettings();
        loadPreviousLog();
    }

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                printHelp();
                return;
            }
            if (Runtime.version().feature() < 21) {
                throw new IllegalStateException("UnForge requires Java 21 or newer.");
            }
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("The UnForge launcher requires a graphical desktop.");
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    new UnforgeLauncher(options).show();
                } catch (Exception exception) {
                    showFatal(exception);
                }
            });
        } catch (Exception exception) {
            showFatal(exception);
        }
    }

    private void show() {
        frame.setVisible(true);
        appendLog("Launcher " + APP_VERSION + " started.");
        appendLog("Update source: " + options.manifestUrl);
        startUpdate(true);
    }

    private void buildWindow() {
        frame.setTitle("UnForge Launcher");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 650));
        frame.setSize(1120, 710);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(new BackgroundPanel(loadBackground()));

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setOpaque(false);
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        frame.add(root);
    }

    private JPanel buildTopBar() {
        JPanel bar = transparentPanel(new BorderLayout());
        bar.setBorder(new InsetsBorder(22, 28, 16, 25));
        JLabel logo = new JLabel("UNFORGE  /  LAUNCHER");
        logo.setForeground(WHITE);
        logo.setFont(displayFont(Font.BOLD, 18));
        bar.add(logo, BorderLayout.WEST);

        JPanel controls = transparentPanel(new GridLayout(1, 2, 7, 0));
        JButton minimize = new JButton("—");
        JButton close = new JButton("×");
        for (JButton button : new JButton[] {minimize, close}) {
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setForeground(MUTED);
            button.setFont(new Font("Segoe UI", Font.BOLD, 20));
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setFocusPainted(false);
        }
        minimize.addActionListener(event -> frame.setState(JFrame.ICONIFIED));
        close.addActionListener(event -> closeLauncher());
        controls.add(minimize);
        controls.add(close);
        bar.add(controls, BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragX = event.getX();
                dragY = event.getY();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                frame.setLocation(event.getXOnScreen() - dragX, event.getYOnScreen() - dragY);
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        return bar;
    }

    private JPanel buildContent() {
        JPanel content = transparentPanel(new GridBagLayout());
        content.setBorder(new InsetsBorder(12, 45, 40, 45));

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.weightx = 0.62;
        left.weighty = 1;
        left.fill = GridBagConstraints.BOTH;
        left.insets = new Insets(0, 0, 0, 18);
        content.add(buildMainCard(), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.weightx = 0.38;
        right.weighty = 1;
        right.fill = GridBagConstraints.BOTH;
        content.add(buildUpdateCard(), right);
        return content;
    }

    private JPanel buildMainCard() {
        GlassPanel card = new GlassPanel(PANEL, 22);
        card.setLayout(new BorderLayout());
        card.setBorder(new InsetsBorder(38, 38, 31, 38));

        JPanel intro = transparentPanel(new GridBagLayout());
        GridBagConstraints introText = new GridBagConstraints();
        introText.gridx = 0;
        introText.gridy = 0;
        introText.weightx = 1;
        introText.anchor = GridBagConstraints.WEST;
        introText.fill = GridBagConstraints.HORIZONTAL;
        JLabel kicker = label("MMO RPG  /  REVISION 239", 11, RED);
        intro.add(kicker, introText);
        introText.gridy++;
        JLabel title = label("<html>THE WORLD<br><font color='#ee261d'>KEEPS SCORE.</font></html>", 55, WHITE);
        title.setFont(displayFont(Font.BOLD, 55));
        title.setBorder(BorderFactory.createEmptyBorder(14, 0, 10, 0));
        intro.add(title, introText);
        introText.gridy++;
        JLabel copy = label(
                "<html>Companions that learn. Item instances that remember.<br>"
                        + "A living world forged for the ones who keep going.</html>",
                14,
                MUTED);
        copy.setFont(bodyFont(Font.PLAIN, 14));
        intro.add(copy, introText);
        introText.gridy++;
        versionLabel.setFont(monoFont(Font.PLAIN, 10));
        versionLabel.setForeground(MUTED);
        versionLabel.setBorder(BorderFactory.createEmptyBorder(24, 0, 0, 0));
        intro.add(versionLabel, introText);
        card.add(intro, BorderLayout.NORTH);

        JPanel actionArea = transparentPanel(new BorderLayout(0, 18));
        actionArea.setBorder(BorderFactory.createEmptyBorder(26, 0, 0, 0));
        JPanel actions = transparentPanel(new GridLayout(1, 2, 13, 0));
        playButton.setEnabled(false);
        updateButton.addActionListener(event -> startUpdate(false));
        playButton.addActionListener(event -> play());
        actions.add(updateButton);
        actions.add(playButton);
        actionArea.add(actions, BorderLayout.NORTH);

        JPanel progressArea = transparentPanel(new BorderLayout(0, 9));
        progressBar.setStringPainted(false);
        progressBar.setBorderPainted(false);
        progressBar.setForeground(RED);
        progressBar.setBackground(new Color(35, 37, 38));
        progressBar.setPreferredSize(new Dimension(10, 5));
        progressArea.add(progressBar, BorderLayout.NORTH);
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(monoFont(Font.PLAIN, 10));
        progressArea.add(statusLabel, BorderLayout.CENTER);
        actionArea.add(progressArea, BorderLayout.CENTER);
        card.add(actionArea, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildUpdateCard() {
        GlassPanel card = new GlassPanel(new Color(6, 8, 10, 218), 22);
        card.setLayout(new BorderLayout(0, 13));
        card.setBorder(new InsetsBorder(28, 26, 26, 26));
        JLabel heading = label("UPDATE LOG", 12, WHITE);
        heading.setFont(monoFont(Font.BOLD, 12));
        card.add(heading, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(updateLog);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 25)));
        logScroll.getViewport().setOpaque(false);
        logScroll.setOpaque(false);
        card.add(logScroll, BorderLayout.CENTER);

        JPanel notesPanel = transparentPanel(new BorderLayout(0, 7));
        JLabel notesHeading = label("LATEST RELEASE", 10, RED);
        notesHeading.setFont(monoFont(Font.BOLD, 10));
        notesPanel.add(notesHeading, BorderLayout.NORTH);
        JScrollPane notesScroll = new JScrollPane(releaseNotes);
        notesScroll.setPreferredSize(new Dimension(10, 132));
        notesScroll.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 25)));
        notesScroll.getViewport().setOpaque(false);
        notesScroll.setOpaque(false);
        notesPanel.add(notesScroll, BorderLayout.CENTER);
        card.add(notesPanel, BorderLayout.SOUTH);
        return card;
    }

    private void startUpdate(boolean automatic) {
        if (!updateRunning.compareAndSet(false, true)) {
            return;
        }
        updateButton.setEnabled(false);
        playButton.setEnabled(false);
        setStatus(automatic ? "Checking for the latest client..." : "Checking for updates...");
        workers.submit(() -> {
            try {
                checkAndUpdate(automatic);
            } finally {
                updateRunning.set(false);
                SwingUtilities.invokeLater(() -> updateButton.setEnabled(true));
            }
        });
    }

    private void checkAndUpdate(boolean automatic) {
        try {
            Files.createDirectories(clientDirectory);
            ClientManifest manifest = fetchManifest();
            setReleaseNotes(manifest.releaseNotes);
            appendLog("Manifest found: client " + manifest.version + ".");
            Properties state = loadProperties(statePath);
            String installedVersion = state.getProperty("version", "");
            boolean hashMatches = Files.isRegularFile(clientPath) && sha256(clientPath).equalsIgnoreCase(manifest.sha256);
            if (installedVersion.equals(manifest.version) && hashMatches) {
                readyClient = clientPath;
                appendLog("Client is up to date.");
                setReady("READY  /  " + manifest.version);
                return;
            }
            appendLog("New client available: " + manifest.version + ".");
            downloadClient(manifest);
            Properties updated = new Properties();
            updated.setProperty("version", manifest.version);
            updated.setProperty("sha256", manifest.sha256);
            updated.setProperty("updatedAt", LocalDateTime.now().toString());
            saveProperties(statePath, updated);
            readyClient = clientPath;
            appendLog("Client update verified and installed.");
            setReady("READY  /  " + manifest.version);
        } catch (Exception exception) {
            appendLog("Update check failed: " + safeMessage(exception));
            try {
                readyClient = ensureBundledClient();
                appendLog("Offline fallback ready: bundled client.");
                setReady("READY  /  OFFLINE FALLBACK");
            } catch (Exception fallbackException) {
                appendLog("No playable client found: " + safeMessage(fallbackException));
                setStatus("Update required before playing.");
                versionLabel.setText("CLIENT STATUS  /  UPDATE FAILED");
            }
        }
    }

    private ClientManifest fetchManifest() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(options.manifestUrl))
                .timeout(java.time.Duration.ofSeconds(12))
                .header("Cache-Control", "no-cache")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Manifest returned HTTP " + response.statusCode());
        }
        return ClientManifest.parse(response.body());
    }

    private void downloadClient(ClientManifest manifest) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(manifest.clientUrl))
                .timeout(java.time.Duration.ofMinutes(10))
                .GET()
                .build();
        Path part = clientPath.resolveSibling(clientPath.getFileName() + ".part");
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Client download returned HTTP " + response.statusCode());
        }
        long expected = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long copied = 0;
        setProgress(0);
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                copied += count;
                if (expected > 0) {
                    setProgress((int) Math.min(100, copied * 100 / expected));
                }
                setStatus(expected > 0 ? "Downloading client  /  " + formatBytes(copied) + " of " + formatBytes(expected) : "Downloading client  /  " + formatBytes(copied));
            }
        }
        String downloadedHash = sha256(part);
        if (!downloadedHash.equalsIgnoreCase(manifest.sha256)) {
            Files.deleteIfExists(part);
            throw new SecurityException("Client checksum mismatch.");
        }
        try {
            Files.move(part, clientPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(part, clientPath, StandardCopyOption.REPLACE_EXISTING);
        }
        setProgress(100);
    }

    private Path ensureBundledClient() throws IOException {
        Files.createDirectories(clientDirectory);
        if (!Files.isRegularFile(clientPath)) {
            try (InputStream input = requiredResource(CLIENT_RESOURCE)) {
                Files.copy(input, clientPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return clientPath;
    }

    private void play() {
        Path client = readyClient;
        if (client == null || !Files.isRegularFile(client)) {
            appendLog("Play blocked: no verified client is ready.");
            return;
        }
        playButton.setEnabled(false);
        appendLog("Starting UnForge client.");
        workers.submit(() -> {
            try {
                byte[] worldList = readResource(WORLD_LIST_RESOURCE);
                LocalConfigServer local = new LocalConfigServer(host, port, worldList);
                configServer = local;
                local.start();
                Process process = new ProcessBuilder(
                                javaExecutable(),
                                "-ea",
                                "-jar",
                                client.toString(),
                                "--disable-telemetry",
                                "--jav_config=" + local.configUrl(),
                                "--noupdate")
                        .inheritIO()
                        .start();
                SwingUtilities.invokeLater(() -> setStatus("PLAYING  /  client running"));
                int exitCode = process.waitFor();
                local.close();
                configServer = null;
                appendLog("Client closed with exit code " + exitCode + ".");
                setReady(versionLabel.getText().replace("CLIENT STATUS  /  ", ""));
            } catch (Exception exception) {
                appendLog("Client start failed: " + safeMessage(exception));
                setStatus("Unable to start the client.");
            } finally {
                SwingUtilities.invokeLater(() -> playButton.setEnabled(readyClient != null));
            }
        });
    }

    private void loadSettings() {
        Properties properties = loadProperties(settingsPath());
        String savedHost = properties.getProperty("server.host");
        if (savedHost != null && !savedHost.isBlank()) {
            host = validateHost(savedHost);
        }
        String savedPort = properties.getProperty("server.port");
        if (savedPort != null && !savedPort.isBlank()) {
            try {
                int parsed = Integer.parseInt(savedPort);
                if (parsed == DEFAULT_GAME_PORT) {
                    port = parsed;
                }
            } catch (NumberFormatException ignored) {
                // Keep the safe default.
            }
        }
        properties.setProperty("server.host", host);
        properties.setProperty("server.port", Integer.toString(port));
        try {
            saveProperties(settingsPath(), properties);
        } catch (IOException exception) {
            appendLog("Settings could not be saved: " + safeMessage(exception));
        }
    }

    private void loadPreviousLog() {
        if (!Files.isRegularFile(updateLogPath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(updateLogPath, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - 60);
            for (int index = start; index < lines.size(); index++) {
                updateLog.append(lines.get(index));
                updateLog.append("\n");
            }
        } catch (IOException exception) {
            updateLog.append("Could not load previous log.\n");
        }
    }

    private void appendLog(String message) {
        String line = "[" + LocalDateTime.now().format(LOG_TIME) + "] " + message;
        SwingUtilities.invokeLater(() -> {
            updateLog.append(line);
            updateLog.append("\n");
            updateLog.setCaretPosition(updateLog.getDocument().getLength());
        });
        try {
            Files.createDirectories(updateLogPath.getParent());
            Files.writeString(updateLogPath, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // The on-screen log remains available if the local log file cannot be written.
        }
    }

    private void setReady(String version) {
        SwingUtilities.invokeLater(() -> {
            setStatus("Ready to play.");
            versionLabel.setText("CLIENT STATUS  /  " + version);
            progressBar.setValue(100);
            playButton.setEnabled(readyClient != null);
        });
    }

    private void setStatus(String value) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(value));
    }

    private void setProgress(int value) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(value));
    }

    private void setReleaseNotes(List<String> notes) {
        SwingUtilities.invokeLater(() -> {
            releaseNotes.setText("");
            for (String note : notes) {
                releaseNotes.append("• " + note + "\n");
            }
            releaseNotes.setCaretPosition(0);
        });
    }

    private void closeLauncher() {
        try {
            if (configServer != null) {
                configServer.close();
            }
        } catch (IOException ignored) {
            // Closing the launcher should still complete.
        }
        workers.shutdownNow();
        frame.dispose();
    }

    private BufferedImage loadBackground() {
        try (InputStream input = requiredResource(BACKGROUND_RESOURCE)) {
            return ImageIO.read(input);
        } catch (Exception exception) {
            return null;
        }
    }

    private static JButton actionButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(monoFont(Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(primary ? RED : new Color(255, 255, 255, 40)));
        button.setBackground(primary ? RED : new Color(20, 23, 25));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(10, 52));
        return button;
    }

    private static JTextArea logArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(new Color(206, 210, 208));
        area.setBackground(new Color(5, 7, 9, 185));
        area.setFont(monoFont(Font.PLAIN, 10));
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return area;
    }

    private static JTextArea notesArea() {
        JTextArea area = logArea();
        area.setText("Waiting for the latest release manifest...");
        return area;
    }

    private static JPanel transparentPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(bodyFont(Font.PLAIN, size));
        return label;
    }

    private static Font displayFont(int style, int size) {
        return new Font("Bahnschrift Condensed", style, size);
    }

    private static Font bodyFont(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    private static Font monoFont(int style, int size) {
        return new Font("Consolas", style, size);
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
                // Use defaults.
            }
        }
        return properties;
    }

    private static void saveProperties(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "UnForge launcher state");
        }
    }

    private static Path settingsPath() {
        return Path.of(System.getProperty("user.home"), ".unforge", "launcher.properties");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024d / 1024d);
    }

    private static String javaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        Path javaHome = Path.of(System.getProperty("java.home"), "bin", executable);
        return Files.isRegularFile(javaHome) ? javaHome.toString() : executable;
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream input = requiredResource(resource); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static InputStream requiredResource(String resource) throws IOException {
        InputStream input = UnforgeLauncher.class.getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("Launcher resource is missing: " + resource);
        }
        return input;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String validateHost(String value) {
        String host = value.trim();
        if (host.isEmpty() || host.contains("/") || host.contains("\\") || host.contains(" ") || host.contains("=")) {
            throw new IllegalArgumentException("Server host is not valid.");
        }
        return host;
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void showFatal(Exception exception) {
        String message = safeMessage(exception);
        System.err.println("UnForge launcher: " + message);
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            javax.swing.JOptionPane.showMessageDialog(null, message, "UnForge launcher", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void printHelp() {
        System.out.println("UnForge Launcher " + APP_VERSION);
        System.out.println("  --server-host <host>  game server host");
        System.out.println("  --server-port <port>  game server port");
        System.out.println("  --manifest-url <url>   client update manifest URL");
        System.out.println("  --help                show this help");
    }

    private record Options(String host, int port, String manifestUrl, boolean help) {
        private static Options parse(String[] args) {
            String host = null;
            int port = -1;
            String manifest = DEFAULT_MANIFEST_URL;
            boolean help = false;
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if (argument.equals("--help") || argument.equals("-h")) {
                    help = true;
                } else if (argument.equals("--server-host") && index + 1 < args.length) {
                    host = args[++index];
                } else if (argument.startsWith("--server-host=")) {
                    host = argument.substring("--server-host=".length());
                } else if (argument.equals("--server-port") && index + 1 < args.length) {
                    port = parsePort(args[++index]);
                } else if (argument.startsWith("--server-port=")) {
                    port = parsePort(argument.substring("--server-port=".length()));
                } else if (argument.equals("--manifest-url") && index + 1 < args.length) {
                    manifest = args[++index];
                } else if (argument.startsWith("--manifest-url=")) {
                    manifest = argument.substring("--manifest-url=".length());
                } else {
                    throw new IllegalArgumentException("Unknown launcher argument: " + argument);
                }
            }
            return new Options(host, port, manifest, help);
        }

        private static int parsePort(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return -1;
            }
        }
    }

    private record ClientManifest(String version, String clientUrl, String sha256, List<String> releaseNotes) {
        private static ClientManifest parse(String json) {
            String version = jsonString(json, "clientVersion", jsonString(json, "version", ""));
            String clientUrl = jsonString(json, "clientUrl", "");
            String sha256 = jsonString(json, "sha256", "");
            List<String> notes = jsonStringArray(json, "releaseNotes");
            if (version.isBlank() || clientUrl.isBlank() || sha256.length() != 64) {
                throw new IllegalArgumentException("Update manifest is incomplete.");
            }
            return new ClientManifest(version, clientUrl, sha256, notes);
        }

        private static String jsonString(String json, String key, String fallback) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
            Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                return fallback;
            }
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
        }

        private static List<String> jsonStringArray(String json, String key) {
            Pattern arrayPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher arrayMatcher = arrayPattern.matcher(json);
            List<String> result = new ArrayList<>();
            if (!arrayMatcher.find()) {
                return result;
            }
            Matcher itemMatcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(arrayMatcher.group(1));
            while (itemMatcher.find()) {
                result.add(itemMatcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n"));
            }
            return result;
        }
    }

    private static final class BackgroundPanel extends JPanel {
        private final BufferedImage image;

        private BackgroundPanel(BufferedImage image) {
            this.image = image;
            setLayout(new BorderLayout());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (image != null) {
                double scale = Math.max((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
                int width = (int) Math.ceil(image.getWidth() * scale);
                int height = (int) Math.ceil(image.getHeight() * scale);
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;
                g.drawImage(image, x, y, width, height, null);
            } else {
                g.setColor(new Color(7, 9, 11));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setPaint(new GradientPaint(0, 0, new Color(3, 5, 7, 205), getWidth(), 0, new Color(3, 5, 7, 70)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setPaint(new GradientPaint(0, getHeight(), new Color(4, 5, 6, 220), 0, getHeight() * .35f, new Color(4, 5, 6, 25)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(238, 38, 29, 45));
            g.fillOval(getWidth() - 410, -145, 590, 590);
            g.dispose();
        }
    }

    private static final class GlassPanel extends JPanel {
        private final Color fill;
        private final int arc;

        private GlassPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, arc, arc));
            g.setColor(new Color(255, 255, 255, 34));
            g.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, arc, arc));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class InsetsBorder extends javax.swing.border.EmptyBorder {
        private InsetsBorder(int top, int left, int bottom, int right) {
            super(top, left, bottom, right);
        }
    }

    private static final class LocalConfigServer implements AutoCloseable {
        private final String host;
        private final int gamePort;
        private final byte[] worldList;
        private final AtomicBoolean running = new AtomicBoolean();
        private ServerSocket serverSocket;
        private Thread thread;

        private LocalConfigServer(String host, int gamePort, byte[] worldList) {
            this.host = host;
            this.gamePort = gamePort;
            this.worldList = worldList;
        }

        private void start() throws IOException {
            serverSocket = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
            running.set(true);
            thread = new Thread(this::serve, "unforge-launcher-config");
            thread.setDaemon(true);
            thread.start();
        }

        private String configUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/jav_local_239.ws";
        }

        private void serve() {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread handler = new Thread(() -> handle(socket), "unforge-launcher-http");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException exception) {
                    if (running.get()) {
                        System.err.println("Local config server stopped: " + exception.getMessage());
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String request = reader.readLine();
                if (request == null || !request.startsWith("GET ")) {
                    writeResponse(socket, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String headerLine;
                while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                    // Consume request headers.
                }
                String path = request.substring(4, request.indexOf(' ', 5));
                if (path.equals("/jav_local_239.ws")) {
                    writeResponse(socket, 200, "application/octet-stream", config().getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/world_list.ws")) {
                    writeResponse(socket, 200, "application/octet-stream", worldList);
                } else {
                    writeResponse(socket, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception exception) {
                System.err.println("Local config request failed: " + exception.getMessage());
            }
        }

        private String config() {
            return "title=UnForge Modern 239\n"
                    + "codebase=http://" + hostForUrl(host) + "/\n"
                    + "cachedir=unforge239\n"
                    + "storebase=0\n"
                    + "initial_jar=" + GAMEPACK_NAME + "\n"
                    + "initial_class=client.class\n"
                    + "viewerversion=124\n"
                    + "param=3=true\n"
                    + "param=4=1\n"
                    + "param=7=0\n"
                    + "param=13=" + host + "\n"
                    + "param=9=\n"
                    + "param=18=\n"
                    + "param=25=239\n"
                    + "param=5=1\n"
                    + "param=6=0\n"
                    + "param=17=http://127.0.0.1:" + serverSocket.getLocalPort() + "/world_list.ws\n"
                    + "param=8=true\n"
                    + "param=14=0\n"
                    + "param=16=false\n"
                    + "param=15=0\n"
                    + "param=12=1\n"
                    + "param=10=5\n";
        }

        private static String hostForUrl(String value) {
            return value.contains(":") && !value.startsWith("[") ? "[" + value + "]" : value;
        }

        private static void writeResponse(Socket socket, int status, String contentType, byte[] body) throws IOException {
            OutputStream output = socket.getOutputStream();
            String header = "HTTP/1.1 " + status + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        }

        @Override
        public void close() throws IOException {
            running.set(false);
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}
