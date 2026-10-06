package com.rslnabk.aivideotest.ui.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.catalog.BrowserScreen
import com.rslnabk.aivideotest.ui.effect.EffectScreen
import com.rslnabk.aivideotest.ui.generator.*
import com.rslnabk.aivideotest.ui.result.ResultScreen
import com.rslnabk.aivideotest.ui.offers.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.theme.Ds

class AppNavigator(val nav: NavHostController) {
    var root by mutableStateOf(AppTab.VIDEO)
    var dialog by mutableStateOf("")
    var dialogKey by mutableStateOf("")
    var dialogValue by mutableStateOf("")
    val currentJobId get() = nav.currentBackStackEntry?.arguments?.getString("job")
    fun show(type: String, key: String = "", value: String = "") { dialogKey = key; dialogValue = value; dialog = type }
    fun dismiss() { dialog = "" }
    fun selectTab(tab: AppTab) {
        nav.popBackStack("root/${root.name}", false)
        nav.navigate("root/${tab.name}") {
            popUpTo("root/VIDEO") { saveState = true }
            launchSingleTop = true; restoreState = true
        }
        root = tab
    }
    fun openLibrary(kind: MediaKind) {
        selectTab(AppTab.LIBRARY); nav.currentBackStackEntry?.savedStateHandle?.set("kind", kind.name)
    }
    fun openPrompt(kind: MediaKind) {
        selectTab(if (kind == MediaKind.VIDEO) AppTab.VIDEO else AppTab.PHOTO)
        nav.currentBackStackEntry?.savedStateHandle?.set("mode", 1)
    }
    fun openCategory(kind: MediaKind, category: Category) = nav.navigate("category/${kind.name}/${category.name}")
    fun openEffect(id: String) = nav.navigate("effect/${Uri.encode(id)}")
    fun openGenerator(key: String) = nav.navigate("generator/${Uri.encode(key)}")
    fun openJob(job: GenerationJob) {
        val route = if (job.status == JobStatus.SUCCEEDED) "result/${job.id}" else "creating/${job.id}"
        nav.navigate(route) { launchSingleTop = true }
    }
    fun showReadyResult(id: String) {
        nav.navigate("result/$id") { popUpTo("creating/{job}") { inclusive = true }; launchSingleTop = true }
    }
    fun back() { nav.popBackStack() }
    fun openOffers(kind: OfferKind, continuation: CreationIntent? = null) {
        closeOffers()
        nav.navigate("offers/${kind.name}?action=${continuation?.action?.name.orEmpty()}&target=${Uri.encode(continuation?.target.orEmpty())}") { launchSingleTop = true }
    }
    fun closeOffers() {
        if (nav.currentDestination?.route?.startsWith("offers/") == true) nav.popBackStack()
    }

}

@Composable fun AiVideoApp(host: MainActivity, model: AppViewModel, bind: (AppNavigator) -> Unit) {
    val nav = rememberNavController()
    val saver = remember(nav) { listSaver<AppNavigator, String>(
        save = { listOf(it.root.name, it.dialog, it.dialogKey, it.dialogValue) },
        restore = { AppNavigator(nav).apply { root = AppTab.valueOf(it[0]); dialog = it[1]; dialogKey = it[2]; dialogValue = it[3] } }) }
    val actions = rememberSaveable(saver = saver) { AppNavigator(nav) }
    SideEffect { bind(actions) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snapshot by model.snapshot.observeAsState(model.snapshot.value!!)
    val export by model.exports.state.observeAsState()
    val focus = LocalFocusManager.current
    val keyboard = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val root = route?.startsWith("root/") == true
    BackHandler(root && actions.root != AppTab.VIDEO) { focus.clearFocus(); actions.selectTab(AppTab.VIDEO) }
    // Resume is the boundary for system UI effects; neither recomposition nor rotation replays a share.
    LifecycleResumeEffect(export?.id, export?.phase) {
        when (export?.phase) {
            ExportPhase.SHARE_READY -> host.shareReady()
            ExportPhase.CANCELLED -> { export?.let { model.exports.acknowledge(it.id) }; host.toast(R.string.export_cancelled) }
            else -> Unit
        }
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize().background(Ds.colors.backgroundPrimary).safeDrawingPadding().imePadding()) {
        NavHost(nav, startDestination = "root/VIDEO", modifier = Modifier.weight(1f)) {
            AppTab.entries.forEach { tab -> composable("root/${tab.name}") { backStack ->
                BrowserScreen(tab, null, null, backStack.savedStateHandle, snapshot, model, host, actions)
            } }
            composable("category/{kind}/{category}") { backStack ->
                BrowserScreen(if (backStack.arguments!!.getString("kind") == "VIDEO") AppTab.VIDEO else AppTab.PHOTO,
                    MediaKind.valueOf(backStack.arguments!!.getString("kind")!!), Category.valueOf(backStack.arguments!!.getString("category")!!),
                    backStack.savedStateHandle, snapshot, model, host, actions)
            }
            composable("effect/{effect}") { EffectScreen(it.arguments!!.getString("effect")!!, snapshot, model, host, actions) }
            composable("generator/{key}") { GeneratorScreen(it.arguments!!.getString("key")!!, snapshot, model, host, actions) }
            composable("creating/{job}") { CreatingScreen(it.arguments!!.getString("job")!!, snapshot, host, actions) }
            composable("result/{job}") { ResultScreen(it.arguments!!.getString("job")!!, snapshot, export, host, actions) }
            composable("offers/{kind}?action={action}&target={target}", arguments = listOf(
                navArgument("action") { defaultValue = "" }, navArgument("target") { defaultValue = "" })) { backStack ->
                val args = backStack.arguments!!
                val continuation = runCatching { CreationIntent(CreationAction.valueOf(args.getString("action")!!), args.getString("target")!!) }.getOrNull()
                OfferScreen(OfferKind.valueOf(args.getString("kind")!!), continuation, snapshot, model, actions)
            }
        }
        if (root && !keyboard) {
            val icons = listOf(R.drawable.ic_video, R.drawable.ic_photo, R.drawable.ic_heart_outline, R.drawable.ic_clock, R.drawable.ic_settings)
            NavigationBar(containerColor = Ds.colors.backgroundPrimary, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(actions.root == tab, { focus.clearFocus(); actions.selectTab(tab) },
                        icon = { DsIcon(icons[index], null, if (actions.root == tab) Ds.colors.accentPrimary else Ds.colors.labelTertiary) },
                        label = { Text(stringResource(tab.title), style = Ds.type.caption2Regular, maxLines = 1) },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ds.colors.accentPrimary, selectedTextColor = Ds.colors.accentPrimary,
                            indicatorColor = Ds.colors.backgroundPrimary, unselectedTextColor = Ds.colors.labelTertiary))
                }
            }
        }
    }
    AppDialogs(actions, snapshot, model, host)
    PurchaseStatus(snapshot.commerce.operation, host, model, actions)
    if (export?.phase == ExportPhase.SUCCEEDED || export?.phase == ExportPhase.FAILED) {
        val operation = export!!
        val failed = operation.phase == ExportPhase.FAILED
        val title = if (failed) when (operation.destination) { ExportDestination.GALLERY -> R.string.gallery_error; ExportDestination.FILES -> R.string.files_error; ExportDestination.SHARE -> R.string.share_error } else null
        val message = if (failed) operation.message ?: R.string.export_error_body else if (operation.destination == ExportDestination.GALLERY) R.string.saved_gallery else R.string.saved_files
        InfoDialog(title?.let { stringResource(it) }, stringResource(message), { model.exports.acknowledge(operation.id) },
            confirmText = stringResource(if (failed) R.string.refresh else R.string.okay), confirm = {
                model.exports.acknowledge(operation.id)
                if (failed) host.exportResult(operation.jobId, operation.destination)
            }, cancelText = if (failed) stringResource(R.string.okay) else null)
    }
}
