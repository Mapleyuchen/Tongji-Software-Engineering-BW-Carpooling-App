package com.example.white_web.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.PolylineOptions
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RouteSearch
import com.example.white_web.APISERVICCE
import com.example.white_web.PosDetail
import com.example.white_web.map.estimateDrivingDistanceMeters
import com.example.white_web.map.zoomToFit
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonGreen
import com.example.white_web.ui.theme.StarWhite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriverNavigationUiState(
    val loading: Boolean = false,
    val order: CurrentOrderData? = null,
    val error: String? = null
)

data class NavigationStats(
    val remainingDistance: String = "路线计算中...",
    val currentLocation: String = "等待定位...",
    val routeSource: String = "正在规划路线"
)

class DriverNavigationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DriverNavigationUiState())
    val uiState = _uiState.asStateFlow()

    fun loadOrder(orderId: Int) {
        viewModelScope.launch {
            _uiState.value = DriverNavigationUiState(loading = true)
            try {
                val response = APISERVICCE.getCurrentOrder()
                val body = response.body()
                if (response.isSuccessful && body?.code == 200 && body.data != null) {
                    val data = body.data
                    if (data.order.orderId == orderId) {
                        _uiState.value = DriverNavigationUiState(order = data)
                    } else {
                        _uiState.value = DriverNavigationUiState(
                            order = data,
                            error = "当前订单已变化，正在显示最新行程"
                        )
                    }
                } else {
                    _uiState.value = DriverNavigationUiState(
                        error = body?.message ?: "导航订单加载失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DriverNavigationUiState(error = "网络请求错误: ${e.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverNavigationScreen(
    navController: NavHostController,
    orderId: Int,
    viewModel: DriverNavigationViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var stats by remember { mutableStateOf(NavigationStats()) }

    LaunchedEffect(orderId) {
        viewModel.loadOrder(orderId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("实时导航") },
                navigationIcon = {
                    IconButton(onClick = { exitNavigation(navController) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出导航")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadOrder(orderId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xF7FFF9F5),
                    titleContentColor = NeonCyan,
                    navigationIconContentColor = NeonCyan,
                    actionIconContentColor = NeonCyan
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFFFF9F5))
        ) {
            when {
                uiState.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = NeonCyan
                    )
                }

                uiState.order == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "没有可导航的当前订单",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { exitNavigation(navController) }) {
                            Text("返回当前订单")
                        }
                    }
                }

                else -> {
                    val order = uiState.order!!
                    RealtimeNavigationMap(
                        modifier = Modifier.fillMaxSize(),
                        start = order.start,
                        end = order.end,
                        onStatsChanged = { stats = it }
                    )

                    NavigationInfoPanel(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        order = order,
                        stats = stats,
                        warning = uiState.error,
                        onExit = { exitNavigation(navController) },
                        onOpenExternal = { openExternalGoogleNavigation(context, order.end) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationInfoPanel(
    modifier: Modifier,
    order: CurrentOrderData,
    stats: NavigationStats,
    warning: String?,
    onExit: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xF8FFFFFF),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = NeonBlue
                )
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        text = "${order.order.departure} -> ${order.order.destination}",
                        color = DeepSpace,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stats.routeSource,
                        color = Color(0xFF6B4A30),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "剩余距离：${stats.remainingDistance}",
                color = NeonGreen,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stats.currentLocation,
                color = Color(0xFF6B4A30),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!warning.isNullOrBlank()) {
                Text(
                    text = warning,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenExternal,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Text("外部导航", modifier = Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("退出导航", color = DeepSpace)
                }
            }
        }
    }
}

@Composable
private fun RealtimeNavigationMap(
    modifier: Modifier,
    start: PosDetail,
    end: PosDetail,
    onStatsChanged: (NavigationStats) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestStats = remember { mutableStateOf(NavigationStats()) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var lastRoutePoint by remember { mutableStateOf<PosDetail?>(null) }

    fun publish(transform: (NavigationStats) -> NavigationStats) {
        mainHandler.post {
            latestStats.value = transform(latestStats.value)
            onStatsChanged(latestStats.value)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!hasLocationPermission) {
            publish {
                it.copy(
                    currentLocation = "未授予定位权限，显示订单起点到终点路线",
                    routeSource = "定位未授权，使用订单路线"
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        onStatsChanged(latestStats.value)
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(start.name, start.lon, start.lat, end.name, end.lon, end.lat) {
        drawNavigationRoute(
            context = context,
            mapView = mapView,
            routeStart = start,
            end = end,
            routeSource = "订单路线"
        ) { distance, source ->
            publish {
                it.copy(
                    remainingDistance = PriceUtils.formatDistanceDisplay(distance.toString()),
                    routeSource = source
                )
            }
        }
    }

    DisposableEffect(hasLocationPermission, start.name, start.lon, start.lat, end.name, end.lon, end.lat) {
        val locationClient = if (hasLocationPermission) {
            createNavigationLocationClient(context) { lat, lon ->
                val current = PosDetail("当前位置", lon, lat)
                val currentText = "当前位置：%.5f, %.5f".format(lat, lon)
                if (!isLocationUsableForOrder(current, start, end)) {
                    val distanceToOrder = nearestEndpointDistanceMeters(current, start, end)
                    publish {
                        it.copy(
                            currentLocation = "$currentText，距订单区域约${PriceUtils.formatDistanceDisplay(distanceToOrder.toString())}，已忽略",
                            routeSource = "定位偏离订单过远，使用订单路线"
                        )
                    }
                    return@createNavigationLocationClient
                }

                val movedEnough = lastRoutePoint
                    ?.let { estimateDrivingDistanceMeters(it, current) > 50f }
                    ?: true

                publish { it.copy(currentLocation = currentText) }

                if (movedEnough) {
                    lastRoutePoint = current
                    drawNavigationRoute(
                        context = context,
                        mapView = mapView,
                        routeStart = current,
                        end = end,
                        routeSource = "实时路线"
                    ) { distance, source ->
                        publish {
                            it.copy(
                                remainingDistance = PriceUtils.formatDistanceDisplay(distance.toString()),
                                routeSource = source
                            )
                        }
                    }
                }
            }
        } else {
            null
        }

        onDispose {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { _ ->
            mapView.apply {
                onCreate(null)
                onResume()
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isCompassEnabled = true
            }
        }
    )
}

private fun createNavigationLocationClient(
    context: Context,
    onLocation: (lat: Double, lon: Double) -> Unit
): AMapLocationClient? {
    return try {
        AMapLocationClient(context.applicationContext).apply {
            setLocationOption(
                AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    interval = 2000L
                    isNeedAddress = false
                    isOnceLocation = false
                }
            )
            setLocationListener { location ->
                if (location != null && location.errorCode == 0) {
                    onLocation(location.latitude, location.longitude)
                } else {
                    Log.e(
                        "DriverNavigation",
                        "Location error: ${location?.errorCode}, ${location?.errorInfo}"
                    )
                }
            }
            startLocation()
        }
    } catch (e: Exception) {
        Log.e("DriverNavigation", "Failed to start location", e)
        null
    }
}

private fun drawNavigationRoute(
    context: Context,
    mapView: MapView,
    routeStart: PosDetail,
    end: PosDetail,
    routeSource: String,
    onDistance: (distanceMeters: Float, routeSource: String) -> Unit
) {
    try {
        val routeSearch = RouteSearch(context.applicationContext)
        routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
            override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
                if (errorCode == AMapException.CODE_AMAP_SUCCESS) {
                    val path = result?.paths?.firstOrNull()
                    if (path != null) {
                        renderNavigationRoute(mapView, routeStart, end, path)
                        val distance = path.distance.takeIf { it > 0f }
                            ?: estimateDrivingDistanceMeters(routeStart, end)
                        onDistance(distance, routeSource)
                    } else {
                        renderFallbackRoute(mapView, routeStart, end)
                        onDistance(estimateDrivingDistanceMeters(routeStart, end), "路线估算")
                    }
                } else {
                    Log.e("DriverNavigation", "Route error code: $errorCode")
                    renderFallbackRoute(mapView, routeStart, end)
                    onDistance(estimateDrivingDistanceMeters(routeStart, end), "路线估算")
                }
            }

            override fun onBusRouteSearched(
                result: com.amap.api.services.route.BusRouteResult?,
                errorCode: Int
            ) = Unit

            override fun onWalkRouteSearched(
                result: com.amap.api.services.route.WalkRouteResult?,
                errorCode: Int
            ) = Unit

            override fun onRideRouteSearched(
                result: com.amap.api.services.route.RideRouteResult?,
                errorCode: Int
            ) = Unit
        })

        val fromAndTo = RouteSearch.FromAndTo(
            LatLonPoint(routeStart.lat, routeStart.lon),
            LatLonPoint(end.lat, end.lon)
        )
        val query = RouteSearch.DriveRouteQuery(
            fromAndTo,
            RouteSearch.DRIVING_NORMAL_CAR,
            null,
            null,
            ""
        )
        routeSearch.calculateDriveRouteAsyn(query)
    } catch (e: Exception) {
        Log.e("DriverNavigation", "Route query failed", e)
        renderFallbackRoute(mapView, routeStart, end)
        onDistance(estimateDrivingDistanceMeters(routeStart, end), "路线估算")
    }
}

private fun renderNavigationRoute(
    mapView: MapView,
    routeStart: PosDetail,
    end: PosDetail,
    path: DrivePath
) {
    mapView.map.clear()
    addNavigationMarkers(mapView, routeStart, end)
    val polylineOptions = PolylineOptions()
    path.steps.forEach { step ->
        polylineOptions.addAll(step.polyline.map { LatLng(it.latitude, it.longitude) })
    }
    mapView.map.addPolyline(polylineOptions.width(12f).color(AndroidColor.rgb(0, 145, 255)))
    zoomToFit(mapView, routeStart, end)
}

private fun renderFallbackRoute(mapView: MapView, routeStart: PosDetail, end: PosDetail) {
    mapView.map.clear()
    addNavigationMarkers(mapView, routeStart, end)
    mapView.map.addPolyline(
        PolylineOptions()
            .add(LatLng(routeStart.lat, routeStart.lon), LatLng(end.lat, end.lon))
            .width(8f)
            .color(AndroidColor.rgb(0, 170, 136))
    )
    zoomToFit(mapView, routeStart, end)
}

private fun addNavigationMarkers(mapView: MapView, routeStart: PosDetail, end: PosDetail) {
    mapView.map.addMarker(
        MarkerOptions()
            .position(LatLng(routeStart.lat, routeStart.lon))
            .title(routeStart.name)
    )
    mapView.map.addMarker(
        MarkerOptions()
            .position(LatLng(end.lat, end.lon))
            .title(end.name)
    )
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

private fun isLocationUsableForOrder(
    current: PosDetail,
    start: PosDetail,
    end: PosDetail
): Boolean {
    if (current.lat !in -90.0..90.0 || current.lon !in -180.0..180.0) {
        return false
    }

    val routeSpan = estimateDrivingDistanceMeters(start, end)
    val allowedDistance = (routeSpan * 2f).coerceAtLeast(20_000f)
    return nearestEndpointDistanceMeters(current, start, end) <= allowedDistance
}

private fun nearestEndpointDistanceMeters(
    current: PosDetail,
    start: PosDetail,
    end: PosDetail
): Float {
    return minOf(
        estimateDrivingDistanceMeters(current, start),
        estimateDrivingDistanceMeters(current, end)
    )
}

private fun exitNavigation(navController: NavHostController) {
    if (!navController.popBackStack("currentOrders", inclusive = false)) {
        navController.navigate("currentOrders")
    }
}

private fun openExternalGoogleNavigation(context: Context, destination: PosDetail) {
    val googleUri = Uri.parse("google.navigation:q=${destination.lat},${destination.lon}&mode=d")
    val googleIntent = Intent(Intent.ACTION_VIEW, googleUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(googleIntent)
    } catch (_: Exception) {
        val browserUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
                "&destination=${destination.lat},${destination.lon}&travelmode=driving"
        )
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, browserUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "未找到可用的外部导航应用", Toast.LENGTH_SHORT).show()
        }
    }
}
