package com.rslnabk.aivideotest.ui.effect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun EffectScreen(id: String, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity, actions: AppNavigator) {
    val effect = model.catalog.effect(id) ?: return
    val liked = id in snapshot.favorites
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(effect.title), actions::back) { BalanceButton(snapshot, actions) }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(32.dp))) {
            Image(painterResource(effect.image), stringResource(effect.title), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (effect.kind == MediaKind.VIDEO) Text(stringResource(R.string.still_preview), Modifier.align(Alignment.BottomStart).padding(24.dp),
                color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
        }
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundAction(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                stringResource(if (liked) R.string.remove_favorite else R.string.add_favorite), Modifier.testTag("like").semantics { selected = liked },
                if (liked) Ds.colors.accentPrimary else Ds.colors.labelPrimary) { model.toggleFavorite(id) }
            DsButton(stringResource(R.string.use_effect), Modifier.weight(1f).testTag("use_effect"), primary = true) { host.openGenerator(id) }
        }
    }
}
