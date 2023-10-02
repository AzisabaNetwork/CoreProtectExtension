package net.azisaba.coreprotectextension.util

import net.azisaba.coreprotectextension.config.PluginConfig
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import xyz.acrylicstyle.util.InvalidArgumentException
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

object Util {
    fun InvalidArgumentException.toComponent(): TextComponent {
        val errorComponent = TextComponent("${ChatColor.RED}Invalid syntax: $message")
        val context = this.context ?: return errorComponent
        val prev = context.peekWithAmount(-min(context.index(), 15))
        var next = context.peekWithAmount(
            min(
                context.readableCharacters(),
                max(15, length)
            )
        )
        if (next.isEmpty()) {
            next = " ".repeat(length)
        }
        val c = TextComponent("")
        c.addExtra(errorComponent)
        c.addExtra(TextComponent("\n"))
        c.addExtra(TextComponent(prev).apply { color = ChatColor.WHITE.asBungee() })
        val left = next.substring(0, length)
        val right = next.substring(length, next.length)
        val problem = TextComponent(left).apply { color = ChatColor.RED.asBungee() }
        problem.isUnderlined = true
        c.addExtra(problem)
        c.addExtra(TextComponent(right).apply { color = ChatColor.WHITE.asBungee() })
        return c
    }

    fun LocalDateTime.getTimeSince(current: LocalDateTime): String {
        val thisTime = this.toEpochSecond(PluginConfig.instance.getZoneOffsetAt(this))
        val currentTime = current.toEpochSecond(PluginConfig.instance.getZoneOffsetAt(current))
        var timeSince = currentTime - thisTime.toDouble()
        timeSince /= 60
        if (timeSince < 60) {
            return DecimalFormat("0.00").format(timeSince) + "m"
        }
        timeSince /= 60
        if (timeSince < 24) {
            return DecimalFormat("0.00").format(timeSince) + "h"
        }
        timeSince /= 24
        return DecimalFormat("0.00").format(timeSince) + "d"
    }

    fun LocalDateTime.toInstant(): Instant = this.toInstant(PluginConfig.instance.getZoneOffsetAt(this))

    private fun getServerImplVersion() = Bukkit.getServer().javaClass.name.split('.')[3]

    fun ItemStack.asNMSCopy(): Any =
        Class.forName("org.bukkit.craftbukkit.${getServerImplVersion()}.inventory.CraftItemStack")
            .getMethod("asNMSCopy", ItemStack::class.java)
            .invoke(null, this)

    fun ItemStack.getSNBT(): String? {
        val nms = asNMSCopy()
        val tagField = nms.javaClass.declaredFields.find { it.type.simpleName == "NBTTagCompound" || it.type.simpleName == "CompoundTag" }
            ?: error("Could not find tag field")
        tagField.isAccessible = true
        return tagField.get(nms)?.toString()
    }
}
