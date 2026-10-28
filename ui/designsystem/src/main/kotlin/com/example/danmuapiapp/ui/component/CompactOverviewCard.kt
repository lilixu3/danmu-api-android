package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.ui.theme.glassBorderColor
import com.example.danmuapiapp.ui.theme.glassSurfaceColor

data class CompactOverviewItem(
    val label: String,
    val value: String,
    val accent: Color = Color.Unspecified,
    val onClick: (() -> Unit)? = null
)

@Composable
fun CompactOverviewCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    status: String,
    statusColor: Color,
    items: List<CompactOverviewItem>,
    modifier: Modifier = Modifier,
    containerColor: Color = glassSurfaceColor(),
    borderColor: Color = glassBorderColor()
) {
    val colors = MaterialTheme.colorScheme
    val compactText = PlatformTextStyle(includeFontPadding = false)

    AppGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            platformStyle = compactText
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            platformStyle = compactText
                        ),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.09f),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).background(statusColor, CircleShape))
                        Text(
                            text = status,
                            modifier = Modifier.widthIn(max = 96.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                platformStyle = compactText
                            ),
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = colors.outlineVariant.copy(alpha = 0.35f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(12.dp)
                                .background(colors.outlineVariant.copy(alpha = 0.45f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                item.onClick?.let { action ->
                                    Modifier.clickable(role = Role.Button, onClick = action)
                                } ?: Modifier
                            )
                            .semantics(mergeDescendants = true) {}
                            .heightIn(min = 32.dp)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                platformStyle = compactText
                            ),
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = item.value,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                platformStyle = compactText
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.accent == Color.Unspecified) colors.onSurface else item.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
