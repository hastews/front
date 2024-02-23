package ws.haste.front;

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class WebServer {
    private final @NotNull Config config;
    private final @NotNull HttpServer server;
    public WebServer(final @NotNull Config config) {
        Front.getLogger().info("Starting...");
        this.config = config;
        this.server = Front.vertx.createHttpServer();

        registerDefaultErrorPages();
        server.requestHandler(this::requestListener);
        server.listen(config.port());
        Front.getLogger().info("Listening on port " + config.port());
    }
    public void stop() {
        final @NotNull CompletableFuture<@NotNull Void> closeWs = new CompletableFuture<>();
        server.close().onComplete((v) -> {
            closeWs.complete(v.result());
        });
        closeWs.join();

        final @NotNull CompletableFuture<@NotNull Void> closeVertx = new CompletableFuture<>();
        Front.vertx.close().onComplete((v) -> {
            closeVertx.complete(v.result());
        });
        closeVertx.join();
    }

    private boolean wildcardMatch(final @NotNull String pattern, final @NotNull String text) {
        int m = pattern.length();
        int n = text.length();
        boolean[][] dp = new boolean[m + 1][n + 1];

        dp[0][0] = true;

        for (int i = 1; i <= m; ++i)
            if (pattern.charAt(i - 1) == '*')
                dp[i][0] = dp[i - 1][0];

        for (int i = 1; i <= m; ++i)
            for (int j = 1; j <= n; ++j) {
                if (pattern.charAt(i - 1) == '*' || Character.toLowerCase(pattern.charAt(i - 1)) == Character.toLowerCase(text.charAt(j - 1)))
                    dp[i][j] = dp[i - 1][j - 1] || dp[i][j - 1] || dp[i - 1][j];
                else dp[i][j] = false;
            }

        return dp[m][n];
    }

    private @NotNull String removeTrailingSlashes(final @NotNull String url) {
        final @NotNull String s = url.strip();
        if (s.equals("/") || s.isEmpty()) return "/";
        int i = url.length() - 1;
        while (i >= 0 && url.charAt(i) == '/') --i;
        return url.substring(0, i + 1);
    }

    private @NotNull Optional<@NotNull FileResource> findResource(final @NotNull HttpServerRequest req) {
        return Arrays.stream(config.resources()).filter(r -> wildcardMatch(r.url, removeTrailingSlashes(req.path()))).findFirst();
    }

    private void sendError(final @NotNull HttpServerRequest req, final int status) {
        final @NotNull HttpServerResponse res = req.response();
        final @NotNull Optional<@NotNull ErrorResource> errorResource = Optional.ofNullable(config.errorResources().get(status));
        if (errorResource.isEmpty()) sendFailSafeServerError(req);
        else {
            res.setStatusCode(status);
            res.headers().set("Content-Type", errorResource.get().contentType);
            try {
                errorResource.get().serve(req);
            }
            catch (final @NotNull Throwable ignored) {
                sendFailSafeServerError(req);
            }
        }
    }
    
    private void sendFailSafeServerError(final @NotNull HttpServerRequest req) {
        final @NotNull HttpServerResponse res = req.response();
        try {
            final @NotNull ErrorResource e500 = Objects.requireNonNull(config.errorResources().get(500));
            e500.serve(req);
        }
        catch (final @NotNull WebServerException ignored) {
            res.setStatusCode(500);
            res.headers().set("Content-Type", "text/plain");
            res.end("500");
        }
    }
    private void sendError(final @NotNull HttpServerRequest req, final @NotNull WebServerException error) {
        Front.getLogger().error(error);
        sendError(req, error.status);
    }

    private void requestListener(final @NotNull HttpServerRequest req) {
        final @NotNull HttpServerResponse res = req.response();
        res.headers().setAll(config.headers());
        try {
            final @NotNull Optional<@NotNull FileResource> resource = findResource(req);
            if (resource.isEmpty()) sendError(req, 404);
            else resource.get().serve(req);
        }
        catch (final @NotNull WebServerException e) {
            sendError(req, e);
        }
    }

    public static @NotNull Optional<@NotNull Float> parseFloat(final @NotNull String s) {
        try {
            return Optional.of(Float.parseFloat(s));
        }
        catch (final @NotNull NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static @NotNull Optional<@NotNull Long> parseLong(final @NotNull String s) {
        try {
            return Optional.of(Long.parseLong(s));
        }
        catch (final @NotNull NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void registerDefaultErrorPages() {
        final int @NotNull [] statuses = {404, 500};
        for (final int status : statuses) {
            if (!config.errorResources().containsKey(status)) {
                final @NotNull File tempFile;
                try {
                    tempFile = File.createTempFile(String.valueOf(status), ".html");
                    tempFile.deleteOnExit();
                } catch (final @NotNull IOException e) {
                    Front.getLogger().error("Failed to create temporary file for default " + status + " error page", e);
                    return;
                }
                try (final @NotNull InputStream inputStream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("error/" + status + ".html"));
                     final @NotNull OutputStream outputStream = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }
                catch (final @NotNull IOException e) {
                    Front.getLogger().error("Failed to write to temporary file for default " + status + " error page", e);
                    return;
                }
                config.errorResources().put(status, new ErrorResource(
                        "text/html",
                        new HashMap<>() {{
                            put(FileResource.Encoding.Identity, tempFile);
                        }},
                        null,
                        null
                ));
            }
        }
    }
}
