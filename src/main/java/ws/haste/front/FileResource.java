package ws.haste.front;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class FileResource extends Resource {
    public final @NotNull String contentType;
    public final @NotNull HashMap<@NotNull Encoding, @NotNull String> files;
    public final @Nullable String etag;

    public FileResource(final @NotNull String url, final @NotNull String contentType, final @NotNull HashMap<@NotNull Encoding, @NotNull String> files, final @Nullable String etag, final @Nullable HashMap<@NotNull String, @Nullable String> headers) {
        super(url, headers == null ? new HashMap<>() : headers);
        this.contentType = contentType;
        this.files = files;
        this.etag = etag;
    }

    private @NotNull Encoding @NotNull [] fileEncodings() {
        return this.files.keySet().toArray(new Encoding[0]);
    }

    private @NotNull Encoding pickEncoding(@NotNull HttpServerRequest req) throws WebServerException {
        final @NotNull WeightedEncoding @NotNull [] reqEncodings = WeightedEncoding.fromRequest(req);
        final @NotNull Encoding @NotNull [] fileEncodings = this.fileEncodings();
        final @NotNull Optional<@NotNull WeightedEncoding> pick = Arrays.stream(reqEncodings).filter(e -> Arrays.stream(fileEncodings).anyMatch(f -> f == e.encoding)).findFirst();
        if (pick.isPresent() && pick.get().encoding == Encoding.Identity && !WeightedEncoding.identityAllowed(reqEncodings)) {
            throw new WebServerException(415, new HashMap<>() {{
                put("Accept-Encoding", Arrays.stream(fileEncodings).map(Encoding::toString).collect(Collectors.joining(", ")));
            }});
        }
        return pick.map(weightedEncoding -> weightedEncoding.encoding).orElse(Encoding.Identity);
    }

    public void serve(final @NotNull HttpServerRequest req) throws WebServerException {
        final @NotNull HttpMethod method = req.method();
        if (method != HttpMethod.HEAD && method != HttpMethod.GET) throw new WebServerException(405);
        final @NotNull Encoding encoding = pickEncoding(req);
        final @NotNull String file = this.files.get(encoding);
        final @NotNull HttpServerResponse res = req.response();
        super.writeHead(res);
        if (etag != null) {
            res.headers().set("ETag", etag);
            final @NotNull Optional<@NotNull String> ifNoneMatch = Optional.ofNullable(req.getHeader("If-None-Match"));
            if (ifNoneMatch.map(etag::equals).orElse(false)) {
                res.setStatusCode(304);
                res.headers().set("Content-Length", contentType);
                res.end();
                return;
            }
        }
        final @NotNull Optional<@NotNull String> rangesHeader = Optional.ofNullable(req.getHeader("Range"));
        final @NotNull Optional<@NotNull Ranges> r = rangesHeader.map(Ranges::fromString);
        final int currentStatus = res.getStatusCode();
        if (file.startsWith("haste://") || currentStatus < 200 || currentStatus >= 300 || r.isEmpty() || r.get().ranges.length == 0 || r.get().unit.equalsIgnoreCase("bytes")) {
            serveFile(file, encoding, res);
            return;
        }
        if (false) {
            // TODO: memcached pages
        }
        else {
            final long size = file.length();
            final @NotNull Ranges.AbsoluteRange @NotNull [] ranges = (Ranges.AbsoluteRange[]) r.get().optimiseRanges(size).ranges;
            if (ranges.length == 0) {
                serveFile(file, encoding, res);
                return;
            }
            else res.setChunked(true);
            if (ranges.length == 1) {
                final @NotNull Ranges.AbsoluteRange range = ranges[0];
                final @NotNull RandomAccessFile raf;
                try (final @NotNull RandomAccessFile tmp = new RandomAccessFile(file, "r")) {
                    raf = tmp;
                }
                catch (final @NotNull IOException e) {
                    throw new WebServerException(404, e);
                }
                try {
                    res.setStatusCode(204);
                    res.headers().set("Content-Type", contentType);
                    res.headers().set("Content-Range", "bytes " + range.start + "-" + range.end + "/" + size);

                    raf.seek(range.start);
                    long remainingBytes = range.end - range.start + 1;
                    while (remainingBytes > 0) {
                        int bytesToRead = (int) Math.min(chunkSize, remainingBytes);
                        final byte @NotNull [] buffer = new byte[bytesToRead];
                        final int read = raf.read(buffer);
                        if (read == -1) break;
                        res.write(Buffer.buffer(buffer));
                        remainingBytes -= read;
                    }
                }
                catch (final @NotNull IOException e) {
                    throw new WebServerException(500, e);
                }
                finally {
                    try {
                        raf.close();
                    }
                    catch (final @NotNull IOException ignored) {}
                }
                res.end();
            }
            else {
                final @NotNull RandomAccessFile raf;
                try (final @NotNull RandomAccessFile tmp = new RandomAccessFile(file, "r")) {
                    raf = tmp;
                }
                catch (final @NotNull IOException e) {
                    throw new WebServerException(404, e);
                }
                final @NotNull String boundary = generateBoundary();
                res.setStatusCode(206);
                res.headers().set("Content-Type", "multipart/byteranges; boundary=" + boundary);
                if (encoding != Encoding.Identity) res.headers().set("Content-Encoding", encoding.toString());

                for (final @NotNull Ranges.AbsoluteRange range : ranges) try {
                    res.write("--" + boundary + CRLF + "Content-Type: " + this.contentType + CRLF + "Content-Range: bytes " + range.start + "-" + range.end + "/" + size + CRLF + CRLF);
                    raf.seek(range.start);
                    long remainingBytes = range.end - range.start + 1;
                    while (remainingBytes > 0) {
                        int bytesToRead = (int) Math.min(chunkSize, remainingBytes);
                        final byte @NotNull [] buffer = new byte[bytesToRead];
                        final int read = raf.read(buffer);
                        if (read == -1) break;
                        res.write(Buffer.buffer(buffer));
                        remainingBytes -= read;
                    }
                    res.write(CRLF + CRLF);
                }
                catch (final @NotNull IOException e) {
                    throw new WebServerException(500, e);
                }
                res.write("--" + boundary + "--");
                res.write(CRLF);
                res.end();
            }
        }
    }
    private static final int chunkSize = 4096;
    private static final @NotNull String CRLF = "\r\n";

    private void serveFile(final @NotNull String filePath, final @NotNull Encoding encoding, final @NotNull HttpServerResponse res) throws WebServerException {
        final @NotNull File file = new File(filePath);
        res.headers().set("Content-Type", this.contentType);
        if (encoding != Encoding.Identity) res.headers().set("Content-Encoding", encoding.toString());
        if (false) {
            // TODO: memcached
        }
        else {
            if (filePath.startsWith("haste://")) {
                final @NotNull Optional<@NotNull InputStream> inputStream = Front.getInternalFile(filePath);
                if (inputStream.isEmpty()) throw new WebServerException(404);
                res.setChunked(true);
                // send in 4KB chunks
                try {
                    long remainingBytes = inputStream.get().available();
                    while (remainingBytes > 0) {
                        int bytesToRead = (int) Math.min(chunkSize, remainingBytes);
                        final byte @NotNull [] buffer = new byte[bytesToRead];
                        final int read = inputStream.get().read(buffer);
                        if (read == -1) break;
                        res.write(Buffer.buffer(buffer));
                        remainingBytes -= read;
                    }
                }
                catch (final @NotNull IOException e) {
                    throw new WebServerException(500, e);
                }
                finally {
                    res.end();
                }
            }
            // send file in 4KB chunks
            else try (final @NotNull RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                res.setChunked(true);
                long remainingBytes = file.length();
                while (remainingBytes > 0) {
                    int bytesToRead = (int) Math.min(chunkSize, remainingBytes);
                    final byte @NotNull [] buffer = new byte[bytesToRead];
                    final int read = raf.read(buffer);
                    if (read == -1) break;
                    res.write(Buffer.buffer(buffer));
                    remainingBytes -= read;
                }
            }
            catch (final @NotNull IOException e) {
                throw new WebServerException(500, e);
            }
            finally {
                res.end();
            }
        }
    }


    private static final char @NotNull [] boundaryCharacters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int boundaryLength = 8;
    private static final char @NotNull [] boundary = new char[boundaryLength];
    private static @NotNull String generateBoundary() {
        final @NotNull Random r = new Random();
        for (int i = 0; i < boundaryLength; ++i) boundary[i] = boundaryCharacters[r.nextInt(boundaryCharacters.length)];
        return new String(boundary);
    }

    public enum Encoding {
        Identity("identity"), Gzip("gzip"), Deflate("deflate"), Brotli("br"), CatchAll("*");

        private final @NotNull String name;

        Encoding(final @NotNull String name) {
            this.name = name;
        }

        public static @NotNull Optional<@NotNull Encoding> fromString(final @NotNull String s) {
            for (final @NotNull Encoding e : Encoding.values())
                if (e.name.equals(s)) return Optional.of(e);
            return Optional.empty();
        }

        @Override
        public @NotNull String toString() {
            return this.name;
        }
    }

    public record WeightedEncoding(@NotNull Encoding encoding, float weight) {

        public static @NotNull Optional<@NotNull WeightedEncoding> fromString(final @NotNull String s) {
            final @NotNull String @NotNull [] parts = Arrays.stream(s.split(";", 2)).map(String::strip).toArray(String[]::new);
            if (parts.length == 0) return Optional.empty();
            final @NotNull String encoding = parts[0];
            final @NotNull Optional<@NotNull Encoding> e = Encoding.fromString(encoding);
            if (e.isEmpty()) return Optional.empty();
            final float weight;
            if (parts.length == 1) weight = 0f;
            else {
                final @NotNull String @NotNull [] weightParts = parts[1].split("=", 2);
                if (weightParts.length != 2) return Optional.empty();
                weight = WebServer.parseFloat(weightParts[1]).orElse(-1f);
                if (weight < 0f || weight > 1f) return Optional.empty();
            }
            return Optional.of(new WeightedEncoding(e.get(), weight));
        }

        public static @NotNull WeightedEncoding @NotNull [] fromRequest(final @NotNull HttpServerRequest req) {
            final @NotNull Optional<@NotNull String> header = Optional.ofNullable(req.getHeader("Accept-Encoding"));
            return header.map(s -> Arrays.stream(s.split(",")).map(WeightedEncoding::fromString).filter(Optional::isPresent).map(Optional::get).toArray(WeightedEncoding[]::new)).orElseGet(() -> new WeightedEncoding[]{});
        }

        public static boolean identityAllowed(final @NotNull WeightedEncoding @NotNull [] encodings) {
            final @NotNull Optional<@NotNull WeightedEncoding> identity = Arrays.stream(encodings).filter(e -> e.encoding == Encoding.Identity).findFirst();
            final @NotNull Optional<@NotNull WeightedEncoding> catchAll = Arrays.stream(encodings).filter(e -> e.encoding == Encoding.CatchAll).findFirst();
            return !((identity.isEmpty() && catchAll.isPresent() && catchAll.get().weight == 0f) || (identity.isPresent() && identity.get().weight == 0f));
        }
    }
}
