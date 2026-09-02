"""
声纹识别（Speaker Verification）测试脚本
使用 ModelScope 提供的 CAM++ 模型进行声纹比对，判断两段音频是否属于同一个人。
"""
from __future__ import annotations
from modelscope.pipelines import pipeline

# 1. 加载模型
# 初始化声纹识别 Pipeline。这里使用的是达摩院开源的 CAM++ 预训练模型 (16kHz, 中文/通用场景)
sv_pipeline = pipeline(
    task='speaker-verification',
    model='damo/speech_campplus_sv_zh-cn_16k-common',
    model_revision='v1.0.0'
)

# 2. 准备测试用的音频文件路径
# 这里假设有两名说话人，其中 speaker1 有两段不同音频（a 和 b），speaker2 有一段音频（a）
speaker1_a_wav = 'examples/speaker1_a_cn_16k.wav'
speaker1_b_wav = 'examples/speaker1_b_cn_16k.wav'
speaker2_a_wav = 'examples/speaker2_a_cn_16k.wav'

print("=" * 40)
print("🚀 AI 声纹鉴定专家已就绪")
print("=" * 40)


def print_result(title, audio_list):
    """
    运行声纹比对，并打印易读的格式化结果。
    
    Args:
        title (str): 实验组的自定义标题
        audio_list (list): 包含待比对音频文件路径的列表（通常为两个文件）
    """
    print(f"\n🔍 {title}")
    
    # 执行推理，计算列表中两段音频的相似度
    result = sv_pipeline(audio_list)
    
    # score 代表两段音频的余弦相似度得分 (Cosine Similarity)
    score = result['score']

    # 核心一目了然的判断逻辑
    if score >= 0.25:  # CAM++ 模型的推荐阈值通常在 0.25 左右
        judgment = "✅ [ 鉴定结果：同一个人 ]"
    else:
        judgment = "❌ [ 鉴定结果：不是同一个人 ]"

    print("-" * 30)
    print(f"   声纹相似度得分: {score:.4f}")
    print(f"   最终结论: {judgment}")
    print("-" * 30)


# 3. 运行测试
print_result("实验组 1：对比 A 与 B", [speaker1_a_wav, speaker1_b_wav])
print_result("实验组 2：对比 A 与 C", [speaker1_a_wav, speaker2_a_wav])

print("\n💡 提示：得分越接近 1 表示声音越像；负数或接近 0 表示声音差异很大。")
print("=" * 40)
print("✨ 测试任务全部完成")