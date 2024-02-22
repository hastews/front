package ws.haste.front;

import io.vertx.core.Vertx;
import org.jetbrains.annotations.NotNull;
import sun.misc.Signal;

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
                        put(FileResource.Encoding.Identity, new File("index.html"));
                    }}, null, null)
                },
                new HashMap<>(),
                new HashMap<>() {{
                    put("Server", "haste.ws");
                }}
        );
        final @NotNull WebServer ws = new WebServer(config);

        Signal.handle(new Signal("INT"), signal -> {
            ws.stop().andThen(future -> {
                System.exit(0);
            });
        });
    }

    public final static Vertx vertx = Vertx.vertx();
}
