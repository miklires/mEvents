package io.github.miklires.mevents.event;

import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class EventListener implements Listener {
    private final EventService service;
    public EventListener(EventService service) { this.service = service; }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null
                && service.claimDrop(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void inventoryOpen(InventoryOpenEvent event) {
        var location = event.getInventory().getLocation();
        if (location != null && service.protects(location)) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player player) service.claimDrop(player, location);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (service.protects(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> service.protects(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void entityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> service.protects(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void entityChangeBlock(EntityChangeBlockEvent event) {
        if (service.protects(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonExtend(BlockPistonExtendEvent event) {
        if (touchesProtected(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent event) {
        if (touchesProtected(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    private boolean touchesProtected(java.util.List<Block> blocks, org.bukkit.block.BlockFace direction) {
        return blocks.stream().anyMatch(block -> service.protects(block.getLocation())
                || service.protects(block.getRelative(direction).getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void move(InventoryMoveItemEvent event) {
        var source = event.getSource().getLocation();
        var destination = event.getDestination().getLocation();
        if ((source != null && service.protects(source))
                || (destination != null && service.protects(destination))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Player player = EventService.playerAttacker(event);
        if (player == null) return;
        double damage = event.getFinalDamage();
        if (event.getEntity() instanceof LivingEntity living) damage = Math.min(damage, living.getHealth());
        service.recordDamage(event.getEntity().getUniqueId(), player, damage);
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        service.bossDied(event.getEntity().getUniqueId());
    }
}
