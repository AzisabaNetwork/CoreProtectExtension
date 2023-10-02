package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.database.CPDatabase
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

object SQLQueryCommand : Command {
    override val name = "sql-query"
    override val summary = "Query arbitrary SQL query."
    override val usage = listOf("<sql>")

    override fun execute(sender: CommandSender, args: Array<String>) {
        val sql = args.joinToString(" ")
        CPDatabase.getConnection()?.use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    var resultCount = 0
                    val labels = (1..rs.metaData.columnCount).map { rs.metaData.getColumnLabel(it) }
                    while (rs.next()) {
                        if (resultCount++ < 5) {
                            sender.sendMessage("${ChatColor.WHITE}---------- ${ChatColor.WHITE}Row${ChatColor.AQUA}#$resultCount ${ChatColor.GRAY}-----------")
                            labels.forEach { label ->
                                sender.sendMessage("${ChatColor.GOLD}$label ${ChatColor.GRAY}= ${ChatColor.WHITE}${rs.getObject(label)}")
                            }
                        }
                    }
                    if (resultCount > 5) {
                        sender.sendMessage("${ChatColor.GRAY}(${resultCount - 5} more rows...)")
                    }
                }
            }
        } ?: run {
            sender.sendMessage("${ChatColor.RED}Failed to obtain database connection.")
        }
    }
}
