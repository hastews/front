package ws.haste.front;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record Config(int port, @NotNull FileResource @NotNull [] resources,
                     @NotNull HashMap<@NotNull Integer, @NotNull ErrorResource> errorResources,
                     @NotNull HashMap<@NotNull String, @NotNull String> headers) {
    public static @NotNull Config fromYaml(final @NotNull String configPath) throws ConfigException {
        final @NotNull HashSet<@NotNull String> availableEncodings = Arrays.stream(FileResource.Encoding.values()).map(FileResource.Encoding::toString).collect(Collectors.toCollection(HashSet::new));

        // snake yaml engine
        final @NotNull LoadSettings settings = LoadSettings.builder().build();
        final @NotNull Load load = new Load(settings);
        final @NotNull InputStream fis;
        if (configPath.startsWith("haste://")) {
            final @NotNull String realPath = configPath.substring("haste://".length());
            final @NotNull Optional<@NotNull InputStream> is = Optional.ofNullable(Config.class.getClassLoader().getResourceAsStream(realPath));
            if (is.isEmpty()) throw new ConfigException("Built-in resource `" + realPath + "` not found");
            fis = is.get();
        }
        else try {
            fis = new FileInputStream(configPath);
        }
        catch (final IOException e) {
            throw new ConfigException("File not found: " + configPath);
        }
        final @Nullable Object obj;
        try {
             obj = load.loadFromInputStream(fis);
        }
        catch (final @NotNull Exception e) {
            throw new ConfigException("Error " + e.getMessage());
        }

        if (!(obj instanceof Map)) throw new ConfigException("Invalid config file");
        @SuppressWarnings("unchecked") final @NotNull Map<@NotNull String, @Nullable Object> yaml = (Map<@NotNull String, @Nullable Object>) obj;

        // port
        if (!(yaml.get("port") instanceof final @NotNull Integer port))
            throw new ConfigException("port: must be an integer");
        if (port < 0 || port > 65535) throw new ConfigException("port: must be in range 0–65535, got " + port);

        // headers
        final @Nullable Object headersObj = yaml.get("headers");
        if (headersObj != null) {
            if (!(headersObj instanceof Map)) throw new ConfigException("headers: must be an object");
            if (((Map<?, ?>) headersObj).keySet().stream().anyMatch(k -> !(k instanceof String) && k != null))
                throw new ConfigException("headers: header names must be strings");
            if (((Map<?, ?>) headersObj).values().stream().anyMatch(v -> !(v instanceof String) && v != null))
                throw new ConfigException("headers: header values must be strings");
        }
        @SuppressWarnings("unchecked") final @NotNull Optional<@NotNull Map<@NotNull String, @NotNull String>> headers = Optional.ofNullable((Map<@NotNull String, @NotNull String>) headersObj);

        // resources
        final @NotNull List<@NotNull FileResource> resources = new ArrayList<>();
        final @Nullable Object resourcesObj = yaml.get("resources");
        if (!(resourcesObj instanceof List)) throw new ConfigException("resources: must be an array");
        for (final @NotNull Object resourceObj : (List<?>) resourcesObj) {
            if (!(resourceObj instanceof Map)) throw new ConfigException("resources.[n]: must be an object");
            if (((Map<?, ?>) resourceObj).keySet().stream().anyMatch(k -> !(k instanceof String) && k != null))
                throw new ConfigException("resources.[n]: keys must be strings");
            @SuppressWarnings("unchecked") final @NotNull Map<@NotNull String, @NotNull Object> resourceMap = (Map<@NotNull String, @NotNull Object>) resourceObj;

            // resources.[n].path
            if (!(resourceMap.get("path") instanceof final @NotNull String path))
                throw new ConfigException("resources.[n].path: must be a string");

            // resources.[n].content-type
            if (!(resourceMap.get("content-type") instanceof final @NotNull String contentType))
                throw new ConfigException("resources.[n].content-type: must be a string");

            // resources.[n].etag
            if (resourceMap.get("etag") != null && !(resourceMap.get("etag") instanceof String))
                throw new ConfigException("resources.[n].etag: must be either null or a string");
            final @NotNull Optional<@NotNull String> etag = Optional.ofNullable((String) resourceMap.get("etag"));
            if (etag.isPresent() && !etag.get().matches("^(W/)?\".+?\"$"))
                throw new ConfigException("resources.[n].etag: must be either null or in format `W/\"etag\"` or `\"etag\"`, got " + etag.get());

            // resources.[n].headers
            final @Nullable Object resourceHeadersObj = resourceMap.get("headers");
            if (resourceHeadersObj != null) {
                if (!(resourceHeadersObj instanceof Map))
                    throw new ConfigException("resources.[n].headers: must be an object");
                if (((Map<?, ?>) resourceHeadersObj).keySet().stream().anyMatch(k -> !(k instanceof String) && k != null))
                    throw new ConfigException("resources.[n].headers: header names must be strings");
                if (!((Map<?, ?>) resourceHeadersObj).values().stream().allMatch(v -> (v instanceof String) || v == null))
                    throw new ConfigException("resources.[n].headers: header values must be null or strings");
            }
            @SuppressWarnings("unchecked") final @NotNull Optional<@NotNull Map<@NotNull String, @Nullable String>> resourceHeaders = Optional.ofNullable((Map<@NotNull String, @NotNull String>) resourceHeadersObj);

            // resources.[n].files
            final @Nullable Object filesObj = resourceMap.get("files");
            if (!(filesObj instanceof Map)) throw new ConfigException("resources.[n].files: must be an object");
            if (((Map<?, ?>) filesObj).keySet().stream().anyMatch(e -> !(e instanceof String) || !availableEncodings.contains(e)))
                throw new ConfigException("resources.[n].files: encoding must be one of " + String.join(", ", availableEncodings));
            final @NotNull HashMap<FileResource.@NotNull Encoding, @NotNull String> files = new HashMap<>();
            for (final @NotNull Map.Entry<?, ?> entry : ((Map<?, ?>) filesObj).entrySet()) {
                final @NotNull String encodingString = (String) entry.getKey();
                final @NotNull FileResource.Encoding encoding = FileResource.Encoding.fromString(encodingString).orElseThrow(() -> new ConfigException("resources.[n].files: encoding must be one of " + String.join(", ", availableEncodings)));
                if (!(entry.getValue() instanceof final @NotNull String filePath))
                    throw new ConfigException("resources.[n].files." + encodingString + ": must be a string");
                final @NotNull File resourceFile = new File(filePath);
                if (!filePath.startsWith("haste://") && !resourceFile.exists())
                    throw new ConfigException("resources.[n].files." + encodingString + ": " + filePath + " does not exist");
                files.put(encoding, filePath);
            }

            resources.add(new FileResource(path, contentType, files, etag.orElse(null), resourceHeaders.map(HashMap::new).orElse(null)));
        }

        // error-pages
        final @NotNull HashMap<@NotNull Integer, @NotNull ErrorResource> errorResources = new HashMap<>();
        final @Nullable Object errorPagesObj = yaml.get("error-pages");
        if (!(errorPagesObj instanceof Map) && errorPagesObj != null) throw new ConfigException("error-pages: must be an object");
        else if (errorPagesObj != null) for (final @NotNull Map.Entry<?, ?> entry : ((Map<?, ?>) errorPagesObj).entrySet()) {
            final @NotNull Object keyObj = entry.getKey();
            if (!(keyObj instanceof Integer || keyObj instanceof String))
                throw new ConfigException("error-pages: error status must be number");
            final int key;
            if (keyObj instanceof @NotNull String keyString) {
                if (!keyString.matches("^\\d+$"))
                    throw new ConfigException("error-pages: error status must be number, got " + keyString);
                key = Integer.parseInt(keyString);
            }
            else key = (int) keyObj;
            if (key < 400 || key >= 600)
                throw new ConfigException("error-pages: error status must be in range 400–599, got " + key);

            final @Nullable Object errorResourceObj = entry.getValue();
            if (!(errorResourceObj instanceof final @NotNull Map<?, ?> errMap))
                throw new ConfigException("error-pages." + key + ": must be an object");
            if (errMap.keySet().stream().anyMatch(k -> !(k instanceof String)))
                throw new ConfigException("error-pages." + key + ": object keys must be strings");

            // error-pages.[n].content-type
            if (!(errMap.get("content-type") instanceof final @NotNull String contentType))
                throw new ConfigException("error-pages." + key + ".content-type: must be a string");

            // error-pages.[n].etag
            if (errMap.get("etag") != null && !(errMap.get("etag") instanceof String))
                throw new ConfigException("error-pages" + key + "].etag: must be either null or a string");
            final @NotNull Optional<@NotNull String> etag = Optional.ofNullable((String) errMap.get("etag"));
            if (etag.isPresent() && !etag.get().matches("^(W/)?\".+?\"$"))
                throw new ConfigException("error-pages." + key + ".etag: must be either null or in format `W/\"etag\"` or `\"etag\"`, got " + etag.get());

            // resources.[n].headers
            final @Nullable Object errHeadersObj = errMap.get("headers");
            if (errHeadersObj != null) {
                if (!(errHeadersObj instanceof Map))
                    throw new ConfigException("resources.[n].headers: must be an object");
                if (((Map<?, ?>) errHeadersObj).keySet().stream().anyMatch(k -> !(k instanceof String) && k != null))
                    throw new ConfigException("resources.[n].headers: header names must be strings");
                if (((Map<?, ?>) errHeadersObj).values().stream().anyMatch(v -> !(v instanceof String)))
                    throw new ConfigException("resources.[n].headers: header values must be null or strings");
            }
            @SuppressWarnings("unchecked") final @NotNull Optional<@NotNull Map<@NotNull String, @NotNull String>> resourceHeaders = Optional.ofNullable((Map<@NotNull String, @NotNull String>) errHeadersObj);

            // error-pages.[n].files
            final @Nullable Object filesObj = errMap.get("files");
            if (!(filesObj instanceof Map)) throw new ConfigException("error-pages." + key + ".files: must be an object");
            if (((Map<?, ?>) filesObj).keySet().stream().anyMatch(e -> !(e instanceof String) || !availableEncodings.contains(e)))
                throw new ConfigException("error-pages." + key + ".files: encoding must be one of " + String.join(", ", availableEncodings));
            final @NotNull HashMap<FileResource.@NotNull Encoding, @NotNull String> files = new HashMap<>();
            for (final @NotNull Map.Entry<?, ?> fileEntry : ((Map<?, ?>) filesObj).entrySet()) {
                final @NotNull String encodingString = (String) fileEntry.getKey();
                final @NotNull FileResource.Encoding encoding = FileResource.Encoding.fromString(encodingString).orElseThrow(() -> new ConfigException("error-pages." + key + ".files: encoding must be one of " + String.join(", ", availableEncodings)));
                if (!(fileEntry.getValue() instanceof final @NotNull String filePath))
                    throw new ConfigException("error-pages." + key + ".files." + encodingString + ": must be a string");
                final @NotNull File resourceFile = new File(filePath);
                if (!filePath.startsWith("haste://") && !resourceFile.exists())
                    throw new ConfigException("error-pages." + key + ".files." + encodingString + ": " + filePath + " does not exist");
                files.put(encoding, filePath);
            }

            errorResources.put(key, new ErrorResource(contentType, files, etag.orElse(null), resourceHeaders.map(HashMap::new).orElse(null)));
        }
        return new Config(port, resources.toArray(new FileResource[0]), errorResources, headers.map(HashMap::new).orElse(new HashMap<>()));
    }

    public static class ConfigException extends RuntimeException {
        public ConfigException(final @NotNull String message) {
            super(message);
        }
    }
}
