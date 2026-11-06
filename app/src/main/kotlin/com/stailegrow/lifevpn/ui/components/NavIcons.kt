package com.stailegrow.lifevpn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.stailegrow.lifevpn.R

/** Контурные значки нижней навигации — та же геометрия, что у значков меню в Windows-версии (сетка 24, линии 2, 22dp). */
@Composable
internal fun NavigationOutline(@DrawableRes resource: Int, color: Color, modifier: Modifier = Modifier) {
    Image(painterResource(resource), contentDescription = null,
        modifier = modifier.size(22.dp), colorFilter = ColorFilter.tint(color))
}

@Composable
fun HomeIcon(color: Color, modifier: Modifier = Modifier) {
    NavigationOutline(R.drawable.nav_home_outline, color, modifier)
}

@Composable
fun ServersIcon(color: Color, modifier: Modifier = Modifier) {
    NavigationOutline(R.drawable.nav_servers_outline, color, modifier)
}

@Composable
fun StatsIcon(color: Color, modifier: Modifier = Modifier) {
    NavigationOutline(R.drawable.nav_stats_outline, color, modifier)
}

@Composable
fun SettingsIcon(color: Color, modifier: Modifier = Modifier) {
    NavigationOutline(R.drawable.nav_settings_outline, color, modifier)
}
