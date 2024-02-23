package ws.haste.front;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Main class
 */
public class Front {
    public static void main(final @NotNull String @NotNull [] args) {
        start();
    }

    public static void start() {
        final @NotNull Config config;
        try {
            config = findConfig();
        }
        catch (final @NotNull Config.ConfigException e) {
            logger.fatal("Configuration Error: " + e.getMessage());
            System.exit(1);
            return;
        }
        final @NotNull List<@NotNull Integer> portsRequireRoot = Arrays.asList(80, 443);
        if (portsRequireRoot.contains(config.port()) && Optional.ofNullable(System.getProperty("user.name")).map(u -> !u.equals("root")).orElse(false))
            getLogger().warn("Requested port is " + config.port() + ". This port may require administrative privileges.");
        final @NotNull WebServer ws = new WebServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            getLogger().info("Stopping...");
            ws.stop();
        }));
    }

    public static @NotNull Logger getLogger() {
        return logger;
    }

    public final static @NotNull Vertx vertx = Vertx.vertx();
    private static final @NotNull Logger logger = LogManager.getLogger(Front.class);

    private static @NotNull Config findConfig() throws Config.ConfigException {
        final @NotNull Optional<@NotNull String> env = Optional.ofNullable(System.getenv("HASTE_CONFIG"));
        if (env.isPresent()) return Config.fromYaml(env.get());

        final @NotNull HashSet<@NotNull String> locations = new HashSet<>(Arrays.asList(
                "haste.yaml",
                "haste.yml",
                "/etc/haste/config.yaml",
                "/etc/haste/config.yml"
        ));
        for (final @NotNull String location : locations)
            if (new File(location).exists()) return Config.fromYaml(location);
        getLogger().warn("Did not find configuration file, using default config");
        return Config.fromYaml("haste://config.yaml");
    }

    public static @NotNull Optional<@NotNull InputStream> getInternalFile(final @NotNull String filePath) {
        if (!filePath.startsWith("haste://")) return Optional.empty();
        final @NotNull String realPath = filePath.substring("haste://".length());
        try {
            return Optional.ofNullable(Front.class.getClassLoader().getResourceAsStream(realPath));
        }
        catch (final @NotNull Exception e) {
            return Optional.empty();
        }
    }
}
