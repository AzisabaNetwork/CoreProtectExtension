package net.azisaba.coreprotectextension.database

import org.intellij.lang.annotations.Language
import java.sql.ResultSet

class QueryBuilder(@Language("SQL") var sql: String, vararg values: Any?, var suffix: String = "") {
    private val values = values.toMutableList()
    private val where = mutableListOf<String>()

    fun addWhereIfNotNull(where: String, value: Any?) {
        if (value != null) {
            this.where.add("($where)")
            this.values.add(value)
        }
    }

    fun addWhere(where: String, vararg values: Any?) {
        this.where.add("($where)")
        this.values.addAll(values)
    }

    fun <R> executeQuery(action: (ResultSet) -> R): R {
        var sql = this.sql
        if (where.isNotEmpty()) {
            sql += " WHERE " + where.joinToString(" AND ")
        }
        sql += " $suffix"
        return CPDatabase.getConnectionOrThrow().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                values.forEachIndexed { index, obj ->
                    ps.setObject(index + 1, obj)
                }
                ps.executeQuery().use(action)
            }
        }
    }
}
