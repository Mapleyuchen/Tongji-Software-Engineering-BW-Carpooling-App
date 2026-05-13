package com.example.white_web.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.white_web.APISERVICCE
import com.example.white_web.R
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.StarWhite

data class User(
    val username: String,
    val password: String
)

data class LookResponse(
    val code: Int,
    val message: String,
    val data: Data?
) {
    data class Data(
        val table: List<User>
    )
}

@Composable
fun MyInfoScreen() {
    var tableData by remember { mutableStateOf<List<User>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = APISERVICCE.look()
            if (response.isSuccessful && response.body()?.code == 200) {
                tableData = response.body()?.data?.table
            }
        } catch (_: Exception) { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0x66FFFFFF)))

        if (tableData != null) {
            DisplayTable(tableData!!)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "载入中...",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = NeonCyan,
                        letterSpacing = 4.sp
                    )
                )
            }
        }
    }
}

@Composable
fun DisplayTable(users: List<User>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "用户数据总览",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = NeonCyan
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(NeonCyan.copy(0.15f), NeonBlue.copy(0.1f))),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(NeonCyan.copy(0.6f), NeonBlue.copy(0.4f))),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "用户名",
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Text(
                text = "密码",
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
        }

        // Data rows
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    GlowCyan30,
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                )
                .background(Color(0xF0FFFFFF), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(horizontal = 16.dp)
        ) {
            items(users) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = user.username,
                        modifier = Modifier.weight(1f),
                        color = StarWhite
                    )
                    Text(
                        text = user.password,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF8B6A50)
                    )
                }
                HorizontalDivider(color = GlowCyan30, thickness = 0.5.dp)
            }
        }
    }
}

@Preview
@Composable
fun PreviewMyinfo() {
    MyInfoScreen()
}
