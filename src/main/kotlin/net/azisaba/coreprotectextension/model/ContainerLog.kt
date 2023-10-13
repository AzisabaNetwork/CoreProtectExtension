package net.azisaba.coreprotectextension.model

import net.coreprotect.database.Rollback
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.LocalDateTime

@Suppress("ArrayInDataClass")
data class ContainerLog(
    val time: LocalDateTime,
    val user: User,
    val worldId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val type: Material,
    val amount: Int,
    val metadata: ByteArray?,
    val action: Action,
    val rolledBack: Boolean,
) {
    enum class Action(val color: Char, val short: Char) {
        REMOVED('c', '-'),
        ADDED('a', '+')
    }

    fun getItemStack() = ItemStack(type, amount).let { Rollback.populateItemStack(it, metadata)[2] as ItemStack }
}
