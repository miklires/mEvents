package io.github.miklires.mevents.command;

import io.github.miklires.mevents.MEventsPlugin;
import io.github.miklires.mevents.event.EventCatalog;
import io.github.miklires.mevents.event.EventService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class EventCommand implements CommandExecutor, TabCompleter {
    private final MEventsPlugin plugin;
    private final EventService service;
    private final Supplier<EventCatalog.LoadReport> reload;

    public EventCommand(MEventsPlugin plugin, EventService service, Supplier<EventCatalog.LoadReport> reload) {
        this.plugin = plugin;
        this.service = service;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("mevents.use") && !sender.hasPermission("mevents.admin")) {
            reply(sender, plugin.messages().prefixed("command.no-permission"));
            return true;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "status", "time" -> status(sender);
            case "info" -> info(sender, args);
            case "history" -> history(sender);
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "reload" -> reload(sender);
            default -> reply(sender, plugin.messages().prefixed("command.usage"));
        }
        return true;
    }

    private void history(CommandSender sender) {
        if (!service.isReady()) { reply(sender, plugin.messages().prefixed("command.not-ready")); return; }
        service.recentRuns(10).whenComplete((runs, error) -> {
            if (error != null) { reply(sender, Component.text("mEvents: " + rootMessage(error))); return; }
            if (runs.isEmpty()) { reply(sender, plugin.messages().prefixed("command.no-history")); return; }
            String history = String.join(", ", runs.stream().map(run ->
                    run.templateId() + "=" + run.state().name().toLowerCase(Locale.ROOT) + " (" + run.runId() + ")").toList());
            reply(sender, plugin.messages().prefixed("command.history", Map.of("runs", history)));
        });
    }

    private void list(CommandSender sender) {
        reply(sender, plugin.messages().prefixed("command.templates",
                Map.of("templates", String.join(", ", service.templateIds()))));
        status(sender);
    }

    private void status(CommandSender sender) {
        var active = service.runtimeViews();
        if (active.isEmpty()) {
            reply(sender, plugin.messages().prefixed("command.none-active"));
            return;
        }
        String value = String.join(", ", active.stream()
                .map(run -> run.template() + " (" + run.id() + ")").toList());
        reply(sender, plugin.messages().prefixed("command.active", Map.of("events", value)));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) { reply(sender, plugin.messages().prefixed("command.usage-info")); return; }
        var definition = service.template(args[1]);
        if (definition.isEmpty()) {
            reply(sender, plugin.messages().prefixed("command.unknown-template", Map.of("template", args[1])));
            return;
        }
        var event = definition.get();
        reply(sender, plugin.messages().prefixed("command.info", Map.of(
                "name", event.displayName(), "type", event.type().name(), "world", event.world(),
                "preparation", String.valueOf(event.preparationTicks() / 20),
                "duration", String.valueOf(event.durationTicks() / 20))));
    }

    private void start(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) { reply(sender, plugin.messages().prefixed("command.usage-start")); return; }
        if (!service.isReady()) { reply(sender, plugin.messages().prefixed("command.not-ready")); return; }
        service.start(args[1]).whenComplete((run, error) -> {
            if (error != null) reply(sender, Component.text("mEvents: " + rootMessage(error)));
            else reply(sender, plugin.messages().prefixed("command.scheduled", Map.of("run", run.runId().toString())));
        });
    }

    private void stop(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) { reply(sender, plugin.messages().prefixed("command.usage-stop")); return; }
        UUID id;
        try { id = UUID.fromString(args[1]); }
        catch (IllegalArgumentException error) { reply(sender, plugin.messages().prefixed("command.invalid-run")); return; }
        if (!service.isReady()) { reply(sender, plugin.messages().prefixed("command.not-ready")); return; }
        service.cancel(id, "stopped by " + sender.getName()).whenComplete((changed, error) -> {
            if (error != null) reply(sender, Component.text("mEvents: " + rootMessage(error)));
            else reply(sender, plugin.messages().prefixed(changed ? "command.stopped" : "command.not-found"));
        });
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("mevents.reload")) {
            reply(sender, plugin.messages().prefixed("command.no-permission"));
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                EventCatalog.LoadReport report = reload.get();
                reply(sender, plugin.messages().prefixed("command.reloaded", Map.of(
                        "loaded", String.valueOf(report.loaded()), "skipped", String.valueOf(report.skipped()))));
            } catch (RuntimeException error) {
                reply(sender, Component.text("mEvents: " + rootMessage(error)));
            }
        });
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("mevents.admin")) return true;
        reply(sender, plugin.messages().prefixed("command.no-permission"));
        return false;
    }

    private void reply(CommandSender sender, Component component) {
        if (sender instanceof Player player) {
            player.getScheduler().execute(plugin, () -> player.sendMessage(component), null, 1L);
        } else {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(component));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            choices.addAll(List.of("list", "status", "time", "info", "history"));
            if (sender.hasPermission("mevents.admin")) choices.addAll(List.of("start", "stop"));
            if (sender.hasPermission("mevents.reload")) choices.add("reload");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("info"))) {
            choices.addAll(service.templateIds());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            choices.addAll(service.runtimeViews().stream().map(run -> run.id().toString()).toList());
        } else return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
