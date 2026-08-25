package net.cococraft.tweaks;

import dev.aurelium.auraskills.api.event.damage.DamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * LISTENER TEMPORAL DE DIAGNOSTICO. No cambia nada; solo IMPRIME en consola,
 * por reflexion, TODOS los metodos y valores reales de DamageMeta cuando
 * AuraSkills procesa un golpe. Esto es porque la documentacion publica no
 * confirma si DamageMeta expone si el golpe fue critico (isCritical, tipo
 * de critico, etc) - la reflexion no necesita saber los nombres de
 * antemano, asi que compila seguro y nos deja ver la estructura real.
 *
 * Registrar en onEnable (temporalmente):
 *   AuraSkillsDamageDebugListener.register(this);
 *
 * Golpea algo unas cuantas veces (normal y en salto-critico) y mira la
 * consola por lineas [ASK-DEBUG]. Luego quita este listener.
 */
public final class AuraSkillsDamageDebugListener implements Listener {

    private final JavaPlugin plugin;

    private AuraSkillsDamageDebugListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(
                new AuraSkillsDamageDebugListener(plugin), plugin);
        plugin.getLogger().warning("[ASK-DEBUG] Listener de diagnostico de AuraSkills ACTIVO (quitar en produccion).");
    }

    private void log(String s) {
        plugin.getLogger().warning("[ASK-DEBUG] " + s);
    }

    @EventHandler
    public void onDamage(DamageEvent event) {
        log("=== DamageEvent: modifiedAttackDamage=" + event.getModifiedAttackDamage()
                + " modifiedDamage=" + event.getModifiedDamage() + " ===");

        Object meta;
        try {
            meta = event.getDamageMeta();
        } catch (Throwable t) {
            log("getDamageMeta() lanzo excepcion: " + t);
            return;
        }

        if (meta == null) {
            log("DamageMeta es null");
            return;
        }

        log("DamageMeta class = " + meta.getClass().getName());
        for (Method m : meta.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            if (m.getReturnType() == void.class) continue;
            try {
                m.setAccessible(true);
                Object value = m.invoke(meta);
                log("  " + m.getName() + "() = " + value);
            } catch (Exception e) {
                log("  " + m.getName() + "() lanzo: " + e.getMessage());
            }
        }
    }
}
