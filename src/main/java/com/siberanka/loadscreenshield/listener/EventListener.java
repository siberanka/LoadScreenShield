package com.siberanka.loadscreenshield.listener;

import com.siberanka.loadscreenshield.LoadScreenShield;
import com.siberanka.loadscreenshield.manager.ShieldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class EventListener implements Listener {

    private final LoadScreenShield plugin;
    private final ShieldManager shieldManager;

    public EventListener(LoadScreenShield plugin, ShieldManager shieldManager) {
        this.plugin = plugin;
        this.shieldManager = shieldManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay activation by 1 tick to ensure player is properly spawned
        scheduleTask(player, () -> {
            if (!plugin.getConfigManager().getBoolean("protection.enabled", true)) {
                return;
            }
            if (player.isOnline()) {
                shieldManager.activateShield(player);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        shieldManager.cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        if (!shieldManager.isShielded(player))
            return;

        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                scheduleTask(player, () -> shieldManager.deactivateShield(player,
                        plugin.getLangManager().getMessage("shield-deactivated")));
                break;
            case DECLINED:
                scheduleTask(player, () -> shieldManager.deactivateShield(player,
                        plugin.getLangManager().getMessage("shield-declined")));
                break;
            case FAILED_DOWNLOAD:
            case DISCARDED:
                scheduleTask(player, () -> shieldManager.deactivateShield(player,
                        plugin.getLangManager().getMessage("shield-failed")));
                break;
            case ACCEPTED:
            case DOWNLOADED:
                // Still waiting
                break;
        }
    }

    // --- Exploit Prevention & Damage Cancel ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (shieldManager.isShielded(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().getBoolean("protection.cancel-movement", true))
            return;

        if (shieldManager.isShielded(event.getPlayer())) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getY() != event.getTo().getY() ||
                    event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(plugin.getLangManager().getMessage("cannot-move"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (shieldManager.isShielded(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().getBoolean("protection.cancel-commands", true))
            return;

        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getLangManager().getMessage("cannot-command"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (shieldManager.isShielded(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (shieldManager.isShielded(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (shieldManager.isShielded(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void scheduleTask(Player player, Runnable task) {
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {
        }

        if (isFolia) {
            org.bukkit.Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), t -> task.run(), 1L);
        } else {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, task, 1L);
        }
    }
}
