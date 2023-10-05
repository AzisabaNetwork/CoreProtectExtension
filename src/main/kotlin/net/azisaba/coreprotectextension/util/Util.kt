package net.azisaba.coreprotectextension.util

import net.azisaba.coreprotectextension.config.PluginConfig
import net.azisaba.coreprotectextension.database.CPDatabase
import net.azisaba.coreprotectextension.model.ContainerLog
import net.azisaba.coreprotectextension.model.ContainerLookupResult
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
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
            val text = TextComponent()
            val time = TextComponent("${log.time.getTimeSince(now)} ago ")
                .apply { color = ChatColor.GRAY.asBungee() }
            time.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(formats[0].format(log.time.toInstant().toEpochMilli())))
            val user = TextComponent("${log.user.name} ").apply { color = ChatColor.GOLD.asBungee() }
            user.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(log.user.uuid.toString()))
            val item = TextComponent(log.type.name.lowercase()).apply { color = ChatColor.AQUA.asBungee() }
            val itemStack = log.getItemStack()
            val tag = "{\"id\":\"minecraft:${log.type.name.lowercase()}\",Count:${log.amount},tag:${itemStack.getSNBT()}}"
            if (tag.toByteArray().size < 260000) {
                item.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_ITEM, arrayOf(TextComponent(tag)))
            } else if (itemStack.itemMeta?.hasDisplayName() == true) {
                item.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                    itemStack.itemMeta!!.displayName + "\n§7§o(Item data is too big to be shown)\n" +
                            "§7§o(You can click here to obtain the actual item, but you do so at §c§oyour own risk§7§o)"
                ))
            } else {
                item.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                    "§7§o(Item data is too big to be shown)\n" +
                            "§7§o(You can click here to obtain the actual item, but you do so at §c§oyour own risk§7§o)"
                ))
            }
            item.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=${pageIndex + 1} getitemindex=${index}")
            text.addExtra(time)
            text.addExtra(user)
            text.addExtra("§${log.action.color}${log.action.short}${log.amount} ")
            text.addExtra(item)
            sender.spigot().sendMessage(text)
            if (showLocation) {
                val location = TextComponent(
                    " §r §r §r §r §r §r §r §r §r §r §r §r §r §r §7 ^ §o(x${log.x}/y${log.y}/z${log.z}/${CPDatabase.getWorldName(log.worldId)})"
                )
                location.clickEvent = ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/co teleport wid:${log.worldId} ${log.x + 0.5} ${log.y} ${log.z + 0.5}",
                )
                sender.spigot().sendMessage(location)
            }
        }
        val navigation = TextComponent()
        val first = TextComponent("First ")
        if (pageIndex > 0) {
            first.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("<< First page"))
            first.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=1")
        } else {
            first.color = ChatColor.DARK_GRAY.asBungee()
        }
        val back = TextComponent("<< ")
        if (pageIndex > 0) {
            back.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("<< Previous page"))
            back.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=$pageIndex")
        } else {
            back.color = ChatColor.DARK_GRAY.asBungee()
        }
        val next = TextComponent(" >> ")
        if ((result.lastPageIndex >= 0 && result.lastPageIndex > pageIndex) || (result.lastPageIndex < 0 && result.data.size == resultsPerPage)) {
            next.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Next page >>"))
            next.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=${pageIndex + 2}")
        } else {
            next.color = ChatColor.DARK_GRAY.asBungee()
        }
        val last = TextComponent("Last")
        if (result.lastPageIndex > pageIndex) {
            last.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Last page >>"))
            last.clickEvent =
                ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=${result.lastPageIndex + 1}")
        } else if (result.lastPageIndex >= 0) {
            last.color = ChatColor.DARK_GRAY.asBungee()
        } else {
            last.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Page data not available"))
            last.color = ChatColor.DARK_GRAY.asBungee()
        }
        val current = if (result.lastPageIndex == -1) {
            TextComponent(" ${ChatColor.GRAY}| ${ChatColor.WHITE}Page ${ChatColor.AQUA}${pageIndex + 1}")
        } else {
            TextComponent(" ${ChatColor.GRAY}| ${ChatColor.WHITE}Page ${ChatColor.AQUA}${pageIndex + 1}${ChatColor.WHITE}/${ChatColor.AQUA}${result.lastPageIndex + 1}")
        }
        current.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Click to select page"))
        current.clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "$commandWithoutPage page=")
        navigation.addExtra(first)
        navigation.addExtra(back)
        navigation.addExtra(next)
        navigation.addExtra(last)
        navigation.addExtra(current)
        sender.spigot().sendMessage(navigation)
    }

    fun <R> runInMain(plugin: Plugin, action: () -> R): R =
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            CompletableFuture.supplyAsync(action) { Bukkit.getScheduler().runTask(plugin, it) }.join()
        }
}
