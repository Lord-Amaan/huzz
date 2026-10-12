package com.example.huzz.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.huzz.ui.theme.HuzzFontFamily
import com.example.huzz.R
import com.example.huzz.core.ui.icon.HuzzIcon
import com.example.huzz.ui.theme.HuzzMotion
import com.example.huzz.ui.theme.LocalHuzzPalette

/**
 * Building blocks for the redesigned About sheet.
 *
 * Kept in a separate file from [AboutSheet] because the sheet itself is mostly wiring for
 * preferences, whereas these are pure presentation.
 */

import com.example.huzz.ui.theme.SpaceMonoFamily
import com.example.huzz.ui.theme.DotoFamily
import com.example.huzz.ui.theme.SpaceGroteskFamily
import androidx.compose.foundation.border

/** Horizontal inset shared by every About section, so cards and labels align to one grid. */
internal val AboutHorizontalPadding = 20.dp

/** Card corner radius for grouped rows (Nothing 8-12px standard). */
internal val AboutCardShape = RoundedCornerShape(12.dp)

/** Leading icon column in settings-style sheet rows. */
internal val SheetRowLeadingSlot = 22.dp
internal val SheetRowLeadingGutter = 16.dp
internal val SheetRowHorizontal = 16.dp
internal val SheetRowVertical = 13.dp

/**
 * Exact height of a people-list row.
 *
 * Fixed rather than derived from content: these lists reorder themselves constantly, and a row
 * whose height depends on its content makes the whole card change height every time the order
 * changes. Equals the leading glyph plus [SheetRowVertical] above and below.
 */
internal val SheetRowHeight = SheetRowLeadingSlot + SheetRowVertical * 2
internal val SheetRowDividerInset = SheetRowHorizontal + SheetRowLeadingSlot + SheetRowLeadingGutter
/** Selection indicator sized for [SheetRowLeadingSlot]. */
internal val SheetRowSelectedDot = 12.dp

/**
 * Two top-level views of the sheet: what the app is and how to drive it, versus the knobs.
 */
enum class AboutTab {
    Info,
    Settings,
}

/**
 * Small uppercase section label, e.g. `SETTINGS`.
 *
 * Nothing standard: Space Mono ALL CAPS with generous letter spacing.
 */
@Composable
internal fun AboutSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalHuzzPalette.current
    Text(
        text = text.uppercase(),
        fontFamily = SpaceMonoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = palette.textTertiary,
        modifier = modifier.padding(start = AboutHorizontalPadding, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * Icon + title on one line, optional short subtitle beneath. Used by location / network sheets.
 */
@Composable
internal fun SheetIconSectionHeader(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                fontSize = 17.sp,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = SpaceMonoFamily,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Inset divider used inside grouped sheet cards (aligns with text column after the leading slot). */
@Composable
internal fun SheetCardDivider() {
    val colorScheme = MaterialTheme.colorScheme
    HorizontalDivider(
        modifier = Modifier.padding(start = SheetRowDividerInset),
        thickness = 1.dp,
        color = colorScheme.outlineVariant
    )
}

/**
 * Centered app identity block: logo, wordmark, tagline, version.
 */
@Composable
internal fun AboutHero(
    versionName: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalHuzzPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_huzz_logo),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            letterSpacing = (-0.5).sp,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.about_tagline),
            fontFamily = SpaceGroteskFamily,
            fontSize = 15.sp,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.version_prefix, versionName).uppercase(),
            fontFamily = SpaceMonoFamily,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.textTertiary
        )
    }
}

/**
 * Two-up tab bar with a sliding underline indicator.
 *
 * The indicator animates its offset rather than cross-fading two static bars, which is what
 * makes the switch feel physically connected to the tap.
 */
@Composable
internal fun AboutTabBar(
    selected: AboutTab,
    onSelect: (AboutTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    var rowWidth by remember { mutableStateOf(0.dp) }
    val tabWidth = rowWidth / 2
    val indicatorOffset by animateDpAsState(
        targetValue = if (selected == AboutTab.Info) 0.dp else tabWidth,
        animationSpec = tween(HuzzMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "aboutTabIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding)
            .onSizeChanged { size ->
                rowWidth = with(density) { size.width.toDp() }
            }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AboutTabLabel(
                text = stringResource(R.string.about_tab_info),
                isSelected = selected == AboutTab.Info,
                onClick = { onSelect(AboutTab.Info) },
                modifier = Modifier.weight(1f)
            )
            AboutTabLabel(
                text = stringResource(R.string.about_tab_settings),
                isSelected = selected == AboutTab.Settings,
                onClick = { onSelect(AboutTab.Settings) },
                modifier = Modifier.weight(1f)
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    // Lambda overload: the offset is animated every frame, and the non-lambda
                    // version would invalidate composition rather than just layout.
                    .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
                    .width(tabWidth)
                    .height(2.dp)
                    .background(colorScheme.primary)
            )
        }
    }
}

@Composable
private fun AboutTabLabel(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val color by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
        animationSpec = tween(HuzzMotion.QUICK_MS, easing = FastOutSlowInEasing),
        label = "aboutTabLabelColor"
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(onClickLabel = text) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = SpaceMonoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = color
        )
    }
}

/** One line of the "How To Use" list: an icon plus a single instruction. */
@Composable
private fun AboutInstructionRow(
    @DrawableRes iconRes: Int,
    text: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
        )
        Text(
            text = text,
            fontFamily = HuzzFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colorScheme.onSurface
        )
    }
}

/**
 * The "How To Use" tab: a short, scannable list of the gestures that are not self-evident.
 */
@Composable
internal fun AboutHowToUseSection(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.about_how_to_use_heading),
            fontFamily = HuzzFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.primary,
            modifier = Modifier.padding(
                start = AboutHorizontalPadding,
                top = 20.dp,
                bottom = 8.dp
            )
        )

        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_person,
            text = stringResource(R.string.about_howto_nickname)
        )
        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_globe,
            text = stringResource(R.string.about_howto_channels)
        )
        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_people,
            text = stringResource(R.string.about_howto_people)
        )
        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_bookmark_outline,
            text = stringResource(R.string.about_howto_bookmark)
        )
        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_mention,
            text = stringResource(R.string.about_howto_mention)
        )
        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_command,
            text = stringResource(R.string.about_howto_commands)
        )
        AboutInstructionRow(
            iconRes = R.drawable.ic_spec_waveform,
            text = stringResource(R.string.about_howto_panic)
        )
    }
}

/**
 * Capability list, laid out like [AboutHowToUseSection]: flat rows, no card surface or dividers.
 */
@Composable
internal fun AboutFeatureCard(modifier: Modifier = Modifier) {
    val features = listOf(
        Triple(
            R.drawable.ic_spec_wifi_off,
            R.string.about_offline_mesh_title,
            R.string.about_offline_mesh_desc
        ),
        Triple(
            R.drawable.ic_spec_lock,
            R.string.about_e2e_title,
            R.string.about_e2e_desc
        ),
        Triple(
            R.drawable.ic_spec_globe,
            R.string.about_online_geohash_title,
            R.string.about_online_geohash_desc
        ),
        Triple(
            R.drawable.ic_spec_eye_off,
            R.string.about_no_tracking_title,
            R.string.about_no_tracking_desc
        ),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        features.forEach { (icon, titleRes, descRes) ->
            AboutFeatureRow(
                iconRes = icon,
                title = stringResource(titleRes),
                subtitle = stringResource(descRes)
            )
        }
    }
}

@Composable
private fun AboutFeatureRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontFamily = SpaceGroteskFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontFamily = SpaceMonoFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Small uppercase pill, e.g. the `RECOMMENDED` badge beside the Tor routing toggle.
 */
@Composable
internal fun HuzzBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = SpaceMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            color = colorScheme.onSurfaceVariant
        )
    }
}

// MARK: - Shared bottom-sheet primitives

/** Horizontal inset for sheet content. Cards inset from here; labels align to the same edge. */
internal val SheetHorizontalPadding = 16.dp

/**
 * Section divider label used across the sheets, e.g. `BOOKMARKED`, `NEARBY`, `ON LOCATION`.
 */
@Composable
internal fun SheetSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalHuzzPalette.current
    Text(
        text = text.uppercase(),
        fontFamily = SpaceMonoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = palette.textTertiary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SheetHorizontalPadding + 14.dp,
                end = SheetHorizontalPadding,
                top = 20.dp,
                bottom = 6.dp
            )
    )
}

/**
 * Circular tinted badge holding a section's icon, used at the top of the sheets.
 */
@Composable
internal fun SheetHeaderBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(44.dp)
            .background(colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Full-width destructive action, e.g. `REMOVE LOCATION ACCESS`.
 *
 * Tinted fill plus an outline rather than a solid red button: the action is legitimate but
 * rarely wanted, and a solid red block would dominate the sheet.
 */
@Composable
internal fun SheetDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (isDestructive) colorScheme.error else colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.uppercase(),
                fontFamily = SpaceMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
                color = accent
            )
        }
    }
}
