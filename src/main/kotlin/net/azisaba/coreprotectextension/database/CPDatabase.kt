package net.azisaba.coreprotectextension.database

import net.coreprotect.database.Database
import java.sql.Connection

object CPDatabase {
    fun getConnection(): Connection? = Database.getConnection(true, false, false, 1000)
}
