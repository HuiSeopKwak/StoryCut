package com.ssafy.storycut.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.ssafy.storycut.data.local.datastore.TokenManager
import kotlinx.coroutines.flow.first
import android.util.Log
import kotlinx.coroutines.delay

private const val TAG = "RoomVideoDetailScreen"

@UnstableApi
@Composable
fun RoomVideoDetailScreen(
    roomId: String,
    videoId: String,
    navController: NavController,
    roomViewModel: RoomViewModel = hiltViewModel(),
    tokenManager: TokenManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val roomVideos by roomViewModel.roomVideos.collectAsState()

    // 비디오 업로더 정보 상태 수집
    val uploaderInfo by roomViewModel.videoUploaderInfo.collectAsState()
    val isUploaderInfoLoading by roomViewModel.isUploaderInfoLoading.collectAsState()
    val uploaderInfoError by roomViewModel.uploaderInfoError.collectAsState()

    // 상태 변수들
    var isInitialLoading by remember { mutableStateOf(true) } // 초기 로딩 (데이터 로드 전)
    var error by remember { mutableStateOf<String?>(null) }
    var isExiting by remember { mutableStateOf(false) }
    var targetIndex by remember { mutableStateOf(-1) } // 대상 비디오 인덱스
    var initializationComplete by remember { mutableStateOf(false) } // 완전한 초기화 완료 여부

    // 플레이어 맵
    val players = remember { mutableMapOf<Int, ExoPlayer>() }
    var appInBackground by remember { mutableStateOf(false) }

    // 로깅
    LaunchedEffect(Unit) {
        Log.d(TAG, "화면 초기화 시작: roomId=$roomId, videoId=$videoId")
    }

    // 먼저 데이터를 완전히 로드하고 대상 인덱스를 찾는 효과
    LaunchedEffect(Unit) {
        isInitialLoading = true
        try {
            Log.d(TAG, "데이터 로드 시작")
            val token = tokenManager.accessToken.first()
            if (token == null) {
                error = "인증 정보가 없습니다. 다시 로그인해주세요."
                isInitialLoading = false
                return@LaunchedEffect
            }

            // 비디오 상세 정보 조회 (이 부분 추가)
            roomViewModel.getVideoDetail(videoId)

            // 비디오 목록 로드 및 대기
            if (roomVideos.isEmpty()) {
                Log.d(TAG, "비디오 목록 로드 시작")
                roomViewModel.getRoomVideos(roomId)

                // 비디오 목록 로드 대기
                var attempts = 0
                val maxAttempts = 10  // 더 긴 대기 시간 허용

                while (roomVideos.isEmpty() && attempts < maxAttempts) {
                    delay(300)
                    attempts++
                    Log.d(TAG, "비디오 목록 로드 대기 중... ($attempts/$maxAttempts)")
                }
            }

            // 목록 로드 후 인덱스 확인
            if (roomVideos.isNotEmpty()) {
                Log.d(TAG, "비디오 목록 로드 완료: ${roomVideos.size}개 항목")

                // 대상 비디오 인덱스 찾기
                targetIndex = roomVideos.indexOfFirst { it.id.toString() == videoId }
                Log.d(TAG, "대상 비디오 인덱스: $targetIndex (videoId=$videoId)")

                if (targetIndex >= 0) {
                    val video = roomVideos[targetIndex]
                    Log.d(TAG, "대상 비디오 찾음: id=${video.id}, title=${video.title}")
                } else {
                    Log.w(TAG, "대상 비디오를 찾지 못함: videoId=$videoId")
                    error = "요청한 비디오를 찾을 수 없습니다."
                }
            } else {
                Log.w(TAG, "비디오 목록을 로드하지 못했거나 비어 있습니다.")
                error = "비디오 목록을 불러올 수 없습니다."
            }

        } catch (e: Exception) {
            Log.e(TAG, "데이터 로드 오류", e)
            error = "비디오를 로드할 수 없습니다: ${e.message}"
        } finally {
            // 로딩 완료 - 이제 UI 표시 가능
            isInitialLoading = false
            initializationComplete = true

            Log.d(TAG, "초기화 완료: targetIndex=$targetIndex, error=$error")
        }
    }

    // 페이지 변경 시 해당 비디오의 업로더 정보 로드
    val pagerState = if (initializationComplete && targetIndex >= 0) {
        rememberPagerState(initialPage = targetIndex) { roomVideos.size }
    } else {
        // 초기화가 안되었거나 타겟을 못 찾은 경우 더미 상태
        rememberPagerState(initialPage = 0) { 1 }
    }

    // 페이지가 변경될 때 해당 비디오의 업로더 정보를 가져오는 효과
    LaunchedEffect(pagerState.currentPage, initializationComplete) {
        if (initializationComplete && pagerState.currentPage >= 0 && pagerState.currentPage < roomVideos.size) {
            val currentVideo = roomVideos[pagerState.currentPage]
            roomViewModel.getVideoDetail(currentVideo.id.toString())
        }
    }

    // 뒤로가기 처리
    val handleBackPress = {
        if (!isExiting) {
            Log.d(TAG, "뒤로가기 처리")
            isExiting = true

            // 플레이어 해제
            players.values.forEach { player ->
                try {
                    player.stop()
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "플레이어 해제 오류", e)
                }
            }
            players.clear()

            // 비디오 상세 및 업로더 정보 상태 초기화
            roomViewModel.clearVideoDetail()

            // 화면 전환
            navController.popBackStack()
        }
    }

    // 생명주기 관리
    DisposableEffect(lifecycleOwner) {
        val activity = context as? androidx.activity.ComponentActivity
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }

        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                appInBackground = true
                players.values.forEach { player ->
                    if (player.isPlaying) player.pause()
                }
            }

            override fun onResume(owner: LifecycleOwner) {
                if (appInBackground) {
                    appInBackground = false
                    players[pagerState.currentPage]?.play()
                }
            }
        }

        activity?.lifecycle?.addObserver(lifecycleObserver)
        activity?.onBackPressedDispatcher?.addCallback(callback)

        onDispose {
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            callback.remove()
        }
    }

    // 뒤로가기 버튼 핸들러
    BackHandler(enabled = !isExiting) {
        handleBackPress()
    }

    // 화면 종료 시 자원 정리
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "화면 종료: 자원 정리")
            players.values.forEach { player ->
                try {
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "플레이어 해제 오류", e)
                }
            }
            players.clear()
        }
    }

    // UI 렌더링
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isInitialLoading -> {
                // 초기 로딩 화면 (데이터 로드 중)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "비디오 로드 중...",
                            color = Color.White
                        )
                    }
                }
            }
            error != null -> {
                // 오류 화면
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "오류가 발생했습니다",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { handleBackPress() }) {
                        Text("돌아가기")
                    }
                }
            }
            targetIndex >= 0 && initializationComplete -> {
                // 비디오 페이저 (타겟 인덱스를 찾은 경우)
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) { page ->
                    val video = roomVideos.getOrNull(page)
                    if (video != null) {
                        // 현재 페이지가 보이는 비디오인 경우, 업로더 정보 전달
                        val showUploaderInfo = page == pagerState.currentPage
                        val currentUploaderInfo = if (showUploaderInfo && video.id.toString() == videoId) uploaderInfo else null

                        RoomSingleVideoPlayer(
                            video = video,
                            uploaderInfo = currentUploaderInfo, // 업로더 정보 전달
                            isCurrentlyVisible = page == pagerState.currentPage && !isExiting && !appInBackground,
                            onPlayerCreated = { player ->
                                players[page] = player
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "비디오를 불러올 수 없습니다",
                                color = Color.White
                            )
                        }
                    }
                }
            }
            else -> {
                // 비디오를 찾지 못한 경우
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "비디오를 찾을 수 없습니다",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { handleBackPress() }) {
                        Text("돌아가기")
                    }
                }
            }
        }

        // 초기화가 완료된 경우에만 뒤로가기 버튼 표시
        if (initializationComplete) {
            IconButton(
                onClick = { handleBackPress() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color.White
                )
            }
        }
    }
}