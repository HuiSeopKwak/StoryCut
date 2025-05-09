from pydantic import BaseModel, Field
from typing import List, Optional
from app.api.v1.schemas.post_schema import PostResult
# 요청 스키마
class VideoProcessRequest(BaseModel):
    prompt: str
    video_id: int = Field(..., alias="videoId")
    images: Optional[List[str]] = None
    subtitle: bool

    class Config:
        validate_by_name = True
        populate_by_name = True

# 응답 스키마
class VideoPostResponse(BaseModel):
    is_success: bool = Field(..., alias="isSuccess")
    code: int
    message: str
    result: Optional[PostResult]

    class Config:
        populate_by_name = True
        validate_by_name = True
        json_schema_extra = {
            "isSuccess": True,
            "code": 200,
            "message": "🎬 영상 처리 완료",
            "result": {
                "videoId": 1,
                "memberId": 1,
                "videoName": "영상 제목",
                "videoUrl": "https://example.com/video.mp4",
                "thumbnail": "https://example.com/thumbnail.jpg",
                "originalVideoId": None,
                "createdAt": "2025-05-02T08:54:00.000Z",
                "updatedAt": "2025-05-02T08:54:00.000Z"
            }
        }