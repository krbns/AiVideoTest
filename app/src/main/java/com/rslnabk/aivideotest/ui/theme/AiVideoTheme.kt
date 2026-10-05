package com.rslnabk.aivideotest.ui.theme

import android.util.TypedValue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.sp
import com.rslnabk.aivideotest.R

// PDF values remain in design_colors.xml and typography_dimens.xml as the single source.
data class DsColors(
    val accentPrimary: Color,
    val accentPrimaryAlpha: Color,
    val accentSecondary: Color,
    val accentGrey: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentPink: Color,
    val accentYellow: Color,
    val labelPrimary: Color,
    val labelPrimaryInvariably: Color,
    val labelPrimaryInverted: Color,
    val labelPrimaryInvertedInvariably: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelQuaternary: Color,
    val labelQuintuple: Color,
    val labelTertiary2: Color,
    val backgroundPrimary: Color,
    val backgroundPrimaryAlpha: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,
    val backgroundQuaternary: Color,
    val backgroundDim: Color,
    val separatorPrimary: Color,
    val separatorSecondary: Color,
    val systemNeutral: Color,
    val systemWhite: Color,
    val systemBlack: Color
)
data class DsTypography(
    val largeTitleRegular: TextStyle,
    val largeTitleEmphasized: TextStyle,
    val title1Regular: TextStyle,
    val title1Emphasized: TextStyle,
    val title2Regular: TextStyle,
    val title2Emphasized: TextStyle,
    val title3Regular: TextStyle,
    val title3Emphasized: TextStyle,
    val headlineRegular: TextStyle,
    val headlineEmphasized: TextStyle,
    val bodyRegular: TextStyle,
    val bodyEmphasized: TextStyle,
    val bodyItalic: TextStyle,
    val bodyEmphasizedItalic: TextStyle,
    val calloutRegular: TextStyle,
    val calloutEmphasized: TextStyle,
    val calloutItalic: TextStyle,
    val calloutEmphasizedItalic: TextStyle,
    val subheadlineRegular: TextStyle,
    val subheadlineEmphasized: TextStyle,
    val subheadlineItalic: TextStyle,
    val subheadlineEmphasizedItalic: TextStyle,
    val footnoteRegular: TextStyle,
    val footnoteEmphasized: TextStyle,
    val footnoteItalic: TextStyle,
    val footnoteEmphasizedItalic: TextStyle,
    val caption1Regular: TextStyle,
    val caption1Emphasized: TextStyle,
    val caption1Italic: TextStyle,
    val caption1EmphasizedItalic: TextStyle,
    val caption2Regular: TextStyle,
    val caption2Emphasized: TextStyle,
    val caption2Italic: TextStyle,
    val caption2EmphasizedItalic: TextStyle
)
private val LocalColors = staticCompositionLocalOf<DsColors> { error("AiVideoTheme missing") }
private val LocalType = staticCompositionLocalOf<DsTypography> { error("AiVideoTheme missing") }
object Ds {
    val colors: DsColors @Composable get() = LocalColors.current
    val type: DsTypography @Composable get() = LocalType.current
}
@Composable fun AiVideoTheme(content: @Composable () -> Unit) {
    val colors = DsColors(
        accentPrimary = colorResource(R.color.ds_accent_primary),
        accentPrimaryAlpha = colorResource(R.color.ds_accent_primary_alpha),
        accentSecondary = colorResource(R.color.ds_accent_secondary),
        accentGrey = colorResource(R.color.ds_accent_grey),
        accentGreen = colorResource(R.color.ds_accent_green),
        accentRed = colorResource(R.color.ds_accent_red),
        accentPink = colorResource(R.color.ds_accent_pink),
        accentYellow = colorResource(R.color.ds_accent_yellow),
        labelPrimary = colorResource(R.color.ds_label_primary),
        labelPrimaryInvariably = colorResource(R.color.ds_label_primary_invariably),
        labelPrimaryInverted = colorResource(R.color.ds_label_primary_inverted),
        labelPrimaryInvertedInvariably = colorResource(R.color.ds_label_primary_inverted_invariably),
        labelSecondary = colorResource(R.color.ds_label_secondary),
        labelTertiary = colorResource(R.color.ds_label_tertiary),
        labelQuaternary = colorResource(R.color.ds_label_quaternary),
        labelQuintuple = colorResource(R.color.ds_label_quintuple),
        labelTertiary2 = colorResource(R.color.ds_label_tertiary_2),
        backgroundPrimary = colorResource(R.color.ds_background_primary),
        backgroundPrimaryAlpha = colorResource(R.color.ds_background_primary_alpha),
        backgroundSecondary = colorResource(R.color.ds_background_secondary),
        backgroundTertiary = colorResource(R.color.ds_background_tertiary),
        backgroundQuaternary = colorResource(R.color.ds_background_quaternary),
        backgroundDim = colorResource(R.color.ds_background_dim),
        separatorPrimary = colorResource(R.color.ds_separator_primary),
        separatorSecondary = colorResource(R.color.ds_separator_secondary),
        systemNeutral = colorResource(R.color.ds_system_neutral),
        systemWhite = colorResource(R.color.ds_system_white),
        systemBlack = colorResource(R.color.ds_system_black)
    )
    val resources = LocalContext.current.resources
    // complexToFloat preserves raw sp, instead of applying the user's font scale twice.
    fun style(size: Int, height: Int, weight: Int, italic: Boolean = false): TextStyle {
        fun spValue(id: Int): Float = TypedValue().also { resources.getValue(id, it, true) }.let { TypedValue.complexToFloat(it.data) }
        return TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(weight),
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = spValue(size).sp, lineHeight = spValue(height).sp, letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))
    }
    // SF Pro is not supplied/licensed for Android; retain the documented system sans fallback.
    val type = DsTypography(
        largeTitleRegular = style(R.dimen.ds_type_large_title_regular_size, R.dimen.ds_type_large_title_regular_line_height, 400, false),
        largeTitleEmphasized = style(R.dimen.ds_type_large_title_emphasized_size, R.dimen.ds_type_large_title_emphasized_line_height, 600, false),
        title1Regular = style(R.dimen.ds_type_title1_regular_size, R.dimen.ds_type_title1_regular_line_height, 400, false),
        title1Emphasized = style(R.dimen.ds_type_title1_emphasized_size, R.dimen.ds_type_title1_emphasized_line_height, 700, false),
        title2Regular = style(R.dimen.ds_type_title2_regular_size, R.dimen.ds_type_title2_regular_line_height, 400, false),
        title2Emphasized = style(R.dimen.ds_type_title2_emphasized_size, R.dimen.ds_type_title2_emphasized_line_height, 700, false),
        title3Regular = style(R.dimen.ds_type_title3_regular_size, R.dimen.ds_type_title3_regular_line_height, 400, false),
        title3Emphasized = style(R.dimen.ds_type_title3_emphasized_size, R.dimen.ds_type_title3_emphasized_line_height, 600, false),
        headlineRegular = style(R.dimen.ds_type_headline_regular_size, R.dimen.ds_type_headline_regular_line_height, 400, false),
        headlineEmphasized = style(R.dimen.ds_type_headline_emphasized_size, R.dimen.ds_type_headline_emphasized_line_height, 600, false),
        bodyRegular = style(R.dimen.ds_type_body_regular_size, R.dimen.ds_type_body_regular_line_height, 400, false),
        bodyEmphasized = style(R.dimen.ds_type_body_emphasized_size, R.dimen.ds_type_body_emphasized_line_height, 500, false),
        bodyItalic = style(R.dimen.ds_type_body_italic_size, R.dimen.ds_type_body_italic_line_height, 400, true),
        bodyEmphasizedItalic = style(R.dimen.ds_type_body_emphasized_italic_size, R.dimen.ds_type_body_emphasized_italic_line_height, 600, true),
        calloutRegular = style(R.dimen.ds_type_callout_regular_size, R.dimen.ds_type_callout_regular_line_height, 400, false),
        calloutEmphasized = style(R.dimen.ds_type_callout_emphasized_size, R.dimen.ds_type_callout_emphasized_line_height, 600, false),
        calloutItalic = style(R.dimen.ds_type_callout_italic_size, R.dimen.ds_type_callout_italic_line_height, 400, true),
        calloutEmphasizedItalic = style(R.dimen.ds_type_callout_emphasized_italic_size, R.dimen.ds_type_callout_emphasized_italic_line_height, 600, true),
        subheadlineRegular = style(R.dimen.ds_type_subheadline_regular_size, R.dimen.ds_type_subheadline_regular_line_height, 400, false),
        subheadlineEmphasized = style(R.dimen.ds_type_subheadline_emphasized_size, R.dimen.ds_type_subheadline_emphasized_line_height, 600, false),
        subheadlineItalic = style(R.dimen.ds_type_subheadline_italic_size, R.dimen.ds_type_subheadline_italic_line_height, 400, true),
        subheadlineEmphasizedItalic = style(R.dimen.ds_type_subheadline_emphasized_italic_size, R.dimen.ds_type_subheadline_emphasized_italic_line_height, 600, true),
        footnoteRegular = style(R.dimen.ds_type_footnote_regular_size, R.dimen.ds_type_footnote_regular_line_height, 400, false),
        footnoteEmphasized = style(R.dimen.ds_type_footnote_emphasized_size, R.dimen.ds_type_footnote_emphasized_line_height, 600, false),
        footnoteItalic = style(R.dimen.ds_type_footnote_italic_size, R.dimen.ds_type_footnote_italic_line_height, 400, true),
        footnoteEmphasizedItalic = style(R.dimen.ds_type_footnote_emphasized_italic_size, R.dimen.ds_type_footnote_emphasized_italic_line_height, 600, true),
        caption1Regular = style(R.dimen.ds_type_caption1_regular_size, R.dimen.ds_type_caption1_regular_line_height, 400, false),
        caption1Emphasized = style(R.dimen.ds_type_caption1_emphasized_size, R.dimen.ds_type_caption1_emphasized_line_height, 500, false),
        caption1Italic = style(R.dimen.ds_type_caption1_italic_size, R.dimen.ds_type_caption1_italic_line_height, 400, true),
        caption1EmphasizedItalic = style(R.dimen.ds_type_caption1_emphasized_italic_size, R.dimen.ds_type_caption1_emphasized_italic_line_height, 500, true),
        caption2Regular = style(R.dimen.ds_type_caption2_regular_size, R.dimen.ds_type_caption2_regular_line_height, 400, false),
        caption2Emphasized = style(R.dimen.ds_type_caption2_emphasized_size, R.dimen.ds_type_caption2_emphasized_line_height, 600, false),
        caption2Italic = style(R.dimen.ds_type_caption2_italic_size, R.dimen.ds_type_caption2_italic_line_height, 400, true),
        caption2EmphasizedItalic = style(R.dimen.ds_type_caption2_emphasized_italic_size, R.dimen.ds_type_caption2_emphasized_italic_line_height, 600, true)
    )
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = colors.accentPrimary, onPrimary = colors.labelPrimaryInverted,
            secondary = colors.accentSecondary, onSecondary = colors.labelPrimaryInverted,
            background = colors.backgroundPrimary, onBackground = colors.labelPrimary,
            surface = colors.backgroundSecondary, onSurface = colors.labelPrimary,
            surfaceVariant = colors.backgroundSecondary, onSurfaceVariant = colors.labelTertiary,
            error = colors.accentRed, outline = colors.separatorPrimary),
            typography = Typography(displayLarge = type.largeTitleRegular, headlineLarge = type.title1Regular,
                headlineMedium = type.title2Regular, headlineSmall = type.title3Regular,
                titleLarge = type.headlineEmphasized, titleMedium = type.calloutRegular,
                bodyLarge = type.bodyRegular, bodyMedium = type.bodyEmphasized,
                bodySmall = type.footnoteRegular, labelLarge = type.headlineEmphasized,
                labelMedium = type.caption1Regular, labelSmall = type.caption2Regular), content = content)
    }
}
