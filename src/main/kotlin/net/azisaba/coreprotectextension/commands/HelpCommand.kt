package net.azisaba.coreprotectextension.commands

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

class HelpCommand(private val commandManager: CommandManager) : Command {
    override val name = "help"
    override val aliases = setOf("?")
    override val summary = "Shows the usage of a command"
    override val description = """
        Execute ${ChatColor.YELLOW}/cpe ? <command>${ChatColor.AQUA} or ${ChatColor.YELLOW}/cpe help <command>
        ${ChatColor.AQUA}to get details of the command!
    """.trimIndent()
    override val usage = listOf("<command>")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) return sender.sendMessage("${ChatColor.RED}$fullUsage")
        val command = commandManager.commands[args[0].lowercase()]
            ?: return sender.sendMessage("${ChatColor.RED}Invalid sub command: ${args[0]}")
        val names = command.aliases.toMutableList()
        names.add(0, command.name)
        sender.sendMessage("${ChatColor.GOLD}Name(s): ${ChatColor.AQUA}${names.joinToString(", ")}")
        sender.sendMessage("${ChatColor.GOLD}Summary: ${ChatColor.AQUA}${command.summary}")
        if (summary != description) {
            sender.sendMessage("${ChatColor.GOLD}Description:")
            sender.sendMessage(" ${ChatColor.AQUA} " + description.replace("\n", " "))
        }
        sender.sendMessage("${ChatColor.GOLD}Usage: ${ChatColor.AQUA}$fullUsage")
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            return commandManager.commands.keys.filter { it.startsWith(args[0], true) }
        }
        return emptyList()
    }
}
