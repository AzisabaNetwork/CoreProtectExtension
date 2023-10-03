package net.azisaba.coreprotectextension.listener

import net.azisaba.coreprotectextension.CoreProtectExtension
import net.azisaba.coreprotectextension.commands.InspectCommand
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class PlayerListener(private val plugin: CoreProtectExtension) : Listener {
    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerInteract(e: PlayerInteractEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        if (InspectCommand.inspectMode.contains(e.player.uniqueId) && e.action == Action.RIGHT_CLICK_BLOCK && e.player.hasPermission("coreprotectextension.inspect")) {
            val block = e.clickedBlock ?: return
            e.isCancelled = true
            plugin.commandManager
                .getCommandOfType<InspectCommand>()
                .execute(e.player, arrayOf("location=${block.world.name};${block.x};${block.y};${block.z}", "page=1"))
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerInteractEntity(e: PlayerInteractEntityEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        if (InspectCommand.inspectMode.contains(e.player.uniqueId) && e.player.hasPermission("coreprotectextension.inspect")) {
            if (e.rightClicked.type == EntityType.ARMOR_STAND || e.rightClicked.type.name.contains("ITEM_FRAME")) {
                val loc = e.rightClicked.location
                e.isCancelled = true
                plugin.commandManager
                    .getCommandOfType<InspectCommand>()
                    .execute(
                        e.player,
                        arrayOf("location=${loc.world.name};${loc.blockX};${loc.blockY};${loc.blockZ}", "page=1"),
                    )
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerInteractAtEntity(e: PlayerInteractAtEntityEvent) {
        if (e.rightClicked.type == EntityType.ARMOR_STAND) {
            onPlayerInteractEntity(e)
        }
    }
}
