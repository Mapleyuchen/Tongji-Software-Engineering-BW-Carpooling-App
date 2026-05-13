//package com.tongji.user.repository
//
//import com.tongji.user.network.RetrofitClient
//import com.tongji.user.network.UserInfo
//import com.tongji.user.network.UserService
//import retrofit2.Response
//
//class UserRepository {
//    // 真实网络请求
//    private val service = RetrofitClient.instance.create(UserService::class.java)
//
//    suspend fun getUserInfo(username: String): UserInfo {
//        val response = service.getUserInfo(username)
//        if (!response.isSuccessful) throw Exception("API Error")
//        return response.body() ?: throw Exception("Data is null")
//    }
//
////    // 临时测试数据
////    fun getMockUserInfo(): UserInfo {
////        return UserInfo(
////            phone = "157****713",  // 这是模拟数据中的手机号
////            avatarUrl = null,
////            mileageCurrent = 15,
////            mileageTotal = 20,
////            couponsCount = 1,
////            travelCardsCount = 4,
////            welfareFund = 0.0,
////            estimatedLoan = 9.68
////        )
////    }
//}