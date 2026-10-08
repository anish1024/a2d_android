package com.a2d.app.ui.logicgate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.PulseResult

@Composable
fun LogicGateScreen(
    lastResult: PulseResult?,
    errorMessage: String?,
    onSendPulse: (String, String) -> Unit
) {
    var pinText by remember { mutableStateOf("2") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AND Gate Signal Simulator", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pinText,
            onValueChange = { pinText = it },
            label = { Text("Input Pin (Arity)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSendPulse(pinText, "HIGH") }) {
                Text("Pulse HIGH")
            }
            Button(
                onClick = { onSendPulse(pinText, "LOW") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Pulse LOW (Reset)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }

        lastResult?.let { res ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pin: ${res.inputPin}")
                    Text("Pulse: ${res.pulse}")
                    Text("Latched Inputs: ${res.latchedInputs}")
                    Text("Gate Output: ${res.output}", style = MaterialTheme.typography.titleLarge)
                    Text("Reset: ${res.reset}")
                }
            }
        }
    }
}
