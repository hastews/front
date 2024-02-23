package ws.haste.front;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;

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
}
