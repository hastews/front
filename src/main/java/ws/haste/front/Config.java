package ws.haste.front;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public record Config(int port,
                     @NotNull FileResource @NotNull [] resources,
                     @NotNull HashMap<@NotNull Integer, @NotNull ErrorResource> errorResources,
                     @NotNull HashMap<@NotNull String, @NotNull String> headers) {
}
