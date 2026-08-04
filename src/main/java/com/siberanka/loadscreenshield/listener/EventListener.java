package com.siberanka.loadscreenshield.listener;

import com.siberanka.loadscreenshield.LoadScreenShield;
import com.siberanka.loadscreenshield.manager.ConfigManager;
import com.siberanka.loadscreenshield.manager.ShieldManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class EventListener implements Listener {

    private final LoadScreenShield plugin;
    private final ShieldManager shieldManager;

    public EventListener(LoadScreenShield plugin, ShieldManager shieldManager) {
        this.plugin = plugin;
        this.shieldManager = shieldManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        shieldManager.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        shieldManager.cleanupPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        Runnable update = () -> shieldManager.handleResourcePackStatus(
                player, event.getID(), event.getStatus().name()
        );
        if (event.isAsynchronous()) {
            player.getScheduler().execute(plugin, update, null, 1L);
        } else {
            update.run();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && shieldManager.isShielded(player)) {
            event.setCancelled(true);
        }
        if (!(event instanceof EntityDamageByEntityEvent damageByEntity)) {
            return;
        }
        Player attackingPlayer = resolveAttackingPlayer(damageByEntity);
        if (attackingPlayer != null && shieldManager.isShielded(attackingPlayer)) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        ConfigManager.Snapshot config = plugin.getConfigManager().snapshot();
        if (!config.cancelMovement() || !shieldManager.isShielded(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(getLangMessage("cannot-move"));
        }
    }

    private net.kyori.adventure.text.Component getLangMessage(String key) {
        return plugin.getLangManager().getRawMessage(key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (plugin.getConfigManager().snapshot().cancelTeleports() && shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && shieldManager.isShielded(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && shieldManager.isShielded(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && shieldManager.isShielded(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        cancelIfShielded(event.getPlayer(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && shieldManager.isShielded(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().snapshot().cancelCommands() || !shieldManager.isShielded(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getLangManager().getMessage("cannot-command"));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void cancelIfShielded(Player player, Cancellation cancellation) {
        if (shieldManager.isShielded(player)) {
            cancellation.cancel(true);
        }
    }

    @FunctionalInterface
    private interface Cancellation {
        void cancel(boolean cancelled);
    }
}
