package ws.haste.front;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Ranges {
    public final @NotNull String unit;
    public final @NotNull Range @NotNull [] ranges;

    public Ranges(final @NotNull String unit, final @NotNull Range @NotNull [] ranges) {
        this.unit = unit;
        this.ranges = ranges;
    }

    public @NotNull Ranges optimiseRanges(final long size) {
        final @NotNull List<@NotNull AbsoluteRange> ranges = Arrays.stream(this.ranges).map(r -> r.toAbsoluteRange(size)).collect(Collectors.toList());
        if (ranges.size() <= 1) return new Ranges(this.unit, this.ranges);

        // Sort the ranges based on the start value
        ranges.sort(Comparator.comparingLong(a -> a.start));

        @NotNull List<@NotNull AbsoluteRange> optimizedRanges = new ArrayList<>();
        @NotNull AbsoluteRange currentRange = ranges.get(0);

        for (int i = 1; i < ranges.size(); ++i) {
            @NotNull AbsoluteRange nextRange = ranges.get(i);

            // Check if the next range overlaps or is adjacent to the current range
            if (nextRange.start <= currentRange.end) {
                // Merge the ranges
                currentRange.end = Math.max(currentRange.end, nextRange.end);
            }
            else {
                // Add the current range to the optimized ranges
                optimizedRanges.add(currentRange);
                // Move to the next range
                currentRange = nextRange;
            }
        }

        // Add the last range
        optimizedRanges.add(currentRange);

        return new Ranges(this.unit, optimizedRanges.toArray(new Range[0]));
    }

    public static @NotNull Ranges fromString(final @NotNull String rangesHeader) {
        final @NotNull String @NotNull [] parts = rangesHeader.strip().split("=", 2);
        if (parts.length != 2) return new Ranges("", new Range[0]);
        return new Ranges(parts[0].strip(), Arrays.stream(parts[1].strip().split(",")).map(Range::fromString).toArray(Range[]::new));
    }

    public static class Range {
        private final @Nullable Long start;
        private final @Nullable Long end;

        public Range(final @Nullable Long start, final @Nullable Long end) {
            this.start = start;
            this.end = end;
        }

        public @NotNull AbsoluteRange toAbsoluteRange(final long size) {
            return new AbsoluteRange(this.start, this.end, size);
        }

        public static @NotNull Range fromString(final @NotNull String rangeHeader) {
            final @NotNull String @NotNull [] parts = rangeHeader.strip().split("-", 2);
            if (parts.length != 2) return new Range(null, null);
            final @NotNull Optional<@NotNull Long> start = WebServer.parseLong(parts[0].strip());
            final @NotNull Optional<@NotNull Long> end = WebServer.parseLong(parts[1].strip());
            return new Range(start.orElse(null), end.orElse(null));
        }
    }

    public static final class AbsoluteRange extends Range {
        long start;
        long end;

        public AbsoluteRange(final @Nullable Long start, final @Nullable Long end, final long size) {
            super(start, end);
            final long s = super.start == null ? 0 : super.start;
            final long e = super.end == null ? size - 1 : super.end;
            this.start = Math.min(s, e);
            this.end = Math.max(s, e);
        }
    }
}
