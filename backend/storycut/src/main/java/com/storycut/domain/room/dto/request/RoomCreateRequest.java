package com.storycut.domain.room.dto.request;

import com.storycut.domain.room.entity.Room;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    @Size(min = 2, max = 100, message = "방 제목은 2자 이상 100자 이하여야 합니다.")
    private String roomTitle;
    
    private String roomPassword;

    private String roomContext;
    
    public Room toEntity(Long hostMemberId) {
        return Room.builder()
                .hostMemberId(hostMemberId)
                .title(roomTitle)
                .password(roomPassword)
                .context(roomContext)
                .build();
    }
}
