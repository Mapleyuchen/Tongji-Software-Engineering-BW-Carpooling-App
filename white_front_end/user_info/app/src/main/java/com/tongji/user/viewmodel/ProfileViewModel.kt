//package com.tongji.user.viewmodel
//
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import com.tongji.user.network.UserInfo
//import com.tongji.user.repository.UserRepository
//
//// ProfileViewModel.kt
//class ProfileViewModel(private val repo: UserRepository) : ViewModel() {
//    private val _userInfo = MutableLiveData<UserInfo>()
//    val userInfo: LiveData<UserInfo> = _userInfo
//
//    fun loadUserInfo(username: String) {
//        viewModelScope.launch {
//            _userInfo.value = repo.getUserInfo(username)
//        }
//    }
//}