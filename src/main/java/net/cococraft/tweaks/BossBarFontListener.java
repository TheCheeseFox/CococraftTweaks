package net.cococraft.tweaks;
 
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
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
 * Ender Dragon, el Wither y las Raids.
 *
 * En 26.2 el titulo va ANIDADO dentro del objeto "operation" del paquete BOSS,
 * asi que se accede con getStructures() (no con getChatComponents()).
 * Version con logs de DEBUG.
 *
 * Registrar en onEnable:  BossBarFontListener.register(this);
 */
public final class BossBarFontListener {
 
    private static final Key FONT = Key.key("minecraftten", "ten");
 
    private static final Set<String> TARGET_KEYS = Set.of(
            "entity.minecraft.ender_dragon",
            "entity.minecraft.wither",
            "event.minecraft.raid"
    );
 
    private BossBarFontListener() {}
 
    public static void register(Plugin plugin) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.BOSS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                var structures = event.getPacket().getStructures();
                plugin.getLogger().info("[BOSSBAR] structures size=" + structures.size());
                if (structures.size() == 0) return;
 
                for (int i = 0; i < structures.size(); i++) {
                    InternalStructure op;
                    try {
                        op = structures.readSafely(i);
                    } catch (Exception e) {
                        continue;
                    }
                    if (op == null) continue;
 
                    var comps = op.getChatComponents();
                    if (comps.size() == 0) continue;
 
                    WrappedChatComponent wrapped = comps.readSafely(0);
                    if (wrapped == null) continue;
 
                    String json = wrapped.getJson();
                    plugin.getLogger().info("[BOSSBAR] json en structure " + i + " = " + json);
                    if (json == null || json.isEmpty()) continue;
 
                    final Component title;
                    try {
                        title = GsonComponentSerializer.gson().deserialize(json);
                    } catch (Exception ex) {
                        continue;
                    }
 
                    boolean match = isTargetTitle(title);
                    plugin.getLogger().info("[BOSSBAR] structure " + i + " coincide? " + match);
                    if (!match) continue;
 
                    Component fonted = title.font(FONT);
                    wrapped.setJson(GsonComponentSerializer.gson().serialize(fonted));
                    comps.write(0, wrapped);
                    structures.write(i, op);
                    plugin.getLogger().info("[BOSSBAR] font aplicado en structure " + i);
                }
            }
        });
    }
 
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
