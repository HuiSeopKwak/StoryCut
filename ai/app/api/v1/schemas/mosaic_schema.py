from pydantic import BaseModel, Field
from typing import List, Optional

class MosaicRequest(BaseModel):
    video_id: str = Field(..., alias="videoId", example=1)
    images: List[str] = Field(..., example=[
        "app/images/face1.jpg",
        "app/images/face2.jpg"
    ])

    class Config:
        allow_population_by_field_name = True
        schema_extra = {
            "example": {
                "videoId": 1,
                "images": [
                    "app/images/face1.jpg",
                    "app/images/face2.jpg"
                ]
            }
        }
