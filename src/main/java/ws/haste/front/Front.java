package ws.haste.front;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

/**
 * Main class
 */
public class Front {
    public static void main(final @NotNull String @NotNull [] args) {
        final @NotNull Config config = new Config(
                8080,
                new FileResource[] {
                    new FileResource("/", "text/html", new HashMap<>() {{
                        put(FileResource.Encoding.Identity, new File(System.getenv("HASTE_FILE")));
                    }}, null, null)
                },
                new HashMap<>(),
                new HashMap<>() {{
                    put("Server", "haste.ws");
                }}
        );
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
}
