package com.stailegrow.lifevpn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.stailegrow.lifevpn.R

@Composable
fun LogsIcon(color: Color, modifier: Modifier = Modifier) {
    NavigationOutline(R.drawable.nav_logs_outline, color, modifier)
}
