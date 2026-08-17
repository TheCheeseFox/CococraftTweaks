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
 * ProtocolLib. No toca bossbars custom (de plugins) ni el resto del texto.
 *
 * Registrar en onEnable de tu plugin:
 *     BossBarFontListener.register(this);
 */
public final class BossBarFontListener {
 
    // El font añadido de tu resource pack (assets/minecraftten/font/ten.json)
    private static final Key FONT = Key.key("minecraftten", "ten");
 
    // Claves de traducción de los títulos vanilla a los que SÍ aplicamos el font
    private static final Set<String> TARGET_KEYS = Set.of(
            "entity.minecraft.ender_dragon",  // Ender Dragon
            "entity.minecraft.wither",        // Wither
            "event.minecraft.raid"            // Raid / Incursión
    );
 
    private BossBarFontListener() {}
 
    public static void register(Plugin plugin) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.BOSS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                // Solo las acciones ADD y UPDATE_NAME llevan título; el resto
                // (progress, style, remove) no tiene componente -> size 0.
                var components = event.getPacket().getChatComponents();
                if (components.size() == 0) return;
 
                WrappedChatComponent wrapped = components.readSafely(0);
                if (wrapped == null) return;
 
                String json = wrapped.getJson();
                if (json == null || json.isEmpty()) return;
 
                final Component title;
                try {
                    title = GsonComponentSerializer.gson().deserialize(json);
                } catch (Exception ex) {
                    return; // json no parseable -> lo dejamos pasar sin tocar
                }
 
                if (!isTargetTitle(title)) return;
 
                // Aplica el font a todo el título (se hereda al texto traducido)
                Component fonted = title.font(FONT);
                wrapped.setJson(GsonComponentSerializer.gson().serialize(fonted));
                components.write(0, wrapped);
                // No cancelamos: el paquete modificado se reenvía tal cual.
            }
        });
    }
 
    /** true si el título (o alguno de sus hijos) es dragon/wither/raid. */
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
 
