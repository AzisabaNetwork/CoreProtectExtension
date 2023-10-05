@file:Suppress("SqlSourceToSinkFlow", "SqlResolve", "SqlNoDataSourceInspection")

package net.azisaba.coreprotectextension.database

import net.azisaba.coreprotectextension.config.PluginConfig
import net.azisaba.coreprotectextension.model.User
import net.azisaba.coreprotectextension.model.ContainerLog
import net.azisaba.coreprotectextension.model.ContainerLookupResult
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
    val userCache: MutableSet<User> = Collections.synchronizedSet(mutableSetOf())
    val negativeUserCache: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    val worldCache: MutableSet<Pair<Int, String>> = Collections.synchronizedSet(mutableSetOf())

    fun getConnection(): Connection? = Database.getConnection(true, false, false, 1000)

    fun getConnectionOrThrow() = getConnection() ?: error("Connection is not available")

    fun getWorldId(name: String): Int? {
        worldCache.find { it.second.equals(name, true) }?.let { return it.first }
        return getConnectionOrThrow().use { conn ->
            conn.prepareStatement("SELECT `id` FROM `${ConfigHandler.prefix}world` WHERE LOWER(`world`) = ? LIMIT 1").use { ps ->
                ps.setString(1, name.lowercase())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getInt("id").also { worldCache.add(it to name) }
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun getWorldName(id: Int): String? {
        worldCache.find { it.first == id }?.let { return it.second }
        return getConnectionOrThrow().use { conn ->
            conn.prepareStatement("SELECT `world` FROM `${ConfigHandler.prefix}world` WHERE `id` = ? LIMIT 1").use { ps ->
                ps.setInt(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString("world").also { worldCache.add(id to it) }
                    } else {
                        null
                    }
                }
            }
        }
    }

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

    /**
     * Lookup the container logs.
     * @param radius Set to null to search everything of the world. Set to >=0 search by horizontal radius, and negative value to search by exact position.
     */
    fun lookupContainer(
        origin: Location?,
        user: String?,
        after: Instant?,
        before: Instant?,
        radius: Int?,
        page: Int = 0,
        resultsPerPage: Int = 5,
    ): ContainerLookupResult {
        val userId = user?.let { getUserByName(it)?.id }
        val wid = origin?.let { getWorldId(it.world.name) }
        val queryBuilder = QueryBuilder(
            "SELECT * FROM `${ConfigHandler.prefix}container`",
            suffix = "LIMIT $resultsPerPage OFFSET ${max(0, page) * resultsPerPage}",
        )
        queryBuilder.addWhereIfNotNull("user = ?", userId)
        if (origin != null && radius != null && radius >= 0) {
            queryBuilder.addWhere("wid = ?", wid)
            queryBuilder.addWhere("abs(x - ?) <= ?", origin.blockX, radius)
            queryBuilder.addWhere("abs(z - ?) <= ?", origin.blockZ, radius)
        }
        if (origin != null && radius != null && radius < 0) {
            queryBuilder.addWhere("wid = ?", wid)
            queryBuilder.addWhere("x = ?", origin.blockX)
            queryBuilder.addWhere("y = ?", origin.blockY)
            queryBuilder.addWhere("z = ?", origin.blockZ)
        }
        if (after != null) {
            queryBuilder.addWhere("time > ?", after.epochSecond)
        }
        if (before != null) {
            queryBuilder.addWhere("time < ?", before.epochSecond)
        }
        val list = mutableListOf<ContainerLog>()
        queryBuilder.executeQuery { rs ->
            while (rs.next()) {
                list.add(
                    ContainerLog(
                        LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(rs.getLong("time")),
                            PluginConfig.instance.getZoneId()
                        ),
                        getUserById(rs.getInt("user")) ?: User.unknown(rs.getInt("user")),
                        rs.getInt("wid"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        Util.getType(rs.getInt("type")),
                        rs.getInt("amount"),
                        rs.getBytes("metadata"),
                        ContainerLog.Action.fromInt(rs.getInt("action")),
                        rs.getInt("rolled_back") != 0,
                    )
                )
            }
        }

        queryBuilder.sql = "SELECT COUNT(*) FROM (SELECT 1 FROM `${ConfigHandler.prefix}container`"
        queryBuilder.suffix = "LIMIT ${resultsPerPage * 1000} OFFSET ${max(0, page) * resultsPerPage})"
        val lastPageIndex = if (page < 1000000) {
            queryBuilder.executeQuery { rs ->
                if (rs.next()) {
                    page + rs.getInt(1) / resultsPerPage
                } else {
                    -1
                }
            }
        } else {
            -1
        }

        return ContainerLookupResult(list, lastPageIndex)
    }
}
