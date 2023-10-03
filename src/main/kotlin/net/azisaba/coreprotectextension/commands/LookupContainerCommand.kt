package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.CoreProtectExtension
import net.azisaba.coreprotectextension.database.CPDatabase
import net.azisaba.coreprotectextension.util.Util
import net.azisaba.coreprotectextension.util.Util.getSNBT
import net.azisaba.coreprotectextension.util.Util.getTimeSince
import net.azisaba.coreprotectextension.util.Util.toComponent
import net.azisaba.coreprotectextension.util.Util.toInstant
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import xyz.acrylicstyle.util.ArgumentParserBuilder
import xyz.acrylicstyle.util.InvalidArgumentException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.max

class LookupContainerCommand(private val plugin: CoreProtectExtension) : Command {
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z")
    private val parser =
        ArgumentParserBuilder.builder()
            .parseOptionsWithoutDash()
            .disallowEscapedTabCharacter()
            .disallowEscapedLineTerminators()
            .literalBackslash()
            .create()
    override val name = "lookup-container"
    override val usage = listOf("<params>")
    private val params = listOf("user=", "radius=", "page=", "before=", "after=")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            return sender.sendMessage("${ChatColor.RED}This command must be executed by the player.")
        }
        val arguments = try {
            parser.parse(args.joinToString(" "))
        } catch (e: InvalidArgumentException) {
            return sender.spigot().sendMessage(e.toComponent())
        }
        val getItem = arguments.getArgument("getitemindex")?.toInt()
        val argUser = arguments.getArgument("user")
        val argRadius = arguments.getArgument("radius")?.toInt()
        val argPage = max(1, arguments.getArgument("page")?.toInt() ?: 1) - 1
        val after = arguments.getArgument("after")?.let {
            try {
                System.currentTimeMillis() - Util.processTime(it)
            } catch (e: Exception) {
                Util.parseDateTime(it)
            }
        }?.let { Instant.ofEpochMilli(it) }
        val before = arguments.getArgument("before")?.let {
            try {
                System.currentTimeMillis() - Util.processTime(it)
            } catch (e: Exception) {
                Util.parseDateTime(it)
            }
        }?.let { Instant.ofEpochMilli(it) }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val list = try {
                CPDatabase.lookupContainer(sender.location, argUser, after, before, argRadius, argPage).reversed()
            } catch (e: Exception) {
                sender.sendMessage("${ChatColor.RED}An error occurred while executing command.")
                plugin.slF4JLogger.error("Failed to execute command from ${sender.name}: /cpe lookup-container ${args.joinToString(" ")}", e)
                return@Runnable
            }
            if (getItem != null && sender.hasPermission("coreprotectextension.command.lookup-container.get-item")) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.inventory.addItem(list[getItem].getItemStack().apply { amount = 1 })
                })
                return@Runnable
            }
            val now = LocalDateTime.now()
            sender.sendMessage("----------------------------------------")
            list.forEachIndexed { index, result ->
                val text = TextComponent()
                val time = TextComponent("${result.time.getTimeSince(now)} ago ")
                    .apply { color = ChatColor.GRAY.asBungee() }
                time.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(dateFormat.format(result.time.toInstant().toEpochMilli())))
                val user = TextComponent("${result.user.name} ").apply { color = ChatColor.GOLD.asBungee() }
                user.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(result.user.uuid.toString()))
                val item = TextComponent(result.type.name.lowercase()).apply { color = ChatColor.AQUA.asBungee() }
                val itemStack = result.getItemStack()
                val tag = "{\"id\":\"minecraft:${result.type.name.lowercase()}\",Count:${result.amount},tag:${itemStack.getSNBT()}}"
                if (tag.length < 262144) {
                    item.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_ITEM, arrayOf(TextComponent(tag)))
                } else if (itemStack.itemMeta?.hasDisplayName() == true) {
                    item.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                        itemStack.itemMeta!!.displayName + "\n§7§o(Item data is too big to be shown)\n" +
                                "§7§o(You can click here to obtain the actual item, but you do so at §c§oyour own risk§7§o)"
                    ))
                } else {
                    item.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                        "§7§o(Item data is too big to be shown)\n" +
                                "§7§o(You can click here to obtain the actual item, but you do so at §c§oyour own risk§7§o)"
                    ))
                }
                item.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cpe lookup-container ${args.joinToString(" ")} getitemindex=${index}")
                text.addExtra(time)
                text.addExtra(user)
                text.addExtra("§${result.action.color}${result.action.short}${result.amount} ")
                text.addExtra(item)
                sender.spigot().sendMessage(text)
                val location = TextComponent(" §r §r §r §r §r §r §r §r §r §r §r §r §r §r §7 ^ §o(x${result.x}/y${result.y}/z${result.z})")
                location.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/co teleport wid:${result.worldId} ${result.x + 0.5} ${result.y} ${result.z + 0.5}")
                sender.spigot().sendMessage(location)
            }
            val navigation = TextComponent()
            var commandWithoutPage = "/cpe lookup-container "
            argUser?.let { commandWithoutPage += "user=$it " }
            argRadius?.let { commandWithoutPage += "radius=$it " }
            before?.let { commandWithoutPage += "before=\"${dateFormat.format(it.toEpochMilli())}\"" }
            after?.let { commandWithoutPage += "after=\"${dateFormat.format(it.toEpochMilli())}\"" }
            val back = TextComponent("<< ")
            if (argPage > 0) {
                back.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("<< Previous page"))
                back.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=$argPage")
            } else {
                back.color = ChatColor.DARK_GRAY.asBungee()
            }
            val next = TextComponent(" >>")
            if (list.size == 5) {
                next.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Next page >>"))
                next.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "$commandWithoutPage page=${argPage + 2}")
            } else {
                next.color = ChatColor.DARK_GRAY.asBungee()
            }
            val current = TextComponent(" ${ChatColor.GRAY}| ${ChatColor.WHITE}Page ${ChatColor.AQUA}${argPage + 1}")
            navigation.addExtra(back)
            navigation.addExtra(next)
            navigation.addExtra(current)
            sender.spigot().sendMessage(navigation)
        })
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        if (args.last().startsWith("user=")) {
            return Bukkit.getOnlinePlayers().map { it.name }.map { "user=$it" }.filter { it.startsWith(args.last(), true) }
        }
        if (args.last().startsWith("radius=")) {
            return (0..100).map { "radius=$it" }.filter { it.startsWith(args.last(), true) }
        }
        return params.filter { param -> args.all { arg -> !arg.startsWith(param) } }.filter { it.startsWith(args.last()) }
    }
}
