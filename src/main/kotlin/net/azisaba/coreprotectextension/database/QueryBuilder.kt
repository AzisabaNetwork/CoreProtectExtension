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
        println("[CoreProtectExtension] Query: $sql")
        // SELECT COUNT(*) FROM (SELECT 1 FROM `co_container` WHERE (wid = 9) AND (x = 747) AND (y = 71) AND (z = 1697) LIMIT 10000 OFFSET 0)
        return CPDatabase.getConnectionOrThrow().execute(sql) { ps ->
            values.forEachIndexed { index, obj ->
                when (obj) {
                    is Int -> ps.setInt(index + 1, obj)
                    is Long -> ps.setLong(index + 1, obj)
                    is String -> ps.setString(index + 1, obj)
                    is Boolean -> ps.setBoolean(index + 1, obj)
                    is Double -> ps.setDouble(index + 1, obj)
                    is Float -> ps.setFloat(index + 1, obj)
                    is Byte -> ps.setByte(index + 1, obj)
                    is ByteArray -> ps.setBytes(index + 1, obj)
                    is Short -> ps.setShort(index + 1, obj)
                    is java.sql.Date -> ps.setDate(index + 1, obj)
                    is java.sql.Time -> ps.setTime(index + 1, obj)
                    is java.sql.Timestamp -> ps.setTimestamp(index + 1, obj)
                    else -> ps.setObject(index + 1, obj)
                }
            }
            ps.executeQuery().use(action)
        }
    }
}
