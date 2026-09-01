package io.github.miklires.mevents.event;

import io.github.miklires.mevents.MEventsPlugin;
import io.github.miklires.mevents.api.EventRunView;
import io.github.miklires.mevents.api.EventState;
import io.github.miklires.mevents.api.EventType;
import io.github.miklires.mevents.api.MEventsApi;
import io.github.miklires.mevents.storage.EventRepository;
import io.github.miklires.mevents.util.PluginScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Barrel;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;

public final class EventService implements MEventsApi {
    private final MEventsPlugin plugin;
    private final EventCatalog catalog;
    private final EventRepository repository;
    private final PluginScheduler scheduler;
    private final CompletableFuture<Void> ready;
    private final RewardSelector rewards = new RewardSelector(RandomGenerator.getDefault());
    private final ConcurrentMap<UUID, RuntimeEvent> running = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> bosses = new ConcurrentHashMap<>();
    private final ConcurrentMap<BlockKey, UUID> drops = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> starting = new ConcurrentHashMap<>();

    public EventService(MEventsPlugin plugin, EventCatalog catalog, EventRepository repository,
                        CompletableFuture<Void> ready) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.scheduler = new PluginScheduler(plugin);
        this.ready = ready;
    }

    @Override
    public CompletableFuture<EventRunView> start(String templateId) {
        synchronized (starting) {
            if (starting.containsKey(templateId)) return CompletableFuture.failedFuture(new IllegalStateException("Event is already starting"));
            int maximum = Math.max(1, plugin.getConfig().getInt("limits.max-concurrent-events", 3));
            if (running.size() + starting.size() >= maximum) {
                return CompletableFuture.failedFuture(new IllegalStateException("Concurrent event limit reached"));
            }
            starting.put(templateId, Boolean.TRUE);
        }
        return ready.thenCompose(ignored -> startReady(templateId))
                .whenComplete((run, error) -> starting.remove(templateId));
    }

    private CompletableFuture<EventRunView> startReady(String templateId) {
        EventDefinition definition = catalog.find(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown event: " + templateId));
        return active(templateId).thenCompose(existing -> {
            if (existing.isPresent()) return CompletableFuture.failedFuture(new IllegalStateException("Event is already running"));
            return repository.latest(templateId).thenCompose(latest -> {
            if (latest.isPresent() && definition.cooldownTicks() > 0) {
                EventRunView previous = latest.get();
                long eventWindowMillis = (definition.preparationTicks() + definition.durationTicks()) * 50;
                long cooldownBase = previous.endsAt() == null
                        ? previous.createdAt().toEpochMilli() + eventWindowMillis
                        : previous.endsAt().toEpochMilli();
                long unlockAt = cooldownBase + definition.cooldownTicks() * 50;
                long remaining = unlockAt - System.currentTimeMillis();
                if (remaining > 0) return CompletableFuture.failedFuture(new IllegalStateException(
                        "Event cooldown: " + Math.max(1, (remaining + 999) / 1000) + " seconds"));
            }
            UUID id = UUID.randomUUID();
            return repository.create(id, definition.id(), definition.type(), definition.world(), definition.x(), definition.y(), definition.z())
                    .thenCompose(view -> repository.transition(id, EventState.SCHEDULED, EventState.PREPARING,
                                    null, null, "countdown")
                            .thenApply(changed -> {
                                if (!changed) throw new IllegalStateException("Could not prepare event");
                                running.put(id, new RuntimeEvent(id, definition));
                                scheduler.later(definition.preparationTicks(), () -> activate(id));
                                scheduler.later(definition.preparationTicks() + definition.durationTicks(),
                                        () -> cancel(id, "timeout"));
                                broadcast("event.countdown", Map.of(
                                        "name", definition.displayName(),
                                        "seconds", String.valueOf(Math.max(1, definition.preparationTicks() / 20))));
                                return view;
                            }));
            });
        });
    }

    private void activate(UUID id) {
        RuntimeEvent runtime = running.get(id);
        if (runtime == null) return;
        Instant now = Instant.now();
        Instant end = now.plusMillis(runtime.definition.durationTicks() * 50);
        repository.transition(id, EventState.PREPARING, EventState.ACTIVE, now, end, "active")
                .thenAccept(changed -> {
                    if (!changed) return;
                    scheduler.global(() -> {
                        World world = Bukkit.getWorld(runtime.definition.world());
                        if (world == null) {
                            cancel(id, "world unavailable");
                            return;
                        }
                        Location location = new Location(world, runtime.definition.x(), runtime.definition.y(), runtime.definition.z());
                        runtime.location = location;
                        runtime.claimableAtMillis = System.currentTimeMillis() + runtime.definition.graceTicks() * 50;
                        if (runtime.definition.type() == EventType.AIRDROP) spawnDrop(runtime, location);
                        else spawnBoss(runtime, location);
                    });
                }).exceptionally(error -> { logFailure("Could not activate " + id, error); return null; });
    }

    private void spawnDrop(RuntimeEvent runtime, Location location) {
        scheduler.region(location, () -> {
            if (!location.getBlock().getType().isAir()) {
                cancel(runtime.id, "airdrop location occupied");
                plugin.getLogger().warning("Airdrop " + runtime.definition.id() + " did not replace occupied block at "
                        + location.getBlockX() + ',' + location.getBlockY() + ',' + location.getBlockZ());
                return;
            }
            location.getBlock().setType(Material.BARREL, false);
            if (location.getBlock().getState() instanceof Barrel barrel) {
                barrel.customName(net.kyori.adventure.text.Component.text(runtime.definition.displayName()));
                barrel.update(true);
            }
            drops.put(BlockKey.of(location), runtime.id);
            announceStarted(runtime, location);
        });
    }

    private void spawnBoss(RuntimeEvent runtime, Location location) {
        scheduler.region(location, () -> {
            EntityType type;
            try { type = EntityType.valueOf(runtime.definition.bossEntity().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { cancel(runtime.id, "invalid boss entity"); return; }
            Entity raw = location.getWorld().spawnEntity(location, type);
            if (!(raw instanceof LivingEntity boss)) {
                raw.remove();
                cancel(runtime.id, "configured entity is not living");
                return;
            }
            boss.customName(net.kyori.adventure.text.Component.text(runtime.definition.displayName()));
            boss.setCustomNameVisible(true);
            var maximum = boss.getAttribute(Attribute.MAX_HEALTH);
            if (maximum != null) maximum.setBaseValue(runtime.definition.bossHealth());
            boss.setHealth(Math.min(runtime.definition.bossHealth(), maximum == null ? boss.getHealth() : maximum.getValue()));
            boss.setPersistent(false);
            runtime.boss = boss;
            bosses.put(boss.getUniqueId(), runtime.id);
            announceStarted(runtime, location);
        });
    }

    private void announceStarted(RuntimeEvent runtime, Location location) {
        broadcast("event.started", Map.of("name", runtime.definition.displayName(),
                "x", String.valueOf(location.getBlockX()), "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ())));
    }

    public boolean claimDrop(Player player, Location location) {
        UUID id = drops.get(BlockKey.of(location));
        if (id == null) return false;
        RuntimeEvent runtime = running.get(id);
        if (runtime == null) return true;
        long remaining = runtime.claimableAtMillis - System.currentTimeMillis();
        if (remaining > 0) {
            player.sendMessage(plugin.messages().prefixed("event.drop-locked",
                    Map.of("seconds", String.valueOf(Math.max(1, (remaining + 999) / 1000)))));
            return true;
        }
        if (!runtime.claimed.compareAndSet(false, true)) return true;
        RewardDefinition reward = rewards.select(runtime.definition);
        repository.journalReward(id, runtime.definition.id(), player.getUniqueId(), player.getName(), reward.id())
                .whenComplete((transaction, error) -> {
                    if (error != null) {
                        runtime.claimed.set(false);
                        logFailure("Could not journal airdrop reward for " + id, error);
                        return;
                    }
                    complete(id, "claimed by " + player.getName());
                    scheduler.global(() -> deliver(transaction, reward));
                });
        return true;
    }

    public void recordDamage(UUID entity, Player player, double damage) {
        UUID run = bosses.get(entity);
        if (run != null && Double.isFinite(damage) && damage > 0) {
            repository.addDamage(run, player.getUniqueId(), player.getName(), damage)
                    .exceptionally(error -> { logFailure("Could not record boss damage", error); return null; });
        }
    }

    public void bossDied(UUID entity) {
        UUID id = bosses.remove(entity);
        if (id != null) settleBoss(id);
    }

    private void settleBoss(UUID id) {
        RuntimeEvent runtime = running.get(id);
        if (runtime == null || !runtime.settling.compareAndSet(false, true)) return;
        repository.contributions(id).thenCompose(contributions -> {
            var eligible = contributions.stream()
                    .filter(entry -> entry.damage() >= runtime.definition.minimumDamage())
                    .sorted(Comparator.comparingDouble(EventRepository.Contribution::damage).reversed());
            List<EventRepository.Contribution> winners = runtime.definition.maximumWinners() > 0
                    ? eligible.limit(runtime.definition.maximumWinners()).toList() : eligible.toList();
            List<CompletableFuture<PendingDelivery>> journals = new ArrayList<>();
            for (var contribution : winners) {
                RewardDefinition reward = rewards.select(runtime.definition);
                journals.add(repository.journalReward(id, runtime.definition.id(), contribution.playerId(),
                                contribution.playerName(), reward.id())
                        .thenApply(transaction -> new PendingDelivery(transaction, reward)));
            }
            return CompletableFuture.allOf(journals.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> journals.stream().map(CompletableFuture::join).toList());
        }).whenComplete((deliveries, error) -> {
            if (error != null) {
                runtime.settling.set(false);
                logFailure("Could not settle boss rewards for " + id + "; retrying", error);
                scheduler.later(100, () -> settleBoss(id));
                return;
            }
            complete(id, "boss defeated");
            scheduler.global(() -> deliveries.forEach(delivery -> deliver(delivery.transaction, delivery.reward)));
        });
    }

    private void deliver(EventRepository.RewardTransaction transaction, RewardDefinition reward) {
        if (transaction.delivered()) return;
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        boolean success = true;
        for (String configured : reward.commands()) {
            String command = configured
                    .replace("{player}", transaction.playerName())
                    .replace("{uuid}", transaction.playerId().toString())
                    .replace("{event}", transaction.templateId())
                    .replace("{run}", transaction.runId().toString());
            try { success &= Bukkit.dispatchCommand(console, command); }
            catch (RuntimeException error) { success = false; logFailure("Reward command failed: " + configured, error); }
        }
        if (!success) {
            plugin.getLogger().severe("Reward transaction " + transaction.transactionId() + " remains pending because a command failed.");
            return;
        }
        repository.markDelivered(transaction.transactionId()).thenAccept(changed -> {
            if (!changed) return;
            Player player = Bukkit.getPlayer(transaction.playerId());
            if (player != null) scheduler.entity(player, () -> player.sendMessage(plugin.messages().prefixed(
                    "event.reward", Map.of("reward", reward.id()))));
        }).exceptionally(error -> { logFailure("Could not confirm reward delivery", error); return null; });
    }

    public void retryPending() {
        ready.thenCompose(ignored -> repository.pendingRewards()).thenAccept(list -> scheduler.global(() -> {
            for (var transaction : list) {
                catalog.reward(transaction.templateId(), transaction.rewardId())
                        .ifPresentOrElse(reward -> deliver(transaction, reward),
                                () -> plugin.getLogger().severe("Pending reward definition not found: "
                                        + transaction.templateId() + '/' + transaction.rewardId()));
            }
        })).exceptionally(error -> { logFailure("Could not retry pending rewards", error); return null; });
    }

    private void complete(UUID id, String detail) {
        RuntimeEvent runtime = running.remove(id);
        if (runtime == null) return;
        cleanup(runtime);
        repository.transition(id, EventState.ACTIVE, EventState.COMPLETED, null, null, detail)
                .exceptionally(error -> { logFailure("Could not persist completion for " + id, error); return false; });
        broadcast("event.ended", Map.of("name", runtime.definition.displayName()));
    }

    @Override
    public CompletableFuture<Boolean> cancel(UUID id, String reason) {
        return ready.thenCompose(ignored -> {
            RuntimeEvent runtime = running.remove(id);
            if (runtime == null) return CompletableFuture.completedFuture(false);
            cleanup(runtime);
            return repository.active(runtime.definition.id()).thenCompose(view -> {
                if (view.isEmpty()) return CompletableFuture.completedFuture(false);
                return repository.transition(id, view.get().state(), EventState.CANCELLED, null, null, reason);
            }).thenApply(changed -> {
                if (changed) broadcast("event.cancelled", Map.of("name", runtime.definition.displayName()));
                return changed;
            });
        });
    }

    private void cleanup(RuntimeEvent runtime) {
        if (runtime.boss != null) {
            bosses.remove(runtime.boss.getUniqueId());
            scheduler.entity(runtime.boss, runtime.boss::remove);
        }
        if (runtime.location != null && runtime.definition.type() == EventType.AIRDROP) {
            drops.remove(BlockKey.of(runtime.location));
            scheduler.region(runtime.location, () -> {
                if (runtime.location.getBlock().getType() == Material.BARREL) {
                    runtime.location.getBlock().setType(Material.AIR, false);
                }
            });
        }
    }

    public void reconcileOrphans(Collection<EventRunView> orphans) {
        for (EventRunView orphan : orphans) {
            if (orphan.type() != EventType.AIRDROP || orphan.state() != EventState.ACTIVE) continue;
            scheduler.global(() -> {
                World world = Bukkit.getWorld(orphan.world());
                if (world == null) return;
                Location location = new Location(world, orphan.x(), orphan.y(), orphan.z());
                scheduler.region(location, () -> {
                    if (location.getBlock().getType() == Material.BARREL) location.getBlock().setType(Material.AIR, false);
                });
            });
        }
    }

    public boolean protects(Location location) { return drops.containsKey(BlockKey.of(location)); }
    public boolean isReady() { return ready.isDone() && !ready.isCompletedExceptionally(); }
    public Optional<EventDefinition> template(String id) { return catalog.find(id); }
    public Collection<RuntimeView> runtimeViews() {
        return running.values().stream().map(runtime -> new RuntimeView(runtime.id, runtime.definition.id(),
                runtime.definition.displayName(), runtime.definition.type())).toList();
    }
    public CompletableFuture<List<EventRunView>> recentRuns(int limit) { return ready.thenCompose(ignored -> repository.recentRuns(limit)); }

    public void shutdown() {
        // Keep non-terminal database rows for startup reconciliation. Paper/Folia schedulers may already
        // be stopped during server shutdown, so pretending cleanup completed would leave permanent crates.
        running.clear(); bosses.clear(); drops.clear(); starting.clear();
    }

    @Override public Collection<String> templateIds() { return catalog.all().stream().map(EventDefinition::id).toList(); }
    @Override public CompletableFuture<Optional<EventRunView>> active(String templateId) { return ready.thenCompose(ignored -> repository.active(templateId)); }

    public static Player playerAttacker(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private void broadcast(String key, Map<String, String> values) {
        scheduler.global(() -> Bukkit.broadcast(plugin.messages().message(key, values)));
    }

    private void logFailure(String message, Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        plugin.getLogger().severe(message + ": " + (root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage()));
    }

    public record RuntimeView(UUID id, String template, String name, EventType type) {}
    private record PendingDelivery(EventRepository.RewardTransaction transaction, RewardDefinition reward) {}
    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Location location) { return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    }
    private static final class RuntimeEvent {
        private final UUID id;
        private final EventDefinition definition;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final AtomicBoolean settling = new AtomicBoolean();
        private volatile LivingEntity boss;
        private volatile Location location;
        private volatile long claimableAtMillis;
        private RuntimeEvent(UUID id, EventDefinition definition) { this.id = id; this.definition = definition; }
    }
}
