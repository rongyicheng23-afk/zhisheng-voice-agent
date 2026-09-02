from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from funasr import AutoModel
import os
from datetime import datetime
from typing import Optional
from fastapi.middleware.cors import CORSMiddleware  # 新增导入

app = FastAPI()


# 允许的来源列表（根据你的前端地址调整）
origins = [
    "http://localhost:8080",
    ]

# 添加 CORS 中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,  # 指定允许的源
    allow_credentials=True,  # 允许携带凭证（如 Cookies）
    allow_methods=["*"],     # 允许所有 HTTP 方法
    allow_headers=["*"],     # 允许所有请求头
)

# 初始化模型
model = None

def load_model():
    global model
    if model is None:
        print(f"{datetime.now()} - 正在加载ASR模型...")
        model = AutoModel(model="paraformer-zh", vad_model="fsmn-vad", punc_model="ct-punc")
        print(f"{datetime.now()} - ASR模型加载完成")

@app.on_event("startup")
async def startup_event():
    load_model()

@app.get("/")
async def health_check():
    return {"status": "healthy", "message": "ASR服务运行中"}

@app.post("/asr")
async def transcribe_audio(
    file: UploadFile = File(..., max_size=300_000_000),
    batch_size_s: int = 300,
    hotword: Optional[str] = None
):
    try:
        # 检查文件类型
        if not file.filename.lower().endswith(('.wav', '.mp3', '.ogg', '.flac')):
            raise HTTPException(status_code=400, detail="仅支持音频文件 (wav, mp3, ogg, flac)")

        # 保存临时文件
        temp_file = f"temp_{datetime.now().strftime('%Y%m%d%H%M%S')}_{file.filename}"
        print(f"temp_file: {temp_file}")
        with open(temp_file, "wb") as buffer:
            buffer.write(await file.read())

        # 调用ASR模型
        print(f"{datetime.now()} - 开始处理文件: {file.filename}")
        result = model.generate(
            input=temp_file,
            batch_size_s=batch_size_s,
            hotword=hotword
        )

        # 删除临时文件
        os.remove(temp_file)

        return JSONResponse(content={
            "status": "success",
            "filename": file.filename,
            "transcription": result
        })

    except Exception as e:
        # 如果出现错误，确保删除临时文件
        if 'temp_file' in locals() and os.path.exists(temp_file):
            os.remove(temp_file)
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)
