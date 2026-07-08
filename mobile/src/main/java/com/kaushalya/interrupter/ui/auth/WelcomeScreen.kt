package com.kaushalya.interrupter.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onGuest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "StudyShield",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B00)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Turn TV Ads into Learning Time",
                fontSize = 18.sp,
                color = Color(0xFF1E88E5),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = onSignUp,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Sign In", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onGuest) {
                Text(
                    "Continue as Guest",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
