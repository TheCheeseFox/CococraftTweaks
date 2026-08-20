package net.cococraft.tweaks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recolorea el holograma de numeros de daño de CMI (ShowDamageNumbers) cuando
 * el golpe fue critico.
 *
 * CMI los manda como una entidad fantasma "text_display": primero un
 * SPAWN_ENTITY, seguido de un ENTITY_METADATA cuyo indice 23 trae el texto
 * (confirmado con el listener de debug: id=22 glow, 23=texto, 24=line_width,
 * 25=background, 26=opacity, 27=flags).
 *
 * Deteccion de critico: EntityDamageByEntityEvent#isCritical(), el propio
 * calculo interno de vanilla (incluye el attack cooldown, no es una
 * aproximacion nuestra). Es exacto: es la misma condicion que decide si el
 * cliente dibuja la particula CRIT.
 *
 * Registrar en onEnable:  DamageCriticalColorListener.register(this);
 */
public final class DamageCriticalColorListener implements Listener {

    private static final int TEXT_DATA_INDEX = 23;
    // Color para golpes criticos. Ajusta el hex a gusto.
    private static final TextColor CRIT_COLOR = TextColor.fromHexString("#FFD700"); // dorado

    private final Plugin plugin;
    // Jugador que acaba de dar un golpe critico, pendiente de que le llegue su text_display.
    private final Set<UUID> pendingCrit = ConcurrentHashMap.newKeySet();
    // entityId de un text_display que sabemos que corresponde a un golpe critico.
    private final Set<Integer> critEntityIds = ConcurrentHashMap.newKeySet();

    private DamageCriticalColorListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void register(Plugin plugin) {
        DamageCriticalColorListener instance = new DamageCriticalColorListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // 1) Cuando spawnee un text_display justo despues de un golpe critico nuestro,
        //    recordamos su entityId para interceptar su metadata a continuacion.
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SPAWN_ENTITY) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player receiver = event.getPlayer();
                if (receiver == null) return;
                if (!instance.pendingCrit.contains(receiver.getUniqueId())) return;

                // Ojo: no consumir el flag todavia. En combate llegan otros
                // SPAWN_ENTITY (orbes de xp, flechas, etc.) antes que el
                // text_display del numero de daño - si consumimos el flag con
                // el primer SPAWN_ENTITY que sea, se pierde con la entidad
                // equivocada y el text_display real nunca queda marcado.
                Object entityTypeField = event.getPacket().getModifier().read(2);
                if (entityTypeField == null || !entityTypeField.toString().contains("text_display")) return;

                // Recien aqui, confirmado que es el text_display, se consume.
                instance.pendingCrit.remove(receiver.getUniqueId());

                Integer entityId = event.getPacket().getIntegers().readSafely(0);
                if (entityId != null) instance.critEntityIds.add(entityId);
            }
        });

        // 2) Cuando llegue la metadata de ese text_display, recoloreamos su texto.
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Integer entityId = event.getPacket().getIntegers().readSafely(0);
                if (entityId == null || !instance.critEntityIds.remove(entityId)) return;
                instance.recolor(event);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.isCritical()) {
            pendingCrit.add(p.getUniqueId());
        }
    }

    @SuppressWarnings("unchecked")
    private void recolor(PacketEvent event) {
        List<WrappedDataValue> values = (List<WrappedDataValue>)
                (List<?>) event.getPacket().getDataValueCollectionModifier().read(0);
        if (values == null) return;

        for (int i = 0; i < values.size(); i++) {
            WrappedDataValue dv = values.get(i);
            if (dv.getIndex() != TEXT_DATA_INDEX) continue;

            WrappedChatComponent wrapped = WrappedChatComponent.fromHandle(dv.getValue());
            String json = wrapped.getJson();
            if (json == null || json.isEmpty()) return;

            final Component recolored;
            try {
                Component original = GsonComponentSerializer.gson().deserialize(json);
                recolored = original.color(CRIT_COLOR);
            } catch (Exception ex) {
                plugin.getLogger().warning("[CritColor] No se pudo recolorear: " + ex.getMessage());
                return;
            }

            WrappedChatComponent newWrapped = WrappedChatComponent.fromJson(
                    GsonComponentSerializer.gson().serialize(recolored));

            values.set(i, new WrappedDataValue(dv.getIndex(), dv.getSerializer(), newWrapped.getHandle()));
            event.getPacket().getDataValueCollectionModifier().write(0, values);
            plugin.getLogger().info("[CritColor] Numero recoloreado OK.");
            return;
        }
    }
}
