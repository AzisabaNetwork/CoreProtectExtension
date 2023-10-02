package net.azisaba.coreprotectextension

import net.azisaba.coreprotectextension.commands.CommandManager
import org.bukkit.plugin.java.JavaPlugin

class CoreProtectExtension : JavaPlugin() {
    override fun onEnable() {
        getCommand("cpe")?.setExecutor(CommandManager(this))
    }
}
