//package com.tongji.user
//
//import android.os.Bundle
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import com.tongji.user.network.UserInfo
//import com.tongji.user.network.UserService
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//// OwnerActivity.kt
//class OwnerActivity : AppCompatActivity() {
//    private lateinit var tvPhoneSuffix: TextView
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_owner)
//
//        // 初始化视图
//        tvPhoneSuffix = findViewById(R.id.tvPhoneSuffix) // 需要给尾号TextView添加id
//
//        // 加载用户数据
//        loadUserData()
//    }
//
//    private fun loadUserData() {
//        lifecycleScope.launch {
//            try {
//                // 从IO线程获取数据
//                val userInfo = withContext(Dispatchers.IO) {
//                    UserService.getUserInfo()
//                }
//
//                // 更新UI
//                updateUI(userInfo)
//            } catch (e: Exception) {
//                // 处理错误
//                Toast.makeText(this@OwnerActivity, "加载用户数据失败", Toast.LENGTH_SHORT).show()
//                tvPhoneSuffix.text = "尾号0713" // 默认显示
//            }
//        }
//    }
//
//    private fun updateUI(userInfo: UserInfo) {
//        // 提取手机号后4位
//        val phoneSuffix = if (userInfo.phone.length >= 4) {
//            userInfo.phone.substring(userInfo.phone.length - 4)
//        } else {
//            userInfo.phone
//        }
//
//        tvPhoneSuffix.text = "尾号$phoneSuffix"
//    }
//}

package com.tongji.user

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tongji.user.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OwnerActivity : AppCompatActivity() {
    private lateinit var tvPhoneSuffix: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner)
        tvPhoneSuffix = findViewById(R.id.tvPhoneSuffix)
        loadOwnerPhone()
    }

    private fun loadOwnerPhone() {
        tvPhoneSuffix.text = "加载中..." // 初始状态

        lifecycleScope.launch {
            try {
                val phone = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getUserInfo().phone
                }
                updatePhoneDisplay(phone)
            } catch (e: Exception) {
                Toast.makeText(
                    this@OwnerActivity,
                    "获取主人号码失败",
                    Toast.LENGTH_SHORT
                ).show()
                // 保持"加载中"状态不改变
            }
        }
    }

    private fun updatePhoneDisplay(fullPhone: String) {
        val suffix = if (fullPhone.length >= 4) {
            fullPhone.substring(fullPhone.length - 4)
        } else {
            fullPhone
        }
        tvPhoneSuffix.text = "尾号$suffix"
    }
}