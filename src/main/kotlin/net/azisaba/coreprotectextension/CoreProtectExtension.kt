package net.azisaba.coreprotectextension

import net.azisaba.coreprotectextension.commands.CommandManager
import net.azisaba.coreprotectextension.config.PluginConfig
import org.bukkit.plugin.java.JavaPlugin
import xyz.acrylicstyle.util.reflector.Reflector
import xyz.acrylicstyle.util.reflector.executor.MethodExecutorReflection

class CoreProtectExtension : JavaPlugin() {
    override fun onEnable() {
        Reflector.classLoader = classLoader
        Reflector.methodExecutor = MethodExecutorReflection()
        PluginConfig.load()
        getCommand("cpe")?.setExecutor(CommandManager(this))
    }
}
