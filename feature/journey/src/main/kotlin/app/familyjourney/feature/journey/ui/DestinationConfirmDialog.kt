package app.familyjourney.feature.journey.ui

import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.feature.journey.DestinationConfirmUi
import app.familyjourney.feature.journey.R

/**
 * A parked-only confirmation supplied by the app. This UI never starts navigation or saves a trip.
 * Static at every motion policy; the two explicit answers share one gate per destination key.
 */
@Composable
fun DestinationConfirmDialog(
    ui: DestinationConfirmUi,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var answered by remember(ui.key) { mutableStateOf(false) }
    val latestYes by rememberUpdatedState(onYes)
    val latestNo by rememberUpdatedState(onNo)
    fun answer(yes: Boolean) {
        if (answered) return
        answered = true
        if (yes) latestYes() else latestNo()
    }
    Dialog(
        onDismissRequest = { answer(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            // Size the native window before its first draw, not only the Compose card inside it.
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            window?.setDimAmount(.24f)
            window?.setWindowAnimations(0)
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center,
        ) {
            DestinationConfirmCard(
                ui, { answer(true) }, { answer(false) }, !answered,
                modifier.width(maxWidth.coerceAtMost(800.dp)).heightIn(max = maxHeight),
            )
        }
    }
}

/** The same card is used inside the actual modal window and isolated visual verification. */
@Composable
internal fun DestinationConfirmCard(
    ui: DestinationConfirmUi,
    onYes: () -> Unit,
    onNo: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = GgfTheme.colors
    val fontScale = LocalDensity.current.fontScale
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = c.card, shadowElevation = 12.dp) {
        BoxWithConstraints {
            val wide = maxWidth >= 720.dp && fontScale <= 1.3f
            val short = maxHeight < 680.dp
            val stackedButtons = maxWidth < 420.dp * fontScale
            Column(
                Modifier.padding(if (wide) 24.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Only the explanation scrolls, so long names/large type never push answers away.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (wide) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DestinationConfirmArtwork(Modifier.width(220.dp).height(220.dp))
                            DestinationConfirmWords(ui, Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DestinationConfirmArtwork(Modifier.fillMaxWidth().height(if (short) 128.dp else 176.dp))
                            DestinationConfirmWords(ui, Modifier.fillMaxWidth())
                        }
                    }
                }
                if (stackedButtons) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfirmYes(ui.yes, onYes, enabled, Modifier.fillMaxWidth())
                        ConfirmNo(ui.no, onNo, enabled, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConfirmNo(ui.no, onNo, enabled, Modifier.weight(1f))
                        ConfirmYes(ui.yes, onYes, enabled, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationConfirmArtwork(modifier: Modifier) {
    Image(
        painterResource(R.drawable.ggf_dest_confirm), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Fit,
    )
}

@Composable
private fun DestinationConfirmWords(ui: DestinationConfirmUi, modifier: Modifier) {
    val c = GgfTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ui.demo) {
            Surface(color = c.leafSoft, shape = RoundedCornerShape(12.dp)) {
                Text("데모", style = GgfTheme.type.badge, color = c.ink,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        Text(ui.title, style = GgfTheme.type.label, color = c.ink2,
            modifier = Modifier.semantics { heading() })
        Text(
            ui.question,
            style = GgfTheme.type.sentence.copy(lineBreak = LineBreak.Heading),
            color = c.ink,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        ui.route?.let { route ->
            Surface(color = c.leafSoft, shape = RoundedCornerShape(16.dp)) {
                Text(route, style = GgfTheme.type.label, color = c.ink,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun ConfirmYes(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val c = GgfTheme.colors
    Button(
        onClick, modifier.heightIn(min = 64.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = c.forest, contentColor = c.onForest),
    ) { Text(text, style = GgfTheme.type.body, textAlign = TextAlign.Center) }
}

@Composable
private fun ConfirmNo(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(
        onClick, modifier.heightIn(min = 64.dp), enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GgfTheme.colors.ink),
    ) { Text(text, style = GgfTheme.type.label, textAlign = TextAlign.Center) }
}
