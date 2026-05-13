package com.tongji.user.network

// 必须添加这些导入
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

//interface UserService {
//    @GET("/api/user/{username}")  // 修正URL格式，使用花括号包裹路径参数
//    suspend fun getUserInfo(@Path("username") username: String): Response<UserInfo>
//
//    @PUT("user/{userId}")  // 修正URL格式
//    suspend fun updateUserInfo(
//        @Path("userId") userId: String,
//        @Body user: UserInfo  // 这里需要UserInfo数据类
//    ): Response<Unit>  // 修正返回值类型
//}
// UserService.kt
object UserService {
    // 模拟从后端获取用户数据
    fun getUserInfo(): UserInfo {
        // 这里模拟网络请求延迟
        Thread.sleep(500)
        return UserInfo(
            phone = "15712340713",
            estimatedLoan = 1
        )
    }
}