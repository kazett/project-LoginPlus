package kazet.loginplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semua menu GUI: login, register, create character, pilih nationality, input teks (anvil). */
public class GuiManager implements Listener {

    public static class MenuHolder implements InventoryHolder {
        public final Session.Screen screen;
        Inventory inv;

        public MenuHolder(Session.Screen screen) {
            this.screen = screen;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // Glyph resource pack (harus sama dengan font/default.json di resource pack)
    private static final String SHIFT = "";
    private static final String GLYPH_LOGIN = "";
    private static final String GLYPH_REGISTER = "";
    private static final String GLYPH_CHARACTER = "";

    // Area slot (harus sama dengan gambar di resource pack)
    private static final int[] L_PASS = range(19, 25);
    private static final int[] L_CHECK = range(28, 34);
    private static final int[] L_CANCEL = {37, 38, 39, 46, 47, 48};
    private static final int[] L_CONFIRM = {41, 42, 43, 50, 51, 52};

    private static final int[] R_PASS = range(19, 25);
    private static final int[] R_CONF = range(37, 43);
    private static final int[] R_CANCEL = {46, 47, 48};
    private static final int[] R_CONFIRM = {50, 51, 52};

    private static final int[] C_NAME = range(9, 12);
    private static final int[] C_LAST = range(14, 17);
    private static final int[] C_MALE = range(18, 21);
    private static final int[] C_FEMALE = range(23, 26);
    private static final int[] C_DAY = range(27, 29);
    private static final int[] C_MONTH = range(30, 32);
    private static final int[] C_YEAR = range(33, 35);
    private static final int[] C_NATION = range(36, 44);
    private static final int[] C_CANCEL = range(45, 48);
    private static final int[] C_CONFIRM = range(50, 53);

    private static final int N_BACK = 49;

    private static final String[] MONTHS = {"Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"};

    private final LoginPlus plugin;

    public GuiManager(LoginPlus plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ util

    private static int[] range(int a, int b) {
        int[] r = new int[b - a + 1];
        for (int i = 0; i < r.length; i++) r[i] = a + i;
        return r;
    }

    private static boolean in(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    private void msg(Player p, String text) {
        p.sendMessage(LoginPlus.txt(text));
    }

    private void error(Player p, String text) {
        msg(p, "§c✖ " + text);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private ItemStack item(Material m, String name, boolean glow, int amount, String... lore) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LoginPlus.txt(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(LoginPlus.txt(s));
            meta.lore(l);
        }
        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private Inventory base(Session.Screen sc, Session s, String glyph, String plainTitle) {
        MenuHolder h = new MenuHolder(sc);
        Component title = s.textured
                ? Component.text(SHIFT + glyph, NamedTextColor.WHITE)
                : LoginPlus.txt("§8" + plainTitle);
        Inventory inv = Bukkit.createInventory(h, 54, title);
        h.inv = inv;
        return inv;
    }

    private void fillBackground(Inventory inv, Session s) {
        inv.clear();
        if (s.textured) return;
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", false, 1);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    /** Icon di slot pertama group; kalau tanpa texture, sisa slot group diisi pane abu-abu. */
    private void group(Inventory inv, Session s, int[] slots, ItemStack icon) {
        inv.setItem(slots[0], icon);
        if (!s.textured) {
            ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", false, 1);
            for (int i = 1; i < slots.length; i++) inv.setItem(slots[i], pane);
        }
    }

    /** Tombol: dengan texture digambar di resource pack, tanpa texture pakai pane berwarna. */
    private void button(Inventory inv, Session s, int[] slots, Material pane, String name) {
        if (s.textured) return;
        ItemStack it = item(pane, name, false, 1);
        for (int slot : slots) inv.setItem(slot, it);
    }

    private void show(Player p, Session s, Inventory inv, Session.Screen screen) {
        s.opening = true;
        try {
            p.openInventory(inv);
        } finally {
            s.opening = false;
        }
        s.screen = screen;
    }

    // ------------------------------------------------------------------ buka menu

    public void openFirst(Player p, Session s) {
        UUID id = p.getUniqueId();
        Account a = plugin.accounts().get(id);
        if (a == null) {
            openScreen(p, s, Session.Screen.REGISTER);
        } else if (s.verified && !a.hasCharacter) {
            openScreen(p, s, Session.Screen.CHARACTER);
        } else {
            openScreen(p, s, Session.Screen.LOGIN);
        }
    }

    private void openScreen(Player p, Session s, Session.Screen sc) {
        switch (sc) {
            case REGISTER -> openRegister(p, s);
            case CHARACTER -> openCharacter(p, s);
            case NATIONALITY -> openNationality(p, s);
            default -> openLogin(p, s);
        }
    }

    private void openLogin(Player p, Session s) {
        Inventory inv = base(Session.Screen.LOGIN, s, GLYPH_LOGIN, "Login");
        fillLogin(inv, s);
        show(p, s, inv, Session.Screen.LOGIN);
    }

    private void fillLogin(Inventory inv, Session s) {
        fillBackground(inv, s);
        group(inv, s, L_PASS, passwordItem("Password", s.password));
        group(inv, s, L_CHECK, checkboxItem(s.saveLogin));
        button(inv, s, L_CANCEL, Material.RED_STAINED_GLASS_PANE, "§c✖ Cancel");
        button(inv, s, L_CONFIRM, Material.LIME_STAINED_GLASS_PANE, "§a✔ Confirm");
    }

    private ItemStack passwordItem(String label, String value) {
        if (value == null || value.isEmpty()) {
            return item(Material.NAME_TAG, "§7" + label + ": §8(belum diisi)", false, 1,
                    "§eKlik untuk mengisi", "§8Password minimal " + minPass() + " karakter");
        }
        return item(Material.NAME_TAG, "§a" + label + ": §f" + "*".repeat(Math.min(value.length(), 24)), true, 1,
                "§eKlik untuk mengubah");
    }

    private ItemStack checkboxItem(boolean on) {
        String[] lore = {"§7Agar tidak perlu memasukkan password", "§7setiap kali join (UUID + IP sama).",
                "§8Kalau IP berubah, harus login ulang."};
        if (on) return item(Material.LIME_DYE, "§a☑ Simpan data login", true, 1, lore);
        return item(Material.GRAY_DYE, "§7☐ Simpan data login", false, 1, lore);
    }

    private void openRegister(Player p, Session s) {
        Inventory inv = base(Session.Screen.REGISTER, s, GLYPH_REGISTER, "Register");
        fillRegister(inv, s);
        show(p, s, inv, Session.Screen.REGISTER);
    }

    private void fillRegister(Inventory inv, Session s) {
        fillBackground(inv, s);
        group(inv, s, R_PASS, passwordItem("Password", s.password));
        group(inv, s, R_CONF, passwordItem("Confirm Password", s.confirm));
        button(inv, s, R_CANCEL, Material.RED_STAINED_GLASS_PANE, "§c✖ Cancel");
        button(inv, s, R_CONFIRM, Material.LIME_STAINED_GLASS_PANE, "§a✔ Confirm");
    }

    private void openCharacter(Player p, Session s) {
        Inventory inv = base(Session.Screen.CHARACTER, s, GLYPH_CHARACTER, "Create Character");
        fillCharacter(inv, s);
        show(p, s, inv, Session.Screen.CHARACTER);
    }

    private int yearMin() {
        return plugin.getConfig().getInt("character.year-min", 1950);
    }

    private int yearMax() {
        return plugin.getConfig().getInt("character.year-max", 2012);
    }

    private int minPass() {
        return plugin.getConfig().getInt("password.min-length", 6);
    }

    private void fillCharacter(Inventory inv, Session s) {
        fillBackground(inv, s);
        s.year = Math.max(yearMin(), Math.min(yearMax(), s.year));
        s.day = Math.min(s.day, YearMonth.of(s.year, s.month).lengthOfMonth());

        group(inv, s, C_NAME, textItem("Name (Ingame Name)", s.name));
        group(inv, s, C_LAST, textItem("Last Name", s.last));

        boolean male = "Male".equals(s.gender);
        boolean female = "Female".equals(s.gender);
        group(inv, s, C_MALE, item(Material.LIGHT_BLUE_DYE, (male ? "§b§l" : "§b") + "♂ Male", male, 1,
                "§7Klik untuk memilih"));
        group(inv, s, C_FEMALE, item(Material.PINK_DYE, (female ? "§d§l" : "§d") + "♀ Female", female, 1,
                "§7Klik untuk memilih"));

        String[] hint = {"§eKlik kiri: §f+1   §eKlik kanan: §f-1", "§eShift+klik: §flompat lebih jauh"};
        group(inv, s, C_DAY, item(Material.PAPER, "§fTanggal: §e" + s.day, true, s.day, hint));
        group(inv, s, C_MONTH, item(Material.BOOK, "§fBulan: §e" + MONTHS[s.month - 1] + " (" + s.month + ")", true, s.month, hint));
        group(inv, s, C_YEAR, item(Material.CLOCK, "§fTahun: §e" + s.year, true, 1, hint));

        String nat = s.nationality == null ? "§8(belum dipilih)" : "§e" + s.nationality;
        group(inv, s, C_NATION, item(Material.WHITE_BANNER, "§fNationality: " + nat, s.nationality != null, 1,
                "§7Klik untuk memilih dari daftar negara"));

        button(inv, s, C_CANCEL, Material.RED_STAINED_GLASS_PANE, "§c✖ Cancel");
        button(inv, s, C_CONFIRM, Material.LIME_STAINED_GLASS_PANE, "§a✔ Confirm");
    }

    private ItemStack textItem(String label, String value) {
        if (value == null || value.isEmpty()) {
            return item(Material.NAME_TAG, "§7" + label + ": §8(belum diisi)", false, 1, "§eKlik untuk mengisi");
        }
        return item(Material.NAME_TAG, "§a" + label + ": §f" + value, true, 1, "§eKlik untuk mengubah");
    }

    private List<String> nationalities() {
        List<String> l = plugin.getConfig().getStringList("nationalities");
        if (l.isEmpty()) l.add("Indonesia");
        return l;
    }

    private int[] nationSlots() {
        List<Integer> l = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) l.add(row * 9 + col);
        }
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    private void openNationality(Player p, Session s) {
        MenuHolder h = new MenuHolder(Session.Screen.NATIONALITY);
        Inventory inv = Bukkit.createInventory(h, 54, LoginPlus.txt("§8Pilih Nationality"));
        h.inv = inv;
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", false, 1);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        int[] slots = nationSlots();
        List<String> list = nationalities();
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            String c = list.get(i);
            boolean sel = c.equals(s.nationality);
            inv.setItem(slots[i], item(Material.WHITE_BANNER, (sel ? "§a§l" : "§f") + c, sel, 1, "§7Klik untuk memilih"));
        }
        inv.setItem(N_BACK, item(Material.ARROW, "§e« Kembali", false, 1));
        show(p, s, inv, Session.Screen.NATIONALITY);
    }

    private void openInput(Player p, Session s, Session.Field f, String prompt, Session.Screen back) {
        s.inputField = f;
        s.returnTo = back;
        InventoryView v;
        s.opening = true;
        try {
            v = p.openAnvil(p.getLocation(), true);
        } finally {
            s.opening = false;
        }
        if (v == null) {
            error(p, "Input teks tidak bisa dibuka.");
            return;
        }
        s.screen = Session.Screen.INPUT;
        v.setTitle(prompt);
        String cur = switch (f) {
            case NAME -> s.name;
            case LAST -> s.last;
            default -> "";
        };
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta m = paper.getItemMeta();
        m.displayName(Component.text(cur));
        paper.setItemMeta(m);
        v.getTopInventory().setItem(0, paper);
        msg(p, "§7Ketik teks di kotak atas, lalu klik item hasil (kanan) untuk konfirmasi. §8(Esc = batal)");
    }

    // ------------------------------------------------------------------ event: klik

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s == null) return;
        Inventory top = e.getView().getTopInventory();

        if (top instanceof AnvilInventory ai && s.screen == Session.Screen.INPUT) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top && e.getRawSlot() == 2) {
                String t = ai.getRenameText();
                applyInput(p, s, t == null ? "" : t.trim());
            }
            return;
        }

        if (!(top.getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        int slot = e.getRawSlot();
        switch (h.screen) {
            case LOGIN -> clickLogin(p, s, slot, top);
            case REGISTER -> clickRegister(p, s, slot, top);
            case CHARACTER -> clickCharacter(p, s, slot, e.getClick(), top);
            case NATIONALITY -> clickNationality(p, s, slot);
            default -> {
            }
        }
    }

    private void clickLogin(Player p, Session s, int slot, Inventory top) {
        if (in(L_PASS, slot)) {
            click(p);
            later(() -> openInput(p, s, Session.Field.PASSWORD, "Masukkan password", Session.Screen.LOGIN));
        } else if (in(L_CHECK, slot)) {
            click(p);
            s.saveLogin = !s.saveLogin;
            fillLogin(top, s);
        } else if (in(L_CANCEL, slot)) {
            plugin.kick(p, "§cLogin dibatalkan.");
        } else if (in(L_CONFIRM, slot)) {
            doLogin(p, s, top);
        }
    }

    private void doLogin(Player p, Session s, Inventory top) {
        Account a = plugin.accounts().get(p.getUniqueId());
        if (a == null) {
            later(() -> openScreen(p, s, Session.Screen.REGISTER));
            return;
        }
        if (s.password.isEmpty()) {
            error(p, "Isi password dulu.");
            return;
        }
        if (plugin.accounts().verify(a, s.password)) {
            a.savedIp = s.saveLogin ? LockListener.ipOf(p) : null;
            plugin.accounts().save();
            s.password = "";
            later(() -> {
                if (!p.isOnline() || plugin.sessions.get(p.getUniqueId()) != s) return;
                if (!a.hasCharacter) {
                    s.verified = true;
                    plugin.success(p, "§aLogin berhasil! Sekarang buat character kamu.");
                    openScreen(p, s, Session.Screen.CHARACTER);
                } else {
                    plugin.complete(p, a);
                    plugin.success(p, "§aLogin berhasil. Selamat datang, §f" + a.fullName() + "§a!");
                }
            });
        } else {
            s.attempts++;
            s.password = "";
            int max = plugin.getConfig().getInt("login.max-attempts", 5);
            if (s.attempts >= max) {
                plugin.kick(p, "§cTerlalu banyak password salah.");
                return;
            }
            fillLogin(top, s);
            error(p, "Password salah (" + s.attempts + "/" + max + ").");
        }
    }

    private void clickRegister(Player p, Session s, int slot, Inventory top) {
        if (in(R_PASS, slot)) {
            click(p);
            later(() -> openInput(p, s, Session.Field.PASSWORD, "Masukkan password", Session.Screen.REGISTER));
        } else if (in(R_CONF, slot)) {
            click(p);
            later(() -> openInput(p, s, Session.Field.CONFIRM, "Ulangi password", Session.Screen.REGISTER));
        } else if (in(R_CANCEL, slot)) {
            plugin.kick(p, "§cRegister dibatalkan.");
        } else if (in(R_CONFIRM, slot)) {
            doRegister(p, s);
        }
    }

    private void doRegister(Player p, Session s) {
        int min = minPass();
        if (s.password.length() < min) {
            error(p, "Password minimal " + min + " karakter.");
            return;
        }
        if (plugin.getConfig().getBoolean("password.require-letter-and-digit", false)) {
            boolean letter = false, digit = false;
            for (char c : s.password.toCharArray()) {
                if (Character.isLetter(c)) letter = true;
                if (Character.isDigit(c)) digit = true;
            }
            if (!letter || !digit) {
                error(p, "Password harus kombinasi huruf dan angka.");
                return;
            }
        }
        if (!s.password.equals(s.confirm)) {
            error(p, "Konfirmasi password tidak sama.");
            return;
        }
        if (plugin.accounts().get(p.getUniqueId()) != null) {
            later(() -> openScreen(p, s, Session.Screen.LOGIN));
            return;
        }
        plugin.accounts().register(p.getUniqueId(), p.getName(), s.password);
        s.password = "";
        s.confirm = "";
        s.verified = true;
        later(() -> {
            if (!p.isOnline() || plugin.sessions.get(p.getUniqueId()) != s) return;
            plugin.success(p, "§aAkun berhasil dibuat! Sekarang buat character kamu.");
            openScreen(p, s, Session.Screen.CHARACTER);
        });
    }

    private void clickCharacter(Player p, Session s, int slot, ClickType ct, Inventory top) {
        if (in(C_NAME, slot)) {
            click(p);
            later(() -> openInput(p, s, Session.Field.NAME, "Name (Ingame Name)", Session.Screen.CHARACTER));
        } else if (in(C_LAST, slot)) {
            click(p);
            later(() -> openInput(p, s, Session.Field.LAST, "Last Name", Session.Screen.CHARACTER));
        } else if (in(C_MALE, slot)) {
            click(p);
            s.gender = "Male";
            fillCharacter(top, s);
        } else if (in(C_FEMALE, slot)) {
            click(p);
            s.gender = "Female";
            fillCharacter(top, s);
        } else if (in(C_DAY, slot)) {
            click(p);
            adjust(s, 0, ct);
            fillCharacter(top, s);
        } else if (in(C_MONTH, slot)) {
            click(p);
            adjust(s, 1, ct);
            fillCharacter(top, s);
        } else if (in(C_YEAR, slot)) {
            click(p);
            adjust(s, 2, ct);
            fillCharacter(top, s);
        } else if (in(C_NATION, slot)) {
            click(p);
            later(() -> openScreen(p, s, Session.Screen.NATIONALITY));
        } else if (in(C_CANCEL, slot)) {
            plugin.kick(p, "§cPembuatan character dibatalkan. Join lagi untuk melanjutkan.");
        } else if (in(C_CONFIRM, slot)) {
            doCreateCharacter(p, s);
        }
    }

    private static int wrap(int v, int min, int max) {
        int r = max - min + 1;
        return ((v - min) % r + r) % r + min;
    }

    private void adjust(Session s, int which, ClickType ct) {
        int dir = ct.isRightClick() ? -1 : 1;
        boolean big = ct.isShiftClick();
        switch (which) {
            case 0 -> {
                int max = YearMonth.of(s.year, s.month).lengthOfMonth();
                s.day = wrap(s.day + dir * (big ? 5 : 1), 1, max);
            }
            case 1 -> {
                s.month = wrap(s.month + dir * (big ? 3 : 1), 1, 12);
                s.day = Math.min(s.day, YearMonth.of(s.year, s.month).lengthOfMonth());
            }
            default -> {
                s.year = wrap(s.year + dir * (big ? 10 : 1), yearMin(), yearMax());
                s.day = Math.min(s.day, YearMonth.of(s.year, s.month).lengthOfMonth());
            }
        }
    }

    private void doCreateCharacter(Player p, Session s) {
        if (s.name.isEmpty()) {
            error(p, "Name belum diisi.");
            return;
        }
        if (s.last.isEmpty()) {
            error(p, "Last Name belum diisi.");
            return;
        }
        if (s.gender == null) {
            error(p, "Pilih gender dulu.");
            return;
        }
        if (s.nationality == null) {
            error(p, "Pilih nationality dulu.");
            return;
        }
        LocalDate dob;
        try {
            dob = LocalDate.of(s.year, s.month, s.day);
        } catch (Exception ex) {
            error(p, "Tanggal lahir tidak valid.");
            return;
        }
        if (dob.isAfter(LocalDate.now())) {
            error(p, "Tanggal lahir tidak valid.");
            return;
        }
        Account a = plugin.accounts().get(p.getUniqueId());
        if (a == null) {
            later(() -> openScreen(p, s, Session.Screen.REGISTER));
            return;
        }
        a.name = s.name;
        a.lastName = s.last;
        a.gender = s.gender;
        a.nationality = s.nationality;
        a.day = s.day;
        a.month = s.month;
        a.year = s.year;
        a.hasCharacter = true;
        plugin.accounts().save();

        later(() -> {
            if (!p.isOnline()) return;
            plugin.complete(p, a);
            plugin.idCards().fetchFaceAsync(p, a);
            plugin.success(p, "§aCharacter dibuat! Selamat datang, §f" + a.fullName() + "§a. KTP kamu ada di inventory.");
        });
    }

    private void clickNationality(Player p, Session s, int slot) {
        if (slot == N_BACK) {
            click(p);
            later(() -> openScreen(p, s, Session.Screen.CHARACTER));
            return;
        }
        int[] slots = nationSlots();
        List<String> list = nationalities();
        for (int i = 0; i < slots.length && i < list.size(); i++) {
            if (slots[i] == slot) {
                s.nationality = list.get(i);
                click(p);
                later(() -> openScreen(p, s, Session.Screen.CHARACTER));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ input anvil

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s == null || s.screen != Session.Screen.INPUT) return;
        e.setResult(item(Material.LIME_DYE, "§a✔ Klik untuk konfirmasi", true, 1,
                "§7Teks yang kamu ketik akan dipakai."));
        e.getInventory().setRepairCost(1);
    }

    private String cleanName(String raw) {
        String regex = plugin.getConfig().getString("character.name-regex", "[A-Za-z]{2,16}");
        String t = raw.trim().replaceAll("\\s+", " ");
        if (!t.matches(regex)) return null;
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : t.toCharArray()) {
            b.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c));
            up = (c == ' ');
        }
        return b.toString();
    }

    private void applyInput(Player p, Session s, String text) {
        Session.Field f = s.inputField;
        if (f == null) return;
        switch (f) {
            case PASSWORD, CONFIRM -> {
                if (text.isEmpty()) {
                    error(p, "Teks masih kosong, ketik dulu.");
                    return;
                }
                if (text.length() > 32) {
                    error(p, "Maksimal 32 karakter.");
                    return;
                }
                if (f == Session.Field.PASSWORD) s.password = text;
                else s.confirm = text;
            }
            case NAME, LAST -> {
                String v = cleanName(text);
                if (v == null) {
                    error(p, "Hanya huruf A-Z (2-16 karakter).");
                    return;
                }
                if (f == Session.Field.NAME) s.name = v;
                else s.last = v;
            }
        }
        click(p);
        Session.Screen back = s.returnTo;
        later(() -> {
            if (p.isOnline() && plugin.sessions.get(p.getUniqueId()) == s) openScreen(p, s, back);
        });
    }

    // ------------------------------------------------------------------ close = buka lagi

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = plugin.sessions.get(p.getUniqueId());
        if (s == null) return;

        // item di anvil jangan sampai balik ke inventory player
        if (e.getInventory() instanceof AnvilInventory && s.screen == Session.Screen.INPUT) {
            e.getInventory().clear();
        }
        if (s.opening || s.ending) return;

        final Session.Screen target = switch (s.screen) {
            case INPUT -> s.returnTo;
            case NATIONALITY -> Session.Screen.CHARACTER;
            default -> s.screen;
        };
        if (target == Session.Screen.NONE) return;

        later(() -> {
            if (!p.isOnline() || s.ending || plugin.sessions.get(p.getUniqueId()) != s) return;
            if (s.packWait) return;
            openScreen(p, s, target);
        });
    }
}
