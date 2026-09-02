from TTS.api import TTS
import os

# ✅ 支持的情感列表（可调整）
SUPPORTED_EMOTIONS = ["neutral", "happy", "sad", "angry"]

def synthesize_xtts(
    text: str,
    speaker_wav: str,
    emotion: str = "neutral",
    language: str = "en",
    output_path: str = "output.wav"
):
    assert os.path.exists(speaker_wav), f"语音文件不存在: {speaker_wav}"
    assert emotion.lower() in SUPPORTED_EMOTIONS, f"情感应为: {SUPPORTED_EMOTIONS}"

    print("🔊 加载 XTTS-v2 模型...")
    tts = TTS(model_name="tts_models/multilingual/multi-dataset/xtts_v2", progress_bar=True)

    print("🗣️ 合成语音中...")
    tts.tts_to_file(
        text=text,
        speaker_wav=speaker_wav,
        emotion=emotion,
        language=language,
        file_path=output_path,
    )

    print(f"✅ 合成完成: {output_path}")

# 示例调用
if __name__ == "__main__":
    synthesize_xtts(
        text="阳光暖暖的，像撒了一地的蜂蜜。小狗在草地上打滚，尾巴摇成了小风扇。孩子们举着七彩风车咯咯笑，泡泡在风里飘啊飘，炸开一串彩虹。卖冰淇淋的小车叮咚响，草莓味甜到心里去。老爷爷哼着跑调的歌，连路边的蒲公英都跟着摇头晃脑。生活突然变成了棉花糖，咬一口就能甜滋滋地笑出声来！",
        speaker_wav="gwm_voice_sample.wav",  # 替换为你自己的音频文件
        emotion="sad",                      # 可选: neutral, happy, sad, angry
        language="zh-cn",
        output_path="neutral_voice.wav"
    )
