package ws.haste.front;

import io.vertx.core.http.HttpServerResponse;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class Resource {
    public final @NotNull String url;
    public final @NotNull HashMap<@NotNull String, @NotNull String> headers;
    public Resource(final @NotNull String url, final @NotNull HashMap<@NotNull String, @NotNull String> headers) {
        this.url = url;
        this.headers = headers;
    }

    public void writeHead(@NotNull HttpServerResponse res) {
        res.headers().addAll(headers);
    }

    public void serve(@NotNull HttpServerResponse res) {
        this.writeHead(res);
        res.end();
    }
}
