package com.zepinto.resistance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ResistanceApp(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val t = state.lang.strings
    val owner = LocalLifecycleOwner.current
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    // Whatever is secret goes off screen the moment the app is left.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) vm.onPause()
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = state.stage != Stage.SETUP) {
        when {
            state.stage == Stage.REVEAL && state.cardUp -> vm.hide()
            state.stage == Stage.PICK_TEAM -> vm.cancelPick()
            state.stage == Stage.GAME_OVER -> vm.backToSetup()
            else -> confirmLeave = true
        }
    }
    // declared last so it wins: Back closes the rules page first
    BackHandler(enabled = showRules) { showRules = false }

    CompositionLocalProvider(LocalStrings provides t) {
        if (showRules) {
            RulesScreen(onBack = { showRules = false })
        } else {
            val game = state.game
            when (state.stage) {
                Stage.SETUP -> SetupScreen(
                    state = state,
                    onAdd = vm::addPlayer,
                    onRemove = vm::removePlayer,
                    onLanguage = vm::setLanguage,
                    onStart = vm::startGame,
                    onResume = vm::resume,
                    onRules = { showRules = true },
                    onMusic = { vm.setMusic(!state.musicOn) },
                )
                Stage.REVEAL -> RevealScreen(state, onFlip = vm::flip, onHide = vm::hide, onNext = vm::next)
                Stage.BOARD -> if (game != null) BoardScreen(
                    state = state,
                    onChooseTeam = vm::chooseTeam,
                    onHistory = { showHistory = true },
                    onRules = { showRules = true },
                )
                Stage.PICK_TEAM -> if (game != null) PickTeamScreen(state, onToggle = vm::toggleMember, onPropose = vm::proposeTeam, onBack = vm::cancelPick)
                Stage.VOTE_PASS -> PassScreen(state, voting = true, onReady = vm::ready)
                Stage.VOTE_CHOOSE -> ChoiceScreen(state, voting = true, onChoose = { first -> vm.castVote(first) })
                Stage.VOTE_RESULT -> VoteResultScreen(state, onContinue = vm::afterVote)
                Stage.MISSION_PASS -> PassScreen(state, voting = false, onReady = vm::ready)
                Stage.MISSION_CHOOSE -> ChoiceScreen(state, voting = false, onChoose = { first -> vm.playCard(wantsFail = !first) })
                Stage.MISSION_RESULT -> MissionResultScreen(state, onReveal = vm::revealMissionSound, onContinue = vm::afterMission)
                Stage.GAME_OVER -> if (game != null) GameOverScreen(
                    state = state,
                    onNewGame = vm::newGame,
                    onSetup = vm::backToSetup,
                    onHistory = { showHistory = true },
                )
            }
        }

        if (showHistory) state.game?.let { HistoryDialog(it, onClose = { showHistory = false }) }
        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(t.leaveTitle) },
                text = { Text(t.leaveText) },
                confirmButton = { TextButton(onClick = { confirmLeave = false; vm.backToSetup() }) { Text(t.leave) } },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(t.stay) } },
            )
        }
    }
}

// ---------------------------------------------------------------- setup

@Composable
internal fun HelpButton(onClick: () -> Unit) {
    val t = LocalStrings.current
    Surface(
        shape = CircleShape,
        color = Palette.Aqua.copy(alpha = 0.2f),
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = t.rulesButton }
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("?", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Palette.Aqua)
        }
    }
}

/** Music on or off. The sound effects stay on: they are part of the game. */
@Composable
internal fun MusicButton(on: Boolean, label: String, tint: Color, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = if (on) 0.2f else 0.06f),
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (on) "🎵" else "🔇", fontSize = 20.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(
    state: UiState,
    onAdd: (String) -> Boolean,
    onRemove: (Int) -> Unit,
    onLanguage: (Lang) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRules: () -> Unit,
    onMusic: () -> Unit,
) {
    val t = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    val n = state.players.size
    val canStart = n in Rules.MIN_PLAYERS..Rules.MAX_PLAYERS
    val submit = {
        if (onAdd(name)) {
            name = ""
            nameError = null
        } else if (name.isNotBlank()) {
            nameError = if (n >= Rules.MAX_PLAYERS) t.maxPlayers(Rules.MAX_PLAYERS) else t.nameTaken
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // with the keyboard open on a small phone the player list needs the room more than the header does
        val keyboard = WindowInsets.isImeVisible
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!keyboard) {
                Mask(Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(t.appTitle, fontSize = if (keyboard) 24.sp else 34.sp, fontWeight = FontWeight.Black)
                if (!keyboard) Text(t.tagline, color = Palette.Lilac)
            }
            MusicButton(state.musicOn, t.musicButton, Palette.Aqua, onMusic)
            Spacer(Modifier.width(8.dp))
            HelpButton(onRules)
        }
        if (!keyboard) {
            Spacer(Modifier.height(12.dp))
            LanguagePicker(state.lang, onLanguage)
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                isError = nameError != null,
                placeholder = { Text(t.whoIsPlaying) },
                singleLine = true,
                shape = Pill,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PillButton(t.add, submit, enabled = name.isNotBlank(), color = Palette.Aqua, content = Palette.Ink)
        }
        nameError?.let { Text(it, color = Palette.Red, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.players, key = { _, p -> p }) { i, player ->
                Surface(shape = Soft, color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(player, i)
                        Spacer(Modifier.width(12.dp))
                        Text(player, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(i) }) { Text("✕", color = Palette.Red, fontSize = 18.sp, modifier = Modifier.semantics { contentDescription = t.removePlayer }) }
                    }
                }
            }
            item(key = "info") {
                Column {
            if (canStart) {
                Surface(shape = Soft, color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🕵️ ${t.spiesLine(Rules.spyCount(n), n - Rules.spyCount(n))}", fontWeight = FontWeight.Bold)
                        Text(t.teamSizesLabel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (m in 0 until Rules.MISSIONS) {
                                Chip("${Rules.teamSize(n, m)}" + if (Rules.failsNeeded(n, m) == 2) "*" else "", Palette.Blue)
                            }
                        }
                    }
                }
            } else {
                Text(t.addAtLeast(Rules.MIN_PLAYERS), color = Palette.Lilac)
            }
            Spacer(Modifier.height(6.dp))
            Text(t.seatingHint, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text("🔊 ${t.volumeHint}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (state.hasSaved) {
            PillButton(t.resumeGame, onResume, Modifier.fillMaxWidth(), color = Palette.Aqua)
            Spacer(Modifier.height(8.dp))
        }
        PillButton(t.dealRoles, onStart, Modifier.fillMaxWidth(), enabled = canStart)
    }
}

@Composable
internal fun LanguagePicker(selected: Lang, onSelect: (Lang) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Lang.entries.forEach { lang ->
            val on = lang == selected
            Surface(
                shape = Pill,
                color = if (on) Palette.Aqua.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .weight(1f)
                    .border(2.dp, if (on) Palette.Aqua else Color.Transparent, Pill)
                    .clip(Pill)
                    .clickable { onSelect(lang) },
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lang.flag, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(lang.code.uppercase(), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- role cards

/** One role card at a time, face down with its owner's name under it. */
@Composable
private fun RevealScreen(state: UiState, onFlip: () -> Unit, onHide: () -> Unit, onNext: () -> Unit) {
    val t = LocalStrings.current
    val game = state.game ?: return
    val index = state.currentCard
    val seenThis = index in state.seen
    val name = game.players[index]
    val nextName = if (state.onLastCard) null else game.players[state.order[state.position + 1]]

    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Chip("🃏 ${t.cardOf(state.position + 1, game.n)}", Palette.Gold)
        Spacer(Modifier.height(16.dp))

        // a new composition per card, so the flip state never carries over to the next player
        androidx.compose.runtime.key(state.position) {
            FlipCard(
                up = state.cardUp,
                onTap = onFlip,
                modifier = Modifier.weight(1f).fillMaxWidth(0.88f),
                back = { CardBackFace(index) },
                front = { RoleFace(game, index) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, index, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(name, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                state.cardUp -> t.memorize
                seenThis -> t.hiddenPass
                else -> t.tapToFlip(name)
            },
            color = Palette.Lilac,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        when {
            state.cardUp -> PillButton(t.hideCard, onHide, Modifier.fillMaxWidth(), color = Palette.Aqua)
            seenThis -> PillButton(if (nextName != null) t.passTo(nextName) else t.startGame, onNext, Modifier.fillMaxWidth())
            else -> Spacer(Modifier.height(54.dp))
        }
    }
}

/** A card that flips around its vertical axis. The front is only composed once it is told to be up and has turned past the edge. */
@Composable
private fun FlipCard(
    up: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    back: @Composable () -> Unit,
    front: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (up) 180f else 0f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label = "flip",
    )
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16f * density
            }
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onTap),
    ) {
        // Gate on `up` too: hiding must be instant, not after the spring has swung past the edge.
        if (up && rotation > 90f) {
            Box(Modifier.graphicsLayer { rotationY = 180f }) { front() }
        } else {
            back()
        }
    }
}

@Composable
private fun CardBackFace(index: Int) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Palette.Night, Palette.Ink)))
            .border(3.dp, Palette.avatar(index), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Mask(Modifier.size(150.dp), color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Text("?", fontSize = 60.sp, fontWeight = FontWeight.Black, color = Palette.avatar(index))
        }
    }
}

/** The same look for both roles, so a glance from the side reveals nothing. */
@Composable
private fun RoleFace(game: Game, index: Int) {
    val t = LocalStrings.current
    val spy = game.roles[index] == Role.SPY
    val fellows = game.spies.filter { it != index }.map { game.players[it] }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF2B2F6B), Color(0xFF7A4FD6))))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (spy) "🕵️" else "✊", fontSize = 64.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (spy) t.youAreSpy else t.youAreResistance, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(if (spy) t.spyHint else t.resistanceHint, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        if (spy && fellows.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = Soft, color = Color.Black.copy(alpha = 0.25f)) {
                Text(
                    t.fellowSpies(fellows.joinToString(", ")),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- rules

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    val t = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .noirBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← ${t.back}", color = Palette.Aqua, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Text("📖 ${t.rulesTitle}", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            t.rulesSections.forEach { section ->
                Surface(shape = Soft, color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${section.emoji}  ${section.title}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Gold)
                        section.lines.forEach { line ->
                            Row {
                                Text("•", color = Palette.Lilac, modifier = Modifier.width(18.dp))
                                Text(line, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
