package io.github.miklires.mevents.event;

import io.github.miklires.mevents.api.EventType;

import java.util.List;
import java.util.regex.Pattern;

public record EventDefinition(
        String id,
        String displayName,
        EventType type,
        String world,
        int x,
        int y,
        int z,
        long preparationTicks,
        long durationTicks,
        long cooldownTicks,
        String bossEntity,
        double bossHealth,
        double minimumDamage,
        int maximumWinners,
        long graceTicks,
        boolean automatic,
        long initialDelayTicks,
        long intervalTicks,
        long jitterTicks,
        List<RewardDefinition> rewards
) {
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final long MAX_TICKS = 20L * 60 * 60 * 24 * 30;

    public EventDefinition {
        if (id == null || !SAFE_ID.matcher(id).matches()) throw new IllegalArgumentException("unsafe event id");
        if (displayName == null || displayName.isBlank() || displayName.length() > 80) throw new IllegalArgumentException("invalid display name");
        if (type == null) throw new IllegalArgumentException("event type is missing");
        if (world == null || world.isBlank() || world.length() > 128) throw new IllegalArgumentException("invalid world");
        if (preparationTicks < 0 || preparationTicks > MAX_TICKS || durationTicks < 1 || durationTicks > MAX_TICKS) {
            throw new IllegalArgumentException("invalid event duration");
        }
        if (cooldownTicks < 0 || cooldownTicks > MAX_TICKS) throw new IllegalArgumentException("invalid cooldown");
        if (!Double.isFinite(bossHealth) || bossHealth <= 0 || bossHealth > 10_000_000) throw new IllegalArgumentException("invalid boss health");
        if (!Double.isFinite(minimumDamage) || minimumDamage < 0) throw new IllegalArgumentException("invalid minimum damage");
        if (maximumWinners < 0 || maximumWinners > 10_000) throw new IllegalArgumentException("invalid maximum winners");
        if (graceTicks < 0 || graceTicks > durationTicks) throw new IllegalArgumentException("invalid grace period");
        if (automatic && (initialDelayTicks < 1 || intervalTicks < durationTicks + preparationTicks || jitterTicks < 0 || jitterTicks >= intervalTicks)) {
            throw new IllegalArgumentException("invalid automatic schedule");
        }
        rewards = List.copyOf(rewards);
        if (rewards.isEmpty()) throw new IllegalArgumentException("event requires rewards");
        if (rewards.size() > 1000) throw new IllegalArgumentException("event has too many rewards");
        if (rewards.stream().map(RewardDefinition::id).distinct().count() != rewards.size()) {
            throw new IllegalArgumentException("duplicate reward id");
        }
    }

    // Compatibility constructor retained for API consumers and existing tests.
    public EventDefinition(String id, String displayName, EventType type, String world, int x, int y, int z,
                           long preparationTicks, long durationTicks, String bossEntity, double bossHealth,
                           List<RewardDefinition> rewards) {
        this(id, displayName, type, world, x, y, z, preparationTicks, durationTicks, 0, bossEntity, bossHealth,
                0, 0, 0, false, 1200, 72000, 0, rewards);
    }
}
