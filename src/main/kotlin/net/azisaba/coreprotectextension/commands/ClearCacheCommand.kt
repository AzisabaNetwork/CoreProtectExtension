package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.database.CPDatabase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender

object ClearCacheCommand : Command {
    override val name = "clear-cache"

    override fun execute(sender: CommandSender, args: Array<String>) {
        CPDatabase.userCache.clear()
        CPDatabase.negativeUserCache.clear()
        sender.sendMessage(Component.text("Cleared user cache", NamedTextColor.GREEN))
    }
}
