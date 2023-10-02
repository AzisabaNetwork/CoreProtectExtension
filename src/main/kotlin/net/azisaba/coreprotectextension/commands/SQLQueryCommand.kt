package net.azisaba.coreprotectextension.commands

import net.azisaba.coreprotectextension.database.CPDatabase
import org.bukkit.command.CommandSender
import org.bukkit.ChatColor as C

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
                    val labels = (1..rs.metaData.columnCount).map { "${rs.metaData.getColumnLabel(it)}[${rs.metaData.getColumnTypeName(it)}]" }
                    while (rs.next()) {
                        if (resultCount++ < 50) {
                            labels.forEachIndexed { index, label ->
                                sender.sendMessage("${C.GOLD}$label ${C.GRAY}of ${C.AQUA}#$resultCount ${C.GRAY}= ${C.WHITE}${rs.getObject(index + 1)}")
                            }
                        }
                    }
                    if (resultCount > 50) {
                        sender.sendMessage("${C.GRAY}(${resultCount - 50} more results...)")
                    }
                }
            }
        } ?: run {
            sender.sendMessage("${C.RED}Failed to obtain database connection.")
        }
    }
}
