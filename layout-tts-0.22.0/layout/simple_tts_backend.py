from flask import Flask, request, jsonify, send_file
from flask_cors import CORS

from TTS.api import TTS
import os
import uuid

app = Flask(__name__)
CORS(app)  # 启用CORS支持
# 简单配置
UPLOAD_FOLDER = 'uploads'
OUTPUT_FOLDER = 'outputs'

# 创建目录
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(OUTPUT_FOLDER, exist_ok=True)

# 支持的情感和语言
EMOTIONS = ["neutral", "happy", "sad", "angry"]
LANGUAGES = ["en", "zh-cn", "es", "fr", "de"]

def synthesize_speech(text, speaker_file, emotion="neutral", language="en"):
    """简单的语音合成函数"""
    try:
        # 生成输出文件名
        output_file = f"output_{uuid.uuid4().hex[:8]}.wav"
        output_path = os.path.join(OUTPUT_FOLDER, output_file)
        
        # 加载模型并合成
        print(f"正在合成语音，情感: {emotion}")
        tts = TTS(model_name="tts_models/multilingual/multi-dataset/xtts_v2")
        tts.tts_to_file(
            text=text,
            speaker_wav=speaker_file,
            emotion=emotion,
            language=language,
            file_path=output_path
        )
        
        return output_path
    except Exception as e:
        print(f"合成出错: {e}")
        return None

@app.route('/health')
def health():
    """健康检查"""
    return jsonify({
        "status": "ok",
        "emotions": EMOTIONS,
        "languages": LANGUAGES
    })

@app.route('/synthesize', methods=['POST'])
def synthesize():
    """主要的语音合成端点"""
    try:
        # 获取参数
        text = request.form.get('text', '')
        emotion = request.form.get('emotion', 'neutral')
        language = request.form.get('language', 'en')
        
        # 检查音频文件
        if 'audio' not in request.files:
            return jsonify({"error": "请上传音频文件"}), 400
        
        audio_file = request.files['audio']
        if audio_file.filename == '':
            return jsonify({"error": "未选择文件"}), 400
        
        # 保存上传的文件
        upload_path = os.path.join(UPLOAD_FOLDER, f"temp_{uuid.uuid4().hex[:8]}.wav")
        audio_file.save(upload_path)
        
        # 验证参数
        if not text:
            return jsonify({"error": "请输入要合成的文本"}), 400
        
        if emotion not in EMOTIONS:
            return jsonify({"error": f"不支持的情感，请选择: {EMOTIONS}"}), 400
        
        if language not in LANGUAGES:
            return jsonify({"error": f"不支持的语言，请选择: {LANGUAGES}"}), 400
        
        # 合成语音
        result_file = synthesize_speech(text, upload_path, emotion, language)
        
        if result_file and os.path.exists(result_file):
            # 返回音频文件
            response = send_file(result_file, as_attachment=True)
            
            # 清理临时文件
            os.remove(upload_path)
            os.remove(result_file)
            
            return response
        else:
            return jsonify({"error": "语音合成失败"}), 500
            
    except Exception as e:
        return jsonify({"error": f"服务器错误: {str(e)}"}), 500

if __name__ == '__main__':
    print("🚀 启动简单的XTTS后端...")
    print("📍 服务地址: http://localhost:8003")
    print("🎵 合成端点: POST /synthesize")
    print("🔗 健康检查: GET /health")
    
    # Model services must stay single-process: Flask's debug reloader starts a
    # second interpreter and would load the multi-GB XTTS model twice.
    app.run(host='0.0.0.0', port=8003, debug=False, use_reloader=False)
