package kazet.loginplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoginPlus extends JavaPlugin implements TabExecutor {

    /** Player yang belum login/belum selesai bikin character. */
    public final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private AccountManager accounts;
    private IdCardManager idCards;
    private GuiManager gui;

    public static Component txt(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
    }

    public AccountManager accounts() {
        return accounts;
    }

    public IdCardManager idCards() {
        return idCards;
    }

    public GuiManager gui() {
        return gui;
    }

    public boolean isLocked(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        accounts = new AccountManager(this);
        idCards = new IdCardManager(this);
        gui = new GuiManager(this);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(idCards, this);
        Bukkit.getPluginManager().registerEvents(new LockListener(this), this);

        if (getCommand("loginplus") != null) {
            getCommand("loginplus").setExecutor(this);
            getCommand("loginplus").setTabCompleter(this);
        }

        // timeout login + fallback kalau resource pack tidak merespon
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int timeout = getConfig().getInt("login.timeout-seconds", 180);
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Session> en : sessions.entrySet()) {
                Player p = Bukkit.getPlayer(en.getKey());
                Session s = en.getValue();
                if (p == null || !p.isOnline()) continue;
                if (timeout > 0 && now - s.joinedAt > timeout * 1000L) {
                    kick(p, "§cWaktu login habis.");
                    continue;
                }
                if (s.packWait && now - s.joinedAt > 30000L) {
                    s.packWait = false;
                    s.textured = false;
                    gui.openFirst(p, s);
                }
            }
        }, 20L, 20L);

        // pastikan semua player yang sudah login punya KTP
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!isLocked(p)) idCards.ensureCard(p);
            }
        }, 1200L, 1200L);

        getLogger().info("LoginPlus aktif.");
    }

    @Override
    public void onDisable() {
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setInvulnerable(false);
        }
        sessions.clear();
        if (accounts != null) accounts.saveNow();
    }

    public void kick(Player p, String reason) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) s.ending = true;
        p.kick(txt(reason));
    }

    /** Dipanggil kalau player sudah lolos login (dan character sudah ada). */
    public void complete(Player p, Account a) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null) s.ending = true;
        p.closeInventory();
        p.setInvulnerable(false);
        applyIdentity(p, a);
        idCards.ensureCard(p);
    }

    public void applyIdentity(Player p, Account a) {
        if (a == null || !a.hasCharacter) return;
        if (getConfig().getBoolean("character.set-display-name", true)) {
            Component name = Component.text(a.fullName());
            p.displayName(name);
            p.playerListName(name);
        }
    }

    public void success(Player p, String msg) {
        p.sendMessage(txt(msg));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ command admin

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("loginplus.admin")) {
            sender.sendMessage(txt("§cKamu tidak punya izin."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(txt("§e/loginplus reload §7| §eunregister <player> §7| §eresetpass <player> <password> §7| §egivecard <player>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                sender.sendMessage(txt("§aConfig di-reload. (KTP baru pakai setting baru; KTP lama ikut saat server restart)"));
            }
            case "unregister" -> {
                if (args.length < 2) {
                    sender.sendMessage(txt("§c/loginplus unregister <player>"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                if (accounts.get(op.getUniqueId()) == null) {
                    sender.sendMessage(txt("§cAkun tidak ditemukan."));
                    return true;
                }
                accounts.remove(op.getUniqueId());
                Player online = op.getPlayer();
                if (online != null) online.kick(txt("§cAkun kamu dihapus admin. Join lagi untuk register."));
                sender.sendMessage(txt("§aAkun " + args[1] + " dihapus."));
            }
            case "resetpass" -> {
                if (args.length < 3) {
                    sender.sendMessage(txt("§c/loginplus resetpass <player> <password baru>"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                Account a = accounts.get(op.getUniqueId());
                if (a == null) {
                    sender.sendMessage(txt("§cAkun tidak ditemukan."));
                    return true;
                }
                accounts.setPassword(a, args[2]);
                a.savedIp = null;
                accounts.save();
                sender.sendMessage(txt("§aPassword " + args[1] + " direset."));
            }
            case "givecard" -> {
                if (args.length < 2) {
                    sender.sendMessage(txt("§c/loginplus givecard <player>"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                Account a = t == null ? null : accounts.get(t.getUniqueId());
                if (t == null || a == null || !a.hasCharacter) {
                    sender.sendMessage(txt("§cPlayer tidak online / belum punya character."));
                    return true;
                }
                idCards.ensureCard(t);
                sender.sendMessage(txt("§aKTP dicek/diberikan ke " + t.getName() + "."));
            }
            default -> sender.sendMessage(txt("§cSubcommand tidak dikenal."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"reload", "unregister", "resetpass", "givecard"}) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
