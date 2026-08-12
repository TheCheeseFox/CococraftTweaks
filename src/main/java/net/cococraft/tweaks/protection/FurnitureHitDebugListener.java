package net.cococraft.tweaks.protection;
 
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
 
/**
 * LISTENER TEMPORAL DE DIAGNOSTICO. No protege nada; solo IMPRIME en consola
 * que eventos se disparan al golpear/interactuar con el furniture del pack,
 * para saber por que ruta muere el plato en 26.2.
 *
 * Registrar en onEnable (temporalmente):
 *   getServer().getPluginManager().registerEvents(new FurnitureHitDebugListener(this), this);
 *
 * Golpea un plato DOS veces (el primer hit y el segundo que lo rompe) y mira
 * la consola. Luego quita este listener.
 */
public final class FurnitureHitDebugListener implements Listener {
 
    private final JavaPlugin plugin;
 
    public FurnitureHitDebugListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }
 
    private static boolean isFurniture(Entity e) {
        return (e instanceof Interaction || e instanceof ItemDisplay)
                && e.getScoreboardTags().contains("smithed.block");
    }
 
    private void log(String s) {
        plugin.getLogger().warning("[CNK-DEBUG] " + s);
    }
 
    // ¿Se dispara el PrePlayerAttack sobre la interaction? ¿Viene ya cancelado?
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPre(PrePlayerAttackEntityEvent e) {
        if (!isFurniture(e.getAttacked())) return;
        log("PrePlayerAttackEntityEvent  attacked=" + e.getAttacked().getType()
                + " willAttack=" + e.willAttack()
                + " cancelled=" + e.isCancelled()
                + " by=" + e.getPlayer().getName()
                + " gm=" + e.getPlayer().getGameMode());
    }
 
    // ¿Llega el golpe como EntityDamageByEntity? (la otra ruta posible)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDmgByEntity(EntityDamageByEntityEvent e) {
        if (!isFurniture(e.getEntity())) return;
        log("EntityDamageByEntityEvent  entity=" + e.getEntity().getType()
                + " damager=" + e.getDamager().getType()
                + " cause=" + e.getCause()
                + " cancelled=" + e.isCancelled());
    }
 
    // ¿O como EntityDamage generico?
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDmg(EntityDamageEvent e) {
        if (!isFurniture(e.getEntity())) return;
        if (e instanceof EntityDamageByEntityEvent) return; // ya logueado arriba
        log("EntityDamageEvent  entity=" + e.getEntity().getType()
                + " cause=" + e.getCause()
                + " cancelled=" + e.isCancelled());
    }
 
    // ¿Muere la entidad? (confirma la muerte y por que canal)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (!isFurniture(e.getEntity())) return;
        log("EntityDeathEvent  entity=" + e.getEntity().getType() + " MURIO");
    }
 
    // ¿Right-click (por si el 'hit' que rompe es en realidad interaccion)?
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!isFurniture(e.getRightClicked())) return;
        log("PlayerInteractEntityEvent (right-click) entity=" + e.getRightClicked().getType()
                + " cancelled=" + e.isCancelled());
    }
}
