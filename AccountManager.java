package kazet.loginplus;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Simpan akun di plugins/LoginPlus/accounts.yml. Password di-hash PBKDF2 (bukan plain text). */
public class AccountManager {

    private static final int ITERATIONS = 12000;

    private final LoginPlus plugin;
    private final File file;
    private final Object ioLock = new Object();
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();

    public AccountManager(LoginPlus plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "accounts.yml");
        load();
    }

    private void load() {
        accounts.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("accounts");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Account a = new Account(id, s.getString("account-name", ""));
            a.hash = s.getString("hash", "");
            a.salt = s.getString("salt", "");
            a.savedIp = s.getString("saved-ip", null);
            a.hasCharacter = s.getBoolean("has-character", false);
            a.name = s.getString("character.name", "");
            a.lastName = s.getString("character.last-name", "");
            a.gender = s.getString("character.gender", "");
            a.nationality = s.getString("character.nationality", "");
            a.day = s.getInt("character.day", 1);
            a.month = s.getInt("character.month", 1);
            a.year = s.getInt("character.year", 2000);
            a.mapId = s.getInt("map-id", -1);
            accounts.put(id, a);
        }
    }

    public Account get(UUID id) {
        return accounts.get(id);
    }

    public Account byMapId(int mapId) {
        if (mapId < 0) return null;
        for (Account a : accounts.values()) {
            if (a.mapId == mapId) return a;
        }
        return null;
    }

    public Account register(UUID id, String accountName, String password) {
        Account a = new Account(id, accountName);
        setPassword(a, password);
        accounts.put(id, a);
        save();
        return a;
    }

    public void setPassword(Account a, String password) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        a.salt = Base64.getEncoder().encodeToString(saltBytes);
        a.hash = hash(password, saltBytes);
    }

    public boolean verify(Account a, String password) {
        if (a.hash == null || a.hash.isEmpty() || a.salt == null || a.salt.isEmpty()) return false;
        byte[] saltBytes = Base64.getDecoder().decode(a.salt);
        String h = hash(password, saltBytes);
        return MessageDigest.isEqual(h.getBytes(StandardCharsets.UTF_8), a.hash.getBytes(StandardCharsets.UTF_8));
    }

    public void remove(UUID id) {
        accounts.remove(id);
        save();
    }

    private static String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(f.generateSecret(spec).getEncoded());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Account a : accounts.values()) {
            String p = "accounts." + a.uuid + ".";
            y.set(p + "account-name", a.accountName);
            y.set(p + "hash", a.hash);
            y.set(p + "salt", a.salt);
            y.set(p + "saved-ip", a.savedIp);
            y.set(p + "has-character", a.hasCharacter);
            y.set(p + "character.name", a.name);
            y.set(p + "character.last-name", a.lastName);
            y.set(p + "character.gender", a.gender);
            y.set(p + "character.nationality", a.nationality);
            y.set(p + "character.day", a.day);
            y.set(p + "character.month", a.month);
            y.set(p + "character.year", a.year);
            y.set(p + "map-id", a.mapId);
        }
        return y.saveToString();
    }

    /** Simpan async (data di-serialize di main thread dulu). */
    public void save() {
        final String data = serialize();
        if (!plugin.isEnabled()) {
            write(data);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    public void saveNow() {
        write(serialize());
    }

    private void write(String data) {
        synchronized (ioLock) {
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                File tmp = new File(dir, "accounts.yml.tmp");
                Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                plugin.getLogger().severe("Gagal simpan accounts.yml: " + ex.getMessage());
            }
        }
    }
}
