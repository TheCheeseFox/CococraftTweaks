package net.cococraft.tweaks.protection;

import com.palmergames.bukkit.towny.event.executors.TownyActionEventExecutor;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;

/**
 * Integra la proteccion de Towny con el furniture entity-based de Crop & Kettle.
 *
 * Cubre las TRES acciones que el datapack maneja fuera del sistema de bloques
 * vanilla (y que por tanto Towny no ve por si solo):
 *
 *  1) ROMPER  -> el jugador ATACA (click izq.) la entidad `interaction` del mueble.
 *                En Paper eso solo dispara PrePlayerAttackEntityEvent. Cancelarlo
 *                evita que se registre `attack:{}` y el datapack no rompe el mueble.
 *                Gate: TownyActionEventExecutor.canDestroy(...)
 *
 *  2) USAR    -> el jugador hace RIGHT-CLICK sobre la entidad `interaction`
 *                (abrir plato, usar basin/cutting board...). El datapack lo detecta
 *                por `interaction:{}`. Cancelar el PlayerInteractEntityEvent evita
 *                ese registro.  Gate: canSwitch(...)   (accion tipo "switch")
 *
 *  3) COLOCAR -> el jugador usa un item-colocador (item_model cnk:*_item) sobre un
 *                bloque; el datapack lo detecta por advancement + raycast. Cancelar
 *                el PlayerInteractEvent evita que dispare.  Gate: canBuild(...)
 *
 * Todo el furniture del pack lleva el tag "smithed.block", asi que romper/usar se
 * protege de forma uniforme para plate, basin, cutting board, cornucopia, wine
 * rack, mr kettle, scarecrow, etc.
 */
public final class FurnitureProtectionListener implements Listener {

    /** Tag comun a todo el furniture del pack (convencion Smithed). */
    private static final String BLOCK_TAG = "smithed.block";

    /**
     * item_model (namespace:path) de TODOS los items que colocan furniture del pack.
     * Detectar por item_model es API estable (ItemMeta#getItemModel) y no requiere
     * leer NBT. Si añades addons con muebles nuevos, agrega aqui sus item_model.
     */
    private static final Set<String> PLACER_MODELS = Set.of(
            "cnk:basin_item",
            "cnk:calendar_item",
            "cnk:candy_bowl",
            "cnk:cooking_pot_item",
            "cnk:cornucopia_item",
            "cnk:cutting_board_item",
            "cnk:distiller_item",
            "cnk:faucet_item",
            "cnk:fizz_oven_item",
            "cnk:hollow_vessel_item",
            "cnk:milk_pail_item",
            "cnk:mixing_bowl_item",
            "cnk:pail_item",
            "cnk:panless_stove_item",
            "cnk:picnic_basket",
            "cnk:plate_item",
            "cnk:scarecrow_item",
            "cnk:stove_item",
            "cnk:water_pail_item",
            "cnk:wine_rack/empty",
            "cnk:witch_cauldron_item",
            "cnk:wreath_item"
    );

    // ------------------------------------------------------------------ ROMPER
    @EventHandler(priority = EventPriority.LOW)  // sin ignoreCancelled: el evento puede llegar pre-cancelado para interaction
    public void onAttackFurniture(PrePlayerAttackEntityEvent event) {
        final Entity target = event.getAttacked();
        if (!isFurnitureEntity(target)) return;

        final Location loc = target.getLocation();
        if (!TownyActionEventExecutor.canDestroy(event.getPlayer(), loc, materialAt(loc))) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------- USAR
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseFurniture(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // evita doble disparo
        final Entity target = event.getRightClicked();
        if (!isFurnitureEntity(target)) return;

        final Location loc = target.getLocation();
        // "switch" = interactuar/usar sin construir ni destruir (como una palanca).
        if (!TownyActionEventExecutor.canSwitch(event.getPlayer(), loc, materialAt(loc))) {
            event.setCancelled(true);
        }
    }

    // ----------------------------------------------------------------- COLOCAR
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlaceFurniture(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;           // solo mano principal
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;   // colocar es sobre bloque
        if (event.getClickedBlock() == null) return;

        final ItemStack item = event.getItem();
        if (!isPlacerItem(item)) return;

        // Celda donde apareceria el mueble (cara del bloque clicado).
        final Location placeLoc = event.getClickedBlock()
                .getRelative(event.getBlockFace()).getLocation();

        if (!TownyActionEventExecutor.canBuild(event.getPlayer(), placeLoc, item.getType())) {
            event.setCancelled(true);
        }
    }

    // ----------------------------------------------------------------- helpers
    private static boolean isFurnitureEntity(Entity e) {
        return (e instanceof Interaction || e instanceof ItemDisplay)
                && e.getScoreboardTags().contains(BLOCK_TAG);
    }

    private static boolean isPlacerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasItemModel()) return false;
        final NamespacedKey model = meta.getItemModel();
        return model != null && PLACER_MODELS.contains(model.toString());
    }

    private static Material materialAt(Location loc) {
        final Block block = loc.getBlock();
        return block.getType() == Material.AIR ? Material.BARRIER : block.getType();
    }
}
