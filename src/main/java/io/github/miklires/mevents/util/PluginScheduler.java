package io.github.miklires.mevents.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class PluginScheduler {
    private final Plugin plugin;
    public PluginScheduler(Plugin plugin) { this.plugin = plugin; }
    public void global(Runnable task) { plugin.getServer().getGlobalRegionScheduler().execute(plugin, task); }
    public ScheduledTask later(long ticks, Runnable task) {
        return plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), Math.max(1, ticks));
    }
    public void region(Location location, Runnable task) { plugin.getServer().getRegionScheduler().execute(plugin, location, task); }
    public void entity(Entity entity, Runnable task) { entity.getScheduler().execute(plugin, task, null, 1L); }
}
