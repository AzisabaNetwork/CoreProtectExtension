package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.CoreProtectExtension
import net.azisaba.coreprotectextension.database.CPDatabase
import net.azisaba.coreprotectextension.util.Util
import net.azisaba.coreprotectextension.util.Util.toComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import xyz.acrylicstyle.util.ArgumentParserBuilder
import xyz.acrylicstyle.util.InvalidArgumentException
import java.util.UUID
import kotlin.math.max

class InspectCommand(private val plugin: CoreProtectExtension) : Command {
    override val name = "inspect"
    override val aliases = setOf("i")
    override val summary = "Toggles the inspect mode which you can right click on the container to lookup the container logs."
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
        if (!sender.hasPermission("coreprotectextension.inspect")) {
            return sender.sendMessage("${ChatColor.RED}You need coreprotectextension.inspect to use this feature.")
        }
        val arguments = try {
            parser.parse(args.joinToString(" "))
        } catch (e: InvalidArgumentException) {
            return sender.spigot().sendMessage(e.toComponent())
        }
        val getItem = arguments.getArgument("getitemindex")?.toInt()
        val argPage = max(1, arguments.getArgument("page")?.toIntOrNull() ?: return toggleInspectMode(sender)) - 1
        val location = arguments.getArgument("location")?.let {
            try {
                val split = it.split(";")
                Location(Bukkit.getWorld(split[0])!!, split[1].toDouble(), split[2].toDouble(), split[3].toDouble())
            } catch (e: NullPointerException) {
                null
            }
        } ?: return toggleInspectMode(sender)
        if (location.distance(sender.location) > 5) return toggleInspectMode(sender)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val list = try {
                CPDatabase.lookupContainer(location, null, null, null, -1, argPage, resultsPerPage = 10).reversed()
            } catch (e: Exception) {
                sender.sendMessage("${ChatColor.RED}An error occurred while executing command.")
                plugin.slF4JLogger.error("Failed to execute command from ${sender.name}: /cpe inspect ${args.joinToString(" ")}", e)
                return@Runnable
            }
            if (getItem != null) {
                if (sender.hasPermission("coreprotectextension.command.inspect.get-item")) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.inventory.addItem(list[getItem].getItemStack().apply { amount = 1 })
                    })
                } else {
                    sender.sendActionBar("${ChatColor.RED}You don't have permission.")
                }
                return@Runnable
            }
            val commandWithoutPage = "/cpe inspect location=${arguments.getArgument("location")} "
            Util.sendResults(sender, list, commandWithoutPage, argPage, showLocation = false, resultsPerPage = 10)
        })
    }

    private fun toggleInspectMode(player: Player) {
        if (inspectMode.remove(player.uniqueId)) {
            player.sendMessage("${ChatColor.GOLD}Inspect mode is now disabled.")
        } else {
            inspectMode.add(player.uniqueId)
            player.sendMessage("${ChatColor.GOLD}Inspect mode is now enabled.")
        }
    }

    companion object {
        val inspectMode = mutableSetOf<UUID>()
    }
}
