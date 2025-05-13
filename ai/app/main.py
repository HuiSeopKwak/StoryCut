from fastapi import FastAPI
from app.api.v1.endpoints import upload, video,mosaic, video_test
from fastapi.staticfiles import StaticFiles
from fastapi.openapi.utils import get_openapi

app = FastAPI(
    root_path="/api/v1/fastapi",      # Nginx proxy 경로와 일치
    docs_url="/docs",                 # Swagger UI 경로
    redoc_url=None,                   # Redoc 비활성화
    openapi_url="/openapi.json"       # OpenAPI JSON 경로
    root_path_in_servers=True
)

# ✅ Bearer 인증 Swagger에 적용
def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema

    openapi_schema = get_openapi(
        title="fast API",
        version="1.0.0",
        description="API with Authorization header",
        routes=app.routes,
    )

    openapi_schema["components"]["securitySchemes"] = {
        "BearerAuth": {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
        }
    }

    for path in openapi_schema["paths"].values():
        for method in path.values():
            method.setdefault("security", [{"BearerAuth": []}])

    app.openapi_schema = openapi_schema
    return app.openapi_schema

# 🔽 Swagger에 커스텀 openapi 적용
app.openapi = custom_openapi

# ✅ static 파일 mount
app.mount("/static", StaticFiles(directory="app"), name="static")

# ✅ 라우터 등록
app.include_router(upload.router, prefix="/upload", tags=["upload"])
app.include_router(video.router, prefix="/videos", tags=["videos"])
app.include_router(mosaic.router, prefix="/mosaic", tags=["mosaic"])
app.include_router(video_test.router, prefix="/v1", tags=["videoTest"])