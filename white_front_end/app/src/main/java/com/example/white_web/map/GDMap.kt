/**
 * 高德地图集成组件 (Amap Integration Component)
 *
 * 文件功能: 高德地图SDK集成和地图相关功能实现
 *
 * 主要功能:
 * - 集成高德地图SDK
 * - 提供定位服务功能
 * - 实现地图标点和路径展示
 * - 支持路径规划和距离计算
 * - 提供地图交互和选点功能
 *
 * 定位功能:
 * - GPS定位服务
 * - 位置监听器
 * - 定位蓝点显示
 * - 位置更新回调
 *
 * 地图组件:
 * - GDMap: 基础地图组件
 * - GDMapMarker: 带标记的地图组件
 * - 路径展示功能
 * - 相机控制功能
 *
 * 使用场景:
 * - 发布页面的地点选择
 * - 当前订单的路径展示
 * - 距离计算和价格估算
 * - 导航和位置服务
 */
package com.example.white_web.map

import PosDetail
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.scale
import androidx.navigation.NavController
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.MyLocationStyle
import com.amap.api.maps2d.model.PolylineOptions
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RouteSearch
import com.example.white_web.R
import com.example.white_web.ui.theme.White_webTheme

/*      此函数进行位置的监听，图标显示              */
/*      注意：高德默认定位图标显示存在不明bug        */
/*      定位日志正确 但图标异常不显示               */
private fun initLocationListener(
    context: Context,
    mapView: MapView,
    onLocationChanged: (latitude: Double, longitude: Double) -> Unit
) {
    try {
        val locationClient = AMapLocationClient(context)
        val locationListener = AMapLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                val latitude = location.latitude
                val longitude = location.longitude

                onLocationChanged(latitude, longitude)

                val aMap = mapView.map

                val myLocationStyle = MyLocationStyle()
                myLocationStyle.interval(1000)

                aMap.setMyLocationStyle(myLocationStyle)//设置定位蓝点的Style
                aMap.isMyLocationEnabled = true
                Log.d("Location", "Latitude: $latitude, Longitude: $longitude")
            } else {
                Log.e("Location", "Location error: ${location?.errorCode}, ${location?.errorInfo}")
            }
        }

        locationClient.apply {
            setLocationListener(locationListener)
            startLocation()
        }
    } catch (e: Exception) {
        Log.e("ERROR", e.message, e)
    }

}

/*
 * 一个基本的可缩放显示当前定位？的地图
 */
@Composable
fun GDMap(
    modifier: Modifier = Modifier,
//    onLocationUpdated: (LatLng) -> Unit = {}
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var hasInitialized by remember { mutableStateOf(false) }

    // 定位回调处理
    LaunchedEffect(Unit) {
        initLocationListener(context, mapView) { lat, lng ->
//            onLocationUpdated(LatLng(lat, lng))

            if (!hasInitialized) {
                mapView.map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(lat, lng),
                        17f
                    )
                )
                hasInitialized = true
            }
        }
        mapView.map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(31.285629, 121.214728),
                17f
            )
        )

    }

    // 纯地图视图
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            mapView.apply {
                onCreate(null)
                onResume()
                map.uiSettings.isZoomControlsEnabled = true
            }
        }
    )
}

/*
 * 可以显示标点的地图组件
 * 可以设置回调函数
 */
@Composable
fun GDMapMarker(
    modifier: Modifier = Modifier,
    markers: List<PosDetail>,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var hasInitialized by remember { mutableStateOf(false) }
    var selectedMarker: Marker? by remember { mutableStateOf(null) }
    val markerMap = remember { mutableMapOf<Marker, PosDetail>() }

    LaunchedEffect(Unit) {
        initLocationListener(context, mapView) { lat, lng ->
            if (!hasInitialized) {
                if (lat > 31 && lat < 32 && lng > 121 && lng < 122) {
                    mapView.map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(lat, lng),
                            17f
                        )
                    )
                }
                hasInitialized = true
            }
        }

        mapView.map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(31.285629, 121.214728),
                17f
            )
        )


        // 从资源文件加载 Bitmap
        var bitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.point)
        val scaleSize = 7
        bitmap = bitmap.scale(bitmap.width / scaleSize, bitmap.height / scaleSize)
        // 添加标点
        markers.forEach { detail ->
            val markerOptions = MarkerOptions()
                .position(LatLng(detail.lat, detail.lon))
                .title(detail.name)
                .snippet("点击查看详情")
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
//                .anchor(0.5f, 1f) // 设置锚点位置为图标底部中心

            val marker = mapView.map.addMarker(markerOptions)
            markerMap[marker] = detail // 将 PosDetail 对象存储到 markerMap 中
        }

        // 设置全局 Marker 点击监听器
        mapView.map.setOnMarkerClickListener { marker ->
            selectedMarker = marker
            val selectDetail = markerMap[marker]
            mapView.map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(selectDetail!!.lat, selectDetail.lon),
                    17f
                )
            )
            true // 返回 true 表示已处理点击事件
        }
    }

    // 显示浮窗
    if (selectedMarker != null) {
        val detail = markerMap[selectedMarker]
        MarkerOptionsDialog(
            name = detail!!.name,
            onDismissRequest = { selectedMarker = null },
            onOptionSelected = { option ->
                when (option) {
                    "to" -> navController?.navigate("createTrip/to/${detail.name}")
                    "from" -> navController?.navigate("createTrip/from/${detail.name}")
                }
                selectedMarker = null
            }
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            mapView.apply {
                onCreate(null)
                onResume()
                map.uiSettings.isZoomControlsEnabled = true
            }
        }
    )
}

@Composable
fun MarkerOptionsDialog(
    name: String,
    onDismissRequest: () -> Unit,
    onOptionSelected: (option: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = name) },
        confirmButton = {
            TextButton(onClick = {
                onOptionSelected("to")
            }) {
                Text("到这里去")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onOptionSelected("from")
            }) {
                Text("从这出发")
            }
        }
    )
}

@Preview
@Composable
fun MarkerOptionsDialogPreview() {
    White_webTheme {
        MarkerOptionsDialog("上海南站", {}, {})
    }
}

/*
 * 路径规划
 */
@Composable
fun GDMapPath(
    modifier: Modifier = Modifier,
    start: PosDetail,
    end: PosDetail,
    onDistanceCalculated: (Float) -> Unit // 通过参数回传距离
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(Unit) {
        // 规划路线
        try {
            val routeSearch = RouteSearch(context)
            routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
                    if (errorCode == AMapException.CODE_AMAP_SUCCESS) {
                        // 获取路径
                        val path = result?.paths?.firstOrNull()
                        path?.let {
                            drawRoute(path, mapView)
                            // 获取路线距离
                            val distance = it.distance
                            Log.d("Route", "Distance: $distance meters")
                            // 回传
                            onDistanceCalculated(distance)
                        }
                    } else {
                        Log.e("Route", "Error Code: $errorCode")
                    }
                }

                override fun onBusRouteSearched(
                    p0: com.amap.api.services.route.BusRouteResult?,
                    p1: Int
                ) {
                }

                override fun onWalkRouteSearched(
                    p0: com.amap.api.services.route.WalkRouteResult?,
                    p1: Int
                ) {
                }

                override fun onRideRouteSearched(
                    p0: com.amap.api.services.route.RideRouteResult?,
                    p1: Int
                ) {
                }
            })

            // 构建起点终点信息
            val start = RouteSearch.FromAndTo(
                LatLonPoint(start.lat, start.lon),
                LatLonPoint(end.lat, end.lon)
            )
            val query =
                RouteSearch.DriveRouteQuery(start, RouteSearch.DRIVING_NORMAL_CAR, null, null, "")
            routeSearch.calculateDriveRouteAsyn(query)

        } catch (e: Exception) {
            Log.e("ERROR", e.message, e)
        }

    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            mapView.apply {
                onCreate(null)
                onResume()
                map.uiSettings.isZoomControlsEnabled = true
                // 等地图真正加载完成后再 zoom
                map.setOnMapLoadedListener {
                    zoomToFit(mapView, start, end)
                }
            }
        }
    )
}

private fun drawRoute(path: DrivePath, mapView: MapView) {
    val polylineOptions = PolylineOptions()
    path.steps.forEach { step ->
        polylineOptions.addAll(step.polyline.map { LatLng(it.latitude, it.longitude) })
    }
    mapView.map.addPolyline(polylineOptions.width(10f).color(Color.GREEN))
}

fun zoomToFit(mapView: MapView, start: PosDetail, end: PosDetail) {
    val aMap = mapView.map

    // 1. 构建包含两个点的 Bounds
    val bounds = LatLngBounds.Builder()
        .include(LatLng(start.lat, start.lon))
        .include(LatLng(end.lat, end.lon))
        .build()

    // 2. 根据视图尺寸设置 padding（边距），避免点贴边
    val padding = 20

    // 3. 让 AMap 自动计算中心和最佳缩放
    //    注意：必须在 MapView 尺寸已确定后调用
    aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
}