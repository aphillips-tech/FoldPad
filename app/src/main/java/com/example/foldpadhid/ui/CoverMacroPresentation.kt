package com.example.foldpadhid.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.foldpadhid.hid.BluetoothHidController

enum class MacroType { KEYBOARD, CONSUMER }

data class MacroSpec(
    val label: String,
    val sublabel: String,
    val type: MacroType,
    val modifierMask: Int,
    val usageCode: Int
)

private val DEFAULT_COVER_MACROS = listOf(
    MacroSpec("Play / Pause", "Consumer 0x00CD", MacroType.CONSUMER, 0x00, 0x00CD),
    MacroSpec("Vol -", "Consumer 0x00EA", MacroType.CONSUMER, 0x00, 0x00EA),
    MacroSpec("Vol +", "Consumer 0x00E9", MacroType.CONSUMER, 0x00, 0x00E9),
    MacroSpec("Mute", "Consumer 0x00E2", MacroType.CONSUMER, 0x00, 0x00E2),
    MacroSpec("Copy", "Ctrl+C (0x06)", MacroType.KEYBOARD, 0x01, 0x0006),
    MacroSpec("Paste", "Ctrl+V (0x19)", MacroType.KEYBOARD, 0x01, 0x0019),
    MacroSpec("Undo", "Ctrl+Z (0x1D)", MacroType.KEYBOARD, 0x01, 0x001D),
    MacroSpec("Launcher", "GUI+Space (0x2C)", MacroType.KEYBOARD, 0x08, 0x002C)
)

class CoverMacroPresentation(
    outerContext: Context,
    display: Display,
    private val hidController: BluetoothHidController,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateOwner: SavedStateRegistryOwner
) : Presentation(outerContext, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(lifecycleOwner)
            decor.setViewTreeSavedStateRegistryOwner(savedStateOwner)
        }
        val composeView = ComposeView(context).apply {
            setContent {
                CoverMacroDeckContent(hidController = hidController)
            }
        }
        setContentView(composeView)
    }
}

@Composable
fun CoverMacroDeckContent(hidController: BluetoothHidController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Cover Macro Deck",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(DEFAULT_COVER_MACROS) { macro ->
                Button(
                    onClick = {
                        when (macro.type) {
                            MacroType.CONSUMER -> hidController.sendConsumerTap(macro.usageCode)
                            MacroType.KEYBOARD -> hidController.sendKeyTap(macro.modifierMask, macro.usageCode)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(74.dp)
                ) {
                    Column {
                        Text(macro.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(macro.sublabel, fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    }
                }
            }
        }
    }
}
