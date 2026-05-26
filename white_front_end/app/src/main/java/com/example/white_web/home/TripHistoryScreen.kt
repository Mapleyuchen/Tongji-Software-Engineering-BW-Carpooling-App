package com.example.white_web.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.white_web.APISERVICCE
import com.example.white_web.CompletedOrderData
import com.example.white_web.R
import com.example.white_web.USERNAME
import com.example.white_web.ui.theme.GlowCyan10
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.StarWhite
import kotlinx.coroutines.launch

@Composable
fun TripHistoryScreen(navController: NavHostController) {
    var orders by remember { mutableStateOf<List<CompletedOrderData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadOrders() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val response = APISERVICCE.getCompletedOrders()
                if (response.isSuccessful && response.body()?.code == 200) {
                    orders = response.body()?.data?.list.orEmpty()
                } else {
                    error = response.body()?.message ?: "行程历史加载失败：${response.code()}"
                }
            } catch (e: Exception) {
                error = "行程历史加载失败：${e.message ?: "网络异常"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadOrders()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0xAAFFF9F5)))

        Column(modifier = Modifier.fillMaxSize()) {
            HistoryTopBar(
                title = "行程历史",
                onBackClick = { navController.popBackStack() },
                onRefreshClick = { loadOrders() }
            )

            when {
                isLoading -> HistoryLoading()
                error != null -> HistoryMessage(text = error ?: "加载失败")
                orders.isEmpty() -> HistoryMessage(text = "暂无已完成行程")
                else -> HistoryList(
                    orders = orders,
                    onOrderClick = { orderId -> navController.navigate("tripDetail/$orderId") }
                )
            }
        }
    }
}

@Composable
private fun HistoryTopBar(
    title: String,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSurfaceButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = NeonCyan
            )
        }
        Text(
            text = title,
            color = StarWhite,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        IconSurfaceButton(onClick = onRefreshClick) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新",
                tint = NeonCyan
            )
        }
    }
}

@Composable
private fun HistoryLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NeonCyan)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "正在加载行程历史",
                color = StarWhite,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun HistoryList(
    orders: List<CompletedOrderData>,
    onOrderClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "共 ${orders.size} 条已完成行程",
                color = Color(0xFF6B4A30),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        items(orders) { order ->
            HistoryOrderCard(order = order, onClick = { onOrderClick(order.orderId) })
        }
    }
}

@Composable
private fun HistoryOrderCard(
    order: CompletedOrderData,
    onClick: () -> Unit
) {
    val participants = listOfNotNull(
        order.user1.takeIf { it.isNotBlank() },
        order.user2?.takeIf { it.isNotBlank() },
        order.user3?.takeIf { it.isNotBlank() },
        order.user4?.takeIf { it.isNotBlank() }
    )
    val roleText = if (order.driver == USERNAME) "我是司机" else "我是乘客"
    val timeText = formatOrderTime(order.date, order.earliestDepartureTime, order.latestDepartureTime)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF7FFFFFF))
            .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlowCyan10),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = NeonCyan)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${order.departure} → ${order.destination}",
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeText,
                    color = Color(0xFF6B4A30),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            StatusChip(text = "已完成", tint = NeonGreen)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(icon = Icons.Default.Person, text = roleText, tint = NeonPurple)
            InfoChip(icon = Icons.Default.Star, text = order.driver ?: "暂无司机", tint = NeonBlue)
        }

        Text(
            text = "乘客 ${participants.size}/4 · 完成时间 ${formatCompletedAt(order.completedAt)}",
            color = Color(0xFF8B6A50),
            style = MaterialTheme.typography.bodySmall
        )

        if (!order.remark.isNullOrBlank()) {
            Text(
                text = order.remark,
                color = Color(0xFF6B4A30),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF7FFFFFF))
                .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonCyan)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = Color(0xFF6B4A30),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun IconSurfaceButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xEFFFFFFF))
            .border(1.dp, GlowCyan30, RoundedCornerShape(10.dp))
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(
            text = text,
            color = StarWhite,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    tint: Color
) {
    Text(
        text = text,
        color = tint,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

private fun formatCompletedAt(value: String?): String {
    return value
        ?.replace("T", " ")
        ?.substringBefore(".")
        ?.takeIf { it.isNotBlank() }
        ?: "暂无记录"
}
