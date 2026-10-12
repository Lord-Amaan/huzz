package com.example.huzz.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.huzz.R
import com.example.huzz.geohash.Geohash
import com.example.huzz.geohash.GeohashChannelLevel
import com.example.huzz.geohash.LocationChannelManager
import com.example.huzz.ui.globe.GlobeColors
import com.example.huzz.ui.globe.GlobeState
import com.example.huzz.ui.globe.GlobeView
import com.example.huzz.ui.globe.LandData
import com.example.huzz.ui.theme.BASE_FONT_SIZE
import com.example.huzz.ui.theme.HuzzFontFamily
import com.example.huzz.ui.theme.HuzzTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeohashPickerActivity : OrientationAwareActivity() {

    companion object {
        const val EXTRA_INITIAL_GEOHASH = "initial_geohash"
        const val EXTRA_RESULT_GEOHASH = "result_geohash"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialGeohash = intent.getStringExtra(EXTRA_INITIAL_GEOHASH)?.trim()?.lowercase()
        var geohashToFocus: String? = null
        var initLat = 20.0
        var initLon = 0.0

        if (!initialGeohash.isNullOrEmpty()) {
            geohashToFocus = initialGeohash
            try {
                val (lat, lon) = Geohash.decodeToCenter(initialGeohash)
                initLat = lat
                initLon = lon
            } catch (_: Throwable) {}
        } else {
            // If no initial geohash, try to use the user's coarsest location
            val locationManager = LocationChannelManager.getInstance(applicationContext)
            val channels = locationManager.availableChannels.value
            if (!channels.isNullOrEmpty()) {
                val coarsestChannel = channels.minByOrNull { it.geohash.length }
                if (coarsestChannel != null) {
                    geohashToFocus = coarsestChannel.geohash
                    try {
                        val (lat, lon) = Geohash.decodeToCenter(coarsestChannel.geohash)
                        initLat = lat
                        initLon = lon
                    } catch (_: Throwable) {}
                }
            }
        }

        val initialPrecision = (geohashToFocus?.length ?: 2).coerceIn(1, 12)
        val targetLat = initLat
        val targetLon = initLon

        setContent {
            HuzzTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val globeState = remember {
                    GlobeState(
                        targetLat = targetLat,
                        targetLon = targetLon,
                        initialPrecision = initialPrecision,
                        startZoomedOut = true
                    ).apply {
                        introTarget = Triple(targetLat, targetLon, initialPrecision)
                    }
                }

                LaunchedEffect(globeState) { globeState.attach(scope) }

                val land by produceState<List<LandData.Ring>?>(initialValue = null) {
                    value = withContext(Dispatchers.IO) { LandData.load(context) }
                }
                val borders by produceState<List<LandData.Ring>>(initialValue = emptyList()) {
                    value = withContext(Dispatchers.IO) { LandData.loadBorders(context) }
                }
                val cities by produceState<List<LandData.City>>(initialValue = emptyList()) {
                    value = withContext(Dispatchers.IO) { LandData.loadCities(context) }
                }

                val colorScheme = MaterialTheme.colorScheme
                val dark = colorScheme.background.luminance() < 0.5f
                val globeColors = remember(colorScheme, dark) {
                    if (dark) {
                        GlobeColors(
                            accent = com.example.huzz.ui.theme.NothingRed,
                            land = Color(0xFF141414),
                            coastline = Color(0xFF444444),
                            border = Color(0xFF222222),
                            oceanCenter = Color(0xFF000000),
                            oceanEdge = Color(0xFF000000),
                            atmosphere = Color(0xFF222222),
                            graticule = Color(0xFF161616),
                            grid = Color(0xFF2A2A2A),
                            label = Color(0xFF999999),
                            labelHalo = Color(0xFF000000),
                            star = Color(0xFF444444)
                        )
                    } else {
                        GlobeColors(
                            accent = com.example.huzz.ui.theme.NothingRed,
                            land = Color(0xFFEBEBE9),
                            coastline = Color(0xFFCCCCCC),
                            border = Color(0xFFDDDDDC),
                            oceanCenter = Color(0xFFF5F5F3),
                            oceanEdge = Color(0xFFE2E2E0),
                            atmosphere = Color(0xFFD0D0CE),
                            graticule = Color(0xFFE6E6E4),
                            grid = Color(0xFFCCCCCC),
                            label = Color(0xFF666666),
                            labelHalo = Color(0xFFFFFFFF),
                            star = Color(0xFFCCCCCC)
                        )
                    }
                }

                val labelTypeface = remember { ResourcesCompat.getFont(context, R.font.space_mono_regular) }
                val labelTypefaceBold = remember { ResourcesCompat.getFont(context, R.font.space_mono_bold) }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                ) {
                    land?.let { rings ->
                        GlobeView(
                            state = globeState,
                            colors = globeColors,
                            land = rings,
                            borders = borders,
                            cities = cities,
                            labelTypeface = labelTypeface,
                            labelTypefaceBold = labelTypefaceBold,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating info pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 20.dp)
                            .fillMaxWidth(0.85f),
                        color = colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline)
                    ) {
                        Text(
                            text = stringResource(R.string.pan_zoom_instruction).uppercase(),
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    // Floating bottom controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Geohash label (monospace, app style)
                        Surface(
                            color = colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (globeState.selectedGeohash.isNotEmpty()) "#${globeState.selectedGeohash.uppercase()}" else "[SELECT LOCATION]",
                                    fontSize = 16.sp,
                                    fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = colorScheme.onSurface
                                )
                                if (globeState.selectedGeohash.isNotEmpty()) {
                                    Text(
                                        text = "${levelForLength(globeState.precision).displayName.uppercase()} • ${coverageString(globeState.precision).uppercase()}",
                                        fontSize = 10.sp,
                                        fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                        letterSpacing = 0.5.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Button row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decrease precision
                            Button(
                                onClick = { globeState.animatePrecision(globeState.precision - 1) },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease_precision))
                            }

                            // Increase precision
                            Button(
                                onClick = { globeState.animatePrecision(globeState.precision + 1) },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase_precision))
                            }

                            // Select button
                            Button(
                                onClick = {
                                    val gh = globeState.selectedGeohash
                                    if (gh.isNotEmpty()) {
                                        val result = Intent().apply { putExtra(EXTRA_RESULT_GEOHASH, gh) }
                                        setResult(Activity.RESULT_OK, result)
                                        finish()
                                    }
                                },
                                enabled = globeState.selectedGeohash.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.cd_select_geohash))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.select),
                                    fontSize = (BASE_FONT_SIZE - 2).sp,
                                    fontFamily = HuzzFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun levelForLength(length: Int): GeohashChannelLevel {
        return when (length) {
            in 0..2 -> GeohashChannelLevel.REGION
            in 3..4 -> GeohashChannelLevel.PROVINCE
            5 -> GeohashChannelLevel.CITY
            6 -> GeohashChannelLevel.NEIGHBORHOOD
            7 -> GeohashChannelLevel.BLOCK
            else -> GeohashChannelLevel.BUILDING
        }
    }

    private fun coverageString(precision: Int): String {
        val maxMeters = when (precision) {
            2 -> 1_250_000.0
            3 -> 156_000.0
            4 -> 39_100.0
            5 -> 4_890.0
            6 -> 1_220.0
            7 -> 153.0
            8 -> 38.2
            9 -> 4.77
            10 -> 1.19
            else -> if (precision <= 1) 5_000_000.0 else 1.19 * Math.pow(0.25, (precision - 10).toDouble())
        }
        val km = maxMeters / 1000.0
        return when {
            km >= 100 -> "~${String.format(java.util.Locale.US, "%.0f", km)} km"
            km >= 1 -> "~${String.format(java.util.Locale.US, "%.1f", km)} km"
            else -> "~${String.format(java.util.Locale.US, "%.0f", maxMeters)} m"
        }
    }
}
