package com.tongji.user.network
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {
    @GET("api/user/<str:username>") // 替换为您的实际API端点
    suspend fun getUserInfo(): Response<UserInfo>
}