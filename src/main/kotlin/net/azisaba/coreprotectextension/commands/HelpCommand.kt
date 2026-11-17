package net.azisaba.coreprotectextension.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

class HelpCommand(private val commandManager: CommandManager) : Command {
    override val name = "help"
    override val aliases = setOf("?")
    override val summary = "Shows the usage of a command"
    override val description = """
        Execute <yellow>/cpe ? (command)<aqua> or <yellow>/cpe help (command)
        <aqua>to get details of the command!
    """.trimIndent()
    override val usage = listOf("<command>")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) return sender.sendMessage(Component.text(fullUsage, NamedTextColor.RED))
        val command = commandManager.commands[args[0].lowercase()]
            ?: return sender.sendMessage(Component.text("Invalid sub command: ${args[0]}", NamedTextColor.RED))
        val names = command.aliases.toMutableList()
        names.add(0, command.name)
        sender.sendRichMessage("<gold>Name(s): <aqua>${names.joinToString(", ")}")
        sender.sendRichMessage("<gold>Summary: <aqua>${command.summary}")
        if (command.summary != command.description) {
            sender.sendRichMessage("<gold>Description:")
            sender.sendRichMessage(" <aqua> " + command.description.replace("\n", " "))
        }
        sender.sendRichMessage("<gold>Usage: <aqua>${command.fullUsage}")
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            return commandManager.commands.keys.filter { it.startsWith(args[0], true) }
        }
        return emptyList()
    }
}
