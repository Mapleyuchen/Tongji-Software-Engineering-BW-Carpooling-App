////package com.tongji.user
////
////import android.os.Bundle
////import androidx.activity.enableEdgeToEdge
////import androidx.appcompat.app.AppCompatActivity
////import androidx.core.view.ViewCompat
////import androidx.core.view.WindowInsetsCompat
//
////class MainActivity : AppCompatActivity() {
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        enableEdgeToEdge()
////        setContentView(R.layout.activity_main)
////        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
////            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
////            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
////            insets
////        }
////    }
////}
//
//// MainActivity.kt
//package com.tongji.user
//
//////import android.R
////import com.tongji.user.R
//import android.os.Bundle
//import android.util.Log
//import androidx.appcompat.app.AppCompatActivity
//import com.tongji.user.databinding.ActivityProfileBinding
//import com.tongji.user.repository.UserRepository
//
//
//class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityProfileBinding  // 确保是 ActivityProfileBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityProfileBinding.inflate(layoutInflater)  // 确保 inflate 的是 ActivityProfileBinding
//        setContentView(binding.root)
//
//        // 获取数据并设置
//        val userRepository = UserRepository()
//        val userInfo = userRepository.getMockUserInfo()
//        Log.d("MainActivity", "Phone: ${userInfo.phone}") // 检查Logcat输出
//        binding.tvPhone.text = userInfo.phone  // 设置手机号
////        binding.tvPhone.text = userInfo.phone
////        binding.tvPhone.invalidate()  // 强制重绘
////        binding.tvPhone.requestLayout()  // 重新计算布局
//    }
//}
//
//class CouponsActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_coupons)
//    }
//}
////
//class OwnerActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_owner)
//    }
//}
// MainActivity.kt
//package com.tongji.user
//
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.isVisible
//import androidx.lifecycle.lifecycleScope
//import com.tongji.user.databinding.ActivityMainBinding
//import com.tongji.user.viewmodel.MainViewModel
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.launch
//
//@AndroidEntryPoint // 如果使用Hilt依赖注入
//class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMainBinding
//    private val viewModel: MainViewModel by viewModels()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // 初始化ViewBinding
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 初始化UI状态
//        setupLoadingState()
//
//        // 加载用户数据
//        loadUserData()
//
//        // 设置点击事件
//        setupClickListeners()
//    }
//
//    private fun setupLoadingState() {
//        binding.progressBar.isVisible = true
//        binding.contentGroup.isVisible = false
//    }
//
//    private fun loadUserData() {
//        lifecycleScope.launch {
//            try {
//                viewModel.loadUserData().collect { userInfo ->
//                    binding.progressBar.isVisible = false
//                    binding.contentGroup.isVisible = true
//
//                    // 更新UI
//                    with(binding) {
//                        tvPhone.text = formatPhoneNumber(userInfo.phone)
//                        tvMileage.text = "里程值 ${userInfo.mileage}/20"
//                        progressMileage.progress = userInfo.mileage
//                    }
//
//                    Log.d("MainActivity", "Data loaded: $userInfo")
//                }
//            } catch (e: Exception) {
//                Log.e("MainActivity", "Load failed", e)
//                binding.progressBar.isVisible = false
//                // 显示错误状态
//            }
//        }
//    }
//
//    private fun setupClickListeners() {
//        binding.apply {
//            btnProfile.setOnClickListener {
//                ProfileActivity.start(this@MainActivity)
//            }
//
//            btnOwner.setOnClickListener {
//                OwnerActivity.start(this@MainActivity)
//            }
//        }
//    }
//
//    private fun formatPhoneNumber(phone: String): String {
//        return if (phone.length > 7) phone.replaceRange(3..6, "****") else phone
//    }
//}