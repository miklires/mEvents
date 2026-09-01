package io.github.miklires.mevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageService {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern SAFE_LOCALE = Pattern.compile("[a-z]{2}_[A-Z]{2}");

    private final MEventsPlugin plugin;
    private volatile YamlConfiguration messages;

    public MessageService(MEventsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String locale = plugin.getConfig().getString("language", "en_US");
        if (locale == null || !SAFE_LOCALE.matcher(locale).matches()
                || plugin.getResource("lang/" + locale + ".yml") == null) {
            plugin.getLogger().warning("Unsupported language '" + locale + "'; using en_US.");
            locale = "en_US";
        }
        String path = "lang/" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (var stream = plugin.getResource(path)) {
            if (stream != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
                loaded.options().copyDefaults(true);
                loaded.save(file);
            }
        } catch (IOException error) {
            plugin.getLogger().warning("Could not merge language defaults: " + error.getMessage());
        }
        messages = loaded;
    }

    public Component prefixed(String key, Map<String, String> values) {
        return render(raw("prefix") + raw(key), values);
    }

    public Component message(String key, Map<String, String> values) {
        return render(raw(key), values);
    }

    public Component prefixed(String key) { return prefixed(key, Map.of()); }
    public Component message(String key) { return message(key, Map.of()); }

    private String raw(String key) {
        String value = messages.getString(key);
        return value == null ? "<red>Missing message: " + key + "</red>" : value;
    }

    private Component render(String template, Map<String, String> values) {
        ArrayList<TagResolver.Single> placeholders = new ArrayList<>();
        values.forEach((key, value) -> placeholders.add(Placeholder.unparsed(
                key.toLowerCase(Locale.ROOT), value == null ? "" : value)));
        return MINI_MESSAGE.deserialize(template, TagResolver.resolver(placeholders));
    }
}
