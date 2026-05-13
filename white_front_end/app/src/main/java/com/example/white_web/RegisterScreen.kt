package com.example.white_web

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

data class RegisterRequest(
    val username: String, val password: String, val phonenumber: String, val usertype: Int
)

data class RegisterResponse(
    val code: Int, val message: String, val data: Data?
) {
    data class Data(
        val token: String,
        val username: String,
    )
}

// ── Warm human icon ────────────────────────────────────────────────────────────
@Composable
fun UserGroupIcon(modifier: Modifier = Modifier, color: Color = NeonCyan) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(color = color, radius = w * 0.20f, center = Offset(w / 2, h * 0.32f))
        val bodyPath = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = w * 0.18f, top = h * 0.52f, right = w * 0.82f, bottom = h * 0.92f
                ),
                startAngleDegrees = 200f,
                sweepAngleDegrees = 140f,
                forceMoveTo = false
            )
        }
        drawPath(
            path = bodyPath,
            color = color,
            style = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        )
    }
}

// ── Warm car icon ──────────────────────────────────────────────────────────────
@Composable
fun CarIcon(modifier: Modifier = Modifier, color: Color = NeonPurple) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val bodyPath = Path().apply {
            moveTo(w * 0.18f, h * 0.60f)
            lineTo(w * 0.28f, h * 0.40f)
            cubicTo(w * 0.30f, h * 0.36f, w * 0.70f, h * 0.36f, w * 0.72f, h * 0.40f)
            lineTo(w * 0.82f, h * 0.60f)
            close()
        }
        drawPath(path = bodyPath, color = color)
        val windowPath = Path().apply {
            moveTo(w * 0.36f, h * 0.44f)
            lineTo(w * 0.40f, h * 0.40f)
            lineTo(w * 0.60f, h * 0.40f)
            lineTo(w * 0.64f, h * 0.44f)
        }
        drawPath(
            path = windowPath,
            color = Color.White.copy(alpha = 0.7f),
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        )
        drawCircle(color = Color(0xFF37474F), radius = w * 0.10f, center = Offset(w * 0.30f, h * 0.68f))
        drawCircle(color = Color(0xFF37474F), radius = w * 0.10f, center = Offset(w * 0.70f, h * 0.68f))
        drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.30f, h * 0.68f))
        drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.70f, h * 0.68f))
        // Warm amber headlight instead of neon cyan
        drawCircle(color = NeonCyan.copy(alpha = 0.9f), radius = w * 0.025f, center = Offset(w * 0.18f, h * 0.62f))
        drawCircle(color = Color.Red.copy(alpha = 0.7f), radius = w * 0.025f, center = Offset(w * 0.82f, h * 0.62f))
    }
}

@Composable
@Preview
fun RegisterPreview() {
    White_webTheme {
        val navController = rememberNavController()
        RegisterScreen(navController)
    }
}

@Composable
fun RegisterScreen(navController: NavHostController) {
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phoneNumber     by remember { mutableStateOf("") }
    var userType        by remember { mutableIntStateOf(1) }

    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background ────────────────────────────────────────────────────
        Image(
            painter = painterResource(id = R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0x66FFFFFF)))

        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "注  册",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp,
                    color = NeonCyan
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "创 建 新 账 户",
                style = MaterialTheme.typography.titleSmall.copy(
                    letterSpacing = 6.sp,
                    color = Color(0xFF8B6A50)
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // ── Form card ─────────────────────────────────────────────────
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
                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        placeholder = { Text("请输入至少6位用户名") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = scifiTextFieldColors()
                    )

                    // Phone
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("电话号码") },
                        placeholder = { Text("请输入11位手机号") },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = scifiTextFieldColors()
                    )

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        placeholder = { Text("请输入至少6位密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = scifiTextFieldColors()
                    )

                    // Confirm password
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        placeholder = { Text("请再次输入密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = scifiTextFieldColors()
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = GlowCyan30
                    )

                    // User type label
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "选择角色",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 3.sp,
                                color = NeonCyan.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // User type selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Passenger
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (userType == 1) Color(0x33E07830) else Color(0x0DE07830)
                                )
                                .border(
                                    1.dp,
                                    if (userType == 1) NeonCyan else GlowCyan30,
                                    RoundedCornerShape(10.dp)
                                )
                                .selectable(selected = userType == 1, onClick = { userType = 1 })
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = userType == 1,
                                    onClick = { userType = 1 },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NeonCyan,
                                        unselectedColor = GlowCyan30
                                    )
                                )
                                UserGroupIcon(
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                                    color = NeonCyan
                                )
                                Text(
                                    "乘客",
                                    color = if (userType == 1) NeonCyan else StarWhite,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Driver
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (userType == 2) Color(0x33CC6B7A) else Color(0x0DCC6B7A)
                                )
                                .border(
                                    1.dp,
                                    if (userType == 2) NeonPurple else NeonPurple.copy(0.3f),
                                    RoundedCornerShape(10.dp)
                                )
                                .selectable(selected = userType == 2, onClick = { userType = 2 })
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = userType == 2,
                                    onClick = { userType = 2 },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NeonPurple,
                                        unselectedColor = NeonPurple.copy(0.3f)
                                    )
                                )
                                CarIcon(
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                                    color = NeonPurple
                                )
                                Text(
                                    "司机",
                                    color = if (userType == 2) NeonPurple else StarWhite,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Register button ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(listOf(NeonCyan, NeonBlue, Color(0xFFC8703A)))
                    )
                    .clickable {
                        if (username.isBlank()) { Toast.makeText(context, "请输入用户名", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (phoneNumber.isBlank()) { Toast.makeText(context, "请输入电话号码", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (password.isBlank()) { Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (confirmPassword.isBlank()) { Toast.makeText(context, "请输入确认密码", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (username.length < 6) { Toast.makeText(context, "用户名至少需要6位", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (username.length > 20) { Toast.makeText(context, "用户名太长", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (phoneNumber.length != 11) { Toast.makeText(context, "电话号码必须为11位", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (!phoneNumber.all { it.isDigit() }) { Toast.makeText(context, "电话号码只能包含数字", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (password.length < 6) { Toast.makeText(context, "密码至少需要6位", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (password.length > 20) { Toast.makeText(context, "密码太长", Toast.LENGTH_SHORT).show(); return@clickable }
                        if (password != confirmPassword) { Toast.makeText(context, "两次密码不相同", Toast.LENGTH_SHORT).show(); return@clickable }
                        scope.launch {
                            try {
                                val response = APISERVICCE.register(
                                    RegisterRequest(
                                        username = username,
                                        password = password,
                                        phonenumber = phoneNumber,
                                        usertype = userType
                                    )
                                )
                                if (response.isSuccessful && response.body()?.code == 200) {
                                    USERNAME = response.body()?.data?.username
                                    TOKEN    = response.body()?.data?.token
                                    USERTYPE = userType
                                    Toast.makeText(context, "注册成功", Toast.LENGTH_SHORT).show()
                                    navController.navigate("home") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(context, response.body()?.message ?: "注册失败", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "注册失败，请检查网络连接: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "注  册",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        color = DeepSpace
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Back to login ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GlowCyan30, RoundedCornerShape(10.dp))
                    .background(Color(0x1AE07830))
                    .clickable {
                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "已有账号？返回登录",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = NeonCyan,
                        letterSpacing = 2.sp
                    )
                )
            }
        }
    }
}
