@file:Suppress("SqlSourceToSinkFlow", "SqlResolve", "SqlNoDataSourceInspection")

package net.azisaba.coreprotectextension.database

import net.azisaba.coreprotectextension.config.PluginConfig
import net.azisaba.coreprotectextension.model.User
import net.azisaba.coreprotectextension.result.ContainerLookupResult
import net.coreprotect.config.ConfigHandler
import net.coreprotect.database.Database
import net.coreprotect.utility.Util
import org.bukkit.Location
import java.sql.Connection
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import kotlin.math.max

object CPDatabase {
    val userCache: MutableSet<User> = Collections.synchronizedSet(mutableSetOf<User>())
    val negativeUserCache: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    fun getConnection(): Connection? = Database.getConnection(true, false, false, 1000)

    fun getConnectionOrThrow() = getConnection() ?: error("Connection is not available")

    fun getUserById(id: Int): User? {
        userCache.find { it.id == id }?.let { return it }
        return getConnectionOrThrow().use { conn ->
            conn.prepareStatement("SELECT `user`, `uuid` FROM `${ConfigHandler.prefix}user` WHERE `id` = ?").use { ps ->
                ps.setInt(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        User(
                            id,
                            rs.getString("user"),
                            rs.getString("uuid")?.let { UUID.fromString(it) },
                        ).apply { userCache.add(this) }
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun getUserByName(name: String): User? {
        if (negativeUserCache.contains(name)) return null
        userCache.find { it.name == name }?.let { return it }
        return getConnectionOrThrow().use { conn ->
            conn.prepareStatement("SELECT `id`, `user`, `uuid` FROM `${ConfigHandler.prefix}user` WHERE LOWER(`user`) = ? OR LOWER(`uuid`) = ?")
                .use { ps ->
                    ps.setString(1, name.lowercase())
                    ps.setString(2, name.lowercase())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            User(
                                rs.getInt("id"),
                                rs.getString("user"),
                                UUID.fromString(rs.getString("uuid")),
                            ).apply { userCache.add(this) }
                        } else {
                            negativeUserCache.add(name)
                            null
                        }
                    }
                }
        }
    }

    fun lookupContainer(origin: Location?, user: String?, before: LocalDateTime?, after: LocalDateTime?, radius: Int?, page: Int = 0): List<ContainerLookupResult> {
        val userId = user?.let { getUserByName(it)?.id }
        val queryBuilder = QueryBuilder("SELECT * FROM `${ConfigHandler.prefix}container`", suffix = "LIMIT 5 OFFSET ${max(0, page) * 5}")
        queryBuilder.addWhereIfNotNull("`user` = ?", userId)
        if (origin != null && radius != null) {
            queryBuilder.addWhere("abs(x - ?) <= ?", origin.blockX, radius)
            queryBuilder.addWhere("abs(z - ?) <= ?", origin.blockZ, radius)
        }
        val list = mutableListOf<ContainerLookupResult>()
        queryBuilder.executeQuery { rs ->
            while (rs.next()) {
                list.add(ContainerLookupResult(
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(rs.getLong("time")), PluginConfig.instance.getZoneId()),
                    getUserById(rs.getInt("user")) ?: User.unknown(rs.getInt("user")),
                    rs.getInt("wid"),
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    Util.getType(rs.getInt("type")),
                    rs.getInt("amount"),
                    rs.getBytes("metadata"),
                    ContainerLookupResult.Action.fromInt(rs.getInt("action")),
                    rs.getInt("rolled_back") != 0,
                ))
            }
        }
        return list
    }
}
