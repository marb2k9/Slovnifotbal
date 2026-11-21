package cz.slovnifotbal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.PartOfSpeech
import cz.slovnifotbal.domain.ThemeMode
import cz.slovnifotbal.engine.WordFootballEngine
import cz.slovnifotbal.ui.components.AppIcons
import cz.slovnifotbal.ui.components.SectionCard
import cz.slovnifotbal.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, dictionaryWordCount: Int, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rules = settings.rules
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Zpět")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("settings_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Změny pravidel se projeví při zahájení nové hry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionCard("Obtížnost počítače") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Difficulty.entries.forEachIndexed { i, d ->
                            SegmentedButton(
                                selected = rules.difficulty == d,
                                onClick = { viewModel.setDifficulty(d) },
                                shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                                modifier = Modifier.testTag("difficulty_${d.key}"),
                            ) { Text(d.label) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (rules.difficulty) {
                            Difficulty.EASY -> "Náhodná platná slova, bez strategie. Rychlé odpovědi."
                            Difficulty.MEDIUM -> "Hodnotí koncová písmena a snaží se ti omezit výběr. Občas zaváhá."
                            Difficulty.HARD -> "Propočítává tahy dopředu a snaží se tě zahnat do písmene, na které nic nezbývá."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionCard("Pravidla slov") {
                    SwitchRow(
                        title = "Vlastní jména",
                        description = "Jména osob, měst a zemí (Petr, Jana, Praha…).",
                        checked = rules.properNames,
                        onChange = viewModel::setProperNames,
                        tag = "switch_proper_names",
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SwitchRow(
                        title = "Skloňované a časované tvary",
                        description = "Povolí i tvary jako „ženy“, „dělal“, „domů“. Vypnuto = jen základní tvary.",
                        checked = rules.inflectedForms,
                        onChange = viewModel::setInflectedForms,
                        tag = "switch_forms",
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Minimální délka slova", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Alespoň ${WordFootballEngine.lettersText(rules.minWordLength)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { viewModel.setMinWordLength(rules.minWordLength - 1) },
                            enabled = rules.minWordLength > GameRules.MIN_LENGTH_RANGE.first,
                            modifier = Modifier.testTag("min_length_minus"),
                        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text(
                            rules.minWordLength.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .testTag("min_length_value"),
                        )
                        FilledTonalIconButton(
                            onClick = { viewModel.setMinWordLength(rules.minWordLength + 1) },
                            enabled = rules.minWordLength < GameRules.MIN_LENGTH_RANGE.last,
                            modifier = Modifier.testTag("min_length_plus"),
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Povolené slovní druhy", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PartOfSpeech.entries.forEach { pos ->
                            val selected = pos in rules.partsOfSpeech
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.togglePartOfSpeech(pos) },
                                label = { Text(pos.label) },
                                leadingIcon = if (selected) {
                                    { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("pos_${pos.key}"),
                            )
                        }
                    }
                }

                SectionCard("Písmeno CH") {
                    Column(Modifier.selectableGroup()) {
                        RadioRow(
                            "Dvě písmena C + H",
                            "„chata“ začíná na C, „prach“ končí na H (výchozí).",
                            rules.chRule == ChRule.TWO_LETTERS,
                            "ch_two",
                        ) { viewModel.setChRule(ChRule.TWO_LETTERS) }
                        RadioRow(
                            "Samostatné písmeno CH",
                            "„chata“ začíná na CH, „prach“ končí na CH.",
                            rules.chRule == ChRule.SINGLE_LETTER,
                            "ch_single",
                        ) { viewModel.setChRule(ChRule.SINGLE_LETTER) }
                    }
                }

                SectionCard("Navazování na písmena s diakritikou") {
                    Column(Modifier.selectableGroup()) {
                        RadioRow(
                            "Volně",
                            "Na „náměstí“ lze navázat slovem na I i Í, na „země“ slovem na E. Č, Ř, Š, Ž jsou vždy samostatná písmena.",
                            rules.linkRule == LinkRule.RELAXED,
                            "link_relaxed",
                        ) { viewModel.setLinkRule(LinkRule.RELAXED) }
                        RadioRow(
                            "Přesně včetně čárky a kroužku",
                            "Na „náměstí“ jen slovem na Í. Na Ě, Ů a Ý v češtině nezačíná žádné slovo – hra pak bývá velmi krátká.",
                            rules.linkRule == LinkRule.STRICT,
                            "link_strict",
                        ) { viewModel.setLinkRule(LinkRule.STRICT) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Při kontrole slov ve slovníku se diakritika rozlišuje vždy (čaj ≠ caj).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionCard("Vzhled a zvuk") {
                    Text("Vzhled", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                modifier = Modifier.testTag("theme_${mode.key}"),
                            ) { Text(mode.label) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SwitchRow(
                        title = "Zvukové efekty",
                        description = "Krátké zvuky při tazích a na konci hry.",
                        checked = settings.soundEffects,
                        onChange = viewModel::setSoundEffects,
                        tag = "switch_sound",
                    )
                }

                SectionCard("O slovníku") {
                    Text(
                        "Zabudovaný slovník: ${"%,d".format(dictionaryWordCount).replace(',', ' ')} slov a tvarů.\n" +
                            "Zdroj: český slovník pro Hunspell (LibreOffice, licence GNU GPL), rozgenerovaný na tvary, " +
                            "frekvence slov podle FrequencyWords (OpenSubtitles, CC BY-SA 4.0). Aplikace funguje zcela offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(onClick = viewModel::resetToDefaults, modifier = Modifier.fillMaxWidth()) {
                    Text("Obnovit výchozí nastavení")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(title: String, description: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
