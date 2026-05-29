/**
 * 应用主页界面
 *
 * 文件功能: 主页面UI和拼车订单列表展示
 *
 * 主要功能:
 * - 展示可用的拼车订单列表
 * - 提供搜索和筛选功能
 * - 支持地图视图和列表视图切换
 * - 实现下拉刷新和上拉加载
 * - 集成实时位置服务
 *
 * 数据管理:
 * - HomeViewModel状态管理
 * - API数据获取和缓存
 * - 位置信息处理
 * - 搜索结果管理
 *
 * UI组件:
 * - 顶部搜索栏
 * - 拼车订单卡片列表
 * - 下拉刷新指示器
 * - 加载状态指示器
 * - 错误提示界面
 *
 * 订单卡片信息:
 * - 出发地和目的地
 * - 行程距离和时间
 * - 当前人数和目标人数
 * - 预期价格
 * - 司机信息和评分
 * - 车辆类型和标签
 *
 * 技术实现:
 * - StateFlow响应式数据流
 * - Lazy列表性能优化
 * - 协程异步数据处理
 * - Material Design 3组件
 * - 高德地图集成
 */
package com.example.white_web.home

import PosDetail
import android.content.Context
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.StarWhite
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.white_web.APISERVICCE
import com.example.white_web.R
import com.example.white_web.USERTYPE
import com.example.white_web.map.GDMapMarker
import com.example.white_web.map.calculateDrivingDistanceMeters
import com.example.white_web.map.estimateDrivingDistanceMeters
import com.example.white_web.ui.theme.White_webTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

// 拼车项数据类
data class RideShareItem(
    val id: String,
    val startPoint: String,
    val endPoint: String,
    val distance: String,
    val startTime: String,
    val currentPeople: Int,
    val targetPeople: Int,
    val expectedPrice: String,
    val driverName: String? = null,
    val driverRating: Float? = null,
    val carType: String? = null,
    val tags: List<String> = emptyList()
)

// API响应数据类
data class AllOrdersResponse(
    val code: Int, val message: String, val data: ApiData
)

data class ApiData(
    val list: List<OrderItem>
)

data class OrderItem(
    val order_id: Int,
    val user1: String,
    val user2: String?,
    val user3: String?,
    val user4: String?,
    val driver: String?,
    val departure: String,
    val destination: String,
    val date: String,
    val earliest_departure_time: String,
    val latest_departure_time: String,
    val remark: String
)

// 辅助函数：格式化日期和时间为合适字符串
fun formatOrderTime(date: String, earliest: String, latest: String): String {
    try {
        val orderDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)
        val weekFields = WeekFields.of(Locale.getDefault())
        val orderWeek = orderDate.get(weekFields.weekOfWeekBasedYear())
        val todayWeek = today.get(weekFields.weekOfWeekBasedYear())

        val timeRange = "${earliest.trim().padStart(8, '0').substring(0, 5)}-${
            latest.trim().padStart(8, '0').substring(0, 5)
        }"

        return when {
            orderDate == today -> "今天$timeRange"
            orderDate == yesterday -> "昨天$timeRange"
            orderDate == tomorrow -> "明天$timeRange"
            orderDate.year == today.year && orderWeek == todayWeek -> {
                val weekDay = when (orderDate.dayOfWeek.value) {
                    1 -> "星期一"
                    2 -> "星期二"
                    3 -> "星期三"
                    4 -> "星期四"
                    5 -> "星期五"
                    6 -> "星期六"
                    7 -> "星期日"
                    else -> ""
                }
                "$weekDay$timeRange"
            }

            orderDate.year == today.year && orderDate.month == today.month -> "${orderDate.dayOfMonth}日$timeRange"
            orderDate.year == today.year -> "${orderDate.monthValue}月${orderDate.dayOfMonth}日$timeRange"
            else -> "${orderDate.year}年${orderDate.monthValue}月${orderDate.dayOfMonth}日$timeRange"
        }
    } catch (e: Exception) {
        // 解析失败时回退为原始格式
        return "$date $earliest-$latest"
    }
}

// 转换函数
fun convertOrdersToRideShareItems(response: AllOrdersResponse): List<RideShareItem> {
    return response.data.list.map { order ->
        // 计算当前人数 (排除空值)
        val currentPeople = listOfNotNull(
            order.user1.takeIf { it.isNotBlank() },
            order.user2?.takeIf { it.isNotBlank() },
            order.user3?.takeIf { it.isNotBlank() },
            order.user4?.takeIf { it.isNotBlank() }).size

        // 格式化日期和时间
        val timeRange =
            formatOrderTime(order.date, order.earliest_departure_time, order.latest_departure_time)

        RideShareItem(
            id = "${order.order_id}",
            startPoint = order.departure,
            endPoint = order.destination,
            distance = "距离计算中...",
            startTime = timeRange,
            currentPeople = currentPeople,
            targetPeople = 4,  // 假设目标是4人拼车
            expectedPrice = "价格计算中...",
            driverName = if (order.driver.isNullOrEmpty()) "司机未接单" else order.driver,  // 司机
            driverRating = if (order.driver.isNullOrEmpty()) 0f else 5.0f,  // 假设默认评分
            carType = if (order.driver.isNullOrEmpty()) "司机无描述" else "舒适型",  // 示例数据
            tags = if (order.driver.isNullOrEmpty()) listOf() else listOf("准时")  // 示例标签
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "DefaultLocale")
@Composable
fun HomePage(
    navController: NavController? = null, viewModel: HomeViewModel = viewModel()
) {
    // ---------- 初始化屏幕参数 ----------
    // 获取屏幕配置信息
    val configuration = LocalConfiguration.current
    // 获取当前像素密度，用于px与dp转换
    val density = LocalDensity.current

    // 获取屏幕高度(dp)，用于计算列表最大高度
    val screenHeightDp = configuration.screenHeightDp.dp

    // ---------- 设置列表高度范围 ----------
    // 列表最小高度：折叠状态下显示的高度
    val minSheetHeightDp = 200.dp
    // 列表最大高度：完全展开时等于屏幕高度
    val maxSheetHeightDp = screenHeightDp

    // ---------- 状态管理 ----------
    // 创建并记住底部列表当前高度状态
    // 初始状态为最小高度（折叠状态）
    var sheetHeightDp by remember { mutableStateOf(minSheetHeightDp) }

    val animatedSheetHeightDp by animateDpAsState(
        targetValue = sheetHeightDp, label = "sheetHeightAnim"
    )

    // 搜索状态
    var isSearchActive by remember { mutableStateOf(false) }/////
    // 当搜索状态改变时清空错误
    LaunchedEffect(isSearchActive) {
        viewModel.clearError()
    }

    // 自动刷新
    val context = LocalContext.current
    // 监听导航目标的变化
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            // 检查是否返回到当前页面
            if (destination.route == "home") {
                // 触发刷新
                viewModel.fetchOrders()
            }
        }
        navController?.addOnDestinationChangedListener(listener)
        onDispose {
            navController?.removeOnDestinationChangedListener(listener)
        }
    }

    // 计算按钮可见性
    // 当列表高度超过屏幕高度的70%时，开始降低透明度
    val threshold = maxSheetHeightDp * 0.7f
    val buttonsAlpha = if (animatedSheetHeightDp > threshold) {
        1f - ((animatedSheetHeightDp - threshold) / (maxSheetHeightDp - threshold)).coerceIn(
            0f,
            0.7f
        )
    } else {
        1f
    }

//    // 创建并记住列表数据
//    val listItems = remember {
//        List(10) { index ->
//            RideShareItem(
//                id = "ride${index + 1}",
//                startPoint = "北京大学${index + 1}号楼",
//                endPoint = "国贸CBD${('A'.code + index % 5).toChar()}区",
//                distance = "${3 + (index * 1.5)}公里",
//                startTime = "今天 ${10 + index % 12}:${(index * 7) % 60}",
//                currentPeople = (index % 3) + 1,
//                targetPeople = 4,
//                expectedPrice = "${15 + index * 2}元",
//                driverName = if (index % 3 == 0) "张师傅" else if (index % 3 == 1) "李师傅" else "王师傅",
//                driverRating = 4.0f + (index % 10) / 10f,
//                carType = if (index % 2 == 0) "舒适型" else "经济型",
//                tags = listOf("准时", "礼貌", "干净").take((index % 3) + 1)
//            )
//        }
//    }

    // 订单数据
    val listItems by viewModel.listItems.collectAsState()
    val posDetails by viewModel.posDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val posLoading by viewModel.posLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchItems by viewModel.searchItems.collectAsState()

    LaunchedEffect(posDetails.size, listItems.size, searchItems.size) {
        if (posDetails.isNotEmpty() && (
                listItems.any { it.distance == "距离计算中..." } ||
                    searchItems.any { it.distance == "距离计算中..." }
                )
        ) {
            viewModel.refreshDistances(context.applicationContext)
        }
    }

    // 创建协程作用域
    val coroutineScope = rememberCoroutineScope()

    if (isSearchActive) {
        SearchScreen(
            navController = navController,
            viewModel = viewModel,
            context = context.applicationContext,
            onClose = {
                isSearchActive = false
                viewModel.clearError() // 关闭搜索时清空错误
            })
    } else {
        // ---------- 主布局结构 ----------
        // 使用Box作为根容器，允许子元素堆叠显示
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF9F5))
        ) {
            // 地图区域（背景层）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF9F5)),
                contentAlignment = Alignment.Center
            ) {

                if (posLoading) {
                    Text(
                        "位置正在加载",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    GDMapMarker(
                        modifier = Modifier.matchParentSize(),
                        markers = posDetails,
                        navController = navController,
                    )
                }
            }

            // ---------- 可拖拽底部列表 ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()  // 宽度占满屏幕
                    .height(animatedSheetHeightDp)  // 动态高度，用动画高度
                    .align(Alignment.BottomCenter)  // 固定在屏幕底部
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(listOf(NeonCyan.copy(0.5f), NeonBlue.copy(0.3f), NeonCyan.copy(0.5f))),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .background(Color(0xEEFFFFFF))
                    .pointerInput(Unit) {  // 添加手势检测
                        // 检测拖拽手势
                        detectDragGestures(
                            // 开始拖动回调（此处未实现具体逻辑）
                            onDragStart = { /* 开始拖动时的操作，如可添加触觉反馈 */ },
                            // 拖动结束时的回调
                            onDragEnd = {
                                // 拖动结束时根据当前高度决定是展开还是折叠
                                coroutineScope.launch {
                                    // 计算阈值：中间位置
                                    val sheetThreshold = (minSheetHeightDp + maxSheetHeightDp) / 2
                                    // 如果高度小于阈值，则折叠；否则展开
                                    sheetHeightDp = if (sheetHeightDp < sheetThreshold) {
                                        minSheetHeightDp  // 折叠到最小高度
                                    } else {
                                        maxSheetHeightDp  // 展开到最大高度
                                    }
                                }
                            },
                            // 拖动取消回调（此处未实现具体逻辑）
                            onDragCancel = { /* 拖动取消时的操作，如恢复原位 */ },
                            // 拖动过程中的回调
                            onDrag = { _, dragAmount ->
                                // dragAmount.y为垂直拖动距离，向上为负，向下为正
                                // 计算新的高度：当前高度减去垂直拖动距离
                                // 注意减号：向上拖动时dragAmount.y为负，所以减去负值相当于增加高度
                                val newHeight = sheetHeightDp - dragAmount.y.toDp(density)
                                // 限制新高度在最小和最大范围内
                                // coerceIn：确保值在指定范围内
                                sheetHeightDp =
                                    newHeight.coerceIn(minSheetHeightDp, maxSheetHeightDp)
                            })
                    }) {
                // ---------- 列表内部布局 ----------
                Column(
                    modifier = Modifier.fillMaxSize()  // 列布局占满底部列表容器
                ) {
                    // ---------- 顶部拖动指示器 ----------
                    // 可点击的拖动指示器，点击时可折叠列表
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()  // 宽度占满
                            .padding(vertical = 8.dp)  // 上下间距
                            .clickable {
                                // 点击顶部指示器的处理逻辑
                                // 只有当列表展开时（高度大于最小值）才触发折叠
                                if (sheetHeightDp > minSheetHeightDp + 10.dp) {  // 添加缓冲区避免误触
                                    coroutineScope.launch {
                                        // 将列表高度设置回最小值（折叠状态）
                                        sheetHeightDp = minSheetHeightDp
                                    }
                                }
                            }, contentAlignment = Alignment.Center  // 内容居中
                    ) {
                        // 拖动指示器的视觉元素：小横条
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(3.dp)
                                .background(
                                    brush = Brush.horizontalGradient(listOf(NeonBlue, NeonCyan, NeonBlue)),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }

                    // ---------- 标题和提示区域 ----------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,  // 垂直居中对齐
                        horizontalArrangement = Arrangement.SpaceBetween  // 两端对齐
                    ) {
                        // 左侧标题文本
                        Text(
                            text = "| 附近拼车路线",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = NeonCyan
                        )
                        // 右侧上拉提示图标
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "上拉查看更多",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.clickable { viewModel.fetchOrders() })
                        }
                    }

                    // 错误提示
                    if (error != null) {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    // ---------- 列表内容区域 ----------
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        items(listItems) { item ->
                            TripCard(navController, item)
                        }
                    }
                }
            }

            // 搜索图标
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .alpha(buttonsAlpha)
            ) {
                FloatingActionButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    containerColor = Color(0xCCFFFFFF),
                    contentColor = NeonCyan,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(26.dp), tint = NeonCyan)
                }
            }
            // 聊天列表 - 在搜索图标的左侧
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 80.dp, top = 18.dp)
                    .alpha(buttonsAlpha)
            ) {
                FloatingActionButton(
                    onClick = { navController?.navigate("chatList") },
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    containerColor = Color(0xCCFFFFFF),
                    contentColor = Color(0xFF1296DB),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chat_bubble),
                        contentDescription = "聊天列表",
                        modifier = Modifier.size(26.dp),
                        tint = Color(0xFF1296DB)
                    )
                }
            }
            // 正在进行的订单 - 在聊天图标的左侧
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 142.dp, top = 18.dp)
                    .alpha(buttonsAlpha)
            ) {
                FloatingActionButton(
                    onClick = { navController?.navigate("currentOrders") },
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    containerColor = Color(0xCCFFFFFF),
                    contentColor = NeonGreen,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "正在进行的订单", modifier = Modifier.size(26.dp), tint = NeonGreen)
                }
            }
            // 刷新按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 204.dp, top = 18.dp)
                    .alpha(buttonsAlpha)
            ) {
                FloatingActionButton(
                    onClick = {
                        viewModel.fetchOrders()
                        Toast.makeText(context, "刷新成功", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    containerColor = Color(0xCCFFFFFF),
                    contentColor = NeonBlue,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(26.dp), tint = NeonBlue)
                }
            }

            // 用户页面按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .alpha(buttonsAlpha)
            ) {
                FloatingActionButton(
                    onClick = { navController?.navigate("myInfo") },
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    containerColor = Color(0xCCFFFFFF),
                    contentColor = NeonPurple,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = "个人中心", modifier = Modifier.size(26.dp), tint = NeonPurple)
                }
            }

            if (USERTYPE == 1) {
                // 新建拼车按钮 - 底部中央
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .alpha(buttonsAlpha)
                ) {
                    FloatingActionButton(
                        onClick = { navController?.navigate("createTrip") },
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, Brush.sweepGradient(listOf(NeonCyan, NeonBlue, NeonCyan)), CircleShape),
                        shape = CircleShape,
                        containerColor = Color(0xCCFFFFFF),
                        contentColor = NeonCyan,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新建拼车", modifier = Modifier.size(32.dp), tint = NeonCyan)
                    }
                }
            }
        }
    }
}

// ---------- 辅助函数 ----------
// 将float类型的像素值转换为dp值的扩展函数
// 用于手势拖动距离的单位转换
fun Float.toDp(density: androidx.compose.ui.unit.Density) = with(density) { this@toDp.toDp() }

@Composable
fun SearchScreen(
    navController: NavController? = null,
    viewModel: HomeViewModel,
    context: Context? = null,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryKeywords by remember { mutableStateOf("") }
    LocalDensity.current

    // 自动聚焦搜索框
    LaunchedEffect(Unit) {
        delay(100) // 短暂延迟以确保界面已完全加载
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF9F5))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 顶部搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回主页按钮
            IconButton(
                onClick = onClose, modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.surfaceTint
                )
            }
            // 输入框
            TextField(
                modifier = Modifier
                    .weight(10f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color(0xFFFFF5EB))
                    .border(1.dp, GlowCyan30, RoundedCornerShape(percent = 50))
                    .focusRequester(focusRequester),
                value = queryKeywords,
                onValueChange = { queryKeywords = it },
                placeholder = { Text("搜索出发地/目的地") },
                singleLine = true,// 单行输入
                // 输入框颜色
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                // 软键盘设置
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.searchOrders(queryKeywords, context)
                        keyboardController?.hide()
                    }),
                // 输入文字后的删除图标
                trailingIcon = {
                    Row(
                        modifier = Modifier.padding(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = queryKeywords.isNotEmpty(),
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            // 删除图标
                            IconButton(
                                onClick = { queryKeywords = "" }, modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.surfaceTint
                                )
                            }
                        }
                        // 搜索图标
                        IconButton(
                            onClick = {
                                viewModel.searchOrders(queryKeywords, context)
                                keyboardController?.hide()
                            }) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(percent = 50)
                                    ), contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索",
                                    tint = MaterialTheme.colorScheme.surfaceTint
                                )
                            }
                        }
                    }
                },
            )
        }

        // 搜索结果
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val error by viewModel.error.collectAsState()
            val searchItems by viewModel.searchItems.collectAsState()

            when {
                // 正在搜索
                viewModel.isSearching.collectAsState().value -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                // 搜索出错
                error != null -> {
                    Text(
                        text = "搜索出错：${error}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // 无搜索结果
                searchItems.isEmpty() -> {
                    Text(
                        text = "未找到匹配的拼车路线",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 显示搜索结果
                searchItems.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(searchItems) { item ->
                            TripCard(navController, item)
                        }

                    }
                }
            }
        }
    }
}

@Composable
fun TripCard(navController: NavController? = null, tripInfo: RideShareItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(NeonCyan.copy(0.5f), NeonBlue.copy(0.3f))),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { navController?.navigate("tripDetail/${tripInfo.id}") },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 出发地-目的地
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "出发地",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tripInfo.startPoint,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "到",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(18.dp)
                )
                Text(
                    text = tripInfo.endPoint,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                // 距离
                Text(
                    text = tripInfo.distance,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .background(Color(0x1AE07830), RoundedCornerShape(8.dp))
                        .border(1.dp, NeonPurple.copy(0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 时间、人数、价格
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "出发时间",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tripInfo.startTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "人数",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${tripInfo.currentPeople}/${tripInfo.targetPeople}人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "价格",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tripInfo.expectedPrice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 司机信息与标签
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 司机头像（用首字母圆形背景代替）
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x33E07830))
                        .border(1.dp, NeonCyan.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tripInfo.driverName?.firstOrNull()?.toString() ?: "司",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tripInfo.driverName ?: "司机",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (tripInfo.driverRating != null) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "评分",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = "${tripInfo.driverRating}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = tripInfo.carType ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 标签
                Row {
                    tripInfo.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .background(Color(0x1AE07830), RoundedCornerShape(8.dp))
                                .border(1.dp, NeonBlue.copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- 预览函数 ----------
// 用于在Android Studio设计视图中预览界面
@Preview(showBackground = true)
@Composable
fun HomePagePreview() {
    White_webTheme {
        // 示例NavController对象，实际应用中需传入真实NavController
        val navController = NavController(LocalContext.current)
        HomePage(navController)
    }
}

// 创建一个ViewModel
class HomeViewModel : ViewModel() {
    private val _orders = MutableStateFlow<List<RideShareItem>>(emptyList())
    val listItems = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)// 正在加载状态，为真代表正在加载
    val isLoading = _isLoading.asStateFlow()

    private val _posDetails = MutableStateFlow<List<PosDetail>>(emptyList())
    val posDetails = _posDetails.asStateFlow()

    private val _posLoading = MutableStateFlow(true)// 正在加载状态，为真代表正在加载
    val posLoading = _posLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)// 错误提示信息
    val error = _error.asStateFlow()

    private val _searchOrders = MutableStateFlow<List<RideShareItem>>(emptyList())
    val searchItems = _searchOrders.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val distanceCache = mutableMapOf<String, Float?>()

    init {
        fetchOrders()
        fetchposDetails()
    }

    fun refreshDistances(context: Context) {
        val positions = _posDetails.value
        if (positions.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _orders.value = fillDistanceAndPrice(_orders.value, positions, context)
            if (_searchOrders.value.isNotEmpty()) {
                _searchOrders.value = fillDistanceAndPrice(_searchOrders.value, positions, context)
            }
        }
    }

    private suspend fun fillDistanceAndPrice(
        items: List<RideShareItem>,
        positions: List<PosDetail>,
        context: Context
    ): List<RideShareItem> {
        val posMap = positions.associateBy { it.name }
        return items.map { item ->
            val startPoint = posMap[item.startPoint]
            val endPoint = posMap[item.endPoint]
            val cacheKey = "${item.startPoint}->${item.endPoint}"
            val distanceInMeters = if (startPoint != null && endPoint != null) {
                if (distanceCache.containsKey(cacheKey)) {
                    distanceCache[cacheKey]
                } else {
                    val calculatedDistance =
                        calculateDrivingDistanceMeters(context.applicationContext, startPoint, endPoint)
                            ?: estimateDrivingDistanceMeters(startPoint, endPoint)
                    distanceCache[cacheKey] = calculatedDistance
                    calculatedDistance
                }
            } else {
                null
            }

            if (distanceInMeters != null) {
                val distanceText = PriceUtils.formatDistanceDisplay(distanceInMeters.toString())
                val priceText = PriceUtils.calculateExpectedPrice(distanceInMeters.toString())
                item.copy(
                    distance = distanceText,
                    expectedPrice = priceText
                )
            } else {
                item.copy(
                    distance = "距离计算失败",
                    expectedPrice = "价格计算失败"
                )
            }
        }
    }

    fun fetchOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 使用新的接口获取未开始的订单
                val response = APISERVICCE.getNotStartedOrders()

                if (response.isSuccessful) {
                    response.body()?.let { apiResponse ->
                        _orders.value = convertOrdersToRideShareItems(apiResponse)
                    } ?: run {
                        _error.value = "返回数据为空"
                    }
                } else {
                    _error.value = "获取订单失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchposDetails() {
        viewModelScope.launch {
            _posLoading.value = true
            _error.value = null

            try {
                // 获取位置列表
                val response = APISERVICCE.getPos()

                if (response.isSuccessful) {
                    response.body()?.let { apiResponse ->
                        _posDetails.value = apiResponse.data!!.table
                    } ?: run {
                        _error.value = "返回数据为空"
                    }
                } else {
                    _error.value = "获取位置列表失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _posLoading.value = false
            }
        }
    }

    fun searchOrders(queryKeywords: String, context: Context? = null) {
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null

            try {
                val response = APISERVICCE.getSearchResults(keyword = queryKeywords)

                if (response.isSuccessful) {
                    response.body()?.let { apiResponse ->
                        val items = convertOrdersToRideShareItems(apiResponse)
                        _searchOrders.value = if (context != null && _posDetails.value.isNotEmpty()) {
                            fillDistanceAndPrice(items, _posDetails.value, context.applicationContext)
                        } else {
                            items
                        }
                    } ?: run {
                        _error.value = "未搜索到相关拼车行程"
                    }
                } else {
                    _error.value = "服务器搜索行程失败：${response.code()}，${response.message()}"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误：${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
