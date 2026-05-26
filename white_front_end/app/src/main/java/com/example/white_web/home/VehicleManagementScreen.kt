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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.example.white_web.APISERVICCE
import com.example.white_web.R
import com.example.white_web.VehicleData
import com.example.white_web.VehicleRequest
import com.example.white_web.VehicleUpdateRequest
import com.example.white_web.ui.theme.GlowCyan10
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.StarWhite
import kotlinx.coroutines.launch

@Composable
fun VehicleManagementScreen(navController: NavHostController) {
    var vehicles by remember { mutableStateOf<List<VehicleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleData?>(null) }
    var deletingVehicle by remember { mutableStateOf<VehicleData?>(null) }
    val scope = rememberCoroutineScope()

    fun loadVehicles() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val response = APISERVICCE.getMyVehicles()
                if (response.isSuccessful && response.body()?.code == 200) {
                    vehicles = response.body()?.data?.list.orEmpty()
                } else {
                    error = response.body()?.message ?: "车辆信息加载失败：${response.code()}"
                }
            } catch (e: Exception) {
                error = "车辆信息加载失败：${e.message ?: "网络异常"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadVehicles()
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
            VehicleTopBar(
                title = "车辆管理",
                onBackClick = { navController.popBackStack() },
                onRefreshClick = { loadVehicles() },
                onAddClick = {
                    editingVehicle = null
                    showForm = true
                }
            )

            when {
                isLoading -> LoadingVehicleList()
                error != null -> VehicleMessage(text = error ?: "加载失败")
                vehicles.isEmpty() -> VehicleMessage(text = "暂无车辆，请先新增一辆车")
                else -> VehicleList(
                    vehicles = vehicles,
                    onEdit = {
                        editingVehicle = it
                        showForm = true
                    },
                    onDelete = { deletingVehicle = it }
                )
            }
        }
    }

    if (showForm) {
        VehicleFormDialog(
            vehicle = editingVehicle,
            onDismiss = { showForm = false },
            onSubmit = { request ->
                scope.launch {
                    try {
                        val response = if (editingVehicle == null) {
                            APISERVICCE.addVehicle(request = request)
                        } else {
                            APISERVICCE.updateVehicle(
                                vehicleId = editingVehicle!!.vehicleId,
                                request = VehicleUpdateRequest(
                                    licensePlate = request.licensePlate,
                                    brand = request.brand,
                                    model = request.model,
                                    color = request.color,
                                    seatCount = request.seatCount
                                )
                            )
                        }
                        if (response.isSuccessful && (response.body()?.code == 200 || response.body()?.code == 201)) {
                            showForm = false
                            editingVehicle = null
                            loadVehicles()
                        } else {
                            error = response.body()?.message ?: "车辆保存失败：${response.code()}"
                        }
                    } catch (e: Exception) {
                        error = "车辆保存失败：${e.message ?: "网络异常"}"
                    }
                }
            }
        )
    }

    deletingVehicle?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { deletingVehicle = null },
            title = { Text("删除车辆") },
            text = { Text("确认删除 ${vehicle.licensePlate} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val response = APISERVICCE.deleteVehicle(vehicle.vehicleId)
                            if (response.isSuccessful && response.body()?.code == 200) {
                                deletingVehicle = null
                                loadVehicles()
                            } else {
                                error = response.body()?.message ?: "车辆删除失败：${response.code()}"
                            }
                        } catch (e: Exception) {
                            error = "车辆删除失败：${e.message ?: "网络异常"}"
                        }
                    }
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVehicle = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun VehicleTopBar(
    title: String,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAddClick: () -> Unit
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
            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = NeonCyan)
        }
        Spacer(modifier = Modifier.size(8.dp))
        IconSurfaceButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = "新增", tint = NeonGreen)
        }
    }
}

@Composable
private fun LoadingVehicleList() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NeonCyan)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "正在加载车辆信息",
                color = StarWhite,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun VehicleMessage(text: String) {
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
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeonPurple)
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
private fun VehicleList(
    vehicles: List<VehicleData>,
    onEdit: (VehicleData) -> Unit,
    onDelete: (VehicleData) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "共 ${vehicles.size} 辆车辆",
                color = Color(0xFF6B4A30),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        items(vehicles) { vehicle ->
            VehicleCard(
                vehicle = vehicle,
                onEdit = { onEdit(vehicle) },
                onDelete = { onDelete(vehicle) }
            )
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleData,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF7FFFFFF))
            .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
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
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeonCyan)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = vehicle.licensePlate,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${vehicle.brand} ${vehicle.model} · ${vehicle.color}",
                    color = Color(0xFF6B4A30),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusChip(
                text = if (vehicle.isVerified) "已认证" else "待认证",
                tint = if (vehicle.isVerified) NeonGreen else Color(0xFF8B6A50)
            )
        }

        Text(
            text = "座位 ${vehicle.seatCount} 个 · 创建于 ${formatCreatedAt(vehicle.createdAt)}",
            color = Color(0xFF8B6A50),
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                text = "编辑",
                icon = Icons.Default.Edit,
                containerColor = NeonBlue.copy(alpha = 0.14f),
                contentColor = NeonBlue,
                onClick = onEdit
            )
            ActionButton(
                text = "删除",
                icon = Icons.Default.Delete,
                containerColor = Color(0xFFCC4444).copy(alpha = 0.12f),
                contentColor = Color(0xFFCC4444),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun VehicleFormDialog(
    vehicle: VehicleData?,
    onDismiss: () -> Unit,
    onSubmit: (VehicleRequest) -> Unit
) {
    var licensePlate by remember { mutableStateOf(vehicle?.licensePlate.orEmpty()) }
    var brand by remember { mutableStateOf(vehicle?.brand.orEmpty()) }
    var model by remember { mutableStateOf(vehicle?.model.orEmpty()) }
    var color by remember { mutableStateOf(vehicle?.color.orEmpty()) }
    var seatCountText by remember { mutableStateOf(vehicle?.seatCount?.toString().orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF7FFFFFF))
                .border(1.dp, GlowCyan30, RoundedCornerShape(8.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (vehicle == null) "新增车辆" else "编辑车辆",
                color = StarWhite,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedTextField(
                value = licensePlate,
                onValueChange = { licensePlate = it },
                label = { Text("车牌号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("品牌") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("车型") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("颜色") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = seatCountText,
                onValueChange = { seatCountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("座位数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    onClick = {
                        val seatCount = seatCountText.toIntOrNull() ?: 4
                        onSubmit(
                            VehicleRequest(
                                licensePlate = licensePlate.trim(),
                                brand = brand.trim(),
                                model = model.trim(),
                                color = color.trim(),
                                seatCount = seatCount
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.White
                    )
                ) {
                    Text("保存")
                }
            }
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
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, contentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 6.dp)
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

private fun formatCreatedAt(value: String?): String {
    return value?.replace("T", " ")?.substringBefore(".")?.takeIf { it.isNotBlank() } ?: "暂无"
}
