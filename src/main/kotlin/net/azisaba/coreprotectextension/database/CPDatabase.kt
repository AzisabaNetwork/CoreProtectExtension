@file:Suppress("SqlSourceToSinkFlow", "SqlResolve", "SqlNoDataSourceInspection")

package net.azisaba.coreprotectextension.database

import net.azisaba.coreprotectextension.config.PluginConfig
import net.azisaba.coreprotectextension.model.User
import net.azisaba.coreprotectextension.model.ContainerLog
import net.azisaba.coreprotectextension.model.ContainerLookupResult
import net.azisaba.coreprotectextension.model.LookupException
import net.azisaba.coreprotectextension.util.NumberOperation
import net.coreprotect.config.ConfigHandler
import net.coreprotect.database.Database
import net.coreprotect.utility.Util
import org.bukkit.Location
import org.bukkit.Material
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
                                rs.getString("uuid")?.let { UUID.fromString(it) },
                            ).apply { userCache.add(this) }
                        } else {
                            negativeUserCache.add(name)
                            null
                        }
                    }
                }
        }
    }

    private fun matchMaterials(regex: Regex): Set<Material> =
        Material.entries
            .filter { !it.name.startsWith("LEGACY_") }
            .filter { regex.matchEntire(it.name) != null }
            .toSet()

    private fun fillQueryBuilderIncludeExcludeItems(queryBuilder: QueryBuilder, include: String?, exclude: String?) {
        include?.split(",")
            ?.flatMap { matchMaterials(it.toRegex(RegexOption.IGNORE_CASE)) }
            ?.joinToString(" OR ") { "type = " + Util.getMaterialId(it) }
                ?.let { queryBuilder.addWhere(it) }
        exclude?.split(",")?.flatMap { entry ->
            mutableListOf<String>().also { list ->
                try {
                    list += matchMaterials(entry.toRegex(RegexOption.IGNORE_CASE)).map { "type != " + Util.getMaterialId(it) }
                } catch (ignored: IllegalArgumentException) {}
                getUserByName(entry)?.id?.let { list += listOf("user != $it") }
            }
        }?.joinToString(" AND ")?.let { queryBuilder.addWhere(it) }
    }

    private fun fillQueryBuilderGeneric(
        queryBuilder: QueryBuilder,
        origin: Location?,
        user: String?,
        after: Instant?,
        before: Instant?,
        radius: Int?,
    ) {
        val userIds = user?.let { it.split(",").map { name -> (getUserByName(name) ?: LookupException.throwNoUser(name)).id } }
        val wid = origin?.let { getWorldId(it.world.name) }
        userIds?.let {
            queryBuilder.addWhere("user IN (${it.joinToString(", ") { "?" }})", *it.toTypedArray())
        }
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
    }

    private fun getLastPageIndex(queryBuilder: QueryBuilder, page: Int, resultsPerPage: Int) =
        if (page < 1000000) {
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

    /**
     * Lookup the item logs.
     * @param radius Set to null to search everything of the world. Set to >=0 search by horizontal radius, and negative value to search by exact position.
     */
    fun lookupItem(
        filterAction: ContainerLog.Action?,
        origin: Location?,
        user: String?,
        after: Instant?,
        before: Instant?,
        include: String?,
        exclude: String?,
        filterAmount: NumberOperation<Int>? = null,
        radius: Int?,
        page: Int = 0,
        resultsPerPage: Int = 5,
    ): ContainerLookupResult {
        val queryBuilder = QueryBuilder(
            "SELECT * FROM `${ConfigHandler.prefix}item`",
            suffix = "ORDER BY `time` ASC LIMIT $resultsPerPage OFFSET ${max(0, page) * resultsPerPage}",
        )
        when (filterAction) {
            ContainerLog.Action.REMOVED -> queryBuilder.addWhere("action = 2") // drop
            ContainerLog.Action.ADDED -> queryBuilder.addWhere("action = 3") // pickup
            else -> queryBuilder.addWhere("action = 2 OR action = 3") // both
        }
        if (filterAmount != null) queryBuilder.addWhere("amount ${filterAmount.type.op} ${filterAmount.number}")
        fillQueryBuilderGeneric(queryBuilder, origin, user, after, before, radius)
        fillQueryBuilderIncludeExcludeItems(queryBuilder, include, exclude)
        val list = mutableListOf<ContainerLog>()
        queryBuilder.executeQuery { rs ->
            while (rs.next()) {
                val action = when (rs.getInt("action")) {
                    2 -> ContainerLog.Action.REMOVED
                    3 -> ContainerLog.Action.ADDED
                    else -> null
                }
                if (action != null) {
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
                            rs.getBytes("data"),
                            action,
                            rs.getInt("rolled_back") != 0,
                        )
                    )
                }
            }
        }

        queryBuilder.sql = "SELECT COUNT(*) FROM (SELECT 1 FROM `${ConfigHandler.prefix}item`"
        queryBuilder.suffix = "LIMIT ${resultsPerPage * 1000} OFFSET ${max(0, page) * resultsPerPage})"
        return ContainerLookupResult(list, getLastPageIndex(queryBuilder, page, resultsPerPage))
    }

    /**
     * Lookup the container logs.
     * @param radius Set to null to search everything of the world. Set to >=0 search by horizontal radius, and negative value to search by exact position.
     */
    fun lookupContainer(
        filterAction: ContainerLog.Action? = null,
        origin: Location? = null,
        user: String? = null,
        after: Instant? = null,
        before: Instant? = null,
        include: String? = null,
        exclude: String? = null,
        filterAmount: NumberOperation<Int>? = null,
        radius: Int? = null,
        page: Int = 0,
        resultsPerPage: Int = 5,
    ): ContainerLookupResult {
        val queryBuilder = QueryBuilder(
            "SELECT * FROM `${ConfigHandler.prefix}container`",
            suffix = "ORDER BY `time` ASC LIMIT $resultsPerPage OFFSET ${max(0, page) * resultsPerPage}",
        )
        when (filterAction) {
            ContainerLog.Action.REMOVED -> queryBuilder.addWhere("action = 0")
            ContainerLog.Action.ADDED -> queryBuilder.addWhere("action = 1")
            else -> {}
        }
        if (filterAmount != null) queryBuilder.addWhere("amount ${filterAmount.type.op} ${filterAmount.number}")
        fillQueryBuilderGeneric(queryBuilder, origin, user, after, before, radius)
        fillQueryBuilderIncludeExcludeItems(queryBuilder, include, exclude)
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
                        ContainerLog.Action.entries[rs.getInt("action")],
                        rs.getInt("rolled_back") != 0,
                    )
                )
            }
        }

        queryBuilder.sql = "SELECT COUNT(*) FROM (SELECT 1 FROM `${ConfigHandler.prefix}container`"
        queryBuilder.suffix = "LIMIT ${resultsPerPage * 1000} OFFSET ${max(0, page) * resultsPerPage})"
        return ContainerLookupResult(list, getLastPageIndex(queryBuilder, page, resultsPerPage))
    }
}
