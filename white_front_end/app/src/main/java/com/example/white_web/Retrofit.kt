package com.example.white_web

import DetailResponse
import GetPosResponse
import JoinLeaveRedponse
import JoinLeaveRequest
import PublishRequest
import PublishResponse
import UserDetailResponse
import com.example.white_web.home.AllOrdersResponse
import com.example.white_web.home.BaseResponse
import com.example.white_web.home.ConfirmArrivalResponse
import com.example.white_web.home.CurrentOrderResponse
import com.example.white_web.home.DriverRatingRequest
import com.example.white_web.home.LookResponse
import com.example.white_web.home.OrderIdRequest
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

private val retrofit = Retrofit.Builder()
    .baseUrl("http://10.0.2.2:8443/")  // 后端地址
//    .baseUrl("http://59.110.22.187:8443/") // 服务器地址
    .addConverterFactory(GsonConverterFactory.create())
    .build()

var USERNAME: String? = "未登录"
var USERTYPE: Int? = -1
var TOKEN: String? = ""

// 司机评分数据响应
data class DriverRatingResponse(
    val code: Int,
    val message: String,
    val data: DriverRatingData?
)

data class DriverRatingData(
    val username: String,
    val rating: Float,
    val rating_count: Int
)

// 检查用户是否已评分请求
data class CheckUserRatingRequest(
    @SerializedName("order_id")
    val orderId: Int,
    @SerializedName("driver_username")
    val driverUsername: String
)

// 检查用户是否已评分响应
data class CheckUserRatingResponse(
    val code: Int,
    val message: String,
    val data: CheckUserRatingData?
)

data class CheckUserRatingData(
    @SerializedName("has_rated")
    val hasRated: Boolean
)

// 车辆相关数据类
data class VehicleRequest(
    @SerializedName("license_plate")
    val licensePlate: String,
    val brand: String,
    val model: String,
    val color: String,
    @SerializedName("seat_count")
    val seatCount: Int = 4
)

data class VehicleUpdateRequest(
    @SerializedName("license_plate")
    val licensePlate: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val color: String? = null,
    @SerializedName("seat_count")
    val seatCount: Int? = null
)

data class VehicleData(
    @SerializedName("vehicle_id")
    val vehicleId: Int,
    @SerializedName("license_plate")
    val licensePlate: String,
    val brand: String,
    val model: String,
    val color: String,
    @SerializedName("seat_count")
    val seatCount: Int,
    @SerializedName("is_verified")
    val isVerified: Boolean,
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class VehicleResponse(
    val code: Int,
    val message: String,
    val data: VehicleData?
)

data class VehicleListResponse(
    val code: Int,
    val message: String,
    val data: VehicleListData?
)

data class VehicleListData(
    val list: List<VehicleData>
)

// 优惠券相关数据类
data class CouponCreateRequest(
    @SerializedName("coupon_name")
    val couponName: String,
    @SerializedName("discount_type")
    val discountType: String, // "percentage" 或 "fixed"
    @SerializedName("discount_value")
    val discountValue: Double,
    @SerializedName("min_amount")
    val minAmount: Double = 0.0,
    @SerializedName("start_date")
    val startDate: String, // YYYY-MM-DD 格式
    @SerializedName("end_date")
    val endDate: String, // YYYY-MM-DD 格式
    @SerializedName("usage_limit")
    val usageLimit: Int = 1,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class CouponData(
    @SerializedName("coupon_id")
    val couponId: Int,
    @SerializedName("coupon_name")
    val couponName: String,
    @SerializedName("discount_type")
    val discountType: String,
    @SerializedName("discount_value")
    val discountValue: Double,
    @SerializedName("min_amount")
    val minAmount: Double,
    @SerializedName("start_date")
    val startDate: String,
    @SerializedName("end_date")
    val endDate: String,
    @SerializedName("usage_limit")
    val usageLimit: Int,
    @SerializedName("is_active")
    val isActive: Boolean? = null
)

data class UserCouponData(
    @SerializedName("coupon_id")
    val couponId: Int,
    @SerializedName("coupon_name")
    val couponName: String,
    @SerializedName("discount_type")
    val discountType: String,
    @SerializedName("discount_value")
    val discountValue: Double,
    @SerializedName("min_amount")
    val minAmount: Double,
    @SerializedName("start_date")
    val startDate: String,
    @SerializedName("end_date")
    val endDate: String,
    @SerializedName("usage_limit")
    val usageLimit: Int,
    @SerializedName("used_count")
    val usedCount: Int,
    @SerializedName("obtained_at")
    val obtainedAt: String,
    @SerializedName("can_use")
    val canUse: Boolean,
    @SerializedName("is_expired")
    val isExpired: Boolean
)

data class CouponResponse(
    val code: Int,
    val message: String,
    val data: CouponData?
)

data class CouponListResponse(
    val code: Int,
    val message: String,
    val data: CouponListData?
)

data class CouponListData(
    val list: List<CouponData>
)

data class UserCouponListResponse(
    val code: Int,
    val message: String,
    val data: UserCouponListData?
)

data class UserCouponListData(
    val list: List<UserCouponData>
)

data class CouponClaimResponse(
    val code: Int,
    val message: String,
    val data: CouponClaimData?
)

data class CouponClaimData(
    @SerializedName("coupon_name")
    val couponName: String,
    @SerializedName("discount_type")
    val discountType: String,
    @SerializedName("discount_value")
    val discountValue: Double
)

data class CouponUseRequest(
    val amount: Double
)

data class CouponUseResponse(
    val code: Int,
    val message: String,
    val data: CouponUseData?
)

data class CouponUseData(
    @SerializedName("original_amount")
    val originalAmount: Double,
    @SerializedName("discount_amount")
    val discountAmount: Double,
    @SerializedName("final_amount")
    val finalAmount: Double,
    @SerializedName("coupon_name")
    val couponName: String
)

interface ApiService {
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @GET("/api/look")
    suspend fun look(@Header("Authorization") token: String? = TOKEN): Response<LookResponse>

    @GET("/api/orders")
    suspend fun getOrders(): Response<AllOrdersResponse>

    @GET("api/orders/search/{keyword}")
    suspend fun getSearchResults(@Path("keyword") keyword: String): Response<AllOrdersResponse>

    @GET("/api/getpos")
    suspend fun getPos(): Response<GetPosResponse>

    @POST("/api/orders/add")
    suspend fun publish(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: PublishRequest
    ): Response<PublishResponse>

    @GET("/api/orders/{order_id}")
    suspend fun detail(@Path("order_id") orderId: Int): Response<DetailResponse>

    @GET("/api/user/{username}")
    suspend fun getUserDetail(@Path("username") username: String): Response<UserDetailResponse>

    @POST("/api/orders/join")
    suspend fun joinOrder(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: JoinLeaveRequest
    ): Response<JoinLeaveRedponse>

    @POST("/api/orders/leave")
    suspend fun leaveOrder(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: JoinLeaveRequest
    ): Response<JoinLeaveRedponse>

    // 添加到ApiService接口中

    @GET("/api/user/current-order")
    suspend fun getCurrentOrder(@Header("Authorization") token: String? = TOKEN): Response<CurrentOrderResponse>

    @POST("/api/order/confirm-arrival")
    suspend fun confirmArrival(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: OrderIdRequest
    ): Response<ConfirmArrivalResponse>

    @POST("/api/order/start-trip")
    suspend fun startTrip(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: OrderIdRequest
    ): Response<BaseResponse>

    @POST("/api/order/confirm-destination")
    suspend fun confirmDestination(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: OrderIdRequest
    ): Response<BaseResponse>

    @POST("/api/order/rate-driver")
    suspend fun rateDriver(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: DriverRatingRequest
    ): Response<BaseResponse>

    @GET("/api/user/driver-rating/{username}")
    suspend fun getDriverRating(@Path("username") username: String): Response<DriverRatingResponse>

    @POST("/api/order/check-user-rating")
    suspend fun checkUserRating(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: CheckUserRatingRequest
    ): Response<CheckUserRatingResponse>

    @GET("/api/orders/not-started")
    suspend fun getNotStartedOrders(): Response<AllOrdersResponse>

    // 车辆管理接口
    @POST("/api/vehicle/add")
    suspend fun addVehicle(
        @Header("Authorization") token: String? = TOKEN,
        @Body request: VehicleRequest
    ): Response<VehicleResponse>

    @GET("/api/vehicle/my-vehicles")
    suspend fun getMyVehicles(
        @Header("Authorization") token: String? = TOKEN
    ): Response<VehicleListResponse>

    @retrofit2.http.PUT("/api/vehicle/update/{vehicle_id}")
    suspend fun updateVehicle(
        @Path("vehicle_id") vehicleId: Int,
        @Header("Authorization") token: String? = TOKEN,
        @Body request: VehicleUpdateRequest
    ): Response<VehicleResponse>

    @retrofit2.http.DELETE("/api/vehicle/delete/{vehicle_id}")
    suspend fun deleteVehicle(
        @Path("vehicle_id") vehicleId: Int,
        @Header("Authorization") token: String? = TOKEN
    ): Response<BaseResponse>

    // 优惠券管理接口
    @POST("/api/coupon/create")
    suspend fun createCoupon(
        @Body request: CouponCreateRequest
    ): Response<CouponResponse>

    @GET("/api/coupon/available")
    suspend fun getAvailableCoupons(): Response<CouponListResponse>

    @POST("/api/coupon/claim/{coupon_id}")
    suspend fun claimCoupon(
        @Path("coupon_id") couponId: Int,
        @Header("Authorization") token: String? = TOKEN
    ): Response<CouponClaimResponse>

    @GET("/api/coupon/my-coupons")
    suspend fun getMyCoupons(
        @Header("Authorization") token: String? = TOKEN
    ): Response<UserCouponListResponse>

    @POST("/api/coupon/use/{coupon_id}")
    suspend fun useCoupon(
        @Path("coupon_id") couponId: Int,
        @Header("Authorization") token: String? = TOKEN,
        @Body request: CouponUseRequest
    ): Response<CouponUseResponse>
}

val APISERVICCE = retrofit.create(ApiService::class.java)
