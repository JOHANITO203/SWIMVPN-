package com.swimvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.swimvpn.app.R
import com.swimvpn.app.data.network.ServerGroup
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.ui.theme.ElectricBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    serverGroups: List<ServerGroup>,
    activeServerId: String?,
    onBack: () -> Unit,
    onSelectServer: (ServerNode) -> Unit,
    onTogglePinServer: (ServerNode) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(serverGroups, searchQuery) {
        serverGroups.mapNotNull { group ->
            val matchingServers = group.servers.filter { server ->
                server.country.contains(searchQuery, ignoreCase = true) ||
                    server.city.contains(searchQuery, ignoreCase = true) ||
                    server.host.contains(searchQuery, ignoreCase = true) ||
                    group.title.contains(searchQuery, ignoreCase = true)
            }
            if (matchingServers.isEmpty()) null else group.copy(servers = matchingServers)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.title_locations), style = MaterialTheme.typography.headlineLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            filteredGroups.forEach { group ->
                item(key = "group-${group.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        group.subtitle?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(group.servers, key = { it.id }) { server ->
                    ServerItem(
                        server = server,
                        isSelected = server.id == activeServerId,
                        onClick = { onSelectServer(server) },
                        onTogglePin = { onTogglePinServer(server) },
                    )
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: ServerNode,
    isSelected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        server.isPinned -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val flagEmoji = getFlagEmoji(server.countryCode ?: "")
                    Text(
                        text = if (server.countryCode.isNullOrBlank()) server.country else "$flagEmoji ${server.country.uppercase()}",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (server.isPinned) {
                        Spacer(modifier = Modifier.width(8.dp))
                        PinBadge()
                    }
                }
                Text(
                    text = "${server.city} • ${server.protocol.uppercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    text = "${server.host}:${server.port}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin server",
                        tint = if (server.isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (server.ping > 0) {
                    Text(
                        text = "${server.ping}ms",
                        color = getPingColor(server.ping),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                CircularProgressIndicator(
                    progress = { server.load / 100f },
                    modifier = Modifier.size(24.dp),
                    color = getLoadColor(server.load),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Composable
private fun PinBadge() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "PIN",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return "🌐"
    val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

@Composable
fun getPingColor(ping: Int): Color = when {
    ping < 100 -> MaterialTheme.colorScheme.primary
    ping < 200 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

@Composable
fun getLoadColor(load: Int): Color = when {
    load < 50 -> MaterialTheme.colorScheme.primary
    load < 80 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}
