package com.example.white_web

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.white_web.ui.theme.DeepSpace
import com.example.white_web.ui.theme.GlowCyan30
import com.example.white_web.ui.theme.NeonBlue
import com.example.white_web.ui.theme.NeonCyan
import com.example.white_web.ui.theme.NeonPurple
import com.example.white_web.ui.theme.StarWhite
import com.example.white_web.ui.theme.White_webTheme
import kotlinx.coroutines.launch

data class LoginRequest(
    val username: String, val password: String
)

data class LoginResponse(
    val code: Int, val message: String, val data: Data?
) {
    data class Data(
        val token: String,
        val username: String,
        val usertype: Int
    )
}

@Composable
@Preview
fun LoginPreview() {
    White_webTheme {
        val navController = rememberNavController()
        LoginScreen(navController)
    }
}

// ── Shared warm text-field color helper ──────────────────────────────────────
@Composable
fun scifiTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor      = Color(0x1AE07830),
    unfocusedContainerColor    = Color(0x0DE07830),
    focusedBorderColor         = NeonCyan,
    unfocusedBorderColor       = GlowCyan30,
    focusedLabelColor          = NeonCyan,
    unfocusedLabelColor        = Color(0xFF8B6A50),
    focusedTextColor           = StarWhite,
    unfocusedTextColor         = StarWhite,
    cursorColor                = NeonCyan,
    focusedLeadingIconColor    = NeonCyan,
    unfocusedLeadingIconColor  = GlowCyan30,
    focusedPlaceholderColor    = Color(0x808B6A50),
    unfocusedPlaceholderColor  = Color(0x608B6A50),
)

@Composable
fun LoginScreen(navController: NavHostController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Full-screen background image ──────────────────────────────
        Image(
            painter = painterResource(id = R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // ── 2. Warm light overlay for readability ─────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66FFFFFF))
        )

        // ── 3. Content ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title block
            Text(
                text = "同  济 · 星  途",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 10.sp,
                    color = NeonCyan
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "您 的 智 慧 拼 车 出 行 软 件",
                style = MaterialTheme.typography.titleSmall.copy(
                    letterSpacing = 6.sp,
                    color = Color(0xFF8B6A50)
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
                textAlign = TextAlign.Center
            )

            // ── Warm card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(NeonCyan.copy(alpha = 0.6f), NeonPurple.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF0FFFFFF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        placeholder = { Text("请输入至少6位用户名") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "用户名")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = scifiTextFieldColors()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        placeholder = { Text("请输入至少6位密码") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "密码")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = scifiTextFieldColors()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Warm gradient login button ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonCyan, NeonBlue, Color(0xFFC8703A))
                        )
                    )
                    .clickable {
                        if (username.isBlank()) {
                            Toast.makeText(context, "请输入用户名", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        if (password.isBlank()) {
                            Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        if (username.length < 6) {
                            Toast.makeText(context, "用户名长度至少6位", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        if (username.length > 20) {
                            Toast.makeText(context, "用户名太长", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        if (password.length < 6) {
                            Toast.makeText(context, "密码长度至少6位", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        if (password.length > 20) {
                            Toast.makeText(context, "密码太长", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        scope.launch {
                            try {
                                val response = APISERVICCE.login(LoginRequest(username, password))
                                if (response.isSuccessful && response.body()?.code == 200) {
                                    USERNAME = response.body()?.data?.username
                                    TOKEN    = response.body()?.data?.token
                                    USERTYPE = response.body()?.data?.usertype
                                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        response.body()?.message ?: "登录失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "登录失败，请检查网络连接：${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "登  录",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        color = DeepSpace
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Register link ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GlowCyan30, RoundedCornerShape(10.dp))
                    .background(Color(0x1AE07830))
                    .clickable {
                        navController.navigate("register") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有账号？注册",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = NeonCyan,
                        letterSpacing = 2.sp
                    )
                )
            }
        }
    }
}
