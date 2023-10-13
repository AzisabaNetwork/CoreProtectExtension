package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.CoreProtectExtension
import net.azisaba.coreprotectextension.database.CPDatabase
import net.azisaba.coreprotectextension.model.ContainerLog
import net.azisaba.coreprotectextension.model.LookupException
import net.azisaba.coreprotectextension.util.NumberOperation
import net.azisaba.coreprotectextension.util.Util
import net.azisaba.coreprotectextension.util.Util.toComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import xyz.acrylicstyle.util.ArgumentParserBuilder
import xyz.acrylicstyle.util.InvalidArgumentException
import java.text.SimpleDateFormat
import java.time.Instant
import kotlin.math.max

class LookupCommand(private val plugin: CoreProtectExtension) : Command {
    override val name = "lookup"
    override val summary = "Lookup container logs by arguments."
    override val description = "$summary \"time:(time)\" in CoreProtect is \"after=(time)\" in CoreProtectExtension."
    override val usage = listOf("<action=> [user=] [radius=] [page=] [before=] [after=] [include=] [exclude=] [amount=]")
    private val params = listOf("action=", "user=", "radius=", "page=", "before=", "after=", "include=", "exclude=", "amount=")
    private val validActions = listOf("container", "+container", "-container", "item", "+item", "-item")
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z")
    private val parser =
        ArgumentParserBuilder.builder()
            .parseOptionsWithoutDash()
            .disallowEscapedTabCharacter()
            .disallowEscapedLineTerminators()
            .literalBackslash()
            .create()

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            return sender.sendMessage("${ChatColor.RED}This command must be executed by the player.")
        }
        val arguments = try {
            parser.parse(args.joinToString(" "))
        } catch (e: InvalidArgumentException) {
            return sender.spigot().sendMessage(e.toComponent())
        }
        val action = arguments.getArgument("action")
            ?: return sender.sendMessage("${ChatColor.RED}Action must be provided.")
        if (action !in validActions) {
            return sender.sendMessage("${ChatColor.RED}Invalid action: $action")
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
        val include = arguments.getArgument("include")
        val exclude = arguments.getArgument("exclude")
        val amount = arguments.getArgument("amount")?.let { NumberOperation.parse<Int>(it) }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val result = try {
                when (action) {
                    "container" -> CPDatabase.lookupContainer(null, sender.location, argUser, after, before, include, exclude, amount, argRadius, argPage)
                    "+container" -> CPDatabase.lookupContainer(ContainerLog.Action.ADDED, sender.location, argUser, after, before, include, exclude, amount, argRadius, argPage)
                    "-container" -> CPDatabase.lookupContainer(ContainerLog.Action.REMOVED, sender.location, argUser, after, before, include, exclude, amount, argRadius, argPage)
                    "item" -> CPDatabase.lookupItem(null, sender.location, argUser, after, before, include, exclude, amount, argRadius, argPage)
                    "+item" -> CPDatabase.lookupItem(ContainerLog.Action.ADDED, sender.location, argUser, after, before, include, exclude, amount, argRadius, argPage)
                    "-item" -> CPDatabase.lookupItem(ContainerLog.Action.REMOVED, sender.location, argUser, after, before, include, exclude, amount, argRadius, argPage)
                    else -> error("Invalid action: $action")
                }
            } catch (e: LookupException) {
                sender.sendMessage("${ChatColor.RED}${e.message}")
                return@Runnable
            } catch (e: Exception) {
                sender.sendMessage("${ChatColor.RED}An error occurred while executing command.")
                plugin.slF4JLogger.error("Failed to execute command from ${sender.name}: /cpe lookup ${args.joinToString(" ")}", e)
                return@Runnable
            }
            if (getItem != null) {
                if (sender.hasPermission("coreprotectextension.command.lookup.get-item")) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.inventory.addItem(result.data[getItem].getItemStack().apply { this.amount = 1 })
                    })
                } else {
                    sender.sendActionBar("${ChatColor.RED}You don't have permission.")
                }
                return@Runnable
            }
            var commandWithoutPage = "/cpe lookup action=$action "
            argUser?.let { commandWithoutPage += "user=$it " }
            argRadius?.let { commandWithoutPage += "radius=$it " }
            before?.let { commandWithoutPage += "before=\"${dateFormat.format(it.toEpochMilli())}\" " }
            after?.let { commandWithoutPage += "after=\"${dateFormat.format(it.toEpochMilli())}\" " }
            include?.let { commandWithoutPage += "include=\"${include.replace("\"", "\\\"")}\" " }
            exclude?.let { commandWithoutPage += "exclude=\"${exclude.replace("\"", "\\\"")}\" " }
            amount?.let { commandWithoutPage += "amount=\"$it\" " }
            Util.sendResults(sender, result, commandWithoutPage, argPage)
        })
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        if (args.last().startsWith("action=")) {
            return validActions.map { "action=$it" }.filter { it.startsWith(args.last(), true) }
        }
        if (args.last().startsWith("user=")) {
            return Bukkit.getOnlinePlayers().map { it.name }.map { "user=$it" }.filter { it.startsWith(args.last(), true) }
        }
        if (args.last().startsWith("radius=")) {
            return (0..100).map { "radius=$it" }.filter { it.startsWith(args.last(), true) }
        }
        if (args.last().startsWith("include=") || args.last().startsWith("exclude=")) {
            return Material.entries
                .filter { !it.name.startsWith("LEGACY_") }
                .map { "${args.last().substring(0, args.last().indexOf('='))}=${it.name.lowercase()}" }
                .filter { it.startsWith(args.last(), true) }
        }
        if (args.last().startsWith("amount=")) {
            return NumberOperation.Type.entries.map { "amount=\"${it.op}" }.filter { it.startsWith(args.last()) }
        }
        return params.filter { param -> args.all { arg -> !arg.startsWith(param) } }.filter { it.startsWith(args.last()) }
    }
}
