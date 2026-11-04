package com.zepinto.resistance

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Stage {
    SETUP,
    /** Role cards, one face-down card at a time. */
    REVEAL,
    /** The mission track, the leader, and the way into the next round. */
    BOARD,
    PICK_TEAM,
    /** The phone is handed to the next voter; nothing secret is on screen. */
    VOTE_PASS,
    VOTE_CHOOSE,
    VOTE_RESULT,
    MISSION_PASS,
    MISSION_CHOOSE,
    MISSION_RESULT,
    GAME_OVER,
}

/** All the votes of a team, shown together once the last player has voted. */
data class VoteReveal(val team: List<Int>, val approvers: Set<Int>, val approved: Boolean)

/** The shuffled mission cards (true = fail) and how the mission ended. */
data class MissionReveal(val cards: List<Boolean>, val fails: Int, val needed: Int, val result: MissionResult)

data class UiState(
    val lang: Lang,
    val players: List<String>,
    val hasSaved: Boolean,
    val musicOn: Boolean = true,
    val stage: Stage = Stage.SETUP,
    val game: Game? = null,

    // role cards
    val order: List<Int> = emptyList(),
    val position: Int = 0,
    val cardUp: Boolean = false,
    val seen: Set<Int> = emptySet(),

    // the team being picked, and who is passing the phone round (voters, then the mission team)
    val selection: Set<Int> = emptySet(),
    val team: List<Int> = emptyList(),
    val ballot: List<Int> = emptyList(),
    val ballotPos: Int = 0,

    val voteReveal: VoteReveal? = null,
    val missionReveal: MissionReveal? = null,
) {
    val currentCard get() = order[position]
    val onLastCard get() = position == order.lastIndex
    val currentPlayer get() = ballot.getOrNull(ballotPos)
}

/**
 * The whole game on one phone. The secret choices (each vote, each mission card) are kept here and never put
 * in [UiState] until the last player has chosen, so nothing secret is ever on screen or in the state while the
 * phone is being passed round.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("resistance", Context.MODE_PRIVATE)
    private val sfx = Sfx(app)
    private val music = Music(app)
    private val approvers = HashSet<Int>()
    private val cards = ArrayList<Boolean>()

    private val _state = MutableStateFlow(
        UiState(
            lang = Lang.fromCode(prefs.getString(KEY_LANG, null)) ?: Lang.deviceDefault(),
            players = prefs.getString(KEY_PLAYERS, "").orEmpty().split('\n').filter { it.isNotBlank() }.distinctBy { it.lowercase() },
            hasSaved = loadSaved() != null,
            musicOn = prefs.getBoolean(KEY_MUSIC, true),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        music.setEnabled(_state.value.musicOn)
        // the music follows the game: the lobby tune, the table, the missions, and silence for the final sting
        viewModelScope.launch {
            _state.map { trackFor(it.stage) }.distinctUntilChanged().collect { music.play(it) }
        }
    }

    private fun trackFor(stage: Stage): Music.Track? = when (stage) {
        Stage.SETUP, Stage.REVEAL -> Music.Track.LOBBY
        Stage.BOARD, Stage.PICK_TEAM, Stage.VOTE_PASS, Stage.VOTE_CHOOSE, Stage.VOTE_RESULT -> Music.Track.TABLE
        Stage.MISSION_PASS, Stage.MISSION_CHOOSE, Stage.MISSION_RESULT -> Music.Track.MISSION
        Stage.GAME_OVER -> null
    }

    fun setMusic(on: Boolean) {
        _state.update { it.copy(musicOn = on) }
        music.setEnabled(on)
        prefs.edit().putBoolean(KEY_MUSIC, on).apply()
    }

    // ---- setup ----

    fun setLanguage(lang: Lang) {
        _state.update { it.copy(lang = lang) }
        prefs.edit().putString(KEY_LANG, lang.code).apply()
    }

    fun addPlayer(raw: String): Boolean {
        val name = raw.trim().take(MAX_NAME_LENGTH)
        val s = _state.value
        if (name.isEmpty() || s.players.size >= Rules.MAX_PLAYERS || s.players.any { it.equals(name, ignoreCase = true) }) return false
        _state.update { it.copy(players = it.players + name) }
        savePlayers()
        return true
    }

    fun removePlayer(index: Int) {
        _state.update { it.copy(players = it.players.filterIndexed { i, _ -> i != index }) }
        savePlayers()
    }

    fun startGame() {
        val players = _state.value.players
        if (players.size !in Rules.MIN_PLAYERS..Rules.MAX_PLAYERS) return
        val game = Rules.newGame(players)
        clearSaved()
        _state.update {
            it.copy(
                stage = Stage.REVEAL, game = game, hasSaved = false,
                order = players.indices.shuffled(), position = 0, cardUp = false, seen = emptySet(),
                selection = emptySet(), team = emptyList(), voteReveal = null, missionReveal = null,
            )
        }
    }

    fun resume() {
        val game = loadSaved() ?: return
        _state.update { it.copy(game = game, players = game.players, stage = Stage.BOARD, hasSaved = true) }
    }

    // ---- role cards ----

    /** Tap on the card: flip it up, or back down. The first flip chimes, flipping a seen card again buzzes. */
    fun flip() {
        val s = _state.value
        if (s.stage != Stage.REVEAL || s.order.isEmpty()) return
        if (s.cardUp) {
            _state.update { it.copy(cardUp = false) }
        } else {
            val card = s.currentCard
            sfx.play(if (card in s.seen) Cue.WARN else Cue.FLIP)
            _state.update { it.copy(cardUp = true, seen = it.seen + card) }
        }
    }

    fun hide() {
        if (_state.value.cardUp) _state.update { it.copy(cardUp = false) }
    }

    /** Pass the phone on, once this card has been seen and turned back over. */
    fun next() {
        val s = _state.value
        if (s.stage != Stage.REVEAL || s.cardUp || s.currentCard !in s.seen) return
        if (s.onLastCard) enterBoard() else _state.update { it.copy(position = it.position + 1) }
    }

    // ---- a round ----

    fun chooseTeam() {
        if (_state.value.stage == Stage.BOARD) _state.update { it.copy(stage = Stage.PICK_TEAM, selection = emptySet()) }
    }

    fun cancelPick() {
        if (_state.value.stage == Stage.PICK_TEAM) _state.update { it.copy(stage = Stage.BOARD, selection = emptySet()) }
    }

    fun toggleMember(index: Int) {
        val s = _state.value
        val game = s.game ?: return
        if (s.stage != Stage.PICK_TEAM || index !in 0 until game.n) return
        _state.update {
            val next = when {
                index in it.selection -> it.selection - index
                it.selection.size < game.teamSize -> it.selection + index
                else -> it.selection
            }
            it.copy(selection = next)
        }
    }

    /** The leader has picked the team: the secret vote begins with the leader and goes round the table. */
    fun proposeTeam() {
        val s = _state.value
        val game = s.game ?: return
        if (s.stage != Stage.PICK_TEAM || s.selection.size != game.teamSize) return
        approvers.clear()
        _state.update {
            it.copy(
                stage = Stage.VOTE_PASS,
                team = it.selection.sorted(),
                ballot = (0 until game.n).map { k -> (game.leader + k) % game.n },
                ballotPos = 0,
            )
        }
    }

    /** The person holding the phone is the right one: show them the secret screen. */
    fun ready() {
        when (_state.value.stage) {
            Stage.VOTE_PASS -> _state.update { it.copy(stage = Stage.VOTE_CHOOSE) }
            Stage.MISSION_PASS -> _state.update { it.copy(stage = Stage.MISSION_CHOOSE) }
            else -> Unit
        }
    }

    fun castVote(approve: Boolean) {
        val s = _state.value
        val game = s.game ?: return
        val voter = s.currentPlayer ?: return
        if (s.stage != Stage.VOTE_CHOOSE) return
        if (approve) approvers += voter
        sfx.play(Cue.BLIP)
        val pos = s.ballotPos + 1
        if (pos < s.ballot.size) {
            _state.update { it.copy(ballotPos = pos, stage = Stage.VOTE_PASS) }
            return
        }
        val yes = approvers.toSet()
        val next = Rules.vote(game, s.team, yes)
        val approved = Rules.isApproved(game.n, yes)
        sfx.play(if (approved) Cue.APPROVED else Cue.REJECTED)
        _state.update { it.copy(game = next, voteReveal = VoteReveal(s.team, yes, approved), stage = Stage.VOTE_RESULT, ballotPos = pos) }
    }

    /** Leave the vote result: on to the mission, to the next leader, or to the end of the game. */
    fun afterVote() {
        val game = _state.value.game ?: return
        if (_state.value.stage != Stage.VOTE_RESULT) return
        when {
            game.over -> enterGameOver()
            game.pendingTeam != null -> {
                cards.clear()
                _state.update { it.copy(stage = Stage.MISSION_PASS, ballot = game.pendingTeam, ballotPos = 0) }
            }
            else -> enterBoard()
        }
    }

    fun playCard(wantsFail: Boolean) {
        val s = _state.value
        val game = s.game ?: return
        val player = s.currentPlayer ?: return
        if (s.stage != Stage.MISSION_CHOOSE) return
        cards += Rules.playsFail(game.roles[player], wantsFail)
        sfx.play(Cue.BLIP)
        val pos = s.ballotPos + 1
        if (pos < s.ballot.size) {
            _state.update { it.copy(ballotPos = pos, stage = Stage.MISSION_PASS) }
            return
        }
        val fails = cards.count { it }
        val needed = game.failsNeeded
        val next = Rules.finishMission(game, fails)
        val result = next.history.last().result ?: return
        _state.update {
            it.copy(game = next, missionReveal = MissionReveal(cards.shuffled(), fails, needed, result), stage = Stage.MISSION_RESULT, ballotPos = pos)
        }
    }

    /** The mission cards are turned over. */
    fun revealMissionSound() {
        _state.value.missionReveal?.let { sfx.play(if (it.result == MissionResult.SUCCESS) Cue.SUCCESS else Cue.FAIL) }
    }

    fun afterMission() {
        val game = _state.value.game ?: return
        if (_state.value.stage != Stage.MISSION_RESULT) return
        if (game.over) enterGameOver() else enterBoard()
    }

    // ---- the end ----

    fun newGame() = startGame()

    fun backToSetup() {
        clearSaved()
        _state.update { it.copy(stage = Stage.SETUP, game = null, hasSaved = false, cardUp = false) }
    }

    /** The app left the foreground: nothing secret may stay on screen. */
    fun onPause() {
        music.pause()
        when (_state.value.stage) {
            Stage.REVEAL -> hide()
            Stage.VOTE_CHOOSE -> _state.update { it.copy(stage = Stage.VOTE_PASS) }
            Stage.MISSION_CHOOSE -> _state.update { it.copy(stage = Stage.MISSION_PASS) }
            else -> Unit
        }
    }

    fun onResume() = music.resume()

    // ---- helpers ----

    private fun enterBoard() {
        val game = _state.value.game ?: return
        save(game)
        _state.update { it.copy(stage = Stage.BOARD, cardUp = false, selection = emptySet(), hasSaved = true) }
    }

    private fun enterGameOver() {
        val game = _state.value.game ?: return
        clearSaved()
        sfx.play(if (game.winner == Side.RESISTANCE) Cue.WIN else Cue.LOSE)
        _state.update { it.copy(stage = Stage.GAME_OVER, hasSaved = false) }
    }

    private fun savePlayers() {
        prefs.edit().putString(KEY_PLAYERS, _state.value.players.joinToString("\n")).apply()
    }

    private fun save(g: Game) {
        if (g.over || g.pendingTeam != null) clearSaved() else prefs.edit().putString(KEY_GAME, GameCodec.save(g)).apply()
    }

    private fun clearSaved() {
        prefs.edit().remove(KEY_GAME).apply()
    }

    private fun loadSaved(): Game? = prefs.getString(KEY_GAME, null)?.let { GameCodec.load(it) }

    override fun onCleared() {
        sfx.release()
        music.release()
    }

    companion object {
        const val MAX_NAME_LENGTH = 20
        private const val KEY_LANG = "lang"
        private const val KEY_PLAYERS = "players"
        private const val KEY_GAME = "game"
        private const val KEY_MUSIC = "music"
    }
}
