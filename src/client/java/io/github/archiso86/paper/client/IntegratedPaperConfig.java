package io.github.archiso86.paper.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class IntegratedPaperConfig {

    public static final int DEFAULT_SERVER_PORT = 25565;
    public static final int MIN_SERVER_PORT = 1;
    public static final int MAX_SERVER_PORT = 65535;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static IntegratedPaperConfig instance = new IntegratedPaperConfig();

    public int serverPort = DEFAULT_SERVER_PORT;

    public static IntegratedPaperConfig get() {
        return instance;
    }

    public static void load() {
        Path path = configPath();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                IntegratedPaperConfig loaded = GSON.fromJson(
                    reader,
                    IntegratedPaperConfig.class
                );
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException ignored) {
                instance = new IntegratedPaperConfig();
            }
        }

        instance.sanitize();
        save();
    }

    public static void save() {
        instance.sanitize();
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
                writer.write(System.lineSeparator());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save Integrated Paper config", exception);
        }
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
        sanitize();
    }

    private void sanitize() {
        if (serverPort < MIN_SERVER_PORT || serverPort > MAX_SERVER_PORT) {
            serverPort = DEFAULT_SERVER_PORT;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve("integrated-paper.json")
            .toAbsolutePath()
            .normalize();
    }
}
