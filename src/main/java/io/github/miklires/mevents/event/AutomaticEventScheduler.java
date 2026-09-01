package io.github.miklires.mevents.event;

import io.github.miklires.mevents.MEventsPlugin;
import io.github.miklires.mevents.util.PluginScheduler;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class AutomaticEventScheduler {
    private final MEventsPlugin plugin;
    private final EventService service;
    private final PluginScheduler scheduler;
    private final AtomicLong generation = new AtomicLong();

    public AutomaticEventScheduler(MEventsPlugin plugin, EventService service) {
        this.plugin = plugin;
        this.service = service;
        this.scheduler = new PluginScheduler(plugin);
    }

    public void reload(Collection<EventDefinition> definitions) {
        long current = generation.incrementAndGet();
        definitions.stream().filter(EventDefinition::automatic)
                .forEach(definition -> schedule(definition, definition.initialDelayTicks(), current));
    }

    public void stop() { generation.incrementAndGet(); }

    private void schedule(EventDefinition definition, long delay, long expectedGeneration) {
        scheduler.later(delay, () -> {
            if (generation.get() != expectedGeneration || !plugin.isEnabled()) return;
            service.start(definition.id()).whenComplete((run, error) -> {
                if (error != null && !rootMessage(error).contains("already running")) {
                    plugin.getLogger().warning("Automatic event " + definition.id() + " was not started: " + rootMessage(error));
                }
                if (generation.get() == expectedGeneration) {
                    long jitter = definition.jitterTicks() == 0 ? 0
                            : ThreadLocalRandom.current().nextLong(-definition.jitterTicks(), definition.jitterTicks() + 1);
                    schedule(definition, Math.max(20, definition.intervalTicks() + jitter), expectedGeneration);
                }
            });
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
