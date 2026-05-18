/**
 * 当前订单管理界面
 *
 * 文件功能: 用户当前参与订单的详情展示和状态管理
 *
 * 主要功能:
 * - 显示用户当前参与的拼车订单详情
 * - 提供订单状态实时更新和管理
 * - 支持到达确认和行程控制
 * - 实现司机评分系统
 * - 集成地图路径展示
 *
 * 订单状态:
 * - 0: 等待开始 - 等待所有人到达出发点
 * - 1: 进行中 - 行程正在进行
 * - 2: 已完成 - 行程结束，可进行评分
 *
 * 用户角色功能:
 * 乘客功能:
 * - 查看订单详情和地图路径
 * - 确认到达出发点
 * - 查看其他参与者状态
 * - 对司机进行评分
 *
 * 司机功能:
 * - 订单管理和状态控制
 * - 确认到达和开始行程
 * - 查看乘客到达状态
 * - 路径导航支持
 *
 * 数据结构:
 * - CurrentOrderData: 订单详情数据
 * - OrderDetail: 订单基本信息
 * - OrderStatusDetail: 订单状态信息
 * - DriverRatingRequest: 司机评分请求
 *
 * UI组件:
 * - 地图路径展示
 * - 订单详情卡片
 * - 参与者状态列表
 * - 操作按钮组
 * - 评分对话框
 * - 状态指示器
 *
 * 技术实现:
 * - 实时状态同步
 * - 地图集成和路径计算
 * - 响应式UI更新
 * - 角色权限控制
 * - 网络请求错误处理
 */
package com.example.white_web.home

// 导入Compose和相关依赖
import PosDetail
import android.annotation.SuppressLint
import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.NeonRed
import com.example.white_web.ui.theme.StarWhite
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.white_web.APISERVICCE
import com.example.white_web.CheckUserRatingRequest
import com.example.white_web.PaymentRequest
import com.example.white_web.PaymentStatusRequest
import com.example.white_web.USERNAME
import com.example.white_web.map.GDMapPath
import com.example.white_web.ui.theme.White_webTheme
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// -------------------- 数据模型 --------------------

// 当前订单API响应
data class CurrentOrderResponse(
    val code: Int,
    val message: String,
    val data: CurrentOrderData?
)

// 当前订单数据
data class CurrentOrderData(
    val order: OrderDetail,
    val status: OrderStatusDetail,
    val start: PosDetail,
    val end: PosDetail,
)

// 订单详细信息
data class OrderDetail(
    @SerializedName("order_id")
    val orderId: Int,
    val user1: String,
    val user2: String?,
    val user3: String?,
    val user4: String?,
    val driver: String?,  // 添加driver字段
    val departure: String,
    val destination: String,
    val date: String,
    @SerializedName("earliest_departure_time")
    val earliestDepartureTime: String,
    @SerializedName("latest_departure_time")
    val latestDepartureTime: String,
    val remark: String,
    val distance: String? = null
)

// 订单状态信息
data class OrderStatusDetail(
    val status: Int,  // 0: 未开始, 1: 进行中, 2: 已完成
    @SerializedName("user1_arrived")
    val user1Arrived: Boolean,
    @SerializedName("user2_arrived")
    val user2Arrived: Boolean,
    @SerializedName("user3_arrived")
    val user3Arrived: Boolean,
    @SerializedName("user4_arrived")
    val user4Arrived: Boolean,
    @SerializedName("driver_arrived")
    val driverArrived: Boolean
)

// 到达确认请求体
data class OrderIdRequest(
    @SerializedName("order_id")
    val orderId: Int
)

// 到达确认响应
data class ConfirmArrivalResponse(
    val code: Int,
    val message: String,
    val data: ArrivalStatusData?
)

// 到达状态数据
data class ArrivalStatusData(
    val status: Int,
    @SerializedName("user1_arrived")
    val user1Arrived: Boolean,
    @SerializedName("user2_arrived")
    val user2Arrived: Boolean,
    @SerializedName("user3_arrived")
    val user3Arrived: Boolean,
    @SerializedName("user4_arrived")
    val user4Arrived: Boolean,
    @SerializedName("driver_arrived")
    val driverArrived: Boolean,
    @SerializedName("all_arrived")
    val allArrived: Boolean
)

// 司机评分请求体
data class DriverRatingRequest(
    @SerializedName("order_id")
    val orderId: Int,
    val rating: Float
)

// 通用响应
data class BaseResponse(
    val code: Int,
    val message: String
)

// -------------------- ViewModel --------------------

/**
 * 当前订单页面的ViewModel，负责数据获取、状态管理和业务逻辑
 */
class CurrentOrdersViewModel : ViewModel() {
    // 当前订单数据
    private val _currentOrder = MutableStateFlow<CurrentOrderData?>(null)
    val currentOrder = _currentOrder.asStateFlow()

    // 是否正在加载
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // 是否显示评分对话框
    private val _showRatingDialog = MutableStateFlow(false)
    val showRatingDialog = _showRatingDialog.asStateFlow()

    // 评分值
    private val _ratingValue = MutableStateFlow(5.0f)
    val ratingValue = _ratingValue.asStateFlow()

    // 司机评分
    private val _driverRating = MutableStateFlow(5.0f)
    val driverRating = _driverRating.asStateFlow()

    // 司机评分次数
    private val _driverRatingCount = MutableStateFlow(0)
    val driverRatingCount = _driverRatingCount.asStateFlow()

    // 添加到ViewModel中
    private val _hasRated = MutableStateFlow(false)
    val hasRated = _hasRated.asStateFlow()

    // ---------------- 支付相关状态 ----------------

    // 当前用户对当前订单是否已支付
    private val _hasPaid = MutableStateFlow(false)
    val hasPaid = _hasPaid.asStateFlow()

    // 已支付金额（仅在 hasPaid 为 true 时有意义）
    private val _paymentAmount = MutableStateFlow<Double?>(null)
    val paymentAmount = _paymentAmount.asStateFlow()

    // 支付请求是否正在进行中
    private val _paying = MutableStateFlow(false)
    val paying = _paying.asStateFlow()

    // 初始化时获取当前订单
    init {
        fetchCurrentOrder()
    }

    // 获取当前订单
    fun fetchCurrentOrder() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = APISERVICCE.getCurrentOrder()
                if (response.isSuccessful && response.body()?.code == 200) {
                    _currentOrder.value = response.body()?.data
                } else if (response.body()?.code == 404 && response.body()?.message == "未找到相关订单") {
                    // 未找到订单不作为错误处理，只设置currentOrder为null
                    _currentOrder.value = null
                } else {
                    _error.value = response.body()?.message ?: "获取当前订单失败"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 确认到达
    fun confirmArrival(orderId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = APISERVICCE.confirmArrival(request = OrderIdRequest(orderId))
                if (response.isSuccessful && response.body()?.code == 200) {
                    // 更新本地状态
                    _currentOrder.value?.let { currentOrderData ->
                        val updatedStatus = response.body()?.data
                        if (updatedStatus != null) {
                            _currentOrder.value = currentOrderData.copy(
                                status = OrderStatusDetail(
                                    status = updatedStatus.status,
                                    user1Arrived = updatedStatus.user1Arrived,
                                    user2Arrived = updatedStatus.user2Arrived,
                                    user3Arrived = updatedStatus.user3Arrived,
                                    user4Arrived = updatedStatus.user4Arrived,
                                    driverArrived = updatedStatus.driverArrived
                                )
                            )
                        }
                    }
                } else {
                    _error.value = response.body()?.message ?: "确认到达失败"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 确认到达目的地
    fun confirmDestination(orderId: Int, isDriver: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 如果是司机，需要发送请求更新订单状态
                if (isDriver) {
                    val response = APISERVICCE.confirmDestination(request = OrderIdRequest(orderId))
                    if (response.isSuccessful && response.body()?.code == 200) {
                        // 司机确认后刷新订单状态
                        fetchCurrentOrder()
                    } else {
                        _error.value = response.body()?.message ?: "确认到达目的地失败"
                    }
                } else {
                    // 对于普通用户，直接显示评分对话框
                    _showRatingDialog.value = true
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 更新评分值
    fun updateRating(rating: Float) {
        _ratingValue.value = rating
    }

    // 提交司机评分
    fun submitDriverRating(orderId: Int, driverUsername: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = APISERVICCE.rateDriver(
                    request = DriverRatingRequest(
                        orderId = orderId,
                        rating = _ratingValue.value
                    )
                )
                if (response.isSuccessful && response.body()?.code == 200) {
                    _showRatingDialog.value = false
                    _hasRated.value = true  // 评分成功后设置为已评分

                    // 评分成功后重新获取司机评分
                    fetchDriverRating(driverUsername)

                    // 刷新订单状态
                    fetchCurrentOrder()
                } else {
                    _error.value = response.body()?.message ?: "评分提交失败"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 获取司机评分
    fun fetchDriverRating(username: String) {
        viewModelScope.launch {
            try {
                val response = APISERVICCE.getDriverRating(username)
                if (response.isSuccessful && response.body()?.code == 200) {
                    _driverRating.value = response.body()?.data?.rating ?: 5.0f
                    _driverRatingCount.value = response.body()?.data?.rating_count ?: 0
                }
            } catch (e: Exception) {
                // 发生错误时使用默认值
                _driverRating.value = 5.0f
                _driverRatingCount.value = 0
            }
        }
    }

    // 检查用户是否已评分
    fun checkIfUserHasRated(orderId: Int, driverUsername: String) {
        viewModelScope.launch {
            try {
                val response = APISERVICCE.checkUserRating(
                    request = CheckUserRatingRequest(
                        orderId = orderId,
                        driverUsername = driverUsername
                    )
                )
                if (response.isSuccessful && response.body()?.code == 200) {
                    _hasRated.value = response.body()?.data?.hasRated ?: false
                } else {
                    _hasRated.value = false
                }
            } catch (e: Exception) {
                // 网络请求失败时默认为未评分
                _hasRated.value = false
            }
        }
    }

    // 关闭评分对话框
    fun dismissRatingDialog() {
        _showRatingDialog.value = false
    }

    // 清除错误
    fun clearError() {
        _error.value = null
    }

    // 开始行程
    fun startTrip(orderId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = APISERVICCE.startTrip(request = OrderIdRequest(orderId))
                if (response.isSuccessful && response.body()?.code == 200) {
                    // 开始行程成功后刷新订单状态
                    fetchCurrentOrder()
                } else {
                    _error.value = response.body()?.message ?: "开始行程失败"
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ---------------- 模拟支付 ----------------

    /**
     * 查询当前用户对该订单的支付状态。
     * 失败时静默回退到 hasPaid=false，避免阻断 UI。
     */
    fun fetchPaymentStatus(orderId: Int) {
        viewModelScope.launch {
            try {
                val response = APISERVICCE.getPaymentStatus(
                    request = PaymentStatusRequest(orderId = orderId)
                )
                if (response.isSuccessful && response.body()?.code == 200) {
                    val data = response.body()?.data
                    _hasPaid.value = data?.hasPaid ?: false
                    _paymentAmount.value = data?.amount
                } else {
                    _hasPaid.value = false
                }
            } catch (e: Exception) {
                _hasPaid.value = false
            }
        }
    }

    /**
     * 发起模拟支付。支付成功后将 hasPaid 置为 true，并保留实际成交金额。
     * 失败时把 message 写入 _error，UI 会通过错误分支提示。
     */
    fun payOrder(orderId: Int, amount: Double) {
        viewModelScope.launch {
            _paying.value = true
            _error.value = null
            try {
                val response = APISERVICCE.payOrder(
                    request = PaymentRequest(orderId = orderId, amount = amount)
                )
                if (response.isSuccessful && response.body()?.code == 200) {
                    _hasPaid.value = true
                    _paymentAmount.value = response.body()?.data?.amount ?: amount
                } else {
                    _error.value = response.body()?.message ?: "支付失败"
                    // 失败后再次同步一次后端真实状态，避免本地状态错位
                    fetchPaymentStatus(orderId)
                }
            } catch (e: Exception) {
                _error.value = "网络请求错误: ${e.message}"
            } finally {
                _paying.value = false
            }
        }
    }
}

// -------------------- Composable UI --------------------

/**
 * 当前订单主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentOrdersScreen(navController: NavHostController, viewModel: CurrentOrdersViewModel) {
    val currentOrder by viewModel.currentOrder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showRatingDialog by viewModel.showRatingDialog.collectAsState()
    val ratingValue by viewModel.ratingValue.collectAsState()

    // 获取当前用户名
    val currentUsername = USERNAME ?: "未登录"

    // 页面加载时清除错误
    LaunchedEffect(Unit) {
        viewModel.clearError()
    }

    // Scaffold布局
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("当前订单") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchCurrentOrder() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFF9F5),
                    titleContentColor = NeonCyan,
                    navigationIconContentColor = NeonCyan,
                    actionIconContentColor = NeonCyan
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 加载中
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // 错误提示
            else if (error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "未知错误",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.fetchCurrentOrder() }
                    ) {
                        Text("重试")
                    }
                }
            }
            // 没有订单
            else if (currentOrder == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "您当前没有即将进行的拼车订单",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { navController.navigate("createTrip") }
                    ) {
                        Text("发布新拼车")
                    }
                }
            }
            // 有订单，显示详情
            else {
                OrderDetailContent(
                    currentOrder = currentOrder!!,
                    currentUsername = currentUsername,
                    viewModel = viewModel
                )
            }

            // 评分对话框
            if (showRatingDialog && currentOrder != null) {
                RatingDialog(
                    rating = ratingValue,
                    onRatingChange = { viewModel.updateRating(it) },
                    onDismiss = { viewModel.dismissRatingDialog() },
                    onSubmit = {
                        viewModel.submitDriverRating(
                            currentOrder!!.order.orderId,
                            currentOrder!!.order.driver ?: ""
                        )
                    }
                )
            }
        }
    }
}

/**
 * 订单详情内容
 */
@Composable
fun OrderDetailContent(
    currentOrder: CurrentOrderData,
    currentUsername: String,
    viewModel: CurrentOrdersViewModel
) {
    // 解析日期和时间
    val orderDate = try {
        LocalDate.parse(currentOrder.order.date)
    } catch (e: Exception) {
        LocalDate.now()
    }

    val earliestTime = try {
        LocalTime.parse(currentOrder.order.earliestDepartureTime)
    } catch (e: Exception) {
        LocalTime.of(0, 0)
    }

    val latestTime = try {
        LocalTime.parse(currentOrder.order.latestDepartureTime)
    } catch (e: Exception) {
        LocalTime.of(0, 0)
    }

    // 格式化日期和时间
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val formattedDate = orderDate.format(dateFormatter)
    val formattedTimeRange =
        "${earliestTime.format(timeFormatter)}-${latestTime.format(timeFormatter)}"


    // 地图函数计算的距离
    var distance by remember { mutableStateOf<Float>(0f) }

    // 计算距离和价格 - 使用工具类
    val distanceInMeters = distance.toString()
    val formattedDistance = PriceUtils.formatDistanceDisplay(distanceInMeters)
    val calculatedPrice = PriceUtils.calculateExpectedPrice(distanceInMeters)

    // 计算参与人数
    val participants = listOfNotNull(
        currentOrder.order.user1,
        currentOrder.order.user2,
        currentOrder.order.user3,
        currentOrder.order.user4
    )

    // 计算已到达人数
    var arrivedCount = 0
    if (currentOrder.status.user1Arrived) arrivedCount++
    if (currentOrder.status.user2Arrived) arrivedCount++
    if (currentOrder.status.user3Arrived) arrivedCount++
    if (currentOrder.status.user4Arrived) arrivedCount++

    // 判断所有乘客是否已到达（不包括司机）
    val nonDriverParticipants = participants.filter { it != currentOrder.order.driver }
    val nonDriverArrivedCount = when {
        currentOrder.order.user1 != currentOrder.order.driver && currentOrder.status.user1Arrived -> 1
        else -> 0
    } + when {
        currentOrder.order.user2 != null && currentOrder.order.user2 != currentOrder.order.driver && currentOrder.status.user2Arrived -> 1
        else -> 0
    } + when {
        currentOrder.order.user3 != null && currentOrder.order.user3 != currentOrder.order.driver && currentOrder.status.user3Arrived -> 1
        else -> 0
    } + when {
        currentOrder.order.user4 != null && currentOrder.order.user4 != currentOrder.order.driver && currentOrder.status.user4Arrived -> 1
        else -> 0
    }

    val allPassengersArrived =
        nonDriverParticipants.isNotEmpty() && nonDriverArrivedCount == nonDriverParticipants.size

    // 添加hasRated状态
    val hasRated by viewModel.hasRated.collectAsState()

    // 模拟支付相关状态
    val hasPaid by viewModel.hasPaid.collectAsState()
    val paying by viewModel.paying.collectAsState()

    // 判断当前用户是否为司机
    val isDriver = currentOrder.order.driver == currentUsername

    // 进入已完成订单页面时，乘客需要先查支付状态再判断是否已评分
    LaunchedEffect(currentOrder.order.orderId, currentOrder.status.status) {
        if (currentOrder.status.status == 2 && !isDriver && currentOrder.order.driver != null) {
            viewModel.fetchPaymentStatus(currentOrder.order.orderId)
            viewModel.checkIfUserHasRated(currentOrder.order.orderId, currentOrder.order.driver)
        }
    }

    // 判断当前用户是否已到达
    val isCurrentUserArrived = when (currentUsername) {
        currentOrder.order.user1 -> currentOrder.status.user1Arrived
        currentOrder.order.user2 -> currentOrder.status.user2Arrived
        currentOrder.order.user3 -> currentOrder.status.user3Arrived
        currentOrder.order.user4 -> currentOrder.status.user4Arrived
        currentOrder.order.driver -> currentOrder.status.driverArrived  // 如果用户只是司机
        else -> false
    }

    // 判断司机是否已到达
    val isDriverArrived = currentOrder.status.driverArrived

    // 获取司机评分
    LaunchedEffect(currentOrder.order.driver) {
        if (currentOrder.order.driver != null) {
            viewModel.fetchDriverRating(currentOrder.order.driver)
        }
    }

    val driverRating by viewModel.driverRating.collectAsState()
    val driverRatingCount by viewModel.driverRatingCount.collectAsState()

    // 改为使用Box作为根布局容器
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 可滚动的内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                // 添加底部padding以避免内容被按钮遮挡
                .padding(bottom = 120.dp)
        ) {
            // 地图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
//                Column(
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.LocationOn,
//                        contentDescription = "地图",
//                        modifier = Modifier.size(48.dp),
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                    Text(
//                        text = "路线地图",
//                        style = MaterialTheme.typography.titleMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                    Text(
//                        text = "使用高德API根据出发地（${currentOrder.order.departure}） → 目的地（${currentOrder.order.destination}）绘制",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                }
                GDMapPath(
                    modifier = Modifier
                        .matchParentSize(),
                    start = currentOrder.start,
                    end = currentOrder.end,
                    onDistanceCalculated = { d ->
                        distance = d
                    })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 订单状态信息
            OrderStatusCard(
                status = currentOrder.status.status,
                arrivedCount = arrivedCount,
                totalCount = participants.size,
                isDriverArrived = isDriverArrived,
                driverUsername = currentOrder.order.driver,
                driverRating = driverRating,
                driverRatingCount = driverRatingCount,
                allPassengersArrived = allPassengersArrived,
                isDriver = isDriver
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 订单详细信息
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Brush.horizontalGradient(listOf(NeonCyan.copy(0.6f), NeonBlue.copy(0.3f))), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF0FFFFFF))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "| ORDER  DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 3.sp,
                            color = NeonCyan.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    Text(
                        text = "订单详情",
                        style = MaterialTheme.typography.titleMedium.copy(color = NeonCyan),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow("订单ID", "#${currentOrder.order.orderId}")
                    DetailRow("出发地", currentOrder.order.departure)
                    DetailRow("目的地", currentOrder.order.destination)
                    DetailRow("行程距离", formattedDistance)  // 显示实际距离
                    DetailRow("预计费用", calculatedPrice)   // 显示计算的价格
                    DetailRow("日期", formattedDate)
                    DetailRow("预计开始时间", formattedTimeRange)
                    DetailRow("参与人数", "${participants.size}/4")
                    DetailRow("备注", currentOrder.order.remark)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "乘客",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 添加日志，查看实际接收到的用户数据
                    Log.d("CurrentOrdersDebug", "Order ID: ${currentOrder.order.orderId}")
                    Log.d(
                        "CurrentOrdersDebug",
                        "Users data: user1=${currentOrder.order.user1}, user2=${currentOrder.order.user2}, user3=${currentOrder.order.user3}, user4=${currentOrder.order.user4}"
                    )

                    // 改进乘客显示逻辑，确保所有有效用户都显示
                    if (currentOrder.order.user1.isNotBlank()) {
                        ParticipantRow(
                            username = currentOrder.order.user1,
                            isArrived = currentOrder.status.user1Arrived,
                            isDriver = currentOrder.order.driver == currentOrder.order.user1
                        )
                    }

                    if (!currentOrder.order.user2.isNullOrBlank()) {
                        ParticipantRow(
                            username = currentOrder.order.user2,
                            isArrived = currentOrder.status.user2Arrived,
                            isDriver = currentOrder.order.driver == currentOrder.order.user2
                        )
                    }

                    if (!currentOrder.order.user3.isNullOrBlank()) {
                        ParticipantRow(
                            username = currentOrder.order.user3,
                            isArrived = currentOrder.status.user3Arrived,
                            isDriver = currentOrder.order.driver == currentOrder.order.user3
                        )
                    }

                    if (!currentOrder.order.user4.isNullOrBlank()) {
                        ParticipantRow(
                            username = currentOrder.order.user4,
                            isArrived = currentOrder.status.user4Arrived,
                            isDriver = currentOrder.order.driver == currentOrder.order.user4
                        )
                    }

                    // 如果司机不在user1-user4中，单独添加司机行
                    val driverUsername = currentOrder.order.driver
                    if (driverUsername != null &&
                        driverUsername != currentOrder.order.user1 &&
                        driverUsername != currentOrder.order.user2 &&
                        driverUsername != currentOrder.order.user3 &&
                        driverUsername != currentOrder.order.user4
                    ) {

                        ParticipantRow(
                            username = driverUsername,
                            isArrived = currentOrder.status.driverArrived,
                            isDriver = true,
                            rating = driverRating,
                            ratingCount = driverRatingCount
                        )
                    }
                }
            }
        }

        // 底部固定的按钮区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xEEFFFFFF))
                .padding(16.dp)
        ) {
            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), color = GlowCyan30)

            // 从 calculatedPrice (形如 "13元") 解析出整数金额，传给支付按钮
            val paymentAmountInt = calculatedPrice
                .replace("元", "")
                .trim()
                .toIntOrNull() ?: 0

            // 按钮区域
            ActionButtons(
                orderStatus = currentOrder.status.status,
                isCurrentUserArrived = isCurrentUserArrived,
                isDriver = isDriver,
                hasRated = hasRated,
                onConfirmArrival = { viewModel.confirmArrival(currentOrder.order.orderId) },
                onConfirmDestination = {
                    viewModel.confirmDestination(
                        currentOrder.order.orderId,
                        isDriver
                    )
                },
                onStartTrip = { viewModel.startTrip(currentOrder.order.orderId) },
                allPassengersArrived = allPassengersArrived,
                hasPaid = hasPaid,
                paying = paying,
                paymentAmount = paymentAmountInt,
                onPay = {
                    viewModel.payOrder(
                        currentOrder.order.orderId,
                        paymentAmountInt.toDouble()
                    )
                }
            )
        }
    }
}

/**
 * 订单状态卡片
 */
@SuppressLint("DefaultLocale")
@Composable
fun OrderStatusCard(
    status: Int,
    arrivedCount: Int,
    totalCount: Int,
    isDriverArrived: Boolean,
    driverUsername: String? = null,
    driverRating: Float? = null,
    driverRatingCount: Int = 0,
    // 添加新参数
    allPassengersArrived: Boolean = false,
    isDriver: Boolean = false
) {
    val statusText = when (status) {
        0 -> "等待开始"
        1 -> "进行中"
        2 -> "已完成"
        else -> "未知状态"
    }

    val statusDescription = when (status) {
        0 -> "等待所有参与者到达集合点"
        1 -> "司机和乘客已出发，正在前往目的地"
        2 -> "已到达目的地，行程结束"
        else -> ""
    }

    val statusColor = when (status) {
        0 -> NeonPurple
        1 -> NeonCyan
        2 -> NeonGreen
        else -> NeonRed
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, statusColor.copy(0.6f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 状态标题和计数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (status == 0) "已到达 $arrivedCount/$totalCount" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 状态描述
            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 为司机添加一个指示，显示乘客到达情况
            if (status == 0 && isDriver) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (allPassengersArrived)
                        "所有乘客均已到达，可以开始行程"
                    else
                        "等待所有乘客到达后才能开始行程",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allPassengersArrived)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }

            // 司机信息区域
            if (driverUsername != null) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                // 司机用户名和到达状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 司机名称
                    Text(
                        text = "司机：$driverUsername",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // 司机到达状态
                    Text(
                        text = if (isDriverArrived) "已到达" else "未到达",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDriverArrived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 司机评分信息
                if (driverRating != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "评分",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFFFD700)
                        )

                        Text(
                            text = String.format("%.1f", driverRating) +
                                    if (driverRatingCount > 0) " (${driverRatingCount}条评价)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            } else if (status == 0) {
                // 仅显示司机到达状态（当没有司机信息时）
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "司机状态：",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = if (isDriverArrived) "已到达" else "未到达",
                        color = if (isDriverArrived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 订单详情行
 */
@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 参与者行
 */
@SuppressLint("DefaultLocale")
@Composable
fun ParticipantRow(
    username: String,
    isArrived: Boolean,
    isDriver: Boolean = false,
    rating: Float? = null,
    ratingCount: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 用户信息列
        Column(modifier = Modifier.weight(1f)) {
            // 用户名
            Text(
                text = username + if (isDriver) " (司机)" else "",
                style = MaterialTheme.typography.bodyMedium
            )

            // 仅为司机显示评分
            if (isDriver && rating != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // 星星图标
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "评分",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFD700)
                    )

                    // 评分文本
                    Text(
                        text = String.format(
                            "%.1f",
                            rating
                        ) + if (ratingCount > 0) " (${ratingCount}条评价)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }

        // 到达状态
        val statusColor =
            if (isArrived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        val statusText = if (isArrived) "已到达" else "未到达"

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 操作按钮区域
 */
@Composable
fun ActionButtons(
    orderStatus: Int,
    isCurrentUserArrived: Boolean,
    isDriver: Boolean,
    hasRated: Boolean,
    onConfirmArrival: () -> Unit,
    onConfirmDestination: () -> Unit,
    onStartTrip: () -> Unit = {},
    allPassengersArrived: Boolean = false,
    // 模拟支付相关参数（默认值保证旧调用方/旧 Preview 不报错）
    hasPaid: Boolean = false,
    paying: Boolean = false,
    paymentAmount: Int = 0,
    onPay: () -> Unit = {}
) {
    when (orderStatus) {
        0 -> {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDriver) {
                    if (isCurrentUserArrived) {
                        // 司机已确认到达，显示"开始行程"按钮
                        Button(
                            onClick = onStartTrip,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = allPassengersArrived,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (allPassengersArrived) NeonGreen else Color(0xFFD0B8A0),
                                disabledContainerColor = Color(0xFFECE0D6)
                            )
                        ) {
                            Text(
                                text = "开始行程",
                                modifier = Modifier.padding(8.dp),
                                color = if (allPassengersArrived) DeepSpace else Color(0xFF8B6A50)
                            )
                        }

                        // 提示信息
                        if (!allPassengersArrived) {
                            Text(
                                text = "等待所有乘客确认到达后可以开始行程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                            )
                        }
                    } else {
                        // 司机未确认到达，显示"确认到达"按钮
                        Button(
                            onClick = onConfirmArrival,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text(text = "确认到达", modifier = Modifier.padding(8.dp), color = DeepSpace)
                        }
                    }
                } else {
                    // 乘客的确认到达按钮
                    Button(
                        onClick = onConfirmArrival,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        enabled = !isCurrentUserArrived,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCurrentUserArrived) NeonCyan else Color(0xFFD0B8A0),
                            disabledContainerColor = Color(0xFFECE0D6)
                        )
                    ) {
                        Text(
                            text = if (isCurrentUserArrived) "已确认到达" else "确认到达",
                            modifier = Modifier.padding(8.dp),
                            color = if (!isCurrentUserArrived) DeepSpace else Color(0xFF8B6A50)
                        )
                    }
                }
            }
        }

        1 -> {
            // 进行中状态：只有司机可以确认到达目的地
            if (isDriver) {
                Button(
                    onClick = onConfirmDestination,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text(text = "确认到达目的地（司机确认）", modifier = Modifier.padding(8.dp), color = StarWhite)
                }
            } else {
                // 非司机用户显示提示性信息
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "行程进行中，等待司机确认到达目的地",
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        2 -> {
            when {
                // 司机视角：行程已结束，无需支付/评分
                isDriver -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFFECE0D6)
                        )
                    ) {
                        Text("订单已完成", color = NeonGreen)
                    }
                }
                // 乘客视角：未支付 → 显示支付按钮
                !hasPaid -> {
                    Button(
                        onClick = onPay,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !paying && paymentAmount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            disabledContainerColor = Color(0xFFECE0D6)
                        )
                    ) {
                        Text(
                            text = when {
                                paying -> "支付中..."
                                paymentAmount <= 0 -> "金额计算中..."
                                else -> "待支付 ¥${paymentAmount}，点击支付"
                            },
                            modifier = Modifier.padding(8.dp),
                            color = if (paying || paymentAmount <= 0) Color(0xFF8B6A50) else StarWhite
                        )
                    }
                }
                // 乘客视角：已支付但未评分 → 显示评分入口
                !hasRated -> {
                    Button(
                        onClick = onConfirmDestination,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                    ) {
                        Text(
                            text = "已支付，点击评价司机",
                            modifier = Modifier.padding(8.dp),
                            color = StarWhite
                        )
                    }
                }
                // 乘客视角：已支付且已评分 → 终态
                else -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFFECE0D6)
                        )
                    ) {
                        Text("您已完成支付和评分", color = NeonGreen)
                    }
                }
            }
        }
    }
}

/**
 * 评分对话框
 */
@Composable
fun RatingDialog(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "给司机评分",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "请为本次拼车体验评分",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 星级评分
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { onRatingChange(i.toFloat()) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "星级 $i",
                                modifier = Modifier.size(32.dp),
                                tint = if (i <= rating) Color(0xFFFFD700) else Color.Gray
                            )
                        }
                    }
                }

                Text(
                    text = when (rating) {
                        1f -> "很差"
                        2f -> "较差"
                        3f -> "一般"
                        4f -> "不错"
                        5f -> "很好"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    Button(onClick = onSubmit) {
                        Text("提交评分")
                    }
                }
            }
        }
    }
}

// -------------------- 预览 --------------------

// 主屏幕预览
@Preview(showBackground = true)
@Composable
fun CurrentOrdersScreenPreview() {
    White_webTheme {
        val mockViewModel = CurrentOrdersViewModel()
        // 使用模拟NavController来防止预览时错误
        val mockNavController = NavHostController(LocalContext.current)
        CurrentOrdersScreen(navController = mockNavController, viewModel = mockViewModel)
    }
}

// 订单详情内容预览
@Preview(showBackground = true)
@Composable
fun OrderDetailContentPreview() {
    White_webTheme {
        // 创建模拟订单数据
        val mockOrder = OrderDetail(
            orderId = 123,
            user1 = "driver123",
            user2 = "passenger1",
            user3 = "passenger2",
            user4 = null,
            driver = "driver123",
            departure = "嘉定校区",
            destination = "虹桥火车站",
            date = "2025-05-10",
            earliestDepartureTime = "09:00",
            latestDepartureTime = "10:00",
            remark = "TODO()",
        )

        // 创建模拟订单状态数据
        val mockStatus = OrderStatusDetail(
            status = 0, // 未开始状态
            user1Arrived = true,
            user2Arrived = false,
            user3Arrived = true,
            user4Arrived = false,
            driverArrived = true
        )

        val mockCurrentOrder = CurrentOrderData(
            order = mockOrder,
            status = mockStatus,
            start = PosDetail("嘉定校区", 121.214728, 31.285629),
            end = PosDetail("虹桥火车站", 121.320674, 31.194062)
        )

        OrderDetailContent(
            currentOrder = mockCurrentOrder,
            currentUsername = "passenger1",
            viewModel = CurrentOrdersViewModel()
        )
    }
}

// 订单状态卡片预览
@Preview
@Composable
fun OrderStatusCardPreview() {
    White_webTheme {
        OrderStatusCard(
            status = 0,  // 未开始状态
            arrivedCount = 2,
            totalCount = 4,
            isDriverArrived = true,
            driverUsername = "张师傅",
            driverRating = 4.5f,
            driverRatingCount = 10
        )
    }
}

// 参与者行预览
@Preview
@Composable
fun ParticipantRowPreview() {
    White_webTheme {
        ParticipantRow(
            username = "张师傅",
            isArrived = true,
            isDriver = true,
            rating = 4.5f,
            ratingCount = 10
        )
    }
}

// 评分对话框预览
@Preview
@Composable
fun RatingDialogPreview() {
    White_webTheme {
        RatingDialog(
            rating = 3.5f,
            onRatingChange = {},
            onDismiss = {},
            onSubmit = {}
        )
    }
}

// 操作按钮预览 - 未开始状态
@Preview
@Composable
fun ActionButtonsNotStartedPreview() {
    White_webTheme {
        ActionButtons(
            orderStatus = 0,
            isCurrentUserArrived = true,
            isDriver = true,
            hasRated = false,
            onConfirmArrival = {},
            onConfirmDestination = {},
            onStartTrip = {},
            allPassengersArrived = true
        )
    }
}

@Preview
@Composable
fun ActionButtonsNotStartedPreviewDisabled() {
    White_webTheme {
        ActionButtons(
            orderStatus = 0,
            isCurrentUserArrived = true,
            isDriver = true,
            hasRated = false,
            onConfirmArrival = {},
            onConfirmDestination = {},
            onStartTrip = {},
            allPassengersArrived = false
        )
    }
}

// 操作按钮预览 - 进行中状态
@Preview
@Composable
fun ActionButtonsInProgressPreview() {
    White_webTheme {
        ActionButtons(
            orderStatus = 1,  // 进行中
            isCurrentUserArrived = true,
            isDriver = true,
            hasRated = false,
            onConfirmArrival = {},
            onConfirmDestination = {}
        )
    }
}

// 操作按钮预览 - 已完成 + 司机视角
@Preview
@Composable
fun ActionButtonsCompletedDriverPreview() {
    White_webTheme {
        ActionButtons(
            orderStatus = 2,
            isCurrentUserArrived = true,
            isDriver = true,
            hasRated = false,
            onConfirmArrival = {},
            onConfirmDestination = {}
        )
    }
}

// 操作按钮预览 - 已完成 + 乘客 + 未支付
@Preview
@Composable
fun ActionButtonsCompletedUnpaidPreview() {
    White_webTheme {
        ActionButtons(
            orderStatus = 2,
            isCurrentUserArrived = true,
            isDriver = false,
            hasRated = false,
            onConfirmArrival = {},
            onConfirmDestination = {},
            hasPaid = false,
            paying = false,
            paymentAmount = 13,
            onPay = {}
        )
    }
}

// 操作按钮预览 - 已完成 + 乘客 + 已支付未评分
@Preview
@Composable
fun ActionButtonsCompletedPaidUnratedPreview() {
    White_webTheme {
        ActionButtons(
            orderStatus = 2,
            isCurrentUserArrived = true,
            isDriver = false,
            hasRated = false,
            onConfirmArrival = {},
            onConfirmDestination = {},
            hasPaid = true,
            paying = false,
            paymentAmount = 13,
            onPay = {}
        )
    }
}

// 操作按钮预览 - 已完成 + 乘客 + 已支付且已评分
@Preview
@Composable
fun ActionButtonsCompletedFinishedPreview() {
    White_webTheme {
        ActionButtons(
            orderStatus = 2,
            isCurrentUserArrived = true,
            isDriver = false,
            hasRated = true,
            onConfirmArrival = {},
            onConfirmDestination = {},
            hasPaid = true,
            paying = false,
            paymentAmount = 13,
            onPay = {}
        )
    }
}

// 更新价格信息卡片组件
@Composable
fun PriceInfoCard(
    distance: String,
    calculatedPrice: String,
    participantCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "费用信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "行程距离",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "预计总费用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = calculatedPrice,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (participantCount > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                // 使用工具类计算人均价格
                val pricePerPerson =
                    PriceUtils.calculatePricePerPerson(calculatedPrice, participantCount)

                Text(
                    text = "人均费用：$pricePerPerson",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}