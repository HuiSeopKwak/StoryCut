package com.stroycut.domain.room.dto.response;

import com.stroycut.domain.room.entity.Room;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RoomResponse {
    
    private Long roomId;
    private Long hostId;
    private String roomTitle;
    private boolean hasPassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int memberCount;
    
    public static RoomResponse from(Room publicRoom, int memberCount) {
        return RoomResponse.builder()
                .roomId(publicRoom.getId())
                .hostId(publicRoom.getHostId())
                .roomTitle(publicRoom.getTitle())
                .hasPassword(publicRoom.getPassword() != null && !publicRoom.getPassword().isEmpty())
                .createdAt(publicRoom.getCreatedAt())
                .updatedAt(publicRoom.getUpdatedAt())
                .memberCount(memberCount)
                .build();
    }
}
