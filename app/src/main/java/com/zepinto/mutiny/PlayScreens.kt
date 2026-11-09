package com.zepinto.mutiny

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role as A11yRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** The five voyages: the team size to play, then a tick or a cross once decided. The current one is ringed. */
@Composable
internal fun VoyageTrack(game: Game, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (m in 0 until Rules.VOYAGES) {
            val result = game.results[m]
            val current = !game.over && m == game.voyage
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            when (result) {
                                VoyageResult.SUCCESS -> Palette.Blue
                                VoyageResult.FAIL -> Palette.Red
                                null -> Color.White.copy(alpha = 0.1f)
                            }
                        )
                        .then(if (current) Modifier.border(3.dp, Palette.Gold, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (result) {
                            VoyageResult.SUCCESS -> "✓"
                            VoyageResult.FAIL -> "✕"
                            null -> "${Rules.teamSize(game.n, m)}"
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                // "x2": this voyage needs two fail cards
                Text(if (Rules.failsNeeded(game.n, m) == 2) "×2" else " ", fontSize = 12.sp, color = Palette.Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RejectTrack(rejects: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(Rules.MAX_REJECTS) { i ->
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (i < rejects) Palette.Red else Color.White.copy(alpha = 0.14f))
            )
        }
    }
}

@Composable
private fun PlayerTag(name: String, index: Int, captain: Boolean) {
    Surface(
        shape = Pill,
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.then(if (captain) Modifier.border(2.dp, Palette.Gold, Pill) else Modifier),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, index, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            if (captain) Text(" 🧭", fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------- the board

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardScreen(state: UiState, onChooseTeam: () -> Unit, onHistory: () -> Unit, onRules: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val captainName = game.players[game.captain]
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(t.scoreLine(game.successes, game.failures), Palette.Gold)
            Spacer(Modifier.weight(1f))
            HelpButton(onRules)
        }
        Text(t.voyageOf(game.voyage + 1), fontSize = 32.sp, fontWeight = FontWeight.Black)
        VoyageTrack(game)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RejectTrack(game.rejects)
            Text(t.rejectedProposals(game.rejects), fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
        }

        if (game.rejects == Rules.MAX_REJECTS - 1) Text("⚠️ ${t.lastRejectWarning}", color = Palette.Red, fontWeight = FontWeight.Bold)

        Surface(
            shape = Soft,
            color = Palette.Gold.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth().border(2.dp, Palette.Gold, Soft),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(captainName, game.captain, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("🧭 ${t.captainIs(captainName)}", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(t.chooseTeamHint(game.teamSize), color = Color.White.copy(alpha = 0.85f))
                    if (game.failsNeeded == 2) Text(t.failsNeeded(2), color = Palette.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        PillButton(t.chooseTeam, onChooseTeam, Modifier.fillMaxWidth())
        TextButton(onClick = onHistory, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("📜 ${t.history}", color = Palette.Aqua, fontWeight = FontWeight.Bold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            game.players.forEachIndexed { i, p -> PlayerTag(p, i, captain = i == game.captain) }
        }
    }
}

@Composable
internal fun PickTeamScreen(state: UiState, onToggle: (Int) -> Unit, onPropose: () -> Unit, onBack: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← ${t.back}", color = Palette.Aqua, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Text(t.pickTitle, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("🧭 ${t.captainIs(game.players[game.captain])} · ${t.chooseTeamHint(game.teamSize)}", color = Palette.Lilac)
        Chip(t.pickCount(state.selection.size, game.teamSize), Palette.Gold)
        if (game.rejects == Rules.MAX_REJECTS - 1) Text("⚠️ ${t.lastRejectWarning}", color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            game.players.forEachIndexed { i, p ->
                val picked = i in state.selection
                Surface(
                    shape = Soft,
                    color = if (picked) Palette.Aqua.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, if (picked) Palette.Aqua else Color.Transparent, Soft)
                        .clip(Soft)
                        .clickable { onToggle(i) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, i)
                        Spacer(Modifier.width(12.dp))
                        Text(p, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (i == game.captain) Text("🧭", fontSize = 20.sp)
                        if (picked) Text("  ✅", fontSize = 22.sp)
                    }
                }
            }
        }
        PillButton(t.proposeTeam, onPropose, Modifier.fillMaxWidth(), enabled = state.selection.size == game.teamSize)
    }
}

// ---------------------------------------------------------------- the secret vote and the voyage cards

/** Nothing secret on screen: just whose turn it is to take the phone. */
@Composable
internal fun PassScreen(state: UiState, voting: Boolean, onReady: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val player = state.currentPlayer ?: return
    val name = game.players[player]
    // a fast double tap on the previous screen's button must not skip straight past this hand-over
    val shownAt = remember(state.stage, state.ballotPos) { SystemClock.uptimeMillis() }
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Chip(if (voting) "🗳️ ${t.voteTitle}" else "🃏 ${t.voyageOf(game.voyage + 1)}", Palette.Gold)
        Spacer(Modifier.height(28.dp))
        Avatar(name, player, size = 110.dp)
        Spacer(Modifier.height(18.dp))
        Text(t.passPhoneTo(name), fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(t.progressOf(state.ballotPos + 1, state.ballot.size), color = Palette.Lilac, fontSize = 16.sp)
        Spacer(Modifier.height(30.dp))
        PillButton(t.imName(name), { if (SystemClock.uptimeMillis() - shownAt > 500) onReady() }, Modifier.fillMaxWidth())
    }
}

/** The secret choice: two big options and a confirm button, so a stray tap cannot decide anything. */
@Composable
internal fun ChoiceScreen(state: UiState, voting: Boolean, onChoose: (Boolean) -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val player = state.currentPlayer ?: return
    val name = game.players[player]
    var picked by rememberSaveable(state.stage, state.ballotPos) { mutableIntStateOf(-1) } // 0 = approve/success, 1 = reject/fail
    // the two cards swap sides at random for every player, so the position of a tap says nothing to a neighbour
    val swapped by rememberSaveable(state.stage, state.ballotPos) { mutableStateOf(Random.nextBoolean()) }

    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Chip(if (voting) "🗳️ ${t.voteTitle}" else "🃏 ${t.voyageOf(game.voyage + 1)}", Palette.Gold)
        Spacer(Modifier.height(16.dp))
        Avatar(name, player, size = 72.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (voting) t.voteQuestion(name) else t.voyageQuestion(name),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        if (voting) {
            Spacer(Modifier.height(8.dp))
            Text(t.teamIs(state.team.joinToString(", ") { game.players[it] }), color = Palette.Lilac, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                Option(if (voting) "👍" else "⚓", if (voting) t.approve else t.success, if (voting) Palette.Mint else Palette.Blue, 0),
                Option(if (voting) "👎" else "💥", if (voting) t.reject else t.fail, Palette.Red, 1),
            ).let { if (swapped) it.reversed() else it }
            options.forEach { o ->
                ChoiceCard(o.emoji, o.label, o.color, selected = picked == o.value, modifier = Modifier.weight(1f)) { picked = o.value }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(t.secretHint, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(16.dp))
        PillButton(
            if (voting) t.confirmChoice else t.playCard,
            { onChoose(picked == 0) },
            Modifier.fillMaxWidth(),
            enabled = picked >= 0,
        )
    }
}

private class Option(val emoji: String, val label: String, val color: Color, val value: Int)

@Composable
private fun ChoiceCard(emoji: String, label: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = Soft,
        color = color.copy(alpha = if (selected) 0.4f else 0.18f),
        modifier = modifier
            .aspectRatio(0.85f)
            .border(if (selected) 4.dp else 2.dp, if (selected) Color.White else color.copy(alpha = 0.6f), Soft)
            .clip(Soft)
            .selectable(selected = selected, onClick = onClick, role = A11yRole.RadioButton),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 52.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

// ---------------------------------------------------------------- results

@Composable
internal fun VoteResultScreen(state: UiState, onContinue: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val vote = state.voteReveal ?: return
    val yes = game.players.indices.filter { it in vote.approvers }
    val no = game.players.indices.filter { it !in vote.approvers }
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = Soft, color = if (vote.approved) Palette.Mint else Palette.Red, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (vote.approved) "✅ ${t.voteApproved}" else "❌ ${t.voteRejected}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = if (vote.approved) Palette.Ink else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(18.dp),
            )
        }
        Text(t.teamIs(vote.team.joinToString(", ") { game.players[it] }), color = Palette.Lilac, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            VoteColumn("👍 ${t.approvedBy}", yes, game, Palette.Mint, Modifier.weight(1f))
            VoteColumn("👎 ${t.rejectedBy}", no, game, Palette.Red, Modifier.weight(1f))
        }
        if (!vote.approved) Text(t.rejectionsLine(game.rejects), color = Color.White.copy(alpha = 0.8f))
        PillButton(t.continueLabel, onContinue, Modifier.fillMaxWidth())
    }
}

@Composable
private fun VoteColumn(title: String, voters: List<Int>, game: Game, color: Color, modifier: Modifier = Modifier) {
    val t = LocalStrings.current
    Surface(shape = Soft, color = color.copy(alpha = 0.16f), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
            if (voters.isEmpty()) Text(t.nobody, color = Color.White.copy(alpha = 0.6f))
            voters.forEach { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(game.players[i], i, size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(game.players[i], fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The shuffled voyage cards, face down until the group is ready, then all turned over together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoyageResultScreen(state: UiState, onReveal: () -> Unit, onContinue: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val voyage = state.voyageReveal ?: return
    val played = game.history.last().voyage
    var revealed by rememberSaveable(game.history.size) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Chip("🃏 ${t.voyageOf(played + 1)}", Palette.Gold)
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            voyage.cards.forEach { fail ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        !revealed -> Palette.Night
                        fail -> Palette.Red
                        else -> Palette.Blue
                    },
                    modifier = Modifier.size(width = 70.dp, height = 100.dp).border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!revealed) Flag(Modifier.size(44.dp), wave = false, color = Color.White.copy(alpha = 0.85f)) else Text(if (fail) "💥" else "⚓", fontSize = 34.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (!revealed) {
            Text(t.cardsFaceDown, textAlign = TextAlign.Center, color = Palette.Lilac)
            Spacer(Modifier.height(20.dp))
            PillButton(t.revealCards, { revealed = true; onReveal() }, Modifier.fillMaxWidth())
        } else {
            val ok = voyage.result == VoyageResult.SUCCESS
            Surface(shape = Soft, color = if (ok) Palette.Blue else Palette.Red, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (ok) "🎉 ${t.voyageSucceeded}" else "💥 ${t.voyageFailed}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(t.failsLine(voyage.fails, voyage.needed), color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PillButton(t.continueLabel, onContinue, Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------- the end

@Composable
internal fun GameOverScreen(state: UiState, onNewGame: () -> Unit, onSetup: () -> Unit, onHistory: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val loyal = game.winner == Side.LOYAL
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (loyal) "⚓" else "🏴‍☠️", fontSize = 64.sp)
        Surface(shape = Soft, color = if (loyal) Palette.Blue else Palette.Red, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (loyal) t.loyalWins else t.mutineersWin,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    when {
                        game.winReason == WinReason.REJECTIONS -> t.winRejections
                        loyal -> t.winSuccess
                        else -> t.winFail
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        VoyageTrack(game)
        Text(t.rolesTitle, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.align(Alignment.Start))
        game.players.forEachIndexed { i, p ->
            val mutineer = game.roles[i] == Role.MUTINEER
            Surface(shape = Soft, color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, i, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(p, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Chip(if (mutineer) "🏴‍☠️ ${t.roleMutineer}" else "⚓ ${t.roleLoyal}", if (mutineer) Palette.Red else Palette.Blue)
                }
            }
        }
        TextButton(onClick = onHistory) { Text("📜 ${t.history}", color = Palette.Aqua, fontWeight = FontWeight.Bold) }
        PillButton(t.newGame, onNewGame, Modifier.fillMaxWidth())
        TextButton(onClick = onSetup) { Text(t.editPlayers, color = Palette.Aqua, fontWeight = FontWeight.Bold) }
    }
}

/** Every team proposed so far: who led, who was on it, how each player voted and how the voyage went. */
@Composable
internal fun HistoryDialog(game: Game, onClose: () -> Unit) {
    val t = LocalStrings.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("📜 ${t.historyTitle}") },
        text = {
            if (game.history.isEmpty()) {
                Text(t.historyEmpty)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(game.history.asReversed()) { r ->
                        val outcome = when {
                            !r.approved -> "❌ ${t.voteRejected}"
                            r.result == VoyageResult.SUCCESS -> "✅ ${t.voyageSucceeded}"
                            r.result == VoyageResult.FAIL -> "💥 ${t.voyageFailed}"
                            else -> "…"
                        }
                        val yes = game.players.indices.filter { it in r.approvers }.joinToString(", ") { game.players[it] }.ifEmpty { t.nobody }
                        val no = game.players.indices.filter { it !in r.approvers }.joinToString(", ") { game.players[it] }.ifEmpty { t.nobody }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.historyRound(r.voyage + 1, r.team.joinToString(", ") { game.players[it] }, outcome), fontWeight = FontWeight.Bold)
                            Text("🧭 ${game.players[r.captain]}", fontSize = 13.sp)
                            Text("👍 $yes", fontSize = 13.sp)
                            Text("👎 $no", fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(t.close) } },
    )
}
