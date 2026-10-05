package com.rslnabk.aivideotest

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.databinding.ActivityMainBinding
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.catalog.BrowserFragment
import com.rslnabk.aivideotest.ui.effect.EffectFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = AppTab.VIDEO
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStackImmediate()
            else selectTab(AppTab.VIDEO)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        selectedTab = savedInstanceState?.getString("tab")?.let { AppTab.valueOf(it) } ?: AppTab.VIDEO
        binding.navigation.selectedItemId = selectedTab.menuId
        binding.navigation.setOnItemSelectedListener { item ->
            selectTab(AppTab.entries.first { it.menuId == item.itemId }); true
        }
        onBackPressedDispatcher.addCallback(this, back)
        supportFragmentManager.addOnBackStackChangedListener { updateNavigation() }
        if (savedInstanceState == null) selectTab(selectedTab)
        updateNavigation()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", selectedTab.name)
        super.onSaveInstanceState(outState)
    }
    fun selectTab(tab: AppTab) {
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val tag = "root_${tab.name}"
        val target = supportFragmentManager.findFragmentByTag(tag) ?: BrowserFragment.root(tab)
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.filter { it.isAdded && it !== target }.forEach { hide(it) }
            if (target.isAdded) show(target) else add(R.id.content, target, tag)
        }.commitNow()
        selectedTab = tab
        binding.navigation.menu.findItem(tab.menuId).isChecked = true
        updateNavigation()
    }
    fun openCategory(kind: MediaKind, category: Category) = push(BrowserFragment.category(kind, category))
    fun openEffect(id: String) = push(EffectFragment.newInstance(id))
    private fun push(fragment: Fragment) {
        supportFragmentManager.executePendingTransactions()
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.filter { it.isAdded && !it.isHidden }.forEach { hide(it) }
            add(R.id.content, fragment)
            addToBackStack(null)
        }.commit()
    }
    private fun updateNavigation() {
        val nested = supportFragmentManager.backStackEntryCount > 0
        binding.navigation.visibility = if (nested) View.GONE else View.VISIBLE
        back.isEnabled = nested || selectedTab != AppTab.VIDEO
    }
    fun explainCreation() = MaterialAlertDialogBuilder(this).setTitle(R.string.preview_title)
        .setMessage(R.string.preview_creation).setPositiveButton(R.string.got_it, null).show()
}
