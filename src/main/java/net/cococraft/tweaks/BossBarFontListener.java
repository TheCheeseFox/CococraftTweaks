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
 
import java.util.Map;
 
/**
 * Reescribe las bossbars vanilla (Ender Dragon, Wither, Raid) para mostrar:
 *   [icono]  [NOMBRE custom en font minecraftten:ten]
 *
 * El icono usa el font default (donde viven los glifos \uEBB1/2/3 de tu pack)
 * y el nombre usa minecraftten:ten. Se reconstruye el titulo entero, asi que
 * el lang de estas claves ya no importa (el plugin lo reemplaza).
 *
 * Registrar en onEnable:  BossBarFontListener.register(this);
 */
public final class BossBarFontListener {
 
    // Font del TEXTO (tu font anadido)
    private static final Key FONT = Key.key("minecraftten", "ten");
    // Font del ICONO (donde estan mapeados \uEBB1/2/3 = el default de tu pack)
    private static final Key ICON_FONT = Key.key("minecraft", "default");
 
    // ======================================================================
    //  EDITA AQUI los nombres y/o iconos de cada bossbar:
    //    title("<glifo del icono>", "<NOMBRE que quieras mostrar>")
    // ======================================================================
    private static final Map<String, Component> REPLACEMENTS = Map.of(
            "entity.minecraft.ender_dragon",     title("\uEBB1", "ENDERDRAGON"),
            "entity.minecraft.wither",           title("\uEBB2", "WITHER"),
            "event.minecraft.raid",              title("\uEBB3", "INVASION"),
            "event.minecraft.raid.defeat.full",  title("\uEBB3", "INVASION - DERROTA"),
            "event.minecraft.raid.victory.full", title("\uEBB3", "INVASION - VICTORIA")
    );
 
    /** Construye: icono (font default) + espacio + nombre (font minecraftten:ten). */
    private static Component title(String iconGlyph, String name) {
        return Component.text(iconGlyph).font(ICON_FONT)
                .append(Component.text(" " + name).font(FONT));
    }
 
    private BossBarFontListener() {}
 
    public static void register(Plugin plugin) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.BOSS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                var structures = event.getPacket().getStructures();
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
                    if (json == null || json.isEmpty()) continue;
 
                    final Component title;
                    try {
                        title = GsonComponentSerializer.gson().deserialize(json);
                    } catch (Exception ex) {
                        continue;
                    }
 
                    String key = findKey(title);
                    if (key == null) continue;
 
                    Component replacement = REPLACEMENTS.get(key);
                    if (replacement == null) continue;
 
                    wrapped.setJson(GsonComponentSerializer.gson().serialize(replacement));
                    comps.write(0, wrapped);
                    structures.write(i, op);
                }
            }
        });
    }
 
    /** Devuelve la clave de traduccion (dragon/wither/raid) si el titulo la contiene. */
    private static String findKey(Component c) {
        if (c instanceof TranslatableComponent tc && REPLACEMENTS.containsKey(tc.key())) {
            return tc.key();
        }
        for (Component child : c.children()) {
            String k = findKey(child);
            if (k != null) return k;
        }
        return null;
    }
}
