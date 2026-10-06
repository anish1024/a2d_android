package com.a2d.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.a2d.app.ui.theme.A2DTheme

@Composable
fun HelloWorldScreen(
    modifier: Modifier = Modifier,
) {
    var pulseCount by remember { mutableIntStateOf(0) }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "A2D – Adult ADHD Assistant",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Hello World – Environment Ready",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { pulseCount++ },
                modifier = Modifier
                    .padding(top = 40.dp)
                    .padding(horizontal = 24.dp),
            ) {
                Text("Pulse HIGH")
            }
            Text(
                text = "Pulses: $pulseCount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelloWorldScreenPreview() {
    A2DTheme {
        HelloWorldScreen()
    }
}
