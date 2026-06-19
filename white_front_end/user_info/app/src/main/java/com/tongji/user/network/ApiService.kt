package com.tongji.user.network
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @GET("api/user/{username}")
    suspend fun getUserInfo(@Path("username") username: String): Response<UserInfo>
}
