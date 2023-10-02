package net.azisaba.coreprotectextension.commands

import org.bukkit.command.CommandSender

interface Command {
    val name: String
    val aliases: Set<String>
        get() = emptySet()
    val summary: String
        get() = "No description defined."
    val description: String
        get() = summary
    val usage: List<String>
        get() = emptyList()
    val fullUsage: String
        get() = "/cpe $name ${usage.ifEmpty { "" }}".trim()

    fun execute(sender: CommandSender, args: Array<String>)

    fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        return if (args.isEmpty() || (args.size == 1 && args[0].isBlank())) {
            usage
        } else {
            emptyList()
        }
    }
}
