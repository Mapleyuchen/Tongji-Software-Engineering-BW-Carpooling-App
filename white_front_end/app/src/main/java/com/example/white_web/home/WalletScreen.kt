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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.white_web.APISERVICCE
import com.example.white_web.R
import com.example.white_web.USERTYPE
import com.example.white_web.WalletSummaryData
import com.example.white_web.WalletTransactionData
import com.example.white_web.WalletWithdrawRequest
import com.example.white_web.ui.theme.GlowCyan10
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.NeonRed
import com.example.white_web.ui.theme.StarWhite
import kotlinx.coroutines.launch

data class WalletUiState(
    val summary: WalletSummaryData? = null,
    val incomeRecords: List<WalletTransactionData> = emptyList(),
    val withdrawRecords: List<WalletTransactionData> = emptyList(),
    val isLoading: Boolean = true,
    val summaryError: String? = null,
    val incomeError: String? = null,
    val withdrawError: String? = null
)

@Composable
fun WalletScreen(navController: NavHostController? = null) {
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(WalletUiState()) }
    var refreshTick by remember { mutableStateOf(0) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawing by remember { mutableStateOf(false) }
    var lastWithdrawMessage by remember { mutableStateOf<String?>(null) }

    val onBackClick: () -> Unit = {
        val handled = navController?.popBackStack() ?: false
        if (!handled) {
            navController?.navigate("myInfo") { launchSingleTop = true }
        }
    }

    LaunchedEffect(refreshTick) {
        if (USERTYPE != 2) {
            uiState = WalletUiState(
                isLoading = false,
                summaryError = "钱包功能仅向司机用户开放"
            )
            return@LaunchedEffect
        }

        uiState = uiState.copy(isLoading = true)

        var summary: WalletSummaryData? = null
        var summaryError: String? = null
        var incomeList: List<WalletTransactionData> = emptyList()
        var incomeError: String? = null
        var withdrawList: List<WalletTransactionData> = emptyList()
        var withdrawError: String? = null

        try {
            val res = APISERVICCE.getWalletSummary()
            if (res.isSuccessful && res.body()?.code == 200) {
                summary = res.body()?.data
            } else {
                summaryError = res.body()?.message ?: "钱包概要加载失败"
            }
        } catch (e: Exception) {
            summaryError = "钱包概要加载失败：${e.message ?: "网络异常"}"
        }

        try {
            val res = APISERVICCE.getWalletIncomeRecords()
            if (res.isSuccessful && res.body()?.code == 200) {
                incomeList = res.body()?.data?.list.orEmpty()
            } else {
                incomeError = res.body()?.message ?: "收入记录加载失败"
            }
        } catch (e: Exception) {
            incomeError = "收入记录加载失败：${e.message ?: "网络异常"}"
        }

        try {
            val res = APISERVICCE.getWalletWithdrawRecords()
            if (res.isSuccessful && res.body()?.code == 200) {
                withdrawList = res.body()?.data?.list.orEmpty()
            } else {
                withdrawError = res.body()?.message ?: "提现记录加载失败"
            }
        } catch (e: Exception) {
            withdrawError = "提现记录加载失败：${e.message ?: "网络异常"}"
        }

        uiState = WalletUiState(
            summary = summary,
            incomeRecords = incomeList,
            withdrawRecords = withdrawList,
            isLoading = false,
            summaryError = summaryError,
            incomeError = incomeError,
            withdrawError = withdrawError
        )
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
            WalletTopBar(
                title = "司机钱包",
                onBackClick = onBackClick,
                onRefreshClick = { refreshTick += 1 }
            )

            if (uiState.isLoading) {
                LoadingWallet(modifier = Modifier.weight(1f))
            } else {
                WalletContent(
                    modifier = Modifier.weight(1f),
                    state = uiState,
                    lastWithdrawMessage = lastWithdrawMessage,
                    onWithdrawClick = {
                        lastWithdrawMessage = null
                        showWithdrawDialog = true
                    }
                )
            }
        }
    }

    if (showWithdrawDialog) {
        val maxAmount = uiState.summary?.balance ?: 0.0
        WithdrawDialog(
            maxAmount = maxAmount,
            isSubmitting = withdrawing,
            onDismiss = { if (!withdrawing) showWithdrawDialog = false },
            onConfirm = { amount, payeeAccount ->
                if (withdrawing) return@WithdrawDialog
                withdrawing = true
                lastWithdrawMessage = null
                scope.launch {
                    try {
                        val res = APISERVICCE.withdrawFromWallet(
                            request = WalletWithdrawRequest(
                                amount = amount,
                                payeeAccount = payeeAccount
                            )
                        )
                        val body = res.body()
                        if (res.isSuccessful && body?.code == 200 && body.data != null) {
                            lastWithdrawMessage = body.message.ifBlank { "提现成功" }
                            uiState = uiState.copy(
                                summary = body.data.wallet,
                                withdrawRecords = listOf(body.data.transaction) + uiState.withdrawRecords
                            )
                            showWithdrawDialog = false
                        } else {
                            lastWithdrawMessage = body?.message ?: "提现失败，请稍后再试"
                        }
                    } catch (e: Exception) {
                        lastWithdrawMessage = "提现失败：${e.message ?: "网络异常"}"
                    } finally {
                        withdrawing = false
                    }
                }
            }
        )
    }
}

@Composable
private fun WalletTopBar(
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xEFFFFFFF))
                .border(1.dp, GlowCyan30, RoundedCornerShape(10.dp))
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = NeonCyan
                )
            }
        }
        Text(
            text = title,
            color = StarWhite,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xEFFFFFFF))
                .border(1.dp, GlowCyan30, RoundedCornerShape(10.dp))
        ) {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = NeonCyan
                )
            }
        }
    }
}

@Composable
private fun LoadingWallet(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NeonCyan)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "正在加载钱包信息",
                color = StarWhite,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun WalletContent(
    modifier: Modifier = Modifier,
    state: WalletUiState,
    lastWithdrawMessage: String?,
    onWithdrawClick: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            WalletSummaryCard(
                summary = state.summary,
                error = state.summaryError,
                onWithdrawClick = onWithdrawClick
            )
        }

        if (lastWithdrawMessage != null) {
            item {
                WalletNotice(text = lastWithdrawMessage)
            }
        }

        item {
            WalletSectionHeader(
                title = "收入记录",
                subtitle = "${state.incomeRecords.size} 条",
                tint = NeonGreen
            )
        }
        when {
            state.incomeError != null -> item { WalletEmptyState(text = state.incomeError) }
            state.incomeRecords.isEmpty() -> item {
                WalletEmptyState(text = "暂无收入记录，乘客完成支付后会出现在这里")
            }
            else -> items(state.incomeRecords) { txn -> TransactionCard(txn, isIncome = true) }
        }

        item {
            WalletSectionHeader(
                title = "提现记录",
                subtitle = "${state.withdrawRecords.size} 条",
                tint = NeonPurple
            )
        }
        when {
            state.withdrawError != null -> item { WalletEmptyState(text = state.withdrawError) }
            state.withdrawRecords.isEmpty() -> item {
                WalletEmptyState(text = "暂无提现记录")
            }
            else -> items(state.withdrawRecords) { txn -> TransactionCard(txn, isIncome = false) }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun WalletSummaryCard(
    summary: WalletSummaryData?,
    error: String?,
    onWithdrawClick: () -> Unit
) {
    WalletSurface {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(NeonCyan, NeonBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "当前可提现余额",
                        color = Color(0xFF8B6A50),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "¥ ${formatMoney(summary?.balance ?: 0.0)}",
                        color = StarWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WalletStat(
                    modifier = Modifier.weight(1f),
                    label = "累计收入",
                    value = formatMoney(summary?.totalIncome ?: 0.0),
                    tint = NeonGreen
                )
                WalletStat(
                    modifier = Modifier.weight(1f),
                    label = "累计提现",
                    value = formatMoney(summary?.totalWithdraw ?: 0.0),
                    tint = NeonPurple
                )
            }

            Button(
                onClick = onWithdrawClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                enabled = (summary?.balance ?: 0.0) > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.White,
                    disabledContainerColor = NeonCyan.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if ((summary?.balance ?: 0.0) > 0.0) "立即提现" else "暂无可提现余额",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (error != null) WalletEmptyState(text = error)
        }
    }
}

@Composable
private fun WalletStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tint: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = Color(0xFF6B4A30), style = MaterialTheme.typography.bodySmall)
        Text(
            text = "¥ $value",
            color = tint,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun WalletSectionHeader(title: String, subtitle: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            color = StarWhite,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Text(
            text = subtitle,
            color = Color(0xFF8B6A50),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun TransactionCard(txn: WalletTransactionData, isIncome: Boolean) {
    val tint = if (isIncome) NeonGreen else NeonPurple
    val title = if (isIncome) {
        if (txn.orderId != null) "拼车订单 #${txn.orderId}" else "拼车收入"
    } else {
        "提现 #${txn.transactionId}"
    }
    val amountText = (if (isIncome) "+" else "-") + " ¥${formatMoney(txn.amount)}"
    val statusLabel = when (txn.status) {
        "SUCCESS" -> "成功"
        "PENDING" -> "处理中"
        "FAILED" -> "失败"
        else -> txn.status
    }

    WalletSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isIncome) Icons.Default.Star else Icons.Default.Send,
                    contentDescription = null,
                    tint = tint
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = txn.remark ?: if (isIncome) "乘客支付入账" else "余额提现",
                    color = Color(0xFF6B4A30),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDateTime(txn.createdAt),
                    color = Color(0xFF8B6A50),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = amountText,
                    color = tint,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = statusLabel,
                    color = if (txn.status == "SUCCESS") NeonGreen else if (txn.status == "FAILED") NeonRed else Color(0xFF8B6A50),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun WalletNotice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GlowCyan10)
            .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = StarWhite,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun WalletEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xEFFFFFFF))
            .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF6B4A30),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WalletSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF7FFFFFF))
            .border(1.dp, GlowCyan30, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun WithdrawDialog(
    maxAmount: Double,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, payeeAccount: String) -> Unit
) {
    var amountText by remember { mutableStateOf(formatMoney(maxAmount)) }
    var payeeAccountText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "提现",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前可提现余额：¥ ${formatMoney(maxAmount)}",
                    color = StarWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = payeeAccountText,
                    onValueChange = {
                        payeeAccountText = it
                        localError = null
                    },
                    label = { Text("支付宝收款账号") },
                    placeholder = { Text("例如 myaccount@sandbox.com") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlowCyan30
                    )
                )
                Text(
                    text = "请填写支付宝沙箱收款账号（登录账号），格式类似 myaccount@sandbox.com，可在支付宝开放平台沙箱环境中查看。",
                    color = Color(0xFF8B6A50),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        localError = null
                    },
                    label = { Text("提现金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlowCyan30
                    )
                )
                Text(
                    text = "提现优先调用支付宝沙箱转账接口；若沙箱不可用则会回退为本地模拟提现。",
                    color = Color(0xFF8B6A50),
                    style = MaterialTheme.typography.bodySmall
                )
                if (localError != null) {
                    Text(
                        text = localError!!,
                        color = NeonRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    val account = payeeAccountText.trim()
                    if (account.isBlank()) {
                        localError = "请填写支付宝收款账号"
                        return@TextButton
                    }
                    if (!account.contains("@")) {
                        localError = "收款账号格式不正确，示例：myaccount@sandbox.com"
                        return@TextButton
                    }
                    val parsed = amountText.trim().toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        localError = "请输入大于 0 的金额"
                        return@TextButton
                    }
                    if (parsed > maxAmount + 1e-6) {
                        localError = "金额不能超过当前余额"
                        return@TextButton
                    }
                    onConfirm(parsed, account)
                }
            ) {
                Text(if (isSubmitting) "提现中…" else "确认提现", color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text("取消", color = Color(0xFF8B6A50))
            }
        }
    )
}

private fun formatMoney(value: Double): String {
    return "%.2f".format(value)
}

private fun formatDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.replace("T", " ").take(19)
}
