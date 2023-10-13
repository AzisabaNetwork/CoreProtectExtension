@file:Suppress("SqlSourceToSinkFlow")

package net.azisaba.coreprotectextension.database

import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement

fun <R> Connection.execute(@Language("SQL") query: String, action: (PreparedStatement) -> R): R =
    prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use(action)
