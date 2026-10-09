package kazet.loginplus;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KTP = item FILLED_MAP dengan renderer custom.
 * Pegang di tangan (tangan kiri kosong) -> Minecraft otomatis menampilkan map besar di tengah layar.
 * Foto = kepala skin player. KTP tidak bisa di-drop, tidak hilang saat mati, tidak bisa masuk chest.
 */
public class IdCardManager implements Listener {

    private final LoginPlus plugin;
    private final NamespacedKey key;
    private final File faceDir;
    private final Map<UUID, Long> showCooldown = new HashMap<>();

    public IdCardManager(LoginPlus plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "idcard-owner");
        this.faceDir = new File(plugin.getDataFolder(), "faces");
        if (!faceDir.exists()) faceDir.mkdirs();
    }

    // ------------------------------------------------------------------ item KTP

    public boolean isCard(ItemStack it) {
        if (it == null || it.getType() != Material.FILLED_MAP || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public UUID ownerOf(ItemStack it) {
        if (!isCard(it)) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String cardTitle() {
        return plugin.getConfig().getString("id-card.title", "INDONESIA IDENTITY");
    }

    public ItemStack createCard(Account a) {
        MapView view = null;
        if (a.mapId >= 0) view = Bukkit.getMap(a.mapId);
        if (view == null) {
            view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            a.mapId = view.getId();
            plugin.accounts().save();
        }
        setupView(view, a.uuid);

        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta mm = (MapMeta) it.getItemMeta();
        mm.setMapView(view);
        mm.displayName(LoginPlus.txt("§e§lKTP §7- §f" + a.fullName()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(LoginPlus.txt("§8" + cardTitle()));
        lore.add(LoginPlus.txt("§7Name: §f" + a.name));
        lore.add(LoginPlus.txt("§7Last Name: §f" + a.lastName));
        lore.add(LoginPlus.txt("§7Gender: §f" + a.gender));
        lore.add(LoginPlus.txt("§7Tgl Lahir: §f" + a.dobString()));
        lore.add(LoginPlus.txt("§7Nationality: §f" + a.nationality));
        lore.add(LoginPlus.txt(""));
        lore.add(LoginPlus.txt("§8Pegang di tangan (tangan kiri kosong)"));
        lore.add(LoginPlus.txt("§8agar KTP tampil besar di layar."));
        lore.add(LoginPlus.txt("§8Klik kanan player lain = tunjukkan KTP."));
        mm.lore(lore);
        mm.getPersistentDataContainer().set(key, PersistentDataType.STRING, a.uuid.toString());
        it.setItemMeta(mm);
        return it;
    }

    private void setupView(MapView view, UUID owner) {
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new CardRenderer(plugin, owner, loadFace(owner), cardTitle()));
    }

    /** Pastikan player punya tepat 1 KTP miliknya. */
    public void ensureCard(Player p) {
        Account a = plugin.accounts().get(p.getUniqueId());
        if (a == null || !a.hasCharacter) return;
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isCard(it) && a.uuid.equals(ownerOf(it))) {
                if (found) inv.setItem(i, null);
                else found = true;
            }
        }
        if (!found) {
            Map<Integer, ItemStack> left = inv.addItem(createCard(a));
            if (!left.isEmpty()) {
                p.sendMessage(LoginPlus.txt("§eInventory penuh! KTP kamu akan diberikan otomatis saat ada slot kosong."));
            }
        }
    }

    // ------------------------------------------------------------------ foto kepala

    public void fetchFaceAsync(Player p, Account a) {
        final UUID id = a.uuid;
        String skinUrl = null;
        try {
            PlayerProfile profile = p.getPlayerProfile();
            for (ProfileProperty prop : profile.getProperties()) {
                if (!"textures".equals(prop.getName())) continue;
                String json = new String(Base64.getDecoder().decode(prop.getValue()), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                if (m.find()) skinUrl = m.group(1);
            }
        } catch (Exception ex) {
            // ignore, pakai foto default
        }
        if (skinUrl == null) return;
        final String url = skinUrl;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL u = new URL(url);
                String host = u.getHost();
                if (!host.equals("textures.minecraft.net")) return;
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                BufferedImage skin = ImageIO.read(c.getInputStream());
                if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 16) return;
                BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        int base = skin.getRGB(8 + x, 8 + y);
                        int hat = skin.getRGB(40 + x, 8 + y);
                        face.setRGB(x, y, ((hat >>> 24) & 0xFF) > 20 ? hat : (base | 0xFF000000));
                    }
                }
                ImageIO.write(face, "png", new File(faceDir, id + ".png"));
                Bukkit.getScheduler().runTask(plugin, () -> refresh(id));
            } catch (Exception ex) {
                plugin.getLogger().warning("Gagal ambil skin untuk KTP: " + ex.getMessage());
            }
        });
    }

    private BufferedImage loadFace(UUID id) {
        File f = new File(faceDir, id + ".png");
        if (!f.exists()) return null;
        try {
            return ImageIO.read(f);
        } catch (Exception ex) {
            return null;
        }
    }

    public void refresh(UUID id) {
        Account a = plugin.accounts().get(id);
        if (a == null || a.mapId < 0) return;
        MapView view = Bukkit.getMap(a.mapId);
        if (view != null) setupView(view, id);
    }

    @EventHandler
    public void onMapInit(MapInitializeEvent e) {
        Account a = plugin.accounts().byMapId(e.getMap().getId());
        if (a != null) setupView(e.getMap(), a.uuid);
    }

    // ------------------------------------------------------------------ KTP tidak bisa hilang

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isCard(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(LoginPlus.txt("§cKTP tidak bisa dibuang."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Iterator<ItemStack> it = e.getDrops().iterator();
        List<ItemStack> cards = new ArrayList<>();
        while (it.hasNext()) {
            ItemStack st = it.next();
            if (isCard(st)) {
                cards.add(st);
                it.remove();
            }
        }
        if (!e.getKeepInventory()) {
            for (ItemStack c : cards) e.getItemsToKeep().add(c);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !plugin.isLocked(p)) ensureCard(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        boolean ownCraftView = top.getType() == InventoryType.CRAFTING;
        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean clickedTop = e.getClickedInventory() == top;
        boolean cancel = false;

        // taruh KTP ke slot atas (chest, crafting grid, dll)
        if (isCard(cursor) && clickedTop) cancel = true;
        // shift-click KTP dari inventory ke container
        if (e.isShiftClick() && !clickedTop && isCard(cur) && !ownCraftView) cancel = true;
        // tukar via angka / tombol F
        if (clickedTop && e.getClick() == ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
            int b = e.getHotbarButton();
            if (b >= 0 && isCard(p.getInventory().getItem(b))) cancel = true;
        }
        if (clickedTop && e.getClick() == ClickType.SWAP_OFFHAND && e.getWhoClicked() instanceof Player p2) {
            if (isCard(p2.getInventory().getItemInOffHand())) cancel = true;
        }
        // buang lewat klik di luar window / tombol Q
        if (isCard(cursor) && e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE) cancel = true;
        if ((e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) && isCard(cur)) cancel = true;
        if (cancel) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isCard(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ interaksi KTP

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (plugin.isLocked(p)) return;
        if (!isCard(p.getInventory().getItemInMainHand())) return;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && off.getType() != Material.AIR) {
            p.sendActionBar(LoginPlus.txt("§eKosongkan tangan kiri agar KTP tampil besar di layar."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = e.getHand() == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (!isCard(hand)) return;

        if (e.getRightClicked() instanceof ItemFrame) {
            e.setCancelled(true);
            return;
        }
        if (e.getRightClicked() instanceof Player target && e.getHand() == EquipmentSlot.HAND && !plugin.isLocked(p)) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = showCooldown.get(p.getUniqueId());
            if (last != null && now - last < 3000L) return;
            showCooldown.put(p.getUniqueId(), now);
            UUID owner = ownerOf(hand);
            Account a = owner == null ? null : plugin.accounts().get(owner);
            if (a == null) return;
            String[] lines = {
                    "§8§m                              ",
                    "§e§l" + cardTitle(),
                    "§7Name: §f" + a.name,
                    "§7Last Name: §f" + a.lastName,
                    "§7Gender: §f" + a.gender,
                    "§7Tgl Lahir: §f" + a.dobString(),
                    "§7Nationality: §f" + a.nationality,
                    "§8§m                              "
            };
            target.sendMessage(LoginPlus.txt("§e" + a.fullName() + " §7menunjukkan KTP kepadamu:"));
            for (String l : lines) target.sendMessage(LoginPlus.txt(l));
            p.sendMessage(LoginPlus.txt("§7Kamu menunjukkan KTP ke §f" + target.getName() + "§7."));
        }
    }

    // ------------------------------------------------------------------ renderer map

    static class CardRenderer extends MapRenderer {
        private final LoginPlus plugin;
        private final UUID owner;
        private final BufferedImage face;
        private final String title;
        private boolean done;

        CardRenderer(LoginPlus plugin, UUID owner, BufferedImage face, String title) {
            super(false);
            this.plugin = plugin;
            this.owner = owner;
            this.face = face;
            this.title = title;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (done) return;
            Account a = plugin.accounts().get(owner);
            if (a == null) return;
            done = true;
            draw(canvas, a);
        }

        private static String col(Color c) {
            return "§" + MapPalette.matchColor(c) + ";";
        }

        private static void fill(MapCanvas c, int x, int y, int w, int h, Color color) {
            byte b = MapPalette.matchColor(color);
            for (int i = x; i < x + w; i++) {
                for (int j = y; j < y + h; j++) {
                    if (i >= 0 && i < 128 && j >= 0 && j < 128) c.setPixel(i, j, b);
                }
            }
        }

        private static String ascii(String s) {
            StringBuilder b = new StringBuilder();
            for (char ch : s.toUpperCase().toCharArray()) {
                b.append(MinecraftFont.Font.isValid(String.valueOf(ch)) ? ch : '?');
            }
            return b.toString();
        }

        private static String fit(String s, int maxWidth) {
            String t = ascii(s);
            while (t.length() > 1 && MinecraftFont.Font.getWidth(t) > maxWidth) {
                t = t.substring(0, t.length() - 1);
            }
            return t;
        }

        private static void text(MapCanvas c, int x, int y, String s, Color color, int maxWidth) {
            c.drawText(x, y, MinecraftFont.Font, col(color) + fit(s, maxWidth));
        }

        private void draw(MapCanvas c, Account a) {
            Color bg = new Color(176, 214, 242);
            Color border = new Color(40, 70, 130);
            Color head = new Color(30, 60, 120);
            Color white = new Color(255, 255, 255);
            Color label = new Color(70, 95, 140);
            Color value = new Color(15, 25, 55);

            fill(c, 0, 0, 128, 128, border);
            fill(c, 2, 2, 124, 124, bg);
            fill(c, 2, 2, 124, 17, head);

            String t = fit(title, 116);
            int tw = MinecraftFont.Font.getWidth(t);
            c.drawText(Math.max(4, (128 - tw) / 2), 7, MinecraftFont.Font, col(white) + t);

            // foto (kepala player)
            fill(c, 5, 23, 42, 42, new Color(20, 30, 50));
            for (int fx = 0; fx < 8; fx++) {
                for (int fy = 0; fy < 8; fy++) {
                    Color px = pixel(fx, fy);
                    fill(c, 6 + fx * 5, 24 + fy * 5, 5, 5, px);
                }
            }

            text(c, 52, 23, "FIRST NAME", label, 72);
            text(c, 52, 31, a.name, value, 72);
            text(c, 52, 43, "LAST NAME", label, 72);
            text(c, 52, 51, a.lastName, value, 72);

            text(c, 6, 70, "SEX", label, 30);
            text(c, 40, 70, a.gender, value, 82);
            text(c, 6, 82, "DOB", label, 30);
            text(c, 40, 82, a.dobString(), value, 82);
            text(c, 6, 94, "NATIONALITY", label, 116);
            text(c, 6, 103, a.nationality, value, 116);

            fill(c, 2, 118, 124, 8, head);
        }

        private Color pixel(int x, int y) {
            if (face != null && x < face.getWidth() && y < face.getHeight()) {
                return new Color(face.getRGB(x, y), false);
            }
            // foto default kalau skin tidak bisa diambil
            if (y <= 1) return new Color(70, 50, 30);
            if (y == 2 && (x == 0 || x == 7)) return new Color(70, 50, 30);
            if (y == 4 && (x == 2 || x == 5)) return new Color(60, 90, 200);
            if (y == 4 && (x == 1 || x == 6)) return new Color(240, 240, 255);
            if (y == 6 && (x == 3 || x == 4)) return new Color(140, 80, 70);
            return new Color(200, 150, 115);
        }
    }
}
