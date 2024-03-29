package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.CoreProtectExtension
import net.azisaba.coreprotectextension.util.NumberOperation
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class LookupSelfCommand(private val plugin: CoreProtectExtension) : Command {
    override val name = "lookup-self"
    override val summary = "Lookup container logs by arguments."
    override val description = "$summary \"time:(time)\" in CoreProtect is \"after=(time)\" in CoreProtectExtension."
    override val usage = listOf("<action=> [radius=] [page=] [before=] [after=] [include=] [exclude=] [amount=]")
    private val params = listOf("action=", "radius=", "page=", "before=", "after=", "include=", "exclude=", "amount=")
    private val running = Collections.synchronizedSet(mutableSetOf<UUID>())

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            return sender.sendMessage("${ChatColor.RED}This command must be executed by the player.")
        }
        if (running.contains(sender.uniqueId)) {
            return sender.sendMessage("${ChatColor.RED}You are already running this command.")
        }
        running.add(sender.uniqueId)
        LookupCommand.execute(plugin, sender, args, sender.name, "lookup-self").thenRun {
            running.remove(sender.uniqueId)
        }
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        if (args.last().startsWith("action=")) {
            return LookupCommand.validActions.map { "action=$it" }.filter { it.startsWith(args.last(), true) }
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
