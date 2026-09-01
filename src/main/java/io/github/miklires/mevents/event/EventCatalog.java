package io.github.miklires.mevents.event;

import io.github.miklires.mevents.api.EventType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EventCatalog {
    private volatile Map<String, EventDefinition> definitions = Map.of();
    private volatile LoadReport lastReport = new LoadReport(0, 0, List.of());

    public LoadReport load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("events");
        if (root == null) throw new IllegalArgumentException("events.yml has no events section");
        Map<String, EventDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (root.getKeys(false).size() > 1000) throw new IllegalArgumentException("events.yml contains more than 1000 templates");
        for (String id : root.getKeys(false)) {
            try {
                ConfigurationSection section = Objects.requireNonNull(root.getConfigurationSection(id), "not a section");
                loaded.put(id, read(id, section));
            } catch (RuntimeException error) {
                errors.add(id + ": " + rootMessage(error));
            }
        }
        if (loaded.isEmpty()) throw new IllegalArgumentException("events.yml contains no valid events: " + String.join("; ", errors));
        definitions = Map.copyOf(loaded);
        lastReport = new LoadReport(loaded.size(), errors.size(), errors);
        return lastReport;
    }

    private EventDefinition read(String id, ConfigurationSection section) {
        List<RewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("rewards")) {
            String rewardId = String.valueOf(raw.get("id"));
            Object weightValue = raw.containsKey("weight") ? raw.get("weight") : 1;
            double weight = Double.parseDouble(String.valueOf(weightValue));
            Object commandValue = raw.get("commands");
            List<String> commands = commandValue instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
            rewards.add(new RewardDefinition(rewardId, weight, commands));
        }
        return new EventDefinition(
                id,
                section.getString("name", id),
                EventType.valueOf(section.getString("type", "AIRDROP").toUpperCase(Locale.ROOT)),
                section.getString("location.world", "world"),
                section.getInt("location.x"), section.getInt("location.y", 80), section.getInt("location.z"),
                section.getLong("preparation-ticks", 100), section.getLong("duration-ticks", 1200),
                section.getLong("cooldown-ticks", 0),
                section.getString("boss.entity", "ZOMBIE"), section.getDouble("boss.health", 100),
                section.getDouble("boss.minimum-damage", 0), section.getInt("boss.maximum-winners", 0),
                section.getLong("airdrop.grace-ticks", 0),
                section.getBoolean("schedule.enabled", false), section.getLong("schedule.initial-delay-ticks", 1200),
                section.getLong("schedule.interval-ticks", 72000), section.getLong("schedule.jitter-ticks", 0), rewards);
    }

    public Optional<EventDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
    public Collection<EventDefinition> all() { return definitions.values(); }
    public Optional<RewardDefinition> reward(String templateId, String rewardId) {
        return find(templateId).flatMap(definition -> definition.rewards().stream().filter(reward -> reward.id().equals(rewardId)).findFirst());
    }
    public LoadReport lastReport() { return lastReport; }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record LoadReport(int loaded, int skipped, List<String> errors) {
        public LoadReport { errors = List.copyOf(errors); }
    }
}
