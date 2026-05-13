package com.tongji.user.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.yourdomain.com/" // 注意：结尾斜杠

    val instance: UserService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserService::class.java)
    }
}

// 响应数据模型
data class PhoneResponse(
    val phone: String,
    val status: Int
)