"""
声纹比对（Speaker Verification）API 服务
基于 FastAPI 构建，使用 ModelScope 的 CAM++ 模型进行声纹比对。
核心功能：接收两段音频，经过统一重采样处理后，计算相似度并返回判定结果。
"""

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import os
import uuid
import librosa
import soundfile as sf
from datetime import datetime

# 导入魔搭（ModelScope）的声纹识别 pipeline
from modelscope.pipelines import pipeline

app = FastAPI(title="声纹比对 API 服务")

# 添加 CORS 中间件，允许跨域请求（方便前后端分离开发）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],     # 生产环境下建议限制为具体的域名
    allow_credentials=True,  # 允许携带 Cookie
    allow_methods=["*"],     # 允许所有请求方法 (GET, POST 等)
    allow_headers=["*"],     # 允许所有请求头
)

# 全局模型变量，实现单例加载，避免重复加载模型占用内存
sv_model = None


def load_model():
    """初始化并加载声纹模型"""
    global sv_model
    if sv_model is None:
        print(f"{datetime.now()} - 正在加载声纹识别(SV)模型...")
        # 加载 CAM++ 模型 (16k 中文通用版本)
        sv_model = pipeline(
            task='speaker-verification',
            model='damo/speech_campplus_sv_zh-cn_16k-common',
            model_revision='v1.0.0'
        )
        print(f"{datetime.now()} - 🚀 SV模型加载完成！")


@app.on_event("startup")
async def startup_event():
    """服务启动时自动加载模型"""
    load_model()


@app.get("/")
async def health_check():
    """健康检查接口"""
    return {"status": "healthy", "message": "声纹比对服务运行中"}


def ensure_16k_wav(input_path, output_path):
    """
    ✨ 音频净化神器：强制将输入音频转换为模型最喜欢的 16k 采样率、单声道 WAV 格式。
    
    Args:
        input_path: 原始音频路径 (支持 mp3, m4a, wav 等)
        output_path: 输出的标准 WAV 路径
    """
    try:
        # librosa.load 会自动处理格式解析，并强制重采样到 16000Hz，转为单声道(mono)
        y, sr = librosa.load(input_path, sr=16000, mono=True)
        # 将处理后的浮点数音频数据保存为标准的 wav 文件
        sf.write(output_path, y, 16000)
    except Exception as e:
        raise Exception(f"处理音频文件失败，文件可能已损坏或格式不支持: {str(e)}")


@app.post("/verify")
async def verify_speaker(
        file1: UploadFile = File(..., description="第一段语音文件"),
        file2: UploadFile = File(..., description="第二段待比对语音文件")
):
    """
    声纹比对接口：
    1. 保存上传的原始文件到临时目录
    2. 统一转换为 16k 单声道 WAV 格式
    3. 调用声纹模型进行相似度计算
    4. 返回 JSON 格式的比对结果
    """
    raw_file1 = None
    raw_file2 = None
    clean_file1 = None
    clean_file2 = None

    try:
        # 0. 格式预校验
        valid_extensions = ('.wav', '.mp3', '.ogg', '.flac', '.m4a')
        if not file1.filename.lower().endswith(valid_extensions) or not file2.filename.lower().endswith(valid_extensions):
            raise HTTPException(status_code=400, detail="仅支持常见音频格式 (wav, mp3, ogg, flac, m4a)")

        # 1. 为本次请求生成唯一的 ID，防止多人并发请求时文件冲突
        unique_id = uuid.uuid4().hex
        _, ext1 = os.path.splitext(file1.filename)
        _, ext2 = os.path.splitext(file2.filename)

        # 保存原始上传的文件 (Raw)
        raw_file1 = f"raw1_{unique_id}{ext1}"
        raw_file2 = f"raw2_{unique_id}{ext2}"

        with open(raw_file1, "wb") as buffer:
            buffer.write(await file1.read())
        with open(raw_file2, "wb") as buffer:
            buffer.write(await file2.read())

        print(f"{datetime.now()} - 正在预处理音频: [{file1.filename}] VS [{file2.filename}]")

        # 2. 核心净化：转换为 16k 标准 WAV (解决因为采样率不匹配导致的模型计算偏差)
        clean_file1 = f"clean1_{unique_id}.wav"
        clean_file2 = f"clean2_{unique_id}.wav"
        ensure_16k_wav(raw_file1, clean_file1)
        ensure_16k_wav(raw_file2, clean_file2)

        print(f"{datetime.now()} - 预处理完成，开始输入模型进行比对...")

        # 3. 将净化后的音频喂给模型进行推理
        result = sv_model([clean_file1, clean_file2])
        score = float(result['score'])  # 将 numpy float 转换为普通 float 以便 JSON 序列化

        # 4. 根据推荐阈值给出业务判断
        threshold = 0.25 # CAM++ 模型的典型建议阈值
        is_same_person = bool(score >= threshold)
        judgment_text = "同一个人" if is_same_person else "不是同一个人"

        # 返回标准化的响应
        return JSONResponse(content={
            "status": "success",
            "file1_name": file1.filename,
            "file2_name": file2.filename,
            "score": round(score, 4),           # 相似度得分
            "threshold": threshold,              # 使用的判定阈值
            "is_same_person": is_same_person,    # 布尔值结果
            "message": f"鉴定结果：{judgment_text}" # 中文直观描述
        })

    except Exception as e:
        # 异常捕获并输出堆栈信息，方便调试
        print("\n❌ 服务运行异常：")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

    finally:
        # 5. 【重要】无论成功或报错，最后必须清理所有产生的临时文件，防止硬盘溢出
        for f in [raw_file1, raw_file2, clean_file1, clean_file2]:
            if f and os.path.exists(f):
                os.remove(f)


if __name__ == "__main__":
    import uvicorn
    # 默认通过 8004 端口启动，避免和 TTS 服务的 8003 冲突
    port = int(os.getenv("SV_SERVICE_PORT", "8004"))
    uvicorn.run(app, host="0.0.0.0", port=port)
