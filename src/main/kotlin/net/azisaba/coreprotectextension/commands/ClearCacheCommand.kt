package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.database.CPDatabase
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

object ClearCacheCommand : Command {
    override val name = "clear-cache"

    override fun execute(sender: CommandSender, args: Array<String>) {
        CPDatabase.userCache.clear()
        CPDatabase.negativeUserCache.clear()
        sender.sendMessage("${ChatColor.GREEN}Cleared user cache")
    }
}
