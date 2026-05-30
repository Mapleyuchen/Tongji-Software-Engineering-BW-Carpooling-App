package com.example.white_web.chat

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.white_web.R
import com.example.white_web.USERNAME
import com.example.white_web.chat.local.LocalConversationEntity
import com.example.white_web.chat.local.LocalMessageEntity
import com.example.white_web.chat.local.LocalOrderChatCacheEntity
import com.example.white_web.chat.socket.ChatSocketManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


private val ChatPageBackground = Color(0xFFFFF9F5)
private val ChatContentBackground = Color(0x80FFFFFF)
private val ChatAccent = Color(0xFFE07830)
private val ChatBubble = Color(0xFFFEE8D2)
private val MutedText = Color(0x99000000)
private const val CONVERSATION_CLOSED = 1
private const val SEND_TIMEOUT_MS = 5000L


data class ChatMessageUiState(
    val conversation: LocalConversationEntity? = null,
    val order: LocalOrderChatCacheEntity? = null,
    val messages: List<LocalMessageEntity> = emptyList(),
    val loadingFailed: Boolean = false
)


private sealed class MessageDisplayRow {
    data class Timestamp(val text: String, val key: String) : MessageDisplayRow()
    data class Message(val message: LocalMessageEntity) : MessageDisplayRow()
}


class ChatMessageViewModel(
    application: Application,
    private val conversationId: Int
) : AndroidViewModel(application) {
    private val repository = ChatRepository(application.applicationContext)

    val uiState: StateFlow<ChatMessageUiState> =
        combine(
            repository.observeConversation(conversationId),
            repository.observeOrderCache(conversationId)
        ) { conversation, order ->
            conversation to order
        }.combine(repository.observeMessages(conversationId)) { pair, messages ->
            ChatMessageUiState(
                conversation = pair.first,
                order = pair.second,
                messages = messages
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatMessageUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                repository.refreshConversationDetail(conversationId)
                repository.refreshMessages(conversationId)
            }
        }
    }

    fun send(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val pending = repository.createPendingTextMessage(conversationId, trimmed)
            runCatching {
                withTimeout(SEND_TIMEOUT_MS) {
                    repository.submitTextMessage(
                        conversationId = conversationId,
                        content = trimmed,
                        clientMsgId = pending.clientMsgId.orEmpty()
                    )
                }
            }.onFailure {
                pending.clientMsgId?.let { clientMsgId ->
                    repository.markMessageUnknown(conversationId, clientMsgId)
                }
            }
        }
    }

    fun retry(message: LocalMessageEntity) {
        val clientMsgId = message.clientMsgId ?: return
        viewModelScope.launch {
            repository.markMessageSending(conversationId, clientMsgId)
            runCatching {
                withTimeout(SEND_TIMEOUT_MS) {
                    repository.submitTextMessage(
                        conversationId = conversationId,
                        content = message.content,
                        clientMsgId = clientMsgId
                    )
                }
            }.onFailure {
                repository.markMessageUnknown(conversationId, clientMsgId)
            }
        }
    }
}


@Composable
fun ChatMessageScreen(
    navController: NavHostController,
    conversationId: Int
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ChatMessageViewModel = viewModel(
        key = "chat-message-$conversationId",
        factory = chatMessageViewModelFactory(application, conversationId)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(conversationId) {
        ChatSocketManager.connect(context)
        ChatSocketManager.joinConversation(context, conversationId)
        viewModel.refresh()
    }
    DisposableEffect(conversationId) {
        onDispose {
            ChatSocketManager.leaveConversation(context, conversationId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatPageBackground)
    ) {
        ChatOrderTopBar(
            order = uiState.order,
            onBack = { navController.popBackStack() }
        )

        MessageList(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ChatContentBackground),
            messages = uiState.messages,
            onRetry = viewModel::retry
        )

        if (uiState.conversation?.status == CONVERSATION_CLOSED) {
            ClosedInputBar()
        } else {
            MessageInputBar(onSend = viewModel::send)
        }
    }
}


private fun chatMessageViewModelFactory(
    application: Application,
    conversationId: Int
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatMessageViewModel(application, conversationId) as T
        }
    }
}


@Composable
private fun ChatOrderTopBar(
    order: LocalOrderChatCacheEntity?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, top = 10.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = ChatAccent
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_chat_location),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = order?.departure ?: "",
                    color = ChatAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_chat_arrow_right),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(18.dp)
                )
                Text(
                    text = order?.destination ?: "",
                    color = ChatAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_chat_calendar),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = order?.let { formatOrderDateTime(it) } ?: "",
                    color = MutedText,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_chat_person),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${order?.passengerCount ?: 0}/4人",
                    color = MutedText,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(10.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_chat_car),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = order?.driver ?: "司机未接单",
                    color = MutedText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = {}, modifier = Modifier.size(42.dp)) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "群聊成员",
                tint = ChatAccent
            )
        }
    }
}


@Composable
private fun MessageList(
    modifier: Modifier,
    messages: List<LocalMessageEntity>,
    onRetry: (LocalMessageEntity) -> Unit
) {
    val rows = remember(messages) { buildDisplayRows(messages) }
    val listState = rememberLazyListState()

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            listState.animateScrollToItem(rows.lastIndex)
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 10.dp),
        state = listState
    ) {
        items(
            items = rows,
            key = { row ->
                when (row) {
                    is MessageDisplayRow.Timestamp -> row.key
                    is MessageDisplayRow.Message -> "message-${row.message.localId}"
                }
            }
        ) { row ->
            when (row) {
                is MessageDisplayRow.Timestamp -> TimestampRow(row.text)
                is MessageDisplayRow.Message -> MessageRow(
                    message = row.message,
                    onRetry = onRetry
                )
            }
        }
    }
}


@Composable
private fun TimestampRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = MutedText, fontSize = 14.sp)
    }
}


@Composable
private fun SystemMessageRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MutedText,
            fontSize = 16.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun MessageRow(
    message: LocalMessageEntity,
    onRetry: (LocalMessageEntity) -> Unit
) {
    if (message.messageType == ChatRepository.MESSAGE_TYPE_SYSTEM_NOTICE) {
        SystemMessageRow(message.content)
        return
    }

    val isMine = message.senderUsername == USERNAME
    if (isMine) {
        MyMessageRow(message = message, onRetry = onRetry)
    } else {
        OtherMessageRow(message = message)
    }
}


@Composable
private fun OtherMessageRow(message: LocalMessageEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        ChatAvatar(username = message.senderUsername)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = message.senderUsername ?: "",
                color = MutedText,
                fontSize = 14.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(5.dp))
            BubbleWithTail(
                text = message.content,
                mine = false
            )
        }
    }
}


@Composable
private fun MyMessageRow(
    message: LocalMessageEntity,
    onRetry: (LocalMessageEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.sendStatus == ChatRepository.SEND_STATUS_UNKNOWN ||
                    message.sendStatus == ChatRepository.SEND_STATUS_FAILED
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_chat_warning),
                        contentDescription = "重发",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onRetry(message) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                BubbleWithTail(
                    text = message.content,
                    mine = true
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        ChatAvatar(username = USERNAME)
    }
}


@Composable
private fun ChatAvatar(username: String?) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(ChatBubble)
            .border(1.dp, ChatAccent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = username?.take(1)?.ifBlank { "?" } ?: "?",
            color = ChatAccent,
            fontSize = 24.sp
        )
    }
}


@Composable
private fun BubbleWithTail(
    text: String,
    mine: Boolean
) {
    val tailWidth = 10.dp
    val tailHeight = 18.dp
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (!mine) {
            BubbleTail(mine = false, modifier = Modifier.size(tailWidth, tailHeight))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ChatBubble)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = Color.Black,
                fontSize = 17.sp,
                lineHeight = 23.sp
            )
        }
        if (mine) {
            BubbleTail(mine = true, modifier = Modifier.size(tailWidth, tailHeight))
        }
    }
}


@Composable
private fun BubbleTail(mine: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier) {
        if (mine) {
            drawRightTail()
        } else {
            drawLeftTail()
        }
    }
}


private fun DrawScope.drawLeftTail() {
    val path = Path().apply {
        moveTo(size.width, size.height / 2f)
        lineTo(0f, size.height)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, ChatBubble)
}


private fun DrawScope.drawRightTail() {
    val path = Path().apply {
        moveTo(0f, size.height / 2f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, ChatBubble)
}


@Composable
private fun MessageInputBar(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val canSend = text.trim().isNotEmpty()
    val density = LocalDensity.current
    val keyboardOffset = with(density) {
        val imeBottom = WindowInsets.ime.getBottom(this)
        val navBottom = WindowInsets.navigationBars.getBottom(this)
        maxOf(0, imeBottom - navBottom).toDp()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = -keyboardOffset)
            .background(ChatPageBackground)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 88.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, ChatAccent, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                maxLines = 3
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (canSend) ChatAccent else ChatAccent.copy(alpha = 0.45f))
                .clickable(enabled = canSend) {
                    val content = text
                    text = ""
                    onSend(content)
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_chat_send),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "发送", color = Color.White, fontSize = 16.sp)
        }
    }
}


@Composable
private fun ClosedInputBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(ChatPageBackground)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "无法在已关闭的群聊会话中发送消息",
            color = MutedText,
            fontSize = 16.sp
        )
    }
}


private fun buildDisplayRows(messages: List<LocalMessageEntity>): List<MessageDisplayRow> {
    val rows = mutableListOf<MessageDisplayRow>()
    var previousConfirmedTime: LocalDateTime? = null

    messages.forEach { message ->
        val messageTime = if (message.seq != null) parseDateTime(message.createdAt) else null
        if (messageTime != null) {
            val shouldShowTime = previousConfirmedTime == null ||
                Duration.between(previousConfirmedTime, messageTime).toMinutes() > 2
            if (shouldShowTime) {
                rows += MessageDisplayRow.Timestamp(
                    text = formatMessageTimestamp(messageTime),
                    key = "timestamp-${message.localId}-${message.createdAt}"
                )
            }
            previousConfirmedTime = messageTime
        }
        rows += MessageDisplayRow.Message(message)
    }
    return rows
}


private fun formatOrderDateTime(order: LocalOrderChatCacheEntity): String {
    val date = runCatching { LocalDate.parse(order.date) }.getOrNull()
    val earliest = order.earliestDepartureTime?.take(5).orEmpty()
    val latest = order.latestDepartureTime?.take(5).orEmpty()
    if (date == null) return "$earliest-$latest"

    val now = LocalDate.now()
    return if (date.year == now.year && date.month == now.month) {
        "${date.dayOfMonth}日$earliest-$latest"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}


private fun parseDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toLocalDateTime() }
        .getOrElse {
            runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME) }
                .getOrNull()
        }
}


private fun formatMessageTimestamp(time: LocalDateTime): String {
    val today = LocalDate.now()
    val date = time.toLocalDate()
    val hm = "%02d:%02d".format(time.hour, time.minute)
    if (date == today) return hm
    if (date == today.minusDays(1)) return "昨天 $hm"

    val startOfWeek = today.with(DayOfWeek.MONDAY)
    if (!date.isBefore(startOfWeek)) {
        val dayText = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
        return "$dayText $hm"
    }

    return "${date.year}-${date.monthValue}-${date.dayOfMonth} $hm"
}
