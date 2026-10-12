package pl.modlin

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier


/**
 * Renders module's ui if the module is [ModuleState.Active]
 */
@Composable
fun <T : Module> T.moduleUI(
    modifier: Modifier = Modifier,
    content: @Composable T.(modifier: Modifier) -> Unit
) {
    val state by state.collectAsState()
    if (state is ModuleState.Active) {
        content(modifier)
    }
}

@Composable
fun ChildSwitch.childSwitchUI(
    modifier: Modifier,
) {
    val selected by this.currentSelection.collectAsState()
    AnimatedContent(
        modifier = modifier,
        targetState = selected
    ) { target ->

    }
}