package net.azisaba.coreprotectextension

import net.azisaba.coreprotectextension.commands.CommandManager
import net.azisaba.coreprotectextension.config.PluginConfig
import net.azisaba.coreprotectextension.database.CPDatabase
import net.azisaba.coreprotectextension.listener.PlayerListener
import org.bukkit.plugin.java.JavaPlugin
import xyz.acrylicstyle.util.reflector.Reflector
import xyz.acrylicstyle.util.reflector.executor.MethodExecutorReflection

class CoreProtectExtension : JavaPlugin() {
    lateinit var commandManager: CommandManager

    override fun onEnable() {
        Reflector.classLoader = classLoader
        Reflector.methodExecutor = MethodExecutorReflection()
        PluginConfig.load()
        commandManager = CommandManager(this)
        getCommand("cpe")?.setExecutor(commandManager)
        server.pluginManager.registerEvents(PlayerListener(this), this)
    }

    override fun onDisable() {
        CPDatabase.dataSource.close()
    }
}
