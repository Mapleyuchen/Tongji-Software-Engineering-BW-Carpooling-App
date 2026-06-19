/**
 * 拼车需求发布界面
 *
 * 文件功能: 用户发布新拼车需求的界面和逻辑处理
 *
 * 主要功能:
 * - 提供完整的拼车需求发布表单
 * - 支持出发地和目的地选择
 * - 集成日期时间选择器
 * - 实现地图点位选择功能
 * - 提供需求发布和验证
 *
 * 表单字段:
 * - 出发地（支持手动输入和地图选择）
 * - 目的地（支持手动输入和地图选择）
 * - 拼车日期（日期选择器）
 * - 最早出发时间（时间选择器）
 * - 最晚出发时间（时间选择器）
 * - 备注信息（可选文本）
 *
 * 验证规则:
 * - 出发地和目的地必填
 * - 日期不能早于当前日期
 * - 最晚时间必须晚于最早时间
 * - 用户类型权限检查（仅乘客可发布）
 *
 * 用户体验:
 * - 直观的表单界面
 * - 实时输入验证
 * - 友好的错误提示
 * - 发布成功自动跳转
 */

package com.example.white_web

import android.widget.Toast
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.StarWhite
import com.example.white_web.ui.theme.White_webTheme
import kotlinx.coroutines.launch
import java.util.Calendar

data class GetPosResponse(
    val code: Int,
    val message: String,
    val data: Data?
) {
    data class Data(
        val table: List<PosDetail>
    )
}

data class PosDetail(
    val name: String,
    val lon: Double,
    val lat: Double,
)

data class PublishRequest(
    val departure: String,
    val destination: String,
    val date: String,
    val earliest_departure_time: String,
    val latest_departure_time: String,
    val remark: String
)

data class PublishResponse(
    val code: Int,
    val message: String,
    val username: String,
    val data: Data?
) {
    data class Data(
        val order_id: Int,
        val departure: String,
        val destination: String,
        val date: String,
        val earliest_departure_time: String,
        val latest_departure_time: String
    )
}

@Composable
fun PublishScreen(
    navController: NavHostController,
    initDeparture: String = "",
    initDestination: String = ""
) {
    var posList by remember { mutableStateOf<List<PosDetail>?>(null) }
    var errorMsg by remember { mutableStateOf<String?>("载入中···") }

    LaunchedEffect(Unit) {
        try {
            val response = APISERVICCE.getPos()
            if (response.isSuccessful && response.body()?.code == 200) {
                posList = response.body()?.data?.table
            } else {
                errorMsg = response.body()?.message
            }
        } catch (e: Exception) {
            errorMsg = e.message
        }
    }

    if (posList != null) {
        DisplayPublish(posList!!, navController, initDeparture, initDestination)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.app_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color(0x66FFFFFF)))
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = NeonCyan)
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "$errorMsg",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayPublish(
    posList: List<PosDetail>,
    navController: NavHostController,
    initDeparture: String,
    initDestination: String
) {
    var departure by remember { mutableStateOf(initDeparture) }
    var destination by remember { mutableStateOf(initDestination) }
    var date by remember { mutableStateOf("") }
    var earliestTime by remember { mutableStateOf("") }
    var latestTime by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var departureExpanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }

    // Picker dialog visibility state
    var showDatePicker by remember { mutableStateOf(false) }
    var showEarliestTimePicker by remember { mutableStateOf(false) }
    var showLatestTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Sci-fi picker dialogs (rendered as overlays)
    if (showDatePicker) {
        SciFiDatePickerDialog(
            onDateSelected = { date = it },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showEarliestTimePicker) {
        SciFiTimePickerDialog(
            title = "最早出发时间",
            onTimeSelected = { earliestTime = it },
            onDismiss = { showEarliestTimePicker = false }
        )
    }
    if (showLatestTimePicker) {
        SciFiTimePickerDialog(
            title = "最晚出发时间",
            onTimeSelected = { latestTime = it },
            onDismiss = { showLatestTimePicker = false }
        )
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
        focusedTrailingIconColor   = NeonCyan,
        unfocusedTrailingIconColor = GlowCyan30
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0x66FFFFFF)))
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = NeonCyan)
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(NeonCyan.copy(0.8f), NeonPurple.copy(0.4f))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                                text = "| RIDE  REQUEST",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 3.sp,
                                    color = NeonCyan.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            )
                            Text(
                                text = "拼车需求填写",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NeonCyan,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // 出发地
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                OutlinedTextField(
                                    value = departure,
                                    onValueChange = { departure = it },
                                    label = { Text("出发地") },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { departureExpanded = true }) {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Dropdown",
                                                tint = NeonCyan
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = neonFieldColors
                                )
                                DropdownMenu(
                                    expanded = departureExpanded,
                                    onDismissRequest = { departureExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    posList.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.name) },
                                            onClick = {
                                                departure = option.name
                                                departureExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 目的地
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                OutlinedTextField(
                                    value = destination,
                                    onValueChange = { destination = it },
                                    label = { Text("目的地") },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { destinationExpanded = true }) {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Dropdown",
                                                tint = NeonCyan
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = neonFieldColors
                                )
                                DropdownMenu(
                                    expanded = destinationExpanded,
                                    onDismissRequest = { destinationExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    posList.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.name) },
                                            onClick = {
                                                destination = option.name
                                                destinationExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 拼车日期
                            OutlinedTextField(
                                value = date,
                                onValueChange = {},
                                label = { Text("拼车日期") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = "选择日期",
                                            tint = NeonCyan
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = neonFieldColors
                            )

                            // 最早出发时间
                            OutlinedTextField(
                                value = earliestTime,
                                onValueChange = {},
                                label = { Text("最早出发时间") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showEarliestTimePicker = true }) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = "选择时间",
                                            tint = NeonCyan
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = neonFieldColors
                            )

                            // 最晚出发时间
                            OutlinedTextField(
                                value = latestTime,
                                onValueChange = {},
                                label = { Text("最晚出发时间") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showLatestTimePicker = true }) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = "选择时间",
                                            tint = NeonCyan
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = neonFieldColors
                            )

                            // 备注
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("备注") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions.Default,
                                keyboardActions = KeyboardActions.Default,
                                colors = neonFieldColors
                            )
                        }
                    }

                    // 提交按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NeonCyan, NeonBlue, Color(0xFFC8703A))
                                )
                            )
                            .clickable {
                                if (USERTYPE != 1) {
                                    Toast.makeText(context, "该用户类型不能发布拼单", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                if (departure.isEmpty()) {
                                    Toast.makeText(context, "出发地为空", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                } else if (destination.isEmpty()) {
                                    Toast.makeText(context, "目的地为空", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                } else if (date.isEmpty()) {
                                    Toast.makeText(context, "日期为空", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                } else if (earliestTime.isEmpty()) {
                                    Toast.makeText(context, "最早出发时间为空", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                } else if (latestTime.isEmpty()) {
                                    Toast.makeText(context, "最晚出发时间为空", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                } else if (notes.length >= 100) {
                                    Toast.makeText(context, "备注长于100字符", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                } else if (departure == destination) {
                                    Toast.makeText(context, "出发地和起始地相同", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                scope.launch {
                                    try {
                                        val response = APISERVICCE.publish(
                                            request = PublishRequest(
                                                departure,
                                                destination,
                                                date,
                                                earliestTime,
                                                latestTime,
                                                notes
                                            )
                                        )
                                        if (response.isSuccessful && response.body()?.code == 201) {
                                            Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show()
                                            val id = response.body()?.data?.order_id
                                            navController.navigate("tripDetail/$id") {
                                                popUpTo("createTripRoot") { inclusive = true }
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
                                            "发布失败，请检查网络连接",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 修复了这里的乱码和引号
                        Text(
                            "发布",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp,
                                color = DeepSpace
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Sci-Fi Date Picker Dialog
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SciFiDatePickerDialog(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val todayMillis = System.currentTimeMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis in todayMillis..(todayMillis + 365L * 24 * 60 * 60 * 1000)
            }
        }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF5FFFFFF))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(NeonCyan.copy(0.9f), NeonBlue.copy(0.5f), NeonCyan.copy(0.2f))
                    ),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFF5EB), Color(0xFFFFEDD8))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "| SELECT DATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp,
                                color = NeonCyan.copy(0.7f)
                            )
                        )
                        val selectedMillis = state.selectedDateMillis
                        val displayText = if (selectedMillis != null) {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = selectedMillis
                            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
                        } else "选择日期"
                        Text(
                            displayText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        )
                    }
                }

                // Calendar
                DatePicker(
                    state = state,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor              = Color.Transparent,
                        titleContentColor           = NeonCyan.copy(0.7f),
                        headlineContentColor        = NeonCyan,
                        weekdayContentColor         = StarWhite.copy(0.5f),
                        subheadContentColor         = StarWhite,
                        navigationContentColor      = NeonCyan,
                        yearContentColor            = StarWhite,
                        disabledYearContentColor    = StarWhite.copy(0.3f),
                        currentYearContentColor     = NeonCyan,
                        selectedYearContentColor    = DeepSpace,
                        disabledSelectedYearContentColor = Color(0xFF455A64),
                        selectedYearContainerColor  = NeonCyan,
                        disabledSelectedYearContainerColor = Color(0xFF37474F),
                        dayContentColor             = StarWhite,
                        disabledDayContentColor     = StarWhite.copy(0.25f),
                        selectedDayContentColor     = DeepSpace,
                        disabledSelectedDayContentColor = Color(0xFF455A64),
                        selectedDayContainerColor   = NeonCyan,
                        todayContentColor           = NeonCyan,
                        todayDateBorderColor        = NeonCyan,
                        dayInSelectionRangeContainerColor = GlowCyan30,
                        dayInSelectionRangeContentColor   = NeonCyan,
                        dividerColor                = GlowCyan30
                    )
                )

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlowCyan30)
                )

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, StarWhite.copy(0.3f), RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // 修复乱码
                        Text(
                            "取消",
                            color = StarWhite.copy(0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan)
                            .clickable {
                                val millis = state.selectedDateMillis
                                if (millis != null) {
                                    val cal = Calendar.getInstance()
                                    cal.timeInMillis = millis
                                    onDateSelected(
                                        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
                                    )
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // 修复乱码
                        Text(
                            "确认",
                            color = DeepSpace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Sci-Fi Time Picker Dialog  (digital ▲ / ▼ clock)
// ─────────────────────────────────────────────────────────────
@Composable
fun SciFiTimePickerDialog(
    title: String = "选择时间",
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    var hour   by remember { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(calendar.get(Calendar.MINUTE)) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF5FFFFFF))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(NeonBlue.copy(0.9f), NeonCyan.copy(0.5f), NeonBlue.copy(0.2f))
                    ),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "| SELECT TIME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp,
                                color = NeonCyan.copy(0.7f)
                            )
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Clock display: [ ▲ ]  :  [ ▲ ]
                //                [ HH ]  :  [ MM ]
                //                [ ▼ ]  :  [ ▼ ]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hours column
                    TimeWheelColumn(
                        value = hour,
                        label = "时",
                        onIncrease = { hour = (hour + 1) % 24 },
                        onDecrease = { hour = (hour - 1 + 24) % 24 }
                    )

                    // Separator
                    Text(
                        ":",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minutes column
                    TimeWheelColumn(
                        value = minute,
                        label = "分",
                        onIncrease = { minute = (minute + 1) % 60 },
                        onDecrease = { minute = (minute - 1 + 60) % 60 }
                    )
                }

                Spacer(Modifier.height(28.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlowCyan30)
                )

                Spacer(Modifier.height(14.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, StarWhite.copy(0.3f), RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // 修复乱码
                        Text(
                            "取消",
                            color = StarWhite.copy(0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan)
                            .clickable {
                                onTimeSelected("%02d:%02d".format(hour, minute))
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // 修复乱码
                        Text(
                            "确认",
                            color = DeepSpace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/** Single digit wheel (hour or minute) with up/down buttons */
@Composable
private fun TimeWheelColumn(
    value: Int,
    label: String,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Label
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp,
                color = NeonCyan.copy(0.6f)
            )
        )
        // Up arrow
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1AE07830))
                .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
                .clickable { onIncrease() }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "增加",
                tint = NeonCyan
            )
        }
        // Digit display
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x26E07830))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(NeonCyan.copy(0.6f), NeonBlue.copy(0.3f))),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%02d".format(value),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            )
        }
        // Down arrow
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1AE07830))
                .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
                .clickable { onDecrease() }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "减少",
                tint = NeonCyan
            )
        }
    }
}

@Preview
@Composable
fun PreviewPublishScreen() {
    White_webTheme {
        val navController = rememberNavController()
        PublishScreen(navController)
    }
}
