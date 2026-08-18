package net.cococraft.tweaks;
 
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.plugin.Plugin;
 
import java.util.Set;
 
/**
 * Inyecta el font custom "minecraftten:ten" en las bossbars vanilla del
 * Ender Dragon, el Wither y las Raids, interceptando el paquete BOSS con
 * ProtocolLib. Version con logs de DEBUG para diagnosticar.
 *
 * Registrar en onEnable:  BossBarFontListener.register(this);
 */
public final class BossBarFontListener {
 
    // El font anadido de tu resource pack (assets/minecraftten/font/ten.json)
    private static final Key FONT = Key.key("minecraftten", "ten");
 
    // Claves de traduccion de los titulos vanilla a los que aplicamos el font
    private static final Set<String> TARGET_KEYS = Set.of(
            "entity.minecraft.ender_dragon",  // Ender Dragon
            "entity.minecraft.wither",        // Wither
            "event.minecraft.raid"            // Raid / Incursion
    );
 
    private BossBarFontListener() {}
 
    public static void register(Plugin plugin) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.BOSS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                var components = event.getPacket().getChatComponents();
                plugin.getLogger().info("[BOSSBAR] paquete BOSS. chatComponents size=" + components.size());
 
                if (components.size() == 0) return;
 
                WrappedChatComponent wrapped = components.readSafely(0);
                if (wrapped == null) {
                    plugin.getLogger().info("[BOSSBAR] wrapped null");
                    return;
                }
 
                String json = wrapped.getJson();
                plugin.getLogger().info("[BOSSBAR] json titulo = " + json);
                if (json == null || json.isEmpty()) return;
 
                final Component title;
                try {
                    title = GsonComponentSerializer.gson().deserialize(json);
                } catch (Exception ex) {
                    plugin.getLogger().info("[BOSSBAR] json no parseable");
                    return;
                }
 
                boolean match = isTargetTitle(title);
                plugin.getLogger().info("[BOSSBAR] coincide dragon/wither/raid? " + match);
                if (!match) return;
 
                Component fonted = title.font(FONT);
                wrapped.setJson(GsonComponentSerializer.gson().serialize(fonted));
                components.write(0, wrapped);
                plugin.getLogger().info("[BOSSBAR] font aplicado.");
            }
        });
    }
 
    /** true si el titulo (o alguno de sus hijos) es dragon/wither/raid. */
    private static boolean isTargetTitle(Component c) {
        if (c instanceof TranslatableComponent tc && TARGET_KEYS.contains(tc.key())) {
            return true;
        }
        for (Component child : c.children()) {
            if (isTargetTitle(child)) return true;
        }
        return false;
    }
}
