package com.tongji.user.network

object RetrofitClient {
    val instance: UserService
        get() = UserService
}

// 响应数据模型
data class PhoneResponse(
    val phone: String,
    val status: Int
)
