package ws.haste.front;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class WebServerException extends Exception {
    public final int status;
    public final @NotNull HashMap<@NotNull String, @NotNull String> headers;
    public final @Nullable Throwable cause;
    public WebServerException(final int status, final @NotNull HashMap<@NotNull String, @NotNull String> headers, final @Nullable Throwable cause) {
        super("HTTP status: " + status);
        this.status = status;
        this.headers = headers;
        this.cause = cause;
    }

    public WebServerException(final int status) {
        this(status, new HashMap<>(), null);
    }
    public WebServerException(final int status, final @Nullable Throwable cause) {
        this(status, new HashMap<>(), cause);
    }
    public WebServerException(final int status, final @NotNull HashMap<@NotNull String, @NotNull String> headers) {
        this(status, headers, null);
    }
}
