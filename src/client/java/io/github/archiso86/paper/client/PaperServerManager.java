package io.github.archiso86.paper.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;

public final class PaperServerManager {

    private static final Component STARTING_TITLE = Component.literal(
        "Starting Paper server"
    );
    private static final Component ERROR_TITLE = Component.literal(
        "Integrated Paper failed"
    );
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final Object LOCK = new Object();

    private static Process process;
    private static int port;
    private static String levelId;
    private static boolean onlineMode;

    private PaperServerManager() {}

    public static void requireServerJar() {
        Path jar = serverJar();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(
                "Missing Paper server jar: " + jar.toAbsolutePath()
            );
        }
    }

    public static void openWorld(
        Minecraft minecraft,
        LevelStorageSource.LevelStorageAccess access
    ) {
        String nextLevelId = access.getLevelId();
        ProgressScreen progressScreen = new ProgressScreen(true);
        progressScreen.progressStartNoAbort(STARTING_TITLE);
        progressScreen.progressStage(Component.literal(nextLevelId));
        minecraft.setScreen(progressScreen);

        Thread starter = new Thread(
            () -> startAndConnect(minecraft, access, nextLevelId),
            "Integrated Paper Starter"
        );
        starter.setDaemon(true);
        starter.start();
    }

    public static void openWorld(
        Minecraft minecraft,
        LevelStorageSource levelSource,
        String levelId
    ) {
        try {
            openWorld(minecraft, levelSource.validateAndCreateAccess(levelId));
        } catch (Exception exception) {
            showError(minecraft, exception);
        }
    }

    public static void stopCurrentServer() {
        stopProcess(claimCurrentServer());
    }

    public static void stopCurrentServerAsync(
        Minecraft minecraft,
        Screen nextScreen
    ) {
        Process current = claimCurrentServer();
        if (current == null || !current.isAlive()) {
            if (minecraft != null && nextScreen != null) {
                minecraft.execute(() -> minecraft.setScreen(nextScreen));
            }
            return;
        }

        Thread stopper = new Thread(() -> {
            stopProcess(current);
            if (minecraft != null && nextScreen != null) {
                minecraft.execute(() -> minecraft.setScreen(nextScreen));
            }
        }, "Integrated Paper Stopper");
        stopper.setDaemon(true);
        stopper.start();
    }

    private static Process claimCurrentServer() {
        Process current;
        synchronized (LOCK) {
            current = process;
            process = null;
            levelId = null;
            port = 0;
            onlineMode = false;
        }

        return current;
    }

    private static void stopProcess(Process current) {
        if (current == null || !current.isAlive()) {
            return;
        }

        try (
            BufferedWriter writer = current.outputWriter(StandardCharsets.UTF_8)
        ) {
            writer.write("stop");
            writer.newLine();
            writer.flush();
            if (current.waitFor(Duration.ofSeconds(20))) {
                return;
            }
        } catch (IOException exception) {
            current.destroy();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.destroy();
        }

        if (current.isAlive()) {
            current.destroyForcibly();
        }
    }

    public static boolean hasCurrentServer() {
        synchronized (LOCK) {
            return process != null;
        }
    }

    private static void startAndConnect(
        Minecraft minecraft,
        LevelStorageSource.LevelStorageAccess access,
        String nextLevelId
    ) {
        try {
            Path worldPath = access
                .getLevelDirectory()
                .path()
                .toAbsolutePath()
                .normalize();
            User user = minecraft.getUser();
            access.safeClose();
            int nextPort = ensureRunning(worldPath, nextLevelId, user);
            waitForStatusResponse(nextPort);
            minecraft.execute(() -> connect(minecraft, nextPort));
        } catch (Exception exception) {
            stopCurrentServer();
            minecraft.execute(() -> showError(minecraft, exception));
        }
    }

    private static int ensureRunning(
        Path worldPath,
        String nextLevelId,
        User user
    ) throws IOException {
        boolean nextOnlineMode = isLoggedIn(user);
        synchronized (LOCK) {
            if (
                process != null &&
                process.isAlive() &&
                nextLevelId.equals(levelId) &&
                onlineMode == nextOnlineMode
            ) {
                return port;
            }
        }

        stopCurrentServer();

        Path serverDirectory = serverDirectory();
        Files.createDirectories(serverDirectory);
        Files.createDirectories(serverDirectory.resolve("plugins"));

        int nextPort = findOpenPort();
        writeEula(serverDirectory);
        Path universePath = worldPath.getParent();
        Path worldNamePath = worldPath.getFileName();
        if (universePath == null || worldNamePath == null) {
            throw new IOException("Invalid world path: " + worldPath);
        }

        String worldName = worldNamePath.toString();
        writeServerProperties(
            serverDirectory,
            worldName,
            nextPort,
            nextOnlineMode
        );
        writeOps(serverDirectory, user, nextOnlineMode);

        ProcessBuilder builder = new ProcessBuilder(
            javaBinary().toString(),
            "-jar",
            serverJar().toString(),
            "--nogui",
            "--universe",
            universePath.toString(),
            "--world",
            worldName
        );
        builder.directory(serverDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        Process nextProcess = builder.start();
        synchronized (LOCK) {
            process = nextProcess;
            port = nextPort;
            levelId = nextLevelId;
            onlineMode = nextOnlineMode;
        }
        return nextPort;
    }

    private static void writeEula(Path serverDirectory) throws IOException {
        Properties eula = new Properties();
        eula.setProperty("eula", "true");
        try (
            BufferedWriter writer = Files.newBufferedWriter(
                serverDirectory.resolve("eula.txt"),
                StandardCharsets.UTF_8
            )
        ) {
            eula.store(
                writer,
                "Accepted by Integrated Paper for local singleplayer replacement."
            );
        }
    }

    private static void writeServerProperties(
        Path serverDirectory,
        String worldName,
        int nextPort,
        boolean nextOnlineMode
    ) throws IOException {
        Properties properties = new Properties();
        Path propertiesPath = serverDirectory.resolve("server.properties");
        if (Files.isRegularFile(propertiesPath)) {
            try (
                var reader = Files.newBufferedReader(
                    propertiesPath,
                    StandardCharsets.UTF_8
                )
            ) {
                properties.load(reader);
            }
        }

        properties.setProperty("level-name", worldName);
        properties.setProperty("server-port", Integer.toString(nextPort));
        properties.setProperty(
            "online-mode",
            Boolean.toString(nextOnlineMode)
        );
        properties.setProperty("motd", "Integrated Paper");

        try (
            BufferedWriter writer = Files.newBufferedWriter(
                propertiesPath,
                StandardCharsets.UTF_8
            )
        ) {
            properties.store(writer, "Managed by Integrated Paper.");
        }
    }

    private static void writeOps(
        Path serverDirectory,
        User user,
        boolean nextOnlineMode
    )
        throws IOException {
        UUID playerId = playerId(user, nextOnlineMode);
        UUID profileId = user.getProfileId();
        String playerName = user.getName();
        Path opsPath = serverDirectory.resolve("ops.json");
        JsonArray ops = readOps(opsPath);
        JsonArray updatedOps = new JsonArray();
        String playerIdText = playerId.toString();
        String profileIdText = profileId.toString();
        for (JsonElement op : ops) {
            if (!op.isJsonObject()) {
                continue;
            }

            JsonObject opObject = op.getAsJsonObject();
            JsonElement uuid = opObject.get("uuid");
            JsonElement name = opObject.get("name");
            if (uuid != null && uuid.isJsonPrimitive()) {
                String opIdText = uuid.getAsString();
                if (
                    playerIdText.equalsIgnoreCase(opIdText) ||
                    profileIdText.equalsIgnoreCase(opIdText)
                ) {
                    continue;
                }
            }
            if (
                name != null &&
                name.isJsonPrimitive() &&
                playerName.equalsIgnoreCase(name.getAsString())
            ) {
                continue;
            }

            updatedOps.add(opObject);
        }

        JsonObject playerOp = new JsonObject();
        playerOp.addProperty("uuid", playerIdText);
        playerOp.addProperty("name", playerName);
        playerOp.addProperty("level", 4);
        playerOp.addProperty("bypassesPlayerLimit", true);
        updatedOps.add(playerOp);

        try (
            BufferedWriter writer = Files.newBufferedWriter(
                opsPath,
                StandardCharsets.UTF_8
            )
        ) {
            GSON.toJson(updatedOps, writer);
            writer.newLine();
        }
    }

    private static boolean isLoggedIn(User user) {
        String accessToken = user.getAccessToken();
        return (
            accessToken != null &&
            !accessToken.isBlank() &&
            user.getXuid().isPresent() &&
            !offlinePlayerId(user.getName()).equals(user.getProfileId())
        );
    }

    private static UUID playerId(User user, boolean nextOnlineMode) {
        return nextOnlineMode
            ? user.getProfileId()
            : offlinePlayerId(user.getName());
    }

    private static UUID offlinePlayerId(String playerName) {
        return UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static JsonArray readOps(Path opsPath) throws IOException {
        if (!Files.isRegularFile(opsPath)) {
            return new JsonArray();
        }

        try (
            var reader = Files.newBufferedReader(
                opsPath,
                StandardCharsets.UTF_8
            )
        ) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        } catch (RuntimeException exception) {
            return new JsonArray();
        }
    }

    private static void connect(Minecraft minecraft, int nextPort) {
        ServerData serverData = new ServerData(
            "Integrated Paper",
            "127.0.0.1:" + nextPort,
            ServerData.Type.LAN
        );
        ConnectScreen.startConnecting(
            singleplayerScreen(),
            minecraft,
            new ServerAddress("127.0.0.1", nextPort),
            serverData,
            false,
            null
        );
    }

    private static void showError(Minecraft minecraft, Exception exception) {
        minecraft.setScreen(
            new DisconnectedScreen(
                singleplayerScreen(),
                ERROR_TITLE,
                Component.literal(exception.getMessage())
            )
        );
    }

    public static Screen singleplayerScreen() {
        return new SelectWorldScreen(new TitleScreen());
    }

    private static void waitForStatusResponse(int nextPort)
        throws IOException, InterruptedException {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Process current;
            synchronized (LOCK) {
                current = process;
            }
            if (current == null || !current.isAlive()) {
                throw new IOException("Paper server exited during startup");
            }

            try {
                readStatusResponse(nextPort);
                return;
            } catch (IOException ignored) {
                Thread.sleep(500);
            }
        }
        throw new IOException(
            "Timed out waiting for Paper status on 127.0.0.1:" + nextPort
        );
    }

    private static void readStatusResponse(int nextPort) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", nextPort), 500);
            socket.setSoTimeout(3000);
            socket.setTcpNoDelay(true);

            try (
                DataOutputStream output = new DataOutputStream(
                    socket.getOutputStream()
                );
                DataInputStream input = new DataInputStream(
                    socket.getInputStream()
                )
            ) {
                writeHandshake(output, nextPort);
                output.writeByte(1);
                output.writeByte(0);
                output.flush();

                int packetLength = readVarInt(input);
                if (packetLength <= 0) {
                    throw new IOException("Invalid status packet size");
                }

                int packetId = readVarInt(input);
                if (packetId != 0) {
                    throw new IOException("Invalid status packet ID");
                }

                JsonObject response = JsonParser
                    .parseString(readString(input))
                    .getAsJsonObject();
                JsonObject version = response.getAsJsonObject("version");
                if (version == null || !version.has("protocol")) {
                    throw new IOException("Invalid status response");
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid status response", exception);
        }
    }

    private static void writeHandshake(DataOutputStream output, int nextPort)
        throws IOException {
        ByteArrayOutputStream packetBytes = new ByteArrayOutputStream();
        try (DataOutputStream packet = new DataOutputStream(packetBytes)) {
            packet.writeByte(0);
            writeVarInt(packet, 0);
            writeString(packet, "127.0.0.1");
            packet.writeShort(nextPort);
            writeVarInt(packet, 1);
        }

        writeVarInt(output, packetBytes.size());
        output.write(packetBytes.toByteArray());
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        int byteCount = 0;
        byte currentByte;
        do {
            currentByte = input.readByte();
            value |= (currentByte & 0x7F) << (byteCount++ * 7);
            if (byteCount > 5) {
                throw new IOException("VarInt too big");
            }
        } while ((currentByte & 0x80) == 0x80);

        return value;
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > 131068) {
            throw new IOException("Invalid string length: " + length);
        }

        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Unexpected end of status response");
        }

        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > 32767) {
            throw new IOException("String too long: " + value.length());
        }
        return value;
    }

    private static void writeString(DataOutputStream output, String value)
        throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeVarInt(DataOutputStream output, int value)
        throws IOException {
        while ((value & -128) != 0) {
            output.writeByte((value & 127) | 128);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    private static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private static Path javaBinary() {
        String executable = System.getProperty("os.name")
            .toLowerCase()
            .contains("win")
            ? "java.exe"
            : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable)
            .toAbsolutePath()
            .normalize();
    }

    private static Path serverJar() {
        return gameDirectory().resolve("server.jar");
    }

    private static Path serverDirectory() {
        return gameDirectory().resolve("server");
    }

    private static Path gameDirectory() {
        return FabricLoader.getInstance()
            .getGameDir()
            .toAbsolutePath()
            .normalize();
    }
}
