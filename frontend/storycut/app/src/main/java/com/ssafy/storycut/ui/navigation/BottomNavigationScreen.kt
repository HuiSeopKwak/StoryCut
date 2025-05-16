package com.ssafy.storycut.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import com.ssafy.storycut.data.local.datastore.TokenManager
import com.ssafy.storycut.ui.auth.AuthViewModel

class CustomNavigationShape(private val selectedIndex: Int, private val totalItems: Int) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val itemWidth = size.width / totalItems
        val centerX = itemWidth * (selectedIndex + 0.5f)
        val circleRadius = 24.dp.value * density.density
        val curveDepth = 40.dp.value * density.density
        val curveWidth = 92.dp.value * density.density
        val cornerRadius = 24.dp.value * density.density  // 모서리 둥글기 설정

        // 시작점 (왼쪽 상단 모서리)
        path.moveTo(cornerRadius, 0f)

        // 왼쪽 상단 둥근 모서리
        path.arcTo(
            Rect(
                left = 0f,
                top = 0f,
                right = cornerRadius * 2,
                bottom = cornerRadius * 2
            ),
            180f,
            90f,
            forceMoveTo = false
        )

        // 왼쪽 직선에서 곡선 시작점까지
        path.lineTo(centerX - curveWidth / 2, 0f)

        // 왼쪽 곡선
        path.cubicTo(
            centerX - curveWidth / 2.2f, 0f,
            centerX - circleRadius * 1.5f, curveDepth * 0.5f,
            centerX - circleRadius * 0.9f, curveDepth * 0.9f
        )

        // 중앙 하단 곡선
        path.cubicTo(
            centerX - circleRadius * 0.6f, curveDepth,
            centerX, curveDepth * 1.15f,
            centerX + circleRadius * 0.6f, curveDepth
        )

        // 오른쪽 곡선
        path.cubicTo(
            centerX + circleRadius * 0.9f, curveDepth * 0.9f,
            centerX + circleRadius * 1.5f, curveDepth * 0.5f,
            centerX + curveWidth / 2, 0f
        )

        // 오른쪽 직선에서 상단 모서리까지
        path.lineTo(size.width - cornerRadius, 0f)

        // 오른쪽 상단 둥근 모서리
        path.arcTo(
            Rect(
                left = size.width - cornerRadius * 2,
                top = 0f,
                right = size.width,
                bottom = cornerRadius * 2
            ),
            270f,
            90f,
            forceMoveTo = false
        )

        // 나머지 직선들
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.lineTo(0f, cornerRadius)

        path.close()

        return Outline.Generic(path)
    }
}

@Composable
fun MainScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    tokenManager: TokenManager,
    onNavigateToLogin: () -> Unit = {}
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Edit,
        BottomNavItem.Shorts,
        BottomNavItem.MyPage
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.let { if (it < 0) 0 else it }

    // 화면 너비 가져오기
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // 비디오 상세 화면인지 확인 (video_detail/숫자 형식의 경로 확인)
    val isVideoDetailScreen = currentRoute?.startsWith("video_detail/") == true

    // 이전 화면 상태 추적
    val wasVideoDetailScreen = remember { mutableStateOf(false) }

    // 화면 전환 감지 및 처리
    LaunchedEffect(isVideoDetailScreen) {
        // 비디오 상세 화면에서 다른 화면으로 전환될 때
        if (wasVideoDetailScreen.value && !isVideoDetailScreen) {
            // 전환 효과를 위한 약간의 딜레이
            delay(100)
        }
        wasVideoDetailScreen.value = isVideoDetailScreen
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isVideoDetailScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f)
                ) {
                    // 메인 네비게이션 바 배경과 그림자
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 32.dp,
                                spotColor = Color(0xFF000000).copy(alpha = 1.00f),  // 그림자 더 진하게
                                ambientColor = Color(0xFF000000).copy(alpha = 1.00f),  // 주변 그림자도 더 진하게
                                shape = CustomNavigationShape(selectedIndex, items.size)
                            )
                            .background(
                                color = Color.White.copy(alpha = 0.95f),
                                shape = CustomNavigationShape(selectedIndex, items.size)
                            )
                            .height(64.dp)
                            .zIndex(11f)
                    )

                    // 네비게이션 아이템을 위한 컨테이너
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .zIndex(13f)
                    ) {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            items.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentRoute != item.route) {
                                        // 선택되지 않은 항목
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                item.icon,
                                                contentDescription = item.title,
                                                tint = Color.Gray.copy(alpha = 0.5f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // 클릭 이벤트 처리를 위한 투명한 오버레이
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable {
                                                if (currentRoute != item.route) {
                                                    navController.navigateToMainTab(item.route)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // 선택된 아이템들을 위한 컨테이너
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .zIndex(14f)
                    ) {
                        items.forEachIndexed { index, item ->
                            if (currentRoute == item.route) {
                                Box(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .align(Alignment.Center)
                                        .offset(
                                            x = ((index - items.size / 2f + 0.5f) * (screenWidth.value / items.size)).dp
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(y = (-16).dp)
                                            .shadow(
                                                elevation = 16.dp,
                                                shape = CircleShape,
                                                spotColor = Color.Black.copy(alpha = 0.25f),
                                                ambientColor = Color.Black.copy(alpha = 0.2f)
                                            )
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = CircleShape,
                                                spotColor = Color.Black.copy(alpha = 0.35f),
                                                ambientColor = Color.Black.copy(alpha = 0.3f)
                                            )
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFB800))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.title,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = if (isVideoDetailScreen) {
                Modifier
                    .fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .zIndex(0f)
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Navigation.Main.HOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    mainGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        tokenManager = tokenManager,
                        onNavigateToLogin = onNavigateToLogin
                    )
                }
            }
        }
    }
}

/**
 * 하단 내비게이션 바의 아이템을 정의하는 클래스
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // 홈 화면 아이템
    object Home : BottomNavItem(
        route = Navigation.Main.HOME,
        title = "홈",
        icon = Icons.Default.Home
    )

    // 영상 편집 아이템
    object Edit : BottomNavItem(
        route = Navigation.Main.EDIT,
        title = "영상편집",
        icon = Icons.Default.List
    )

    // 쇼츠 업로드 아이템
    object Shorts : BottomNavItem(
        route = Navigation.Main.SHORTS_UPLOAD,
        title = "쇼츠 업로드",
        icon = Icons.Default.Create
    )

    // 마이페이지 아이템
    object MyPage : BottomNavItem(
        route = Navigation.Main.MYPAGE,
        title = "마이페이지",
        icon = Icons.Default.Person
    )
}
