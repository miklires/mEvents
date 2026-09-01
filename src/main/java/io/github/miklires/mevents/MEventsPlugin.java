package io.github.miklires.mevents;

import io.github.miklires.mevents.api.EventRunView;
import io.github.miklires.mevents.api.MEventsApi;
import io.github.miklires.mevents.command.EventCommand;
import io.github.miklires.mevents.event.AutomaticEventScheduler;
import io.github.miklires.mevents.event.EventCatalog;
import io.github.miklires.mevents.event.EventListener;
import io.github.miklires.mevents.event.EventService;
import io.github.miklires.mevents.storage.EventRepository;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class MEventsPlugin extends JavaPlugin {
    private EventRepository repository;
    private EventCatalog catalog;
    private EventService service;
    private MessageService messages;
    private AutomaticEventScheduler automaticScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundled("events.yml");
        messages = new MessageService(this);
        catalog = new EventCatalog();
        logReport(reloadCatalog());

        repository = new EventRepository(getDataFolder().toPath().resolve("events"));
        CompletableFuture<List<EventRunView>> orphans = repository.initialize().thenCompose(ignored -> repository.activeRuns());
        CompletableFuture<Void> ready = orphans.thenCompose(ignored -> repository.cancelOrphans()).thenAccept(count ->
                getLogger().info("Reconciled " + count + " unfinished event(s)."));
        service = new EventService(this, catalog, repository, ready);
        automaticScheduler = new AutomaticEventScheduler(this, service);

        ready.whenComplete((ignored, error) -> {
            if (error != null) {
                Throwable root = root(error);
                getLogger().severe("Database initialization failed: " + root.getMessage());
                getServer().getGlobalRegionScheduler().execute(this,
                        () -> getServer().getPluginManager().disablePlugin(this));
                return;
            }
            service.reconcileOrphans(orphans.join());
            service.retryPending();
            getServer().getGlobalRegionScheduler().execute(this, () -> automaticScheduler.reload(catalog.all()));
        });

        getServer().getPluginManager().registerEvents(new EventListener(service), this);
        PluginCommand command = Objects.requireNonNull(getCommand("event"), "event command missing from plugin.yml");
        EventCommand handler = new EventCommand(this, service, this::reloadAll);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getServicesManager().register(MEventsApi.class, service, this, ServicePriority.Normal);

        if (getConfig().getBoolean("metrics.enabled", true)) {
            int id = Math.max(0, getConfig().getInt("metrics.bstats-id", 27941));
            if (id > 0) new Metrics(this, id);
        }
        getLogger().info("mEvents " + getPluginMeta().getVersion() + " enabled with " + catalog.all().size() + " template(s)." );
    }

    @Override
    public void onDisable() {
        if (automaticScheduler != null) automaticScheduler.stop();
        if (service != null) service.shutdown();
        if (repository != null) repository.close();
    }

    public EventCatalog.LoadReport reloadAll() {
        reloadConfig();
        messages.reload();
        EventCatalog.LoadReport report = reloadCatalog();
        if (automaticScheduler != null && service.isReady()) automaticScheduler.reload(catalog.all());
        logReport(report);
        return report;
    }

    private EventCatalog.LoadReport reloadCatalog() { return catalog.load(new File(getDataFolder(), "events.yml")); }
    private void logReport(EventCatalog.LoadReport report) {
        report.errors().forEach(error -> getLogger().warning("Skipped event template " + error));
    }
    private void saveBundled(String path) { if (!new File(getDataFolder(), path).exists()) saveResource(path, false); }
    private static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }
    public MessageService messages() { return messages; }
}
