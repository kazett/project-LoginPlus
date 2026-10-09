package kazet.loginplus;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Alur join + kunci semua aksi player sebelum login. */
public class LockListener implements Listener {

    private final LoginPlus plugin;

    public LockListener(LoginPlus plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Player p) {
        return plugin.isLocked(p);
    }

    public static String ipOf(Player p) {
        if (p.getAddress() == null || p.getAddress().getAddress() == null) return "unknown";
        return p.getAddress().getAddress().getHostAddress();
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Account a = plugin.accounts().get(id);
        String ip = ipOf(p);
        boolean verified = false;

        if (a != null && a.savedIp != null) {
            if (a.savedIp.equals(ip)) {
                // UUID benar + IP sama -> langsung masuk
                if (a.hasCharacter) {
                    plugin.complete(p, a);
                    plugin.success(p, "§aAuto login berhasil. Selamat datang kembali, §f" + a.fullName() + "§a!");
                    return;
                }
                verified = true;
            } else {
                // IP berubah -> data login lama dihapus, wajib input password lagi
                a.savedIp = null;
                plugin.accounts().save();
                p.sendMessage(LoginPlus.txt("§eIP kamu berubah, silakan login ulang."));
            }
        }

        Session s = new Session();
        s.verified = verified;
        plugin.sessions.put(id, s);
        p.setInvulnerable(true);

        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url != null && !url.isBlank()) {
            s.packWait = true;
            sendPack(p, url);
        } else {
            s.textured = plugin.getConfig().getBoolean("gui.use-texture", false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && plugin.sessions.get(id) == s) plugin.gui().openFirst(p, s);
            }, 10L);
        }
    }

    private void sendPack(Player p, String url) {
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        boolean force = plugin.getConfig().getBoolean("resource-pack.force", false);
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "§eResource pack dibutuhkan untuk tampilan menu login.");
        byte[] hash = null;
        if (sha1 != null && sha1.length() == 40) {
            try {
                hash = new byte[20];
                for (int i = 0; i < 20; i++) {
                    hash[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
                }
            } catch (NumberFormatException ex) {
                hash = null;
            }
        }
        try {
            if (hash != null) {
                p.setResourcePack(url, hash, LoginPlus.txt(prompt), force);
            } else {
                p.setResourcePack(url, (byte[]) null, LoginPlus.txt(prompt), force);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Gagal kirim resource pack: " + ex.getMessage());
            Session s = plugin.sessions.get(p.getUniqueId());
            if (s != null) {
                s.packWait = false;
                s.textured = false;
                plugin.gui().openFirst(p, s);
            }
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s == null || !s.packWait) return;
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                s.packWait = false;
                s.textured = true;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && plugin.sessions.get(p.getUniqueId()) == s) plugin.gui().openFirst(p, s);
                }, 5L);
            }
            case DECLINED, FAILED_DOWNLOAD -> {
                if (plugin.getConfig().getBoolean("resource-pack.force", false)) {
                    plugin.kick(p, "§cKamu harus menerima resource pack server untuk bermain.");
                    return;
                }
                s.packWait = false;
                s.textured = false;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && plugin.sessions.get(p.getUniqueId()) == s) plugin.gui().openFirst(p, s);
                }, 5L);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = plugin.sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) {
            s.ending = true;
            e.getPlayer().setInvulnerable(false);
        }
    }

    // ------------------------------------------------------------------ lock

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!locked(p)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location l = from.clone();
            l.setYaw(to.getYaw());
            l.setPitch(to.getPitch());
            e.setTo(l);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (locked(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(LoginPlus.txt("§cSelesaikan login dulu."));
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent e) {
        if (locked(e.getPlayer())) e.getCommands().clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s == null) return;
        if (!s.opening) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && locked(p)) e.setCancelled(true);
    }
}
