package com.kaushalya.interrupter.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class CarouselSlide(
    val title: String,
    val description: String,
    val icon: String
)

@Composable
fun FeatureCarouselScreen(onFinished: () -> Unit) {
    val slides = listOf(
        CarouselSlide(
            "Welcome to StudyShield",
            "Turn TV ad breaks into fun learning moments for your kids.",
            "\uD83D\uDEE1\uFE0F"
        ),
        CarouselSlide(
            "Interactive Quizzes",
            "Answer multiple choice or fill-in-the-blank questions right on the TV.",
            "\uD83E\uDDE0"
        ),
        CarouselSlide(
            "Track Progress",
            "Monitor your child's learning with detailed stats and history.",
            "\uD83D\uDCCA"
        ),
        CarouselSlide(
            "Ready to Start?",
            "Create an account to unlock all features, or continue as a guest.",
            "\uD83D\uDE80"
        )
    )

    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val slide = slides[pageIndex]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = slide.icon, fontSize = 80.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(slides.size) { iteration ->
                val color = if (pagerState.currentPage == iteration)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < slides.size - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = if (pagerState.currentPage == slides.size - 1) "Get Started" else "Next",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
