package com.rslnabk.aivideotest.ui.catalog

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.databinding.FragmentBrowserBinding
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.generator.PromptEditor
import com.rslnabk.aivideotest.ui.library.GenerationCard

/** Catalog/category/favorites reuse the same card and repository, with no duplicated like state. */
class BrowserFragment : Fragment() {
    private var binding: FragmentBrowserBinding? = null
    private val model get() = ViewModelProvider(requireActivity())[AppViewModel::class.java]
    private val host get() = requireActivity() as MainActivity
    private val tab get() = AppTab.valueOf(requireArguments().getString("tab") ?: AppTab.VIDEO.name)
    private val isCategory get() = requireArguments().containsKey("category")
    private var category = Category.POPULAR
    private var kind = MediaKind.VIDEO
    private var mode = 0
    private var scrollY = 0
    private val cards = mutableListOf<EffectCard>()
    private var editor: PromptEditor? = null
    private var renderedJobs: List<GenerationJob>? = null
    private var renderedFavorites: Set<String>? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        category = Category.valueOf(state?.getString("filter") ?: requireArguments().getString("category") ?: Category.POPULAR.name)
        kind = MediaKind.valueOf(state?.getString("kind") ?: requireArguments().getString("kind") ?: tab.kind?.name ?: MediaKind.PHOTO.name)
        mode = state?.getInt("mode") ?: 0
        scrollY = state?.getInt("scroll") ?: 0
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val view = FragmentBrowserBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        val b = binding!!
        b.title.setText(tab.title)
        if (isCategory) {
            b.title.text = ""
            val back = AppCompatImageButton(requireContext()).apply {
                setImageResource(R.drawable.ic_back); imageTintList = android.content.res.ColorStateList.valueOf(context.color(R.color.ds_accent_primary))
                setBackgroundResource(R.drawable.bg_circle); setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                contentDescription = getString(R.string.back)
                setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            }
            b.header.addView(back, 0, LinearLayout.LayoutParams(requireContext().dp(48), requireContext().dp(48)))
        }
        renderControls()
        renderBody()
        b.scroll.post { binding?.scroll?.scrollTo(0, scrollY) }
        model.snapshot.observe(viewLifecycleOwner) { snapshot ->
            binding?.balance?.bindBalance(model)
            editor?.bind()
            if (tab == AppTab.LIBRARY && renderedJobs != snapshot.jobs) renderBody(keepScroll = true)
            cards.forEach { it.bindFavorite(it.effect.id in snapshot.favorites) }
            if (tab == AppTab.FAVORITES && !isCategory && renderedFavorites != snapshot.favorites) renderBody(keepScroll = true)
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("filter", category.name); out.putString("kind", kind.name); out.putInt("mode", mode)
        out.putInt("scroll", binding?.scroll?.scrollY ?: scrollY)
    }
    override fun onDestroyView() {
        scrollY = binding?.scroll?.scrollY ?: scrollY
        editor = null; renderedJobs = null
        cards.clear(); binding = null; renderedFavorites = null
        super.onDestroyView()
    }
    private fun renderControls() {
        val b = binding ?: return
        val ctx = requireContext()
        b.controls.removeAllViews()
        if (isCategory) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(16), 0, ctx.dp(16), ctx.dp(16)) }
            Category.entries.forEach { filter ->
                val chip = ctx.button(getString(filter.title)).apply {
                    setTextColor(ctx.color(if (filter == category) R.color.ds_accent_primary else R.color.ds_label_tertiary))
                    backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.color(R.color.ds_background_primary))
                    strokeWidth = ctx.dp(1); strokeColor = android.content.res.ColorStateList.valueOf(ctx.color(if (filter == category) R.color.ds_accent_primary else R.color.ds_separator_primary))
                    isSelected = category == filter; contentDescription = getString(R.string.filter_accessibility, getString(filter.title))
                    setOnClickListener { category = filter; scrollY = 0; renderControls(); renderBody(); b.scroll.scrollTo(0, 0) }
                }
                row.addView(chip, LinearLayout.LayoutParams(-2, ctx.dp(40)).apply { marginEnd = ctx.dp(8) })
            }
            b.controls.addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false; addView(row)
                post { val active = row.getChildAt(category.ordinal); scrollTo((active.left - (width - active.width) / 2).coerceAtLeast(0), 0) }
            })
        } else if (tab.kind != null) {
            b.controls.addView(ctx.segmented(listOf(getString(R.string.trends), getString(R.string.prompt)), mode,
                listOf(R.drawable.ic_sparkle, R.drawable.ic_prompt)) { selected ->
                if (mode != selected) { mode = selected; scrollY = 0; renderControls(); renderBody(); b.scroll.scrollTo(0, 0) }
            })
        } else if (tab == AppTab.FAVORITES || tab == AppTab.LIBRARY) {
            b.controls.addView(ctx.segmented(listOf(getString(R.string.photos), getString(R.string.videos)), if (kind == MediaKind.PHOTO) 0 else 1,
                listOf(R.drawable.ic_photo,R.drawable.ic_video)) { selected ->
                kind = if (selected == 0) MediaKind.PHOTO else MediaKind.VIDEO
                scrollY = 0; renderControls(); renderBody(); b.scroll.scrollTo(0, 0)
            })
        }
    }
    private fun renderBody(keepScroll: Boolean = false) {
        val b = binding ?: return
        val position = if (keepScroll) b.scroll.scrollY else 0
        editor = null; b.body.removeAllViews(); cards.clear()
        when {
            isCategory -> addGrid(model.catalog.effects(kind, category))
            tab.kind != null && mode == 0 -> addCatalog()
            tab.kind != null -> {
                editor = PromptEditor(host, model, "prompt_${kind.name.lowercase()}")
                b.body.addView(editor!!.view)
            }
            tab == AppTab.FAVORITES -> {
                val favorites = model.snapshot.value!!.favorites
                renderedFavorites = favorites
                val effects = model.catalog.effects(kind).filter { it.id in favorites }
                if (effects.isEmpty()) addEmpty(R.drawable.demo_empty_favorites, R.string.empty_favorites_title, R.string.empty_favorites_body, R.string.explore_effects) {
                    host.selectTab(if (kind == MediaKind.VIDEO) AppTab.VIDEO else AppTab.PHOTO)
                } else addGrid(effects)
            }
            tab == AppTab.LIBRARY -> {
                renderedJobs = model.snapshot.value!!.jobs
                val jobs = renderedJobs!!.filter { it.draft.kind == kind }.reversed()
                if (jobs.isEmpty()) addEmpty(R.drawable.demo_empty_library, R.string.empty_library_title, R.string.empty_library_body, R.string.start_creating) {
                    host.selectTab(if (kind == MediaKind.VIDEO) AppTab.VIDEO else AppTab.PHOTO)
                    (host.supportFragmentManager.findFragmentByTag("root_${if (kind == MediaKind.VIDEO) AppTab.VIDEO.name else AppTab.PHOTO.name}") as? BrowserFragment)?.showPrompt()
                } else addJobGrid(jobs)
            }
            tab == AppTab.SETTINGS -> addSettings()
        }
        cards.forEach { it.bindFavorite(it.effect.id in model.snapshot.value!!.favorites) }
        if (keepScroll) b.scroll.post { binding?.scroll?.scrollTo(0, position) }
    }
    private fun addJobGrid(jobs: List<GenerationJob>) {
        val ctx = requireContext()
        jobs.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(16),0,ctx.dp(16),ctx.dp(8)) }
            pair.forEachIndexed { index, job ->
                val name = job.draft.effectId?.let { model.catalog.effect(it)?.title }?.let(::getString)
                    ?: job.draft.prompt.ifBlank { getString(if (job.draft.kind == MediaKind.VIDEO) R.string.demo_video_title else R.string.demo_photo_title) }
                row.addView(GenerationCard(ctx,job,name,
                    { if (job.status == JobStatus.FAILED) host.showFailedActions(job.id) else host.openJob(job.id) },
                    { if (job.status == JobStatus.FAILED) host.showFailedActions(job.id) else host.confirmDelete(job.id) }),
                    LinearLayout.LayoutParams(0,-2,1f).apply { if (index > 0) marginStart = ctx.dp(8) })
            }
            if (pair.size == 1) row.addView(Space(ctx),LinearLayout.LayoutParams(0,1,1f).apply { marginStart = ctx.dp(8) })
            binding!!.body.addView(row)
        }
    }
    private fun card(effect: Effect): EffectCard = EffectCard(requireContext(), effect,
        { host.openEffect(effect.id) }, { model.toggleFavorite(effect.id) }).also { cards.add(it) }
    private fun addCatalog() {
        val ctx = requireContext(); val body = binding!!.body
        val banner = MaterialCardView(ctx).apply {
            radius = ctx.dp(24).toFloat(); strokeWidth = 0; cardElevation = 0f
            addView(ImageView(ctx).apply { setImageResource(R.drawable.demo_banner); scaleType = ImageView.ScaleType.CENTER_CROP }, ViewGroup.LayoutParams(-1, -1))
            contentDescription = getString(R.string.try_anime); isFocusable = true; isClickable = true
            setOnClickListener { host.openEffect(model.catalog.effects(kind, Category.ANIME).first().id) }
        }
        val width = ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density - 32
        body.addView(banner, LinearLayout.LayoutParams(-1, ctx.dp((width / 2.15f).toInt())).apply { setMargins(ctx.dp(16), 0, ctx.dp(16), ctx.dp(12)) })
        Category.entries.forEach { category ->
            val heading = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(ctx.dp(16), ctx.dp(4), ctx.dp(16), ctx.dp(4)) }
            heading.addView(ctx.text(getString(category.title), R.style.TextAppearance_AiVideoTest_Title3_Regular), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(ctx.button(getString(R.string.see_all)).apply {
                setTextColor(ctx.color(R.color.ds_accent_primary)); strokeWidth = ctx.dp(1)
                strokeColor = android.content.res.ColorStateList.valueOf(ctx.color(R.color.ds_separator_primary))
                backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.color(R.color.ds_background_primary))
                setOnClickListener { host.openCategory(kind, category) }
                contentDescription = getString(R.string.see_all) + ": " + getString(category.title)
            }, LinearLayout.LayoutParams(-2, ctx.dp(36)))
            body.addView(heading)
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(16)) }
            val effects = model.catalog.effects(kind, category)
            // Column-major layout matches the two rows of horizontally scrolling PDF cards.
            val columns = (effects.size + 1) / 2
            repeat(columns) { column ->
                val stack = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                listOf(column, column + columns).filter { it < effects.size }.forEachIndexed { index, item ->
                    stack.addView(card(effects[item]), LinearLayout.LayoutParams(ctx.dp(130), ctx.dp(231)).apply { if (index > 0) topMargin = ctx.dp(8) })
                }
                row.addView(stack, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ctx.dp(8) })
            }
            body.addView(HorizontalScrollView(ctx).apply {
                id = when (category) {
                    Category.POPULAR -> R.id.popular_scroll; Category.ANIME -> R.id.anime_scroll
                    Category.FASHION -> R.id.fashion_scroll; Category.NEW -> R.id.new_scroll; Category.RETRO -> R.id.retro_scroll
                }; isHorizontalScrollBarEnabled = false; addView(row)
            })
        }
    }
    private fun addGrid(effects: List<Effect>) {
        val ctx = requireContext(); val body = binding!!.body
        val width = (ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density - 44) / 2
        effects.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(16), 0, ctx.dp(16), ctx.dp(8)) }
            pair.forEachIndexed { index, effect ->
                row.addView(card(effect), LinearLayout.LayoutParams(0, ctx.dp((width * 1.78f).toInt()), 1f).apply { if (index > 0) marginStart = ctx.dp(8) })
            }
            if (pair.size == 1) row.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = ctx.dp(8) })
            body.addView(row)
        }
    }
    private fun addEmpty(icon: Int, title: Int, message: Int, action: Int, click: () -> Unit) {
        val ctx = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(ctx.dp(32), ctx.dp(72), ctx.dp(32), ctx.dp(32)) }
        panel.addView(ImageView(ctx).apply {
            setImageResource(icon); scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ctx.dp(220), ctx.dp(240)).apply { bottomMargin = ctx.dp(24) })
        panel.addView(ctx.text(getString(title), R.style.TextAppearance_AiVideoTest_Title2_Emphasized).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
        panel.addView(ctx.text(getString(message)).apply { gravity = Gravity.CENTER; setTextColor(ctx.color(R.color.ds_label_tertiary)) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(12); bottomMargin = ctx.dp(24) })
        panel.addView(ctx.button(getString(action), true).apply { setOnClickListener { click() } }, LinearLayout.LayoutParams(-1, ctx.dp(48)))
        binding!!.body.addView(panel)
    }
    private fun addSettings() {
        val ctx = requireContext()
        val card = MaterialCardView(ctx).apply { radius = ctx.dp(24).toFloat(); strokeColor = ctx.color(R.color.ds_accent_primary); strokeWidth = ctx.dp(1); setCardBackgroundColor(ctx.color(R.color.ds_accent_primary_alpha)) }
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16)) }
        column.addView(ctx.text(getString(R.string.subscription_title), R.style.TextAppearance_AiVideoTest_Title3_Regular))
        column.addView(ctx.text(getString(R.string.subscription_body)).apply { setTextColor(ctx.color(R.color.ds_label_tertiary)) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8); bottomMargin = ctx.dp(16) })
        column.addView(ctx.button(getString(R.string.more)).apply { setTextColor(ctx.color(R.color.ds_accent_primary)); setOnClickListener { MaterialAlertDialogBuilder(ctx).setTitle(R.string.subscription_title).setMessage(R.string.settings_preview).setPositiveButton(R.string.got_it, null).show() } }, LinearLayout.LayoutParams(-1, ctx.dp(48)))
        card.addView(column)
        binding!!.body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(24)) })
        binding!!.body.addView(ctx.text(getString(R.string.demo_account), R.style.TextAppearance_AiVideoTest_Headline_Emphasized).apply { setPadding(ctx.dp(16), 0, ctx.dp(16), 0) })
        binding!!.body.addView(ctx.text(getString(R.string.settings_preview)).apply { setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), 0); setTextColor(ctx.color(R.color.ds_label_tertiary)) })
    }
    fun showMediaKind(value: MediaKind) { if (kind != value) { kind = value; scrollY = 0; if (binding != null) { renderControls(); renderBody() } } }
    fun showPrompt() { mode = 1; scrollY = 0; if (binding != null) { renderControls(); renderBody() } }
    companion object {
        fun root(tab: AppTab) = BrowserFragment().apply { arguments = Bundle().apply { putString("tab", tab.name) } }
        fun category(kind: MediaKind, category: Category) = BrowserFragment().apply {
            arguments = Bundle().apply { putString("tab", if (kind == MediaKind.VIDEO) AppTab.VIDEO.name else AppTab.PHOTO.name); putString("kind", kind.name); putString("category", category.name) }
        }
    }
}
