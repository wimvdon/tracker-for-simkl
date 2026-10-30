package to.wim.watchlist.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import to.wim.watchlist.R

/**
 * The app's own icon, as the launcher shows it: the gradient TV with the checkmark on its white
 * background (the adaptive icon's foreground layer; its safe zone leaves the margin).
 */
@Composable
fun AppIcon(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}
