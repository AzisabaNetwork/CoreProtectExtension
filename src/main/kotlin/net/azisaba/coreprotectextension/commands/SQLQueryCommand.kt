package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.CoreProtectExtension
import net.azisaba.coreprotectextension.database.CPDatabase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.ChatColor as C

class SQLQueryCommand(private val plugin: CoreProtectExtension) : Command {
    override val name = "sql-query"
    override val summary = "Query arbitrary SQL query."
    override val usage = listOf("(sql)")

    override fun execute(sender: CommandSender, args: Array<String>) {
        val sql = args.joinToString(" ")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            CPDatabase.getConnection()?.use { conn ->
                conn.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rs ->
                        var resultCount = 0
                        val labels = (1..rs.metaData.columnCount).map {
                            "${rs.metaData.getColumnLabel(it)}[${
                                rs.metaData.getColumnTypeName(it)
                            }]"
                        }
                        while (rs.next()) {
                            if (resultCount++ < 50) {
                                labels.forEachIndexed { index, label ->
                                    sender.sendMessage(
                                        Component.text(label, NamedTextColor.GOLD)
                                            .append(Component.text(" of ", NamedTextColor.GRAY))
                                            .append(Component.text("#$resultCount", NamedTextColor.AQUA))
                                            .append(Component.text(" = ", NamedTextColor.GRAY))
                                            .append(Component.text("${rs.getObject(index + 1)}", NamedTextColor.WHITE))
                                    )
                                }
                            }
                        }
                        if (resultCount > 50) {
                            sender.sendMessage(Component.text("(${resultCount - 50} more results...)", NamedTextColor.GRAY))
                        }
                    }
                }
            } ?: run {
                sender.sendMessage(Component.text("Failed to obtain database connection.", NamedTextColor.RED))
            }
        })
    }
}
