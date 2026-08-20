package net.cococraft.tweaks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LISTENER TEMPORAL DE DIAGNOSTICO. No cambia nada; solo IMPRIME en consola
 * los paquetes de spawn/metadata/destroy que le llegan a un jugador justo
 * despues de golpear algo, para identificar por cual paquete viaja el
 * holograma de numeros de daño de CMI (ShowDamageNumbers).
 *
 * Registrar en onEnable (temporalmente):
 *   DamageNumberDebugListener.register(this);
 *
 * Golpea a un mob o jugador y mira la consola por lineas [DMG-DEBUG].
 * Luego quita este listener y el registro en el plugin principal.
 */
public final class DamageNumberDebugListener implements Listener {

    private static JavaPlugin plugin;
    private static final Set<UUID> recentlyHit = ConcurrentHashMap.newKeySet();

    private DamageNumberDebugListener() {}

    public static void register(JavaPlugin pl) {
        plugin = pl;
        pl.getServer().getPluginManager().registerEvents(new DamageNumberDebugListener(), pl);

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketAdapter adapter = new PacketAdapter(pl, ListenerPriority.NORMAL,
                PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.ENTITY_METADATA,
                PacketType.Play.Server.ENTITY_DESTROY) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player receiver = event.getPlayer();
                if (receiver == null || !recentlyHit.contains(receiver.getUniqueId())) return;
                log(receiver.getName() + " <- " + event.getPacketType()
                        + "  " + event.getPacket().getModifier().getValues());
            }
        };
        pm.addPacketListener(adapter);
        pl.getLogger().warning("[DMG-DEBUG] Listener de diagnostico de damage numbers ACTIVO (quitar en produccion).");
    }

    private static void log(String s) {
        plugin.getLogger().warning("[DMG-DEBUG] " + s);
    }

    // Marca al golpeador como "recien golpeo" por medio segundo, ventana en la
    // que se imprime todo lo que se le manda por paquete.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        recentlyHit.add(id);
        log("=== " + p.getName() + " golpeo a " + e.getEntity().getType()
                + " (entityId=" + e.getEntity().getEntityId() + ", damage=" + e.getFinalDamage() + ") ===");
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentlyHit.remove(id), 10L);
    }
}
