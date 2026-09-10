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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.R
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
            stringResource(R.string.carousel_slide_1_title),
            stringResource(R.string.carousel_slide_1_desc),
            "\uD83D\uDEE1\uFE0F"
        ),
        CarouselSlide(
            stringResource(R.string.carousel_slide_2_title),
            stringResource(R.string.carousel_slide_2_desc),
            "\uD83E\uDDE0"
        ),
        CarouselSlide(
            stringResource(R.string.carousel_slide_3_title),
            stringResource(R.string.carousel_slide_3_desc),
            "\uD83D\uDCCA"
        ),
        CarouselSlide(
            stringResource(R.string.carousel_slide_4_title),
            stringResource(R.string.carousel_slide_4_desc),
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
                text = if (pagerState.currentPage == slides.size - 1) stringResource(R.string.carousel_get_started) else stringResource(R.string.carousel_next),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
