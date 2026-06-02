package com.example.white_web.home

import UserDetailResponse
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.white_web.APISERVICCE
import com.example.white_web.R
import com.example.white_web.USERNAME
import com.example.white_web.TOKEN
import com.example.white_web.USERTYPE
import com.example.white_web.UserCouponData
import com.example.white_web.VehicleData
import com.example.white_web.WalletSummaryData
import com.example.white_web.ui.theme.GlowCyan10
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
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

data class ProfileUiState(
    val userDetail: UserDetailResponse.Data? = null,
    val vehicles: List<VehicleData> = emptyList(),
    val coupons: List<UserCouponData> = emptyList(),
    val walletSummary: WalletSummaryData? = null,
    val isLoading: Boolean = true,
    val profileError: String? = null,
    val vehicleError: String? = null,
    val couponError: String? = null,
    val walletError: String? = null
)

@Composable
fun MyInfoScreen(navController: NavHostController? = null) {
    var uiState by remember { mutableStateOf(ProfileUiState()) }
    val currentUsername = USERNAME?.takeIf { it.isNotBlank() } ?: "未登录"
    val currentUserType = USERTYPE ?: -1
    val onBackClick: () -> Unit = {
        val handled = navController?.popBackStack() ?: false
        if (!handled) {
            navController?.navigate("home") {
                launchSingleTop = true
            }
        }
    }
    val onLogoutClick: () -> Unit = {
        USERNAME = "未登录"
        TOKEN = ""
        USERTYPE = -1
        navController?.navigate("login") {
            popUpTo("home") { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(currentUsername, currentUserType) {
        if (currentUsername == "未登录") {
            uiState = ProfileUiState(
                isLoading = false,
                profileError = "当前未登录"
            )
            return@LaunchedEffect
        }

        var profile: UserDetailResponse.Data? = null
        var profileError: String? = null
        var vehicles = emptyList<VehicleData>()
        var vehicleError: String? = null
        var coupons = emptyList<UserCouponData>()
        var couponError: String? = null
        var walletSummary: WalletSummaryData? = null
        var walletError: String? = null

        try {
            val response = APISERVICCE.getUserDetail(currentUsername)
            if (response.isSuccessful && response.body()?.code == 200) {
                profile = response.body()?.data
            } else {
                profileError = response.body()?.message ?: "用户资料加载失败"
            }
        } catch (e: Exception) {
            profileError = "用户资料加载失败：${e.message ?: "网络异常"}"
        }

        if (currentUserType == 2) {
            try {
                val response = APISERVICCE.getMyVehicles()
                if (response.isSuccessful && response.body()?.code == 200) {
                    vehicles = response.body()?.data?.list.orEmpty()
                } else {
                    vehicleError = response.body()?.message ?: "车辆信息加载失败"
                }
            } catch (e: Exception) {
                vehicleError = "车辆信息加载失败：${e.message ?: "网络异常"}"
            }

            try {
                val response = APISERVICCE.getWalletSummary()
                if (response.isSuccessful && response.body()?.code == 200) {
                    walletSummary = response.body()?.data
                } else {
                    walletError = response.body()?.message ?: "钱包信息加载失败"
                }
            } catch (e: Exception) {
                walletError = "钱包信息加载失败：${e.message ?: "网络异常"}"
            }
        }

        try {
            val response = APISERVICCE.getMyCoupons()
            if (response.isSuccessful && response.body()?.code == 200) {
                coupons = response.body()?.data?.list.orEmpty()
            } else {
                couponError = response.body()?.message ?: "优惠券加载失败"
            }
        } catch (e: Exception) {
            couponError = "优惠券加载失败：${e.message ?: "网络异常"}"
        }

        uiState = ProfileUiState(
            userDetail = profile,
            vehicles = vehicles,
            coupons = coupons,
            walletSummary = walletSummary,
            isLoading = false,
            profileError = profileError,
            vehicleError = vehicleError,
            couponError = couponError,
            walletError = walletError
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
            ProfileTopBar(
                title = "个人中心",
                onBackClick = onBackClick
            )

            if (uiState.isLoading) {
                LoadingProfile(modifier = Modifier.weight(1f))
            } else {
                ProfileContent(
                    modifier = Modifier.weight(1f),
                    username = currentUsername,
                    userType = currentUserType,
                    state = uiState,
                    onTripHistoryClick = { navController?.navigate("tripHistory") },
                    onVehicleManageClick = { navController?.navigate("vehicleManagement") },
                    onWalletClick = { navController?.navigate("wallet") }
                )
            }

            LogoutAction(onClick = onLogoutClick)
        }
    }
}

@Composable
private fun ProfileTopBar(
    title: String,
    onBackClick: () -> Unit
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
    }
}

@Composable
private fun LoadingProfile(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NeonCyan)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "正在加载个人中心",
                color = StarWhite,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ProfileContent(
    modifier: Modifier = Modifier,
    username: String,
    userType: Int,
    state: ProfileUiState,
    onTripHistoryClick: () -> Unit = {},
    onVehicleManageClick: () -> Unit = {},
    onWalletClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ProfileHeader(
                username = username,
                phoneNumber = state.userDetail?.phonenumber ?: "暂无手机号",
                roleName = state.userDetail?.usertype ?: roleName(userType),
                error = state.profileError
            )
        }

        if (userType == 2) {
            item {
                SectionHeader(
                    title = "我的钱包",
                    subtitle = "查看收入与提现",
                    icon = Icons.Default.ShoppingCart,
                    tint = NeonGreen
                )
            }
            item {
                DriverWalletCard(
                    summary = state.walletSummary,
                    error = state.walletError,
                    onClick = onWalletClick
                )
            }
        }

        item {
            SectionHeader(
                title = "常用功能",
                subtitle = "快捷入口",
                icon = Icons.Default.DateRange,
                tint = NeonBlue
            )
        }

        item {
            ProfileActions(
                userType = userType,
                onTripHistoryClick = onTripHistoryClick,
                onVehicleManageClick = onVehicleManageClick
            )
        }

        if (userType == 2) {
            item {
                SectionHeader(
                    title = "我的车辆",
                    subtitle = "${state.vehicles.size} 辆",
                    icon = Icons.Default.AccountCircle,
                    tint = NeonPurple
                )
            }
            if (state.vehicleError != null) {
                item { EmptyState(text = state.vehicleError) }
            } else if (state.vehicles.isEmpty()) {
                item { EmptyState(text = "还没有车辆信息，可在后续车辆管理页面中添加") }
            } else {
                items(state.vehicles) { vehicle ->
                    VehicleCard(vehicle)
                }
            }
        }

        item {
            SectionHeader(
                title = "我的优惠券",
                subtitle = "${state.coupons.count { it.canUse }} 张可用",
                icon = Icons.Default.ShoppingCart,
                tint = NeonGreen
            )
        }
        if (state.couponError != null) {
            item { EmptyState(text = state.couponError) }
        } else if (state.coupons.isEmpty()) {
            item { EmptyState(text = "暂无优惠券") }
        } else {
            items(state.coupons) { coupon ->
                CouponCard(coupon)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileActions(
    userType: Int,
    onTripHistoryClick: () -> Unit,
    onVehicleManageClick: () -> Unit
) {
    ProfileSurface {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileActionButton(
                text = "行程历史",
                icon = Icons.Default.DateRange,
                color = NeonCyan,
                onClick = onTripHistoryClick
            )
            if (userType == 2) {
                ProfileActionButton(
                    text = "车辆管理",
                    icon = Icons.Default.AccountCircle,
                    color = NeonPurple,
                    onClick = onVehicleManageClick
                )
            }
        }
    }
}

@Composable
private fun DriverWalletCard(
    summary: WalletSummaryData?,
    error: String?,
    onClick: () -> Unit
) {
    ProfileSurface {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "当前余额",
                        color = Color(0xFF8B6A50),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "¥ ${formatAmount(summary?.balance ?: 0.0)}",
                        color = StarWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileChip(
                    icon = Icons.Default.Star,
                    text = "累计收入 ¥${formatAmount(summary?.totalIncome ?: 0.0)}",
                    tint = NeonGreen
                )
                ProfileChip(
                    icon = Icons.Default.DateRange,
                    text = "累计提现 ¥${formatAmount(summary?.totalWithdraw ?: 0.0)}",
                    tint = NeonPurple
                )
            }

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan.copy(alpha = 0.16f),
                    contentColor = NeonCyan
                )
            ) {
                Text(
                    text = "查看钱包详情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (error != null) {
                EmptyState(text = error)
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    username: String,
    phoneNumber: String,
    roleName: String,
    error: String?
) {
    ProfileSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonCyan, NeonBlue))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.firstOrNull()?.uppercase() ?: "我",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = username,
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileChip(icon = Icons.Default.Person, text = roleName, tint = NeonCyan)
                    ProfileChip(icon = Icons.Default.Call, text = phoneNumber, tint = NeonBlue)
                }
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            EmptyState(text = error)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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
private fun VehicleCard(vehicle: VehicleData) {
    ProfileSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = NeonPurple,
                modifier = Modifier.size(30.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = vehicle.licensePlate,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${vehicle.brand} ${vehicle.model} · ${vehicle.color} · ${vehicle.seatCount} 座",
                    color = Color(0xFF6B4A30),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ProfileChip(
                icon = Icons.Default.Star,
                text = if (vehicle.isVerified) "已认证" else "待认证",
                tint = if (vehicle.isVerified) NeonGreen else Color(0xFF8B6A50)
            )
        }
    }
}

@Composable
private fun CouponCard(coupon: UserCouponData) {
    val statusText = when {
        coupon.canUse -> "可使用"
        coupon.isExpired -> "已过期"
        coupon.usedCount >= coupon.usageLimit -> "已用完"
        else -> "不可用"
    }
    val statusColor = if (coupon.canUse) NeonGreen else Color(0xFF8B6A50)
    val discountText = if (coupon.discountType == "percentage") {
        "${coupon.discountValue.toInt()} 折"
    } else {
        "减 ${formatAmount(coupon.discountValue)} 元"
    }

    ProfileSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlowCyan10),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = NeonCyan)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = coupon.couponName,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$discountText · 满 ${formatAmount(coupon.minAmount)} 元可用",
                    color = Color(0xFF6B4A30),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "有效期 ${coupon.startDate} 至 ${coupon.endDate} · 已用 ${coupon.usedCount}/${coupon.usageLimit}",
                    color = Color(0xFF8B6A50),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ProfileChip(
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
private fun EmptyState(text: String) {
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
private fun LogoutAction(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFCC4444),
            contentColor = Color.White
        )
    ) {
        Text(
            text = "退出登录",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun ProfileSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF7FFFFFF))
            .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

private fun roleName(userType: Int): String {
    return when (userType) {
        1 -> "一般用户"
        2 -> "司机"
        else -> "未知角色"
    }
}

private fun formatAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.2f".format(value)
    }
}

@Preview
@Composable
fun PreviewMyinfo() {
    MaterialTheme {
        ProfileContent(
            username = "passenger1",
            userType = 1,
            state = ProfileUiState(
                userDetail = UserDetailResponse.Data("13800000001", "一般用户"),
                coupons = listOf(
                    UserCouponData(
                        couponId = 1,
                        couponName = "新用户立减券",
                        discountType = "fixed",
                        discountValue = 5.0,
                        minAmount = 10.0,
                        startDate = "2026-05-01",
                        endDate = "2026-06-01",
                        usageLimit = 1,
                        usedCount = 0,
                        obtainedAt = "2026-05-26T10:00:00",
                        canUse = true,
                        isExpired = false
                    )
                ),
                isLoading = false
            )
        )
    }
}
