from flask import Flask, request, jsonify, send_file
from flask_cors import CORS

from TTS.api import TTS
import os
import uuid
import io
import threading
from pathlib import Path
from werkzeug.exceptions import HTTPException

app = Flask(__name__)
CORS(app, origins=os.environ.get('TTS_ALLOWED_ORIGINS', 'http://127.0.0.1:8081,http://localhost:8081').split(','))
app.config['MAX_CONTENT_LENGTH'] = 20 * 1024 * 1024
# 简单配置
UPLOAD_FOLDER = str(Path(os.environ.get('TTS_UPLOAD_DIR', 'uploads')).resolve())
OUTPUT_FOLDER = str(Path(os.environ.get('TTS_OUTPUT_DIR', 'outputs')).resolve())

# 创建目录
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(OUTPUT_FOLDER, exist_ok=True)

# 支持的情感和语言
EMOTIONS = ["neutral", "happy", "sad", "angry"]
LANGUAGES = ["en", "zh-cn", "es", "fr", "de"]
_model = None
_synthesis_lock = threading.Lock()

def synthesize_speech(text, speaker_file, emotion="neutral", language="en"):
    """Whole-segment fallback, not streaming. Caller owns _synthesis_lock."""
    global _model
    output_path = None
    try:
        # 生成输出文件名
        output_file = f"output_{uuid.uuid4().hex[:8]}.wav"
        output_path = os.path.join(OUTPUT_FOLDER, output_file)
        
        # 加载模型并合成
        if _model is None:
            _model = TTS(model_name="tts_models/multilingual/multi-dataset/xtts_v2")
        _model.tts_to_file(
            text=text,
            speaker_wav=speaker_file,
            emotion=emotion,
            language=language,
            file_path=output_path
        )
        
        return output_path
    except Exception as e:
        app.logger.error('TTS inference failed (%s)', type(e).__name__)
        if output_path:
            Path(output_path).unlink(missing_ok=True)
        return None

@app.route('/health')
def health():
    """健康检查"""
    return jsonify({
        "status": "ok",
        "modelLoaded": _model is not None,
        "synthesisMode": "segmented",
        "supportsStreaming": False,
        "supportsCancellation": False,
        "emotions": EMOTIONS,
        "languages": LANGUAGES
    })

@app.route('/synthesize', methods=['POST'])
def synthesize():
    """主要的语音合成端点"""
    upload_path = result_file = None
    acquired = False
    try:
        # 获取参数
        text = request.form.get('text', '').strip()
        emotion = request.form.get('emotion', 'neutral')
        language = request.form.get('language', 'en')
        
        # 检查音频文件
        if 'audio' not in request.files:
            return jsonify({"error": "请上传音频文件"}), 400
        
        audio_file = request.files['audio']
        if audio_file.filename == '':
            return jsonify({"error": "未选择文件"}), 400
        
        # 验证参数
        if not text:
            return jsonify({"error": "请输入要合成的文本"}), 400
        if len(text) > 500:
            return jsonify({"error": "文本不能超过500字符，请按语义分段提交"}), 400
        
        if emotion not in EMOTIONS:
            return jsonify({"error": f"不支持的情感，请选择: {EMOTIONS}"}), 400
        
        if language not in LANGUAGES:
            return jsonify({"error": f"不支持的语言，请选择: {LANGUAGES}"}), 400

        acquired = _synthesis_lock.acquire(blocking=False)
        if not acquired:
            return jsonify({"error": "语音合成服务忙，请稍后重试"}), 503, {"Retry-After": "2"}
        upload_path = os.path.join(UPLOAD_FOLDER, f"temp_{uuid.uuid4().hex}.wav")
        audio_file.save(upload_path)
        if os.path.getsize(upload_path) == 0:
            return jsonify({"error": "音频文件为空"}), 400
        
        # 合成语音
        result_file = synthesize_speech(text, upload_path, emotion, language)
        
        if result_file and os.path.exists(result_file):
            # 返回音频文件
            if not 44 < os.path.getsize(result_file) <= 32 * 1024 * 1024:
                raise ValueError('invalid synthesis output')
            # Copy bounded output before cleanup; portable to Windows too.
            response = send_file(io.BytesIO(Path(result_file).read_bytes()),
                                 mimetype='audio/wav', as_attachment=True,
                                 download_name='speech.wav', max_age=0)
            response.headers['X-Synthesis-Mode'] = 'segmented'
            response.headers['Cache-Control'] = 'no-store'
            
            return response
        else:
            return jsonify({"error": "语音合成失败"}), 500
            
    except HTTPException:
        raise
    except Exception as e:
        app.logger.error('TTS request failed (%s)', type(e).__name__)
        return jsonify({"error": "语音合成失败，请检查参考音频或稍后重试"}), 500
    finally:
        for path in (upload_path, result_file):
            if path:
                try:
                    Path(path).unlink(missing_ok=True)
                except OSError:
                    app.logger.warning('TTS temporary cleanup failed')
        if acquired:
            _synthesis_lock.release()

@app.errorhandler(413)
def too_large(_error):
    return jsonify({"error": "上传请求不能超过20 MB"}), 413

if __name__ == '__main__':
    print("🚀 启动简单的XTTS后端...")
    print("📍 服务地址: http://localhost:8003")
    print("🎵 合成端点: POST /synthesize")
    print("🔗 健康检查: GET /health")
    
    # Model services must stay single-process: Flask's debug reloader starts a
    # second interpreter and would load the multi-GB XTTS model twice.
    app.run(host=os.environ.get('TTS_BIND_HOST', '127.0.0.1'), port=8003,
            debug=False, use_reloader=False)
