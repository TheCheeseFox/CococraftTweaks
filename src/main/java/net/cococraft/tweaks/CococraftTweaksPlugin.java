package net.cococraft.tweaks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.Bukkit;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CococraftTweaksPlugin extends JavaPlugin implements Listener {

    private String chestSingle, chestDouble, enderChest, advCommand;
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
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // Fondo de cofres: reescribe el titulo del contenedor al abrir
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

        // Comando al abrir la pantalla de avances vanilla
        pm.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                PacketType.Play.Client.ADVANCEMENTS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!advEnabled || advCommand == null || advCommand.isEmpty()) return;
                Player p = event.getPlayer();
                if (p == null) return;
                String action = readEnum(event);
                UUID id = p.getUniqueId();
                if ("CLOSED_SCREEN".equals(action)) {
                    advScreenOpen.remove(id);
                } else if ("OPENED_TAB".equals(action)) {
                    if (advScreenOpen.add(id)) {
                        Bukkit.getScheduler().runTask(CococraftTweaksPlugin.this, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                        advCommand.replace("%player%", p.getName())));
                    }
                }
            }
        });

        getLogger().info("CococraftTweaks activo.");
    }

    private void loadCfg() {
        reloadConfig();
        chestSingle = getConfig().getString("chest-backgrounds.single", "");
        chestDouble = getConfig().getString("chest-backgrounds.double", "");
        enderChest  = getConfig().getString("chest-backgrounds.ender", "");
        advEnabled  = getConfig().getBoolean("advancements.enabled", true);
        advCommand  = getConfig().getString("advancements.command", "");
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
        if (type == InventoryType.ENDER_CHEST) return enderChest;
        if (type == InventoryType.CHEST) {
            InventoryHolder holder = inv.getHolder();
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
