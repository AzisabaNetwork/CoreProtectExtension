package net.azisaba.coreprotectextension.util

import io.papermc.paper.adventure.AdventureComponent
import io.papermc.paper.adventure.PaperAdventure
import net.azisaba.coreprotectextension.config.PluginConfig
import net.azisaba.coreprotectextension.database.CPDatabase
import net.azisaba.coreprotectextension.model.ContainerLog
import net.azisaba.coreprotectextension.model.ContainerLookupResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component as MinecraftComponent
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import xyz.acrylicstyle.util.InvalidArgumentException
import xyz.acrylicstyle.util.StringReader
import java.text.DecimalFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

const val year = 1000L * 60L * 60L * 24L * 365L
const val month = 1000L * 60L * 60L * 24L * 30L
const val day = 1000L * 60L * 60L * 24L
const val hour = 1000L * 60L * 60L
const val minute = 1000L * 60L
const val second = 1000L

object Util {
    private val formats = listOf(
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss"),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        SimpleDateFormat("yyyy/MM/dd HH:mm"),
        SimpleDateFormat("yyyy-MM-dd HH:mm"),
        SimpleDateFormat("yyyy/MM/dd"), // 6 (add time)
        SimpleDateFormat("yyyy-MM-dd"), // 7 (add time)
        SimpleDateFormat("HH:mm:ss"), // 8 (add date)
        SimpleDateFormat("HH:mm"), // 9 (add date)
        SimpleDateFormat("MM-dd"), // 10 (add year + loop)
        SimpleDateFormat("MM/dd"), // 11 (add year + loop)
        SimpleDateFormat("MM-dd HH:mm"), // 12 (add year + loop)
        SimpleDateFormat("MM/dd HH:mm"), // 13 (add year + loop)
        SimpleDateFormat("MM-dd HH:mm:ss"), // 14 (add year + loop)
        SimpleDateFormat("MM/dd HH:mm:ss"), // 15 (add year + loop)
    ).onEach { it.timeZone = TimeZone.getTimeZone(PluginConfig.instance.timeZone) }

    /**
     * Converts the string of time into duration.
     * For example, this method can convert "1s" to 1000 and "1m1s" to 61000.
     */
    fun processTime(s: String): Long {
        var time = 0L
        var rawNumber = ""
        val reader = StringReader.create(s)
        while (!reader.isEOF) {
            val c = reader.read(1).first()
            if (c.isDigit() || c == '.') {
                rawNumber += c
            } else {
                if (rawNumber.isEmpty()) {
                    throw IllegalArgumentException("Unexpected non-digit character: '$c' at index ${reader.index()}")
                }
                // mo
                if (c == 'm' && !reader.isEOF && reader.peek() == 'o') {
                    reader.skip(1)
                    time += month * rawNumber.toLong()
                    rawNumber = ""
                    continue
                }
                // y(ear), d(ay), h(our), m(inute), s(econd)
                time += when (c) {
                    'y' -> (year * rawNumber.toDouble()).toLong()
                    // mo is not here
                    'w' -> (7 * day * rawNumber.toDouble()).toLong()
                    'd' -> (day * rawNumber.toDouble()).toLong()
                    'h' -> (hour * rawNumber.toDouble()).toLong()
                    'm' -> (minute * rawNumber.toDouble()).toLong()
                    's' -> (second * rawNumber.toDouble()).toLong()
                    else -> throw IllegalArgumentException("Unexpected character: '$c' at index ${reader.index()}")
                }
                rawNumber = ""
            }
        }
        return time
    }

    fun parseDateTime(dateTime: String): Long {
        var loopAgain: Boolean
        var value = -1L
        var s = dateTime
        do {
            loopAgain = false
            formats.forEachIndexed { index, format ->
                if (value != -1L) return@forEachIndexed
                value = try {
                    val date = format.parse(s)
                    val currentTime = System.currentTimeMillis() % 86400000
                    when (index) {
                        6, 7 -> date.time + currentTime // add time
                        8, 9 -> date.time + System.currentTimeMillis() - currentTime // add date
                        10, 11, 12, 13, 14, 15 -> {
                            // Add "<year>/" for prefix and loop again
                            s = LocalDateTime.now(PluginConfig.instance.getZoneId()).year.toString() + "/" + s
                            loopAgain = true
                            -1L
                        }

                        else -> date.time
                    }
                } catch (_: ParseException) {
                    -1L
                }
            }
        } while (loopAgain)
        return value
    }

    fun InvalidArgumentException.toComponent(): Component {
        val errorComponent = Component.text("Invalid syntax: $message", NamedTextColor.RED)
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
        val left = next.substring(0, length)
        val right = next.substring(length, next.length)
        val problem = Component.text(left, NamedTextColor.RED).decorate(TextDecoration.UNDERLINED)
        return Component.empty()
            .append(errorComponent)
            .append(Component.newline())
            .append(Component.text(prev, NamedTextColor.WHITE))
            .append(problem)
            .append(Component.text(right, NamedTextColor.WHITE))
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

    fun ItemStack.asNMSCopy(): net.minecraft.world.item.ItemStack = CraftItemStack.asNMSCopy(this)

    fun ItemStack.trySerializeAsBytes() = try {
        serializeAsBytes()
    } catch (_: Exception) {
        ByteArray(0)
    }

    fun sendResults(
        sender: Player,
        result: ContainerLookupResult,
        commandWithoutPage: String,
        pageIndex: Int,
        showLocation: Boolean = true,
        resultsPerPage: Int = 5,
    ) {
        val now = LocalDateTime.now()
        sender.sendMessage("----------------------------------------")
        result.data.forEachIndexed { index, log ->
//            val time = MinecraftComponent.literal("${log.time.getTimeSince(now)} ago ")
//                .withStyle(ChatFormatting.GRAY)
//                .withStyle { style ->
//                    style.withHoverEvent(
//                        net.minecraft.network.chat.HoverEvent.ShowText(
//                            MinecraftComponent.literal(
//                                formats[0].format(log.time.toInstant().toEpochMilli())
//                            )
//                        )
//                    )
//                }
//            val user = MinecraftComponent.literal("${log.user.name} ")
//                .withColor(log.user.name.hashCode().and(0xFFFFFF))
//                .withStyle { style ->
//                    style.withHoverEvent(
//                        net.minecraft.network.chat.HoverEvent.ShowText(
//                            MinecraftComponent.literal(
//                                log.user.uuid.toString()
//                            )
//                        )
//                    ).withClickEvent(net.minecraft.network.chat.ClickEvent.CopyToClipboard(log.user.uuid.toString()))
//                }
//            val amount = MinecraftComponent.literal("${log.action.short}${log.amount} ")
//                .withStyle(if (log.action == ContainerLog.Action.ADDED) ChatFormatting.GREEN else ChatFormatting.RED)
//            val itemStack = log.getItemStack()
//            val itemName = if (itemStack.itemMeta?.hasCustomName() == true) {
//                MinecraftComponent.empty()
//                    .append(PaperAdventure.asVanilla(itemStack.itemMeta!!.customName()!!))
//            } else {
//                MinecraftComponent.empty()
//                    .append(PaperAdventure.asVanilla(Component.translatable(itemStack)))
//            }.append(MinecraftComponent.literal(" (${log.type.name.lowercase()})").withStyle(ChatFormatting.DARK_GRAY))
//                .withStyle { style ->
//                    style.withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowItem(itemStack.asNMSCopy()))
//                        .withClickEvent(net.minecraft.network.chat.ClickEvent.RunCommand("$commandWithoutPage page=${pageIndex + 1} getitemindex=${index}"))
//                }
//            if (log.rolledBack) {
//                (sender as CraftPlayer).handle.sendSystemMessage(
//                    MinecraftComponent.empty()
//                        .append(time)
//                        .append(user)
//                        .append(amount.withStyle(ChatFormatting.STRIKETHROUGH))
//                        .append(itemName.withStyle(ChatFormatting.STRIKETHROUGH)))
//            } else {
//                (sender as CraftPlayer).handle.sendSystemMessage(
//                    MinecraftComponent.empty()
//                        .append(time)
//                        .append(user)
//                        .append(amount)
//                        .append(itemName))
//            }
            val time = Component.text("${log.time.getTimeSince(now)} ago ", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(formats[0].format(log.time.toInstant().toEpochMilli()))))
            val user = Component.text("${log.user.name} ", TextColor.color(log.user.name.hashCode().and(0xFFFFFF)))
                .hoverEvent(HoverEvent.showText(Component.text(log.user.uuid.toString())))
            val amount = Component.text("${log.action.short}${log.amount} ", log.action.color)
            val itemStack = log.getItemStack()
            val itemName = if (itemStack.itemMeta?.hasCustomName() == true) {
                Component.empty()
                    .append(itemStack.itemMeta!!.customName()!!)
                    .append(Component.text(" (${log.type.name.lowercase()})", NamedTextColor.DARK_GRAY))
            } else {
                Component.empty()
                    .append(Component.translatable(itemStack))
                    .append(Component.text(" (${log.type.name.lowercase()})", NamedTextColor.DARK_GRAY))
            }
            val item = when {
                itemStack.trySerializeAsBytes().size < 260000 -> {
                    itemName.hoverEvent(
                        HoverEvent.showItem(
                            itemStack.type,
                            1,
                            PaperAdventure.asAdventure(itemStack.asNMSCopy().componentsPatch)
                        )
                    )
                }

                itemStack.itemMeta?.hasCustomName() == true -> {
                    itemName.hoverEvent(
                        itemStack.itemMeta!!.customName()!!
                            .append(
                                Component.text("(Item data is too big to be shown)", NamedTextColor.GRAY)
                                    .decorate(TextDecoration.ITALIC)
                            )
                            .append(
                                Component.text(
                                    "(You can click here to obtain the actual item, but you do so at your own risk)",
                                    NamedTextColor.RED
                                )
                                    .decorate(TextDecoration.ITALIC)
                            )
                    )
                }

                else -> {
                    itemName.hoverEvent(
                        Component.text("(Item data is too big to be shown)", NamedTextColor.GRAY)
                            .decorate(TextDecoration.ITALIC)
                            .append(
                                Component.text(
                                    "(You can click here to obtain the actual item, but you do so at your own risk)",
                                    NamedTextColor.RED
                                )
                                    .decorate(TextDecoration.ITALIC)
                            )
                    )
                }
            }.clickEvent(ClickEvent.callback {
                sender.inventory.addItem(itemStack)
            })
            //item.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=${pageIndex + 1} getitemindex=${index}")
            val text = if (log.rolledBack) {
                Component.empty()
                    .append(time)
                    .append(user)
                    .append(amount.decorate(TextDecoration.STRIKETHROUGH))
                    .append(item.decorate(TextDecoration.STRIKETHROUGH))
            } else {
                Component.empty().append(time).append(user).append(amount).append(item)
            }
            sender.sendMessage(text)
            if (showLocation) {
                val location =
                    Component.text("                ^ (x${log.x}/y${log.y}/z${log.z}/${CPDatabase.getWorldName(log.worldId)})", NamedTextColor.GRAY)
                        .decorate(TextDecoration.ITALIC)
                        .clickEvent(ClickEvent.runCommand("/co teleport wid:${log.worldId} ${log.x + 0.5} ${log.y} ${log.z + 0.5}"))
                sender.sendMessage(location)
            }
        }
        val first = if (pageIndex > 0) {
            Component.text("First")
                .hoverEvent(HoverEvent.showText(Component.text("<< First page")))
                .clickEvent(ClickEvent.runCommand("$commandWithoutPage page=1"))
        } else {
            Component.text("First", NamedTextColor.DARK_GRAY)
        }
        val back = if (pageIndex > 0) {
            Component.text(" << ")
                .hoverEvent(HoverEvent.showText(Component.text("<< Previous page")))
                .clickEvent(ClickEvent.runCommand("$commandWithoutPage page=$pageIndex"))
        } else {
            Component.text(" << ", NamedTextColor.DARK_GRAY)
        }
        val next = if ((result.lastPageIndex >= 0 && result.lastPageIndex > pageIndex) || (result.lastPageIndex < 0 && result.data.size == resultsPerPage)) {
            Component.text(" >> ")
                .hoverEvent(HoverEvent.showText(Component.text("Next page >>")))
                .clickEvent(ClickEvent.runCommand("$commandWithoutPage page=${pageIndex + 2}"))
        } else {
            Component.text(" >> ", NamedTextColor.DARK_GRAY)
        }
        val last = if (result.lastPageIndex > pageIndex) {
            Component.text("Last")
                .hoverEvent(HoverEvent.showText(Component.text("Last page >>")))
                .clickEvent(ClickEvent.runCommand("$commandWithoutPage page=${result.lastPageIndex + 1}"))
        } else if (result.lastPageIndex >= 0) {
            Component.text("Last", NamedTextColor.DARK_GRAY)
        } else {
            Component.text("Last", NamedTextColor.DARK_GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Page data not available")))
        }
        val current = if (result.lastPageIndex == -1) {
            Component.text(" | ", NamedTextColor.GRAY)
                .append(Component.text("Page ", NamedTextColor.WHITE))
                .append(Component.text("${pageIndex + 1}", NamedTextColor.AQUA))
        } else {
            Component.text(" | ", NamedTextColor.GRAY)
                .append(Component.text("Page ", NamedTextColor.WHITE))
                .append(Component.text("${pageIndex + 1}", NamedTextColor.AQUA))
                .append(Component.text("/", NamedTextColor.WHITE))
                .append(Component.text("${result.lastPageIndex + 1}", NamedTextColor.AQUA))
        }.hoverEvent(HoverEvent.showText(Component.text("Click to select page")))
            .clickEvent(ClickEvent.suggestCommand("$commandWithoutPage page="))
        sender.sendMessage(Component.empty()
            .append(first)
            .append(back)
            .append(next)
            .append(last)
            .append(current))
    }

    fun <R> runInMain(plugin: Plugin, action: () -> R): R =
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            CompletableFuture.supplyAsync(action) { Bukkit.getScheduler().runTask(plugin, it) }.join()
        }
}
