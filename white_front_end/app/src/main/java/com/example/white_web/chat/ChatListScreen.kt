package com.example.white_web.chat

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.white_web.R
import com.example.white_web.USERNAME
import com.example.white_web.chat.local.LocalConversationWithOrder
import com.example.white_web.chat.local.LocalMessageEntity
import com.example.white_web.chat.local.LocalOrderChatCacheEntity
import com.example.white_web.chat.socket.ChatSocketManager
import com.example.white_web.ui.theme.NeonCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt


private val PageBackground = Color(0xFFFFF9F5)
private val ContentBackground = Color(0x80FFFFFF)
private val ChatOrange = Color(0xFFE07830)
private val ChatPaleOrange = Color(0xFFFEE8D2)
private val DeleteRed = Color(0xFFE53935)

private const val ORDER_STATUS_NOT_STARTED = 0
private const val ORDER_STATUS_IN_PROGRESS = 1
private const val ORDER_STATUS_COMPLETED = 2
private const val CONVERSATION_OPEN = 0
private const val CONVERSATION_CLOSED = 1


enum class ChatListFilter(
    val title: String,
    val iconRes: Int
) {
    CURRENT("当前订单", R.drawable.ic_chat_location),
    NOT_STARTED("未开始订单", R.drawable.ic_chat_clipboard),
    HISTORY("历史订单", R.drawable.ic_chat_clock)
}


data class ChatConversationListItem(
    val row: LocalConversationWithOrder,
    val latestMessage: LocalMessageEntity?
)


class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application.applicationContext)

    private val selectedFilter = MutableStateFlow(ChatListFilter.CURRENT)
    private val loadFailed = MutableStateFlow(false)
    private val refreshNonce = MutableStateFlow(0)

    val selectedFilterState: StateFlow<ChatListFilter> = selectedFilter
    val loadFailedState: StateFlow<Boolean> = loadFailed
    val refreshNonceState: StateFlow<Int> = refreshNonce

    val visibleItems: StateFlow<List<ChatConversationListItem>> =
        combine(repository.observeVisibleConversationRows(), selectedFilter) { rows, filter ->
            filterRows(rows, filter).map { row ->
                ChatConversationListItem(
                    row = row,
                    latestMessage = row.messages
                        .filter { it.seq > row.conversation.clearBeforeSeq && it.seq > 0 }
                        .maxByOrNull { it.seq }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSelected()
    }

    fun selectFilter(filter: ChatListFilter) {
        if (selectedFilter.value == filter) {
            refreshSelected()
            return
        }
        selectedFilter.value = filter
        loadFailed.value = false
        refreshSelected()
    }

    fun refreshSelected() {
        refreshNonce.value += 1
        viewModelScope.launch {
            runCatching {
                when (selectedFilter.value) {
                    ChatListFilter.CURRENT,
                    ChatListFilter.NOT_STARTED,
                    ChatListFilter.HISTORY -> repository.refreshAllConversations()
                }
            }.onSuccess {
                loadFailed.value = false
            }.onFailure {
                loadFailed.value = true
            }
        }
    }

    fun clearConversation(conversationId: Int) {
        viewModelScope.launch {
            runCatching { repository.clearHistory(conversationId) }
                .onFailure { loadFailed.value = true }
        }
    }

    fun hideClosedConversation(conversationId: Int) {
        viewModelScope.launch {
            runCatching { repository.hideClosedConversation(conversationId) }
                .onFailure { loadFailed.value = true }
        }
    }

    fun hideAllClosedConversations() {
        viewModelScope.launch {
            runCatching { repository.hideAllClosedConversations() }
                .onSuccess { loadFailed.value = false }
                .onFailure { loadFailed.value = true }
        }
    }

    private fun filterRows(
        rows: List<LocalConversationWithOrder>,
        filter: ChatListFilter
    ): List<LocalConversationWithOrder> {
        val rowsWithOrder = rows.filter { it.order != null }
        return when (filter) {
            ChatListFilter.CURRENT -> currentConversation(rowsWithOrder)?.let { listOf(it) }.orEmpty()
            ChatListFilter.NOT_STARTED -> rowsWithOrder
                .filter { it.order?.orderStatus == ORDER_STATUS_NOT_STARTED }
                .sortedBy { orderStartEpoch(it.order) }
            ChatListFilter.HISTORY -> rowsWithOrder
                .filter {
                    it.order?.orderStatus == ORDER_STATUS_COMPLETED &&
                        it.conversation.status == CONVERSATION_CLOSED
                }
                .sortedByDescending { parseMessageTime(it.conversation.lastMessageTime) ?: LocalDateTime.MIN }
        }
    }

    private fun currentConversation(rows: List<LocalConversationWithOrder>): LocalConversationWithOrder? {
        rows.firstOrNull {
            it.order?.orderStatus == ORDER_STATUS_COMPLETED &&
                it.conversation.status == CONVERSATION_OPEN
        }?.let { return it }

        rows.firstOrNull {
            it.order?.orderStatus == ORDER_STATUS_IN_PROGRESS
        }?.let { return it }

        return rows
            .filter { it.order?.orderStatus == ORDER_STATUS_NOT_STARTED }
            .minByOrNull { orderStartEpoch(it.order) }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(navController: NavHostController, viewModel: ChatListViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedFilter by viewModel.selectedFilterState.collectAsState()
    val items by viewModel.visibleItems.collectAsState()
    val loadFailed by viewModel.loadFailedState.collectAsState()
    val refreshNonce by viewModel.refreshNonceState.collectAsState()

    LaunchedEffect(Unit) {
        ChatSocketManager.connect(context)
        viewModel.refreshSelected()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("消息") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshSelected() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PageBackground,
                    titleContentColor = NeonCyan,
                    navigationIconContentColor = NeonCyan,
                    actionIconContentColor = NeonCyan
                )
            )
        },
        containerColor = PageBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ContentBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChatFilterTopBar(
                    selectedFilter = selectedFilter,
                    onSelected = viewModel::selectFilter
                )

                ConversationDisplayColumn(
                    modifier = Modifier.weight(1f),
                    selectedFilter = selectedFilter,
                    items = items,
                    loadFailed = loadFailed,
                    refreshNonce = refreshNonce,
                    onClear = { viewModel.clearConversation(it) },
                    onHide = { viewModel.hideClosedConversation(it) },
                    onHideAllHistory = { viewModel.hideAllClosedConversations() }
                )
            }
        }
    }
}


@Composable
private fun ChatFilterTopBar(
    selectedFilter: ChatListFilter,
    onSelected: (ChatListFilter) -> Unit
) {
    val selectedIndex = ChatListFilter.entries.indexOf(selectedFilter)
    val markerPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        label = "chat-filter-marker"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
    ) {
        val markerWidth = 100.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChatListFilter.entries.forEach { filter ->
                    FilterSelector(
                        modifier = Modifier.weight(1f),
                        filter = filter,
                        selected = filter == selectedFilter,
                        onClick = { onSelected(filter) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val markerWidthPx = markerWidth.roundToPx()
                            val markerHeightPx = 4.dp.roundToPx()
                            val placeable = measurable.measure(
                                Constraints.fixed(markerWidthPx, markerHeightPx)
                            )
                            val columnWidth = constraints.maxWidth / 3f
                            val x = (columnWidth * (markerPosition + 0.5f) - placeable.width / 2f)
                                .roundToInt()
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(x, 0)
                            }
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(ChatOrange)
                )
            }
        }
    }
}


@Composable
private fun FilterSelector(
    modifier: Modifier,
    filter: ChatListFilter,
    selected: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (selected) 1f else 0.6f
    val iconOffset = remember { Animatable(0f) }
    var bounceTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(bounceTrigger) {
        if (bounceTrigger > 0) {
            iconOffset.snapTo(0f)
            iconOffset.animateTo(-7f, animationSpec = tween(durationMillis = 90))
            iconOffset.animateTo(0f, animationSpec = tween(durationMillis = 120))
        }
    }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                bounceTrigger += 1
                onClick()
            }
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ChatPaleOrange),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = filter.iconRes),
                contentDescription = filter.title,
                modifier = Modifier
                    .offset(y = iconOffset.value.dp)
                    .size(38.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = filter.title,
            color = Color.Black,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}


@Composable
private fun ConversationDisplayColumn(
    modifier: Modifier,
    selectedFilter: ChatListFilter,
    items: List<ChatConversationListItem>,
    loadFailed: Boolean,
    refreshNonce: Int,
    onClear: (Int) -> Unit,
    onHide: (Int) -> Unit,
    onHideAllHistory: () -> Unit
) {
    var deleteAllConfirming by remember(selectedFilter, loadFailed, refreshNonce) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedFilter == ChatListFilter.HISTORY) {
            item {
                DeleteAllButton(
                    confirming = deleteAllConfirming,
                    onClick = {
                        if (deleteAllConfirming) {
                            onHideAllHistory()
                            deleteAllConfirming = false
                        } else {
                            deleteAllConfirming = true
                        }
                    }
                )
            }
        }

        items(
            items = items,
            key = { "${selectedFilter.name}:$refreshNonce:${it.row.conversation.conversationId}" }
        ) { item ->
            ConversationItem(
                item = item,
                selectedFilter = selectedFilter,
                resetKey = refreshNonce,
                onClear = onClear,
                onHide = onHide
            )
        }

        item {
            TerminalText(text = if (loadFailed) "网络加载失败" else "没有更多消息了")
        }
    }
}


@Composable
private fun DeleteAllButton(
    confirming: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_chat_delete),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (confirming) "确认？" else "删除所有历史订单对话",
            color = Color.Red,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}


@Composable
private fun ConversationItem(
    item: ChatConversationListItem,
    selectedFilter: ChatListFilter,
    resetKey: Int,
    onClear: (Int) -> Unit,
    onHide: (Int) -> Unit
) {
    val density = LocalDensity.current
    val targetWidth = 132.dp
    val targetWidthPx = with(density) { targetWidth.toPx() }
    val itemStateKey = "${selectedFilter.name}:$resetKey:${item.row.conversation.conversationId}"
    var deleteWidthPx by remember(itemStateKey) { mutableFloatStateOf(0f) }
    var confirming by remember(itemStateKey) { mutableStateOf(false) }
    val deleteWidth = with(density) { deleteWidthPx.toDp() }
    val lockedOpen = deleteWidthPx >= targetWidthPx - 1f
    val actionText = if (selectedFilter == ChatListFilter.HISTORY) "删除对话" else "清除对话记录"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(itemStateKey) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        deleteWidthPx = (deleteWidthPx - dragAmount).coerceIn(0f, targetWidthPx)
                    },
                    onDragEnd = {
                        val shouldOpen = deleteWidthPx > targetWidthPx * 0.45f
                        deleteWidthPx = if (shouldOpen) targetWidthPx else 0f
                        if (!shouldOpen) confirming = false
                    },
                    onDragCancel = {
                        val shouldOpen = deleteWidthPx > targetWidthPx * 0.45f
                        deleteWidthPx = if (shouldOpen) targetWidthPx else 0f
                        if (!shouldOpen) confirming = false
                    }
                )
            }
    ) {
        ConversationBox(
            modifier = Modifier
                .offset(x = -deleteWidth)
                .fillMaxWidth(),
            item = item
        )

        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(deleteWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(DeleteRed)
                    .clickable(enabled = lockedOpen) {
                        if (confirming) {
                            if (selectedFilter == ChatListFilter.HISTORY) {
                                onHide(item.row.conversation.conversationId)
                            } else {
                                onClear(item.row.conversation.conversationId)
                            }
                            deleteWidthPx = 0f
                            confirming = false
                        } else {
                            confirming = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (confirming) "确认？" else actionText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}


@Composable
private fun ConversationBox(
    modifier: Modifier,
    item: ChatConversationListItem
) {
    val order = item.row.order ?: return
    val conversation = item.row.conversation
    val latestMessage = item.latestMessage

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConversationCover(driver = order.driver)
            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.Top
            ) {
                OriginDestinationRow(order)
                Spacer(modifier = Modifier.height(5.dp))
                DetailRow(order)
                Spacer(modifier = Modifier.height(5.dp))
                LastMessageRow(
                    modifier = Modifier.height(35.dp),
                    latestMessage = latestMessage,
                    fallbackContent = conversation.lastMessageContent,
                    fallbackTime = conversation.lastMessageTime,
                    unread = conversation.unreadCount > 0
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ChatOrange.copy(alpha = 0.65f))
        )
    }
}


@Composable
private fun ConversationCover(driver: String?) {
    val text = driver?.firstOrNull()?.toString() ?: "司"
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(ChatPaleOrange)
            .border(0.8.dp, ChatOrange, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = ChatOrange,
            fontSize = 28.sp,
            maxLines = 1
        )
    }
}


@Composable
private fun OriginDestinationRow(order: LocalOrderChatCacheEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallIcon(R.drawable.ic_chat_location)
        RouteText(order.departure)
        SmallIcon(R.drawable.ic_chat_arrow_right, modifier = Modifier.padding(horizontal = 4.dp))
        RouteText(order.destination)
    }
}


@Composable
private fun DetailRow(order: LocalOrderChatCacheEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallIcon(R.drawable.ic_chat_calendar, size = 16)
        DetailText(
            text = formatOrderDisplayTime(order.date, order.earliestDepartureTime, order.latestDepartureTime),
            modifier = Modifier.weight(1.25f)
        )
        SmallIcon(R.drawable.ic_chat_person, size = 16)
        DetailText(
            text = "${order.passengerCount}/4人",
            modifier = Modifier.weight(0.55f)
        )
        SmallIcon(R.drawable.ic_chat_car, size = 16)
        DetailText(
            text = order.driver ?: "司机未接单",
            modifier = Modifier.weight(0.95f)
        )
    }
}


@Composable
private fun LastMessageRow(
    modifier: Modifier = Modifier,
    latestMessage: LocalMessageEntity?,
    fallbackContent: String?,
    fallbackTime: String?,
    unread: Boolean
) {
    val preview = buildMessagePreview(latestMessage, fallbackContent)
    val time = latestMessage?.createdAt ?: fallbackTime

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = preview,
                color = Color.Black.copy(alpha = 0.48f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(
            modifier = Modifier.width(66.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = formatMessageDisplayTime(time),
                color = Color.Black.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (unread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                }
            }
        }
    }
}


@Composable
private fun SmallIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
    size: Int = 18
) {
    Image(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        modifier = modifier.size(size.dp)
    )
}


@Composable
private fun RouteText(text: String) {
    Text(
        text = text,
        color = ChatOrange,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 5.dp)
    )
}


@Composable
private fun DetailText(
    text: String,
    modifier: Modifier
) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 4.dp)
    )
}


@Composable
private fun TerminalText(text: String) {
    Text(
        text = text,
        color = ChatOrange,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 28.dp)
    )
}


private fun buildMessagePreview(
    message: LocalMessageEntity?,
    fallbackContent: String?
): String {
    val raw = if (message == null) {
        fallbackContent.orEmpty()
    } else if (
        message.messageType == ChatRepository.MESSAGE_TYPE_SYSTEM_NOTICE ||
        message.senderUsername.isNullOrBlank() ||
        message.senderUsername == USERNAME
    ) {
        message.content
    } else {
        "${message.senderUsername}：${message.content}"
    }
    return raw
}


private fun formatOrderDisplayTime(
    date: String?,
    earliest: String?,
    latest: String?
): String {
    val orderDate = parseOrderDate(date) ?: return ""
    val now = LocalDate.now()
    return if (orderDate.year == now.year && orderDate.month == now.month) {
        "${orderDate.dayOfMonth}日${shortTime(earliest)}-${shortTime(latest)}"
    } else {
        "${orderDate.year}年${orderDate.monthValue}月${orderDate.dayOfMonth}日"
    }
}


private fun formatMessageDisplayTime(value: String?): String {
    val time = parseMessageTime(value) ?: return ""
    val today = LocalDate.now()
    val date = time.toLocalDate()
    return when {
        date == today -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> "${date.monthValue}-${date.dayOfMonth}"
        else -> "${date.year}-${date.monthValue}-${date.dayOfMonth}"
    }
}


private fun shortTime(value: String?): String {
    return value?.take(5).orEmpty()
}


private fun parseOrderDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        LocalDate.parse(value.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()
}


private fun parseMessageTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    val normalized = value.replace(" ", "T")
    return runCatching { LocalDateTime.parse(normalized) }
        .recoverCatching { OffsetDateTime.parse(normalized).toLocalDateTime() }
        .getOrNull()
}


private fun orderStartEpoch(order: LocalOrderChatCacheEntity?): Long {
    val date = parseOrderDate(order?.date) ?: return Long.MAX_VALUE
    val timeText = order?.earliestDepartureTime?.take(5) ?: return date.toEpochDay() * 24 * 60
    val time = runCatching {
        LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(LocalTime.MIN)
    return date.toEpochDay() * 24 * 60 + time.hour * 60L + time.minute
}
