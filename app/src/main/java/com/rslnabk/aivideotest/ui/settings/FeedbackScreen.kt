package com.rslnabk.aivideotest.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun RatingScreen(model: AppViewModel, actions: AppNavigator) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("rating_prompt"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { RoundAction(R.drawable.ic_close, stringResource(R.string.close), Modifier.testTag("rating_close"), onClick = actions::back) }
        Image(painterResource(R.drawable.demo_rating_hearts), null, Modifier.fillMaxWidth().heightIn(max = 280.dp).aspectRatio(1f), contentScale = ContentScale.Fit)
        Text(stringResource(R.string.rating_title), style = Ds.type.largeTitleEmphasized, textAlign = TextAlign.Center)
        Text(stringResource(R.string.rating_body), style = Ds.type.title3Regular, color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DsButton(stringResource(R.string.rating_no), Modifier.weight(1f).testTag("rating_no")) { model.declineRating(); actions.back() }
            DsButton(stringResource(R.string.rating_yes), Modifier.weight(1f).testTag("rating_yes"), primary = true) {
                actions.nav.navigate("review") { popUpTo("rate") { inclusive = true } }
            }
        }
    }
}
@Composable fun ReviewScreen(preferences: DemoPreferences, model: AppViewModel, actions: AppNavigator) {
    Column(Modifier.fillMaxSize().testTag("review_screen")) {
        ScreenHeader(stringResource(R.string.review_title), actions::back) { }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.local_feedback_body), style = Ds.type.calloutRegular, color = Ds.colors.labelTertiary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { star ->
                    Box(Modifier.size(48.dp).selectable(preferences.ratingDraft == star, role = Role.RadioButton) { model.editReview(rating = star) }.testTag("review_star_$star"), contentAlignment = Alignment.Center) {
                        DsIcon(R.drawable.ic_star, stringResource(R.string.star_rating, star), if (star <= preferences.ratingDraft) Ds.colors.accentPrimary else Ds.colors.labelQuaternary, Modifier.size(32.dp))
                    }
                }
            }
            FeedbackField(preferences.reviewName, { model.editReview(name = it) }, R.string.review_name, "review_name", 80, singleLine = true)
            FeedbackField(preferences.reviewText, { model.editReview(text = it) }, R.string.review_text, "review_text", 1000)
            DsButton(stringResource(R.string.save_locally), Modifier.fillMaxWidth().testTag("review_save"), primary = true,
                enabled = preferences.ratingDraft in 1..5 && preferences.reviewName.points() <= 80 && preferences.reviewText.points() <= 1000) {
                if (model.saveReview()) { actions.back(); actions.show("feedback_saved") }
            }
        }
    }
}
@Composable fun MessageScreen(kind: MessageKind, preferences: DemoPreferences, model: AppViewModel, actions: AppNavigator) {
    val value = if (kind == MessageKind.REPORT) preferences.reportDraft else preferences.letterDraft
    Column(Modifier.fillMaxSize().testTag("message_${kind.name.lowercase()}")) {
        ScreenHeader(stringResource(if (kind == MessageKind.REPORT) R.string.report_problem else R.string.letter), actions::back) { }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(if (kind == MessageKind.REPORT) R.string.message_report_body else R.string.message_letter_body), style = Ds.type.title3Regular)
            Text(stringResource(R.string.local_feedback_body), style = Ds.type.calloutRegular, color = Ds.colors.labelTertiary)
            FeedbackField(value, { model.editMessage(kind, it) }, R.string.message_text, "message_input", 1000)
            DsButton(stringResource(R.string.save_locally), Modifier.fillMaxWidth().testTag("message_save"), primary = true, enabled = value.isNotBlank() && value.points() <= 1000) {
                if (model.saveMessage(kind)) { actions.back(); actions.show("feedback_saved") }
            }
        }
    }
}
private fun String.points() = codePointCount(0, length)
@Composable private fun FeedbackField(value: String, update: (String) -> Unit, label: Int, tag: String, limit: Int, singleLine: Boolean = false) {
    OutlinedTextField(value, update, Modifier.fillMaxWidth().testTag(tag), label = { Text(stringResource(label)) }, singleLine = singleLine, minLines = if (singleLine) 1 else 4,
        supportingText = { Text("${value.points()} / $limit", style = Ds.type.caption1Regular) }, isError = value.points() > limit,
        textStyle = Ds.type.calloutRegular, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ds.colors.accentPrimary, unfocusedBorderColor = Ds.colors.separatorPrimary, cursorColor = Ds.colors.accentPrimary))
}
