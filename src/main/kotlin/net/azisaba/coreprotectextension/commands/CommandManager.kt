package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.CoreProtectExtension
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

class CommandManager(private val plugin: CoreProtectExtension) : TabExecutor {
    internal val commands = mutableMapOf<String, Command>()

    private fun registerCommand(command: Command) {
        if (commands[command.name.lowercase()] != null) {
            error("Duplicate command name: ${command.name.lowercase()} (${commands[command.name.lowercase()]} and $command)")
        }
        commands[command.name.lowercase()] = command
        command.aliases.forEach { alias ->
            if (commands[alias.lowercase()] != null) {
                error("Duplicate command name: ${alias.lowercase()} (${commands[alias.lowercase()]} and $command)")
            }
            commands[alias.lowercase()] = command
        }
    }

    internal inline fun <reified T : Command> getCommandOfType() =
        commands.values.filterIsInstance<T>().firstOrNull() ?: error("${T::class.java.name} is not registered")

    override fun onCommand(
        sender: CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        try {
            if (args.isEmpty()) {
                commands.values.distinct().sortedBy { it.name }.forEach { cmd ->
                    if (sender.hasPermission("coreprotectextension.command.${cmd.name}")) {
                        sender.sendMessage("${ChatColor.GOLD}${cmd.fullUsage} ${ChatColor.GRAY}- ${ChatColor.AQUA}${cmd.summary}")
                    }
                }
                return true
            }
            val cmd = commands[args[0].lowercase()]
            if (cmd == null || cmd.name.startsWith("*")) {
                sender.sendMessage("${ChatColor.RED}Invalid sub command: ${args[0]}")
                return true
            }
            if (!sender.hasPermission("coreprotectextension.command.${cmd.name}")) {
                sender.sendMessage("${ChatColor.RED}You do not have permission to do this.")
                return true
            }
            cmd.execute(sender, args.drop(1).toTypedArray())
        } catch (e: Exception) {
            sender.sendMessage("${ChatColor.RED}An error occurred while executing command.")
            plugin.logger.severe("Failed to execute command from ${sender.name}: /$label ${args.joinToString(" ")}")
            e.printStackTrace()
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: org.bukkit.command.Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        try {
            if (args.isEmpty()) {
                return emptyList()
            } else if (args.size == 1) {
                return commands.keys.filter { !it.startsWith("*") && it.startsWith(args[0], true) }
                    .filter { sender.hasPermission("coreprotectextension.command.${commands[it]!!.name}") }
            } else {
                val cmd = commands[args[0].lowercase()] ?: return emptyList()
                if (!sender.hasPermission("coreprotectextension.command.${cmd.name}")) return emptyList()
                return cmd.suggest(sender, args.drop(1).toTypedArray())
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to suggest command to ${sender.name}: /$alias ${args.joinToString(" ")}")
            e.printStackTrace()
            return emptyList()
        }
    }

    init {
        registerCommand(HelpCommand(this))
        registerCommand(SQLQueryCommand(plugin))
        registerCommand(LookupCommand(plugin))
        registerCommand(LookupSelfCommand(plugin))
        registerCommand(ClearCacheCommand)
        registerCommand(InspectCommand(plugin))
    }
}
