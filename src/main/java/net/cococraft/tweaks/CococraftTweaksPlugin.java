package net.cococraft.tweaks;
 
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.cococraft.tweaks.protection.FurnitureProtectionListener;
import net.cococraft.tweaks.protection.FurnitureHitDebugListener;
import org.bukkit.Bukkit;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
 
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
 
public class CococraftTweaksPlugin extends JavaPlugin implements Listener {
 
    private String chestSingle, chestDouble, enderChest, barrel;
    private String advMenu, advCommand;
    private boolean advEnabled;
 
    private final Map<UUID, String> pendingBg = new ConcurrentHashMap<>();
    private final Set<UUID> advScreenOpen = ConcurrentHashMap.newKeySet();
 
    @Override
    public void onEnable() {
        saveDefaultConfig();   // crea config.yml la primera vez
        loadCfg();
 
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib no encontrado. Deshabilitando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
 
        // --- Proteccion Towny para el furniture entity-based de Crop & Kettle ---
        // (plates, basins, cutting boards, y todo lo que lleve el tag "smithed.block")
        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            getServer().getPluginManager().registerEvents(new FurnitureProtectionListener(), this);
            getLogger().info("Proteccion Towny para furniture de Crop&Kettle activada.");
        } else {
            getLogger().warning("Towny no encontrado: proteccion de furniture desactivada.");
        }
 
        // --- DEBUG TEMPORAL: identifica que evento mata el plato. QUITAR luego. ---
        getServer().getPluginManager().registerEvents(new FurnitureHitDebugListener(this), this);
        getLogger().warning("[CNK-DEBUG] Listener de diagnostico ACTIVO (quitar en produccion).");
 
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
 
        // Fondo de contenedores vanilla: reescribe el titulo del contenedor al abrir
        pm.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                PacketType.Play.Server.OPEN_WINDOW) {
            @Override
            public void onPacketSending(PacketEvent event) {
                String glyph = pendingBg.remove(event.getPlayer().getUniqueId());
                if (glyph == null || glyph.isEmpty()) return;
                PacketContainer packet = event.getPacket();
                WrappedChatComponent title = packet.getChatComponents().read(0);
                String json = (title != null) ? title.getJson() : "{\"text\":\"\"}";
                String newJson = "{\"text\":\"" + escape(glyph) + "\",\"extra\":[" + json + "]}";
                packet.getChatComponents().write(0, WrappedChatComponent.fromJson(newJson));
            }
        });
 
        // Al ABRIR la pantalla de avances vanilla: abre el menu de DeluxeMenus directamente
        pm.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                PacketType.Play.Client.ADVANCEMENTS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!advEnabled) return;
                Player p = event.getPlayer();
                if (p == null) return;
                String action = readEnum(event);
                UUID id = p.getUniqueId();
                if ("CLOSED_SCREEN".equals(action)) {
                    advScreenOpen.remove(id);
                } else if ("OPENED_TAB".equals(action)) {
                    if (advScreenOpen.add(id)) {
                        Bukkit.getScheduler().runTask(CococraftTweaksPlugin.this, () -> openAdvMenu(p));
                    }
                }
            }
        });
     
     BossBarFontListener.register(this);
     
        getLogger().info("CococraftTweaks activo.");
    }
 
    private void loadCfg() {
        reloadConfig();
        chestSingle = getConfig().getString("chest-backgrounds.single", "");
        chestDouble = getConfig().getString("chest-backgrounds.double", "");
        enderChest  = getConfig().getString("chest-backgrounds.ender", "");
        barrel      = getConfig().getString("chest-backgrounds.barrel", "");
        advEnabled  = getConfig().getBoolean("advancements.enabled", true);
        advMenu     = getConfig().getString("advancements.menu", "main_menu");
        advCommand  = getConfig().getString("advancements.command", "");
    }
 
    // ---- Apertura del menu al pulsar L ----
    private void openAdvMenu(Player p) {
        // 1) Intento directo por la API de DeluxeMenus (mas rapido, sin pasar por el comando)
        if (advMenu != null && !advMenu.isEmpty() && openDeluxeMenu(p, advMenu)) {
            return;
        }
        // 2) Respaldo: comando (solo si esta configurado)
        if (advCommand != null && !advCommand.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    advCommand.replace("%player%", p.getName()));
        }
    }
 
    /**
     * Abre un menu de DeluxeMenus por su nombre usando reflection, para no depender
     * de la version exacta de la API. Devuelve true si se abrio correctamente.
     */
    private boolean openDeluxeMenu(Player player, String menuName) {
        if (getServer().getPluginManager().getPlugin("DeluxeMenus") == null) return false;
        try {
            Class<?> menuClass = Class.forName("com.extendedclip.deluxemenus.menu.Menu");
            Object menu = null;
 
            // getMenuByName(String) -> Optional<Menu>  (versiones nuevas)
            try {
                Method m = menuClass.getMethod("getMenuByName", String.class);
                Object result = m.invoke(null, menuName);
                menu = (result instanceof Optional) ? ((Optional<?>) result).orElse(null) : result;
            } catch (NoSuchMethodException ignored) {
                // getMenu(String) -> Menu  (versiones antiguas)
                Method m = menuClass.getMethod("getMenu", String.class);
                menu = m.invoke(null, menuName);
            }
 
            if (menu == null) {
                getLogger().warning("DeluxeMenus: el menu '" + menuName + "' no existe.");
                return false;
            }
 
            // Busca openMenu(Player) sin asumir la firma exacta
            for (Method mm : menu.getClass().getMethods()) {
                if (mm.getName().equals("openMenu")
                        && mm.getParameterCount() == 1
                        && mm.getParameterTypes()[0].isAssignableFrom(Player.class)) {
                    mm.invoke(menu, player);
                    return true;
                }
            }
            getLogger().warning("DeluxeMenus: no se encontro el metodo openMenu(Player).");
            return false;
        } catch (Throwable t) {
            getLogger().warning("No se pudo abrir el menu por la API de DeluxeMenus: " + t.getMessage());
            return false;
        }
    }
 
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cococrafttweaks")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("cococrafttweaks.admin")) {
                    sender.sendMessage("No tienes permiso.");
                    return true;
                }
                loadCfg();
                sender.sendMessage("CococraftTweaks: config recargada.");
                return true;
            }
            sender.sendMessage("Uso: /cococrafttweaks reload");
            return true;
        }
        return false;
    }
 
    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        UUID id = e.getPlayer().getUniqueId();
        String glyph = glyphFor(e.getInventory());
        if (glyph != null && !glyph.isEmpty()) pendingBg.put(id, glyph);
        else pendingBg.remove(id);
    }
 
    private String glyphFor(Inventory inv) {
        InventoryType type = inv.getType();
        InventoryHolder holder = inv.getHolder();
        if (type == InventoryType.ENDER_CHEST) return enderChest;
        if (type == InventoryType.BARREL && holder instanceof Barrel) return barrel;
        if (type == InventoryType.CHEST) {
            if (holder instanceof DoubleChest) return chestDouble;
            if (holder instanceof Chest) return chestSingle;
        }
        return null;
    }
 
    private String readEnum(PacketEvent event) {
        for (Object o : event.getPacket().getModifier().getValues()) {
            if (o != null && o.getClass().isEnum()) return ((Enum<?>) o).name();
        }
        return "";
    }
 
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
