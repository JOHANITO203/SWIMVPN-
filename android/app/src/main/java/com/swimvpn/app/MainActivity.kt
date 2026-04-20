package com.swimvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swimvpn.app.ui.theme.SwimVpnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwimVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var isConnected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SWIMVPN+",
            style = MaterialTheme.typography.headlineLarge,
            color = Color(0xFF00C6FF)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = { isConnected = !isConnected },
            modifier = Modifier.size(200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFF00C6FF) else Color(0xFF1E1E1E)
            )
        ) {
            Text(if (isConnected) "Connected" else "Disconneted")
        }
    }
}
