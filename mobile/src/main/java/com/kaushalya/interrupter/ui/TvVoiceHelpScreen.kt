package com.kaushalya.interrupter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How-to for installing TTS voice data (e.g. Hindi) on the TV, so quiz
 * dictation is spoken in the right voice. Reached from the nav drawer
 * ("TV Voice Help"); the TV app also points here when Hindi voice data
 * is missing on the TV.
 */
@Composable
fun TvVoiceHelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "TV Voice Help",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Quiz questions are read aloud on the TV. Hindi quizzes need the Hindi voice installed on the TV — otherwise they sound English or robotic. If the TV shows a \"Hindi voice missing\" message, follow these steps on the TV (not on this phone):",
            fontSize = 15.sp
        )
        val steps = listOf(
            "On the TV, open Settings (gear icon on the home screen).",
            "Go to System (or Device Preferences on some TVs) > Text-to-Speech output.",
            "Make sure Google Text-to-Speech is selected as the preferred engine.",
            "Tap the gear/settings icon next to Google Text-to-Speech.",
            "Tap Install voice data, choose Hindi (हिन्दी), and download it. Add Marathi too if your child takes Marathi quizzes.",
            "Go back and start any Hindi quiz again — the dictation should now speak in Hindi."
        )
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}. ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = step, fontSize = 15.sp)
            }
        }
        HorizontalDivider()
        Text(
            text = "Tips",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        val tips = listOf(
            "The TV needs internet while downloading the voice — keep Wi-Fi on.",
            "On some TVs the path is Settings > Accessibility > Text-to-Speech instead.",
            "If Hindi still sounds English after installing, restart the TV app and try again.",
            "English quizzes always use the British English voice and need no setup."
        )
        tips.forEach { tip ->
            Text(text = "• $tip", fontSize = 15.sp)
        }
    }
}
