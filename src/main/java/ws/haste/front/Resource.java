package ws.haste.front;

import io.vertx.core.http.HttpServerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class Resource {
    public final @NotNull String url;
    public final @NotNull HashMap<@NotNull String, @Nullable String> headers;
    public Resource(final @NotNull String url, final @NotNull HashMap<@NotNull String, @Nullable String> headers) {
        this.url = url;
        this.headers = headers;
    }

    public void writeHead(@NotNull HttpServerResponse res) {
        for (final @NotNull Map.Entry<@NotNull String, @Nullable String> header : headers.entrySet()) {
            if (header.getValue() == null) res.headers().remove(header.getKey());
            else res.headers().set(header.getKey(), header.getValue());
        }
    }

    public void serve(@NotNull HttpServerResponse res) {
        this.writeHead(res);
        res.end();
    }
}
