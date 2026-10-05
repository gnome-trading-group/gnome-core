package group.gnometrading.concurrent;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Gives each {@link ThreadProfile#HOT_PATH} agent its own isolated core, first come first served, and puts
 * everything else on the housekeeping cores. Hot agents that arrive after the isolated cores run out also
 * land on the housekeeping cores, without a dedicated core.
 */
public final class IsolatedCoreAllocator implements CoreAllocator {

    private final int[] isolatedCores;
    private final int[] housekeepingCores;
    private final AtomicInteger nextIsolated = new AtomicInteger();

    public IsolatedCoreAllocator(final int[] isolatedCores, final int[] housekeepingCores) {
        if (housekeepingCores.length == 0) {
            throw new IllegalArgumentException("At least one housekeeping core is required");
        }
        this.isolatedCores = isolatedCores.clone();
        this.housekeepingCores = housekeepingCores.clone();
    }

    @Override
    public Placement allocate(final GnomeAgent agent) {
        if (agent.threadProfile() == ThreadProfile.HOT_PATH) {
            final int index = nextIsolated.getAndIncrement();
            if (index < isolatedCores.length) {
                return new Placement(new int[] {isolatedCores[index]}, true);
            }
        }
        return new Placement(housekeepingCores, false);
    }

    /**
     * Parses a Linux CPU list such as {@code "2-15"} or {@code "0-1,8-9"}, the format used by
     * {@code isolcpus} and {@code /sys/devices/system/cpu/isolated}.
     *
     * @param spec comma-separated CPU ids and inclusive ranges
     * @return the CPU ids in ascending order without duplicates; empty for a blank spec
     */
    public static int[] parseCpuList(final String spec) {
        if (spec == null || spec.isBlank()) {
            return new int[0];
        }
        return Arrays.stream(spec.trim().split(","))
                .map(String::trim)
                .flatMapToInt(part -> {
                    final int dash = part.indexOf('-');
                    if (dash < 0) {
                        return IntStream.of(Integer.parseInt(part));
                    }
                    final int from = Integer.parseInt(part.substring(0, dash).trim());
                    final int to = Integer.parseInt(part.substring(dash + 1).trim());
                    if (to < from) {
                        throw new IllegalArgumentException("Invalid CPU range: " + part);
                    }
                    return IntStream.rangeClosed(from, to);
                })
                .distinct()
                .sorted()
                .toArray();
    }
}
