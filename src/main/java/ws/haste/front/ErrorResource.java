package ws.haste.front;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;

public final class ErrorResource extends FileResource {
    public ErrorResource(final @NotNull String contentType, final @NotNull HashMap<@NotNull Encoding, @NotNull File> files, final @Nullable String etag, final @Nullable HashMap<@NotNull String, @NotNull String> headers) {
        super("", contentType, files, etag, headers);
    }
}
