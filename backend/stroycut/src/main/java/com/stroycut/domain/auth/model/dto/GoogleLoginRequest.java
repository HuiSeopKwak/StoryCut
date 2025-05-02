package com.stroycut.domain.auth.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 안드로이드 앱에서 전송하는 구글 로그인 요청 DTO
 * idToken: 구글 로그인을 통해 앱에서 받은 ID 토큰
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {
    private String idToken;
}
