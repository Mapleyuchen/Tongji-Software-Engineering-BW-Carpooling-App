/**
 * 订单详情查看界面
 *
 * 文件功能: 特定订单的详细信息展示和操作界面
 *
 * 主要功能:
 * - 展示订单的完整详细信息
 * - 提供加入/退出订单功能
 * - 支持司机接单和放弃操作
 * - 显示参与者信息和联系方式
 * - 实现订单状态动态更新
 *
 * 展示信息:
 * - 出发地和目的地
 * - 拼车日期和时间范围
 * - 当前参与人数
 * - 司机信息（如有）
 * - 备注信息
 * - 联系方式
 *
 * 用户操作逻辑:
 * 乘客用户（USERTYPE=1）:
 * - 未参与: 显示"加入拼单"按钮
 * - 已参与: 显示"退出拼单"/"移除拼单"按钮
 * - 人数已满: 显示"人数已满"禁用状态
 *
 * 司机用户（USERTYPE=2）:
 * - 无司机: 显示"接受拼单"按钮
 * - 已接单: 显示"放弃接受"按钮
 * - 他人已接: 显示"已有司机"禁用状态
 *
 * UI组件:
 * - 只读表单字段展示
 * - 动态操作按钮
 * - 状态指示器
 * - Toast消息提示
 */

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.white_web.APISERVICCE
import com.example.white_web.R
import com.example.white_web.USERNAME
import com.example.white_web.USERTYPE
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.NeonRed
import com.example.white_web.ui.theme.StarWhite
import com.example.white_web.ui.theme.White_webTheme
import kotlinx.coroutines.launch
import retrofit2.Response

data class DetailResponse(
    val code: Int,
    val message: String,
    val data: DetailData?
)

data class DetailData(
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
    val remark: String,
    // 订单状态：0 未开始 / 1 进行中 / 2 已完成
    val status: Int = 0
)

data class JoinLeaveRequest(
    val order_id: Int
)

data class JoinLeaveRedponse(
    val code: Int,
    val message: String
)

data class UserDetailResponse(
    val code: Int,
    val message: String,
    val data: Data?
) {
    data class Data(
        val phonenumber: String,
        val usertype: String
    )
}

@Composable
fun DetailScreen(orderId: Int, navController: NavHostController) {
    var detailData by remember { mutableStateOf<DetailData?>(null) }
    var phonenumber by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>("载入中···") }

    LaunchedEffect(Unit) {
        try {
            val response = APISERVICCE.detail(orderId)
            if (response.isSuccessful && response.body()?.code == 200) {
                detailData = response.body()?.data

                try {
                    val response2 = APISERVICCE.getUserDetail(detailData!!.user1)
                    if (response2.isSuccessful && response2.body()?.code == 200) {
                        phonenumber = response2.body()?.data?.phonenumber
                    } else {
                        errorMsg = response.body()?.message
                    }
                } catch (e: Exception) {
                    errorMsg = e.message
                }

            } else {
                errorMsg = response.body()?.message
            }

        } catch (e: Exception) {
            errorMsg = e.message
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0x66FFFFFF)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Top bar with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = NeonCyan
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (detailData != null && phonenumber != null) {
                    DisplayDetail(detailData!!, navController, phonenumber!!)
                } else {
                    Text(
                        text = "$errorMsg",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayDetail(detailData: DetailData, navController: NavHostController, phonenumber: String) {

    val userCount = listOfNotNull(
        detailData.user1.takeIf { it.isNotBlank() },
        detailData.user2?.takeIf { it.isNotBlank() },
        detailData.user3?.takeIf { it.isNotBlank() },
        detailData.user4?.takeIf { it.isNotBlank() }).size

    val hasDriver = !detailData.driver.isNullOrBlank()
    val hasOtherParticipants = userCount > 1 || hasDriver
    val isInitiator = detailData.user1 == USERNAME
    // 行程已开始（进行中=1 / 已完成=2）后，任何人都不能退出拼单
    val tripStarted = detailData.status >= 1

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val buttonColor: Color
    val buttonText: String
    val isClickable: Boolean

    if (USERTYPE == 1) {
        if (listOf(detailData.user1, detailData.user2, detailData.user3, detailData.user4).contains(USERNAME)) {
            if (tripStarted) {
                buttonColor = Color(0xFF607D8B)
                buttonText = "行程已开始，无法退出拼单"
                isClickable = false
            } else if (isInitiator && hasOtherParticipants) {
                buttonColor = Color(0xFF607D8B)
                buttonText = "已有参与者，无法移除拼单"
                isClickable = false
            } else if (isInitiator) {
                buttonColor = NeonRed
                buttonText = "移除拼单"
                isClickable = true
            } else {
                buttonColor = NeonRed
                buttonText = "退出拼单"
                isClickable = true
            }
        } else if (userCount == 4) {
            buttonColor = Color(0xFF607D8B)
            buttonText = "人数已满"
            isClickable = false
        } else {
            buttonColor = NeonCyan
            buttonText = "加入拼单"
            isClickable = true
        }
    } else if (USERTYPE == 2) {
        if (detailData.driver.isNullOrEmpty()) {
            buttonColor = NeonGreen
            buttonText = "接受拼单"
            isClickable = true
        } else if (detailData.driver == USERNAME) {
            if (tripStarted) {
                buttonColor = Color(0xFF607D8B)
                buttonText = "行程已开始，无法退出拼单"
                isClickable = false
            } else {
                buttonColor = NeonRed
                buttonText = "放弃接受"
                isClickable = true
            }
        } else {
            buttonColor = Color(0xFF607D8B)
            buttonText = "已有司机"
            isClickable = false
        }
    } else {
        buttonColor = Color(0xFF607D8B)
        buttonText = "参数错误"
        isClickable = false
    }

    val neonFieldColors = TextFieldDefaults.colors(
        focusedContainerColor   = Color(0x1AE07830),
        unfocusedContainerColor = Color(0x0DE07830),
        focusedIndicatorColor   = NeonCyan,
        unfocusedIndicatorColor = GlowCyan30,
        focusedLabelColor       = NeonCyan,
        unfocusedLabelColor     = Color(0xFF8B6A50),
        focusedTextColor        = StarWhite,
        unfocusedTextColor      = StarWhite,
        cursorColor             = NeonCyan
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(NeonCyan.copy(alpha = 0.8f), NeonBlue.copy(alpha = 0.4f))),
                    RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF0FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "| ORDER  DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 3.sp,
                        color = NeonCyan.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
                Text(
                    text = "拼车需求详情",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = detailData.departure,
                    onValueChange = {},
                    label = { Text("出发地") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = detailData.destination,
                    onValueChange = {},
                    label = { Text("目的地") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = detailData.date,
                    onValueChange = {},
                    label = { Text("拼车日期") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Date Picker",
                                tint = NeonCyan
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = detailData.earliest_departure_time,
                    onValueChange = {},
                    label = { Text("最早出发时间") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Time Picker",
                                tint = NeonCyan
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = detailData.latest_departure_time,
                    onValueChange = {},
                    label = { Text("最晚出发时间") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Time Picker",
                                tint = NeonCyan
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = detailData.remark,
                    onValueChange = {},
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions.Default,
                    readOnly = true,
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = phonenumber,
                    onValueChange = {},
                    label = { Text("联系方式") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = userCount.toString(),
                    onValueChange = {},
                    label = { Text("当前人数") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )

                OutlinedTextField(
                    value = if (detailData.driver.isNullOrEmpty()) "无" else detailData.driver,
                    onValueChange = {},
                    label = { Text("司机") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = neonFieldColors
                )
            }
        }

        // Action button
        val btnBrush = if (isClickable)
            Brush.horizontalGradient(listOf(buttonColor, buttonColor.copy(alpha = 0.7f)))
        else
            Brush.horizontalGradient(listOf(Color(0xFFD0B8A0), Color(0xFFE0C8B0)))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(btnBrush)
                .clickable {
                    if (!isClickable) return@clickable
                    scope.launch {
                        try {
                            val response: Response<JoinLeaveRedponse>
                            if (buttonText == "加入拼单" || buttonText == "接受拼单") {
                                response = APISERVICCE.joinOrder(
                                    request = JoinLeaveRequest(detailData.order_id)
                                )
                            } else {
                                response = APISERVICCE.leaveOrder(
                                    request = JoinLeaveRequest(detailData.order_id)
                                )
                            }

                            if (response.isSuccessful && response.body()?.code == 200) {
                                if (buttonText == "加入拼单" || buttonText == "接受拼单") {
                                    Toast.makeText(context, "加入成功", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                    navController.navigate("tripDetail/${detailData.order_id}")
                                } else {
                                    if (USERTYPE == 1) {
                                        if (userCount > 1) {
                                            Toast.makeText(context, "退出成功", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                            navController.navigate("tripDetail/${detailData.order_id}")
                                        } else {
                                            Toast.makeText(context, "订单已删除", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                        }
                                    } else if (USERTYPE == 2) {
                                        Toast.makeText(context, "退出成功", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    response.body()?.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "请求失败，请检查网络连接",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                buttonText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isClickable) DeepSpace else StarWhite
                )
            )
        }
    }
}


@Preview
@Composable
fun PreviewDetailScreen() {
    White_webTheme {
        val navController = rememberNavController()
        DetailScreen(0, navController)
    }
}
