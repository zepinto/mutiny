package com.zepinto.mutiny

import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

enum class Role { LOYAL, MUTINEER }
enum class Side { LOYAL, MUTINEERS }
enum class VoyageResult { SUCCESS, FAIL }
enum class WinReason { VOYAGES, REJECTIONS }

/** One proposed team, the vote on it and, if it was approved, what happened on the voyage. */
data class Round(
    val voyage: Int,
    val captain: Int,
    val team: List<Int>,
    /** Players who voted to approve the team. */
    val approvers: Set<Int>,
    val approved: Boolean,
    /** Fail cards played, known once the voyage has been played. */
    val fails: Int? = null,
    val result: VoyageResult? = null,
)

data class Game(
    val players: List<String>,
    val roles: List<Role>,
    /** Index of the player who proposes the next team. The role passes clockwise (down the list). */
    val captain: Int,
    /** The voyage being played, 0 to 4. */
    val voyage: Int,
    val results: List<VoyageResult?>,
    /** Teams rejected in a row since the last voyage was played. */
    val rejects: Int,
    /** An approved team waiting to play its voyage. */
    val pendingTeam: List<Int>? = null,
    val winner: Side? = null,
    val winReason: WinReason? = null,
    val history: List<Round> = emptyList(),
) {
    val n get() = players.size
    val over get() = winner != null
    val successes get() = results.count { it == VoyageResult.SUCCESS }
    val failures get() = results.count { it == VoyageResult.FAIL }
    val teamSize get() = Rules.teamSize(n, voyage)
    val failsNeeded get() = Rules.failsNeeded(n, voyage)
    val mutineers get() = roles.indices.filter { roles[it] == Role.MUTINEER }
}

object Rules {
    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 10
    const val VOYAGES = 5
    const val WINS_NEEDED = 3
    const val MAX_REJECTS = 5

    private val MUTINEERS = mapOf(5 to 2, 6 to 2, 7 to 3, 8 to 3, 9 to 3, 10 to 4)

    /** Players on the team of each of the 5 voyages, by number of players. */
    private val TEAMS = mapOf(
        5 to listOf(2, 3, 2, 3, 3),
        6 to listOf(2, 3, 4, 3, 4),
        7 to listOf(2, 3, 3, 4, 4),
        8 to listOf(3, 4, 4, 5, 5),
        9 to listOf(3, 4, 4, 5, 5),
        10 to listOf(3, 4, 4, 5, 5),
    )

    fun mutineerCount(players: Int) = MUTINEERS.getValue(players)
    fun teamSize(players: Int, voyage: Int) = TEAMS.getValue(players)[voyage]

    /** Fail cards a voyage needs to fail: one, except the fourth voyage with 7 or more players, which needs two. */
    fun failsNeeded(players: Int, voyage: Int) = if (players >= 7 && voyage == 3) 2 else 1

    fun newGame(players: List<String>, rng: Random = Random.Default): Game {
        require(players.size in MIN_PLAYERS..MAX_PLAYERS) { "the game is for $MIN_PLAYERS to $MAX_PLAYERS players" }
        val roles = (List(mutineerCount(players.size)) { Role.MUTINEER } + List(players.size - mutineerCount(players.size)) { Role.LOYAL }).shuffled(rng)
        return Game(
            players = players,
            roles = roles,
            captain = rng.nextInt(players.size),
            voyage = 0,
            results = List(VOYAGES) { null },
            rejects = 0,
        )
    }

    /** A team is approved only by a strict majority: a tie rejects it. */
    fun isApproved(players: Int, approvers: Set<Int>) = approvers.size * 2 > players

    /**
     * The captain's team has been voted on. Approved: the team waits to play its voyage.
     * Rejected: the captaincy passes on, and the fifth rejection in a row wins the game for the mutineers.
     */
    fun vote(g: Game, team: List<Int>, approvers: Set<Int>): Game {
        if (g.over || g.pendingTeam != null) return g
        if (team.size != g.teamSize || team.toSet().size != team.size || team.any { it !in 0 until g.n }) return g
        if (approvers.any { it !in 0 until g.n }) return g
        val approved = isApproved(g.n, approvers)
        val round = Round(g.voyage, g.captain, team, approvers, approved)
        if (approved) {
            return g.copy(pendingTeam = team, history = g.history + round)
        }
        val rejects = g.rejects + 1
        val next = g.copy(captain = (g.captain + 1) % g.n, rejects = rejects, history = g.history + round)
        return if (rejects >= MAX_REJECTS) next.copy(winner = Side.MUTINEERS, winReason = WinReason.REJECTIONS) else next
    }

    /** What a player puts on the voyage: only a mutineer can choose to fail it. True means a fail card. */
    fun playsFail(role: Role, wantsFail: Boolean) = role == Role.MUTINEER && wantsFail

    /**
     * The approved team has played, with [fails] fail cards among their cards. The voyage is decided,
     * the captaincy passes on and the count of rejections starts again. Three voyages of one kind win the game.
     */
    fun finishVoyage(g: Game, fails: Int): Game {
        val team = g.pendingTeam ?: return g
        if (g.over || fails !in 0..team.size) return g
        val result = if (fails >= g.failsNeeded) VoyageResult.FAIL else VoyageResult.SUCCESS
        val results = g.results.toMutableList().also { it[g.voyage] = result }
        val history = g.history.toMutableList().also { it[it.lastIndex] = it.last().copy(fails = fails, result = result) }
        val next = g.copy(
            results = results,
            history = history,
            pendingTeam = null,
            rejects = 0,
            captain = (g.captain + 1) % g.n,
            voyage = (g.voyage + 1).coerceAtMost(VOYAGES - 1),
        )
        return when {
            next.successes >= WINS_NEEDED -> next.copy(winner = Side.LOYAL, winReason = WinReason.VOYAGES)
            next.failures >= WINS_NEEDED -> next.copy(winner = Side.MUTINEERS, winReason = WinReason.VOYAGES)
            else -> next
        }
    }
}

/** Saving a game in progress, so that closing the app does not lose it. */
object GameCodec {
    private fun ints(l: Collection<Int>) = JSONArray(l.toList())
    private fun parseInts(a: JSONArray) = List(a.length()) { a.getInt(it) }

    fun save(g: Game): String = JSONObject()
        .put("players", JSONArray(g.players))
        .put("roles", JSONArray(g.roles.map { it.ordinal }))
        .put("captain", g.captain).put("voyage", g.voyage)
        .put("results", JSONArray(g.results.map { it?.ordinal ?: -1 }))
        .put("rejects", g.rejects)
        .put("pending", g.pendingTeam?.let { ints(it) } ?: JSONObject.NULL)
        .put("winner", g.winner?.ordinal ?: -1).put("why", g.winReason?.ordinal ?: -1)
        .put(
            "history",
            JSONArray(
                g.history.map {
                    JSONObject().put("m", it.voyage).put("l", it.captain).put("team", ints(it.team)).put("yes", ints(it.approvers.sorted()))
                        .put("ok", it.approved).put("fails", it.fails ?: -1).put("res", it.result?.ordinal ?: -1)
                }
            )
        )
        .toString()

    fun load(text: String): Game? = try {
        val o = JSONObject(text)
        val players = o.getJSONArray("players").let { a -> List(a.length()) { a.getString(it) } }
        val roles = parseInts(o.getJSONArray("roles")).map { Role.entries[it] }
        val results = parseInts(o.getJSONArray("results")).map { n -> n.takeIf { it >= 0 }?.let { VoyageResult.entries[it] } }
        require(players.size in Rules.MIN_PLAYERS..Rules.MAX_PLAYERS && roles.size == players.size && results.size == Rules.VOYAGES)
        val history = o.getJSONArray("history").let { a ->
            List(a.length()) {
                val r = a.getJSONObject(it)
                Round(
                    voyage = r.getInt("m"), captain = r.getInt("l"), team = parseInts(r.getJSONArray("team")),
                    approvers = parseInts(r.getJSONArray("yes")).toSet(), approved = r.getBoolean("ok"),
                    fails = r.getInt("fails").takeIf { f -> f >= 0 },
                    result = r.getInt("res").takeIf { s -> s >= 0 }?.let { s -> VoyageResult.entries[s] },
                )
            }
        }
        val seats = players.indices
        history.forEach { r ->
            require(r.captain in seats && r.team.all { it in seats } && r.approvers.all { it in seats } && r.voyage in 0 until Rules.VOYAGES)
        }
        Game(
            players = players,
            roles = roles,
            captain = o.getInt("captain").also { require(it in players.indices) },
            voyage = o.getInt("voyage").also { require(it in 0 until Rules.VOYAGES) },
            results = results,
            rejects = o.getInt("rejects").also { require(it in 0 until Rules.MAX_REJECTS) },
            pendingTeam = if (o.isNull("pending")) null else parseInts(o.getJSONArray("pending")).also { p -> require(p.all { it in seats }) },
            winner = o.getInt("winner").takeIf { it >= 0 }?.let { Side.entries[it] },
            winReason = o.getInt("why").takeIf { it >= 0 }?.let { WinReason.entries[it] },
            history = history,
        )
    } catch (_: Exception) {
        null
    }
}
