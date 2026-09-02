# 安装
模型下载地址在TTS/TTS/.models.json文件中， 如果运行模式模型下载不下来，可以直接用地址下载，下载后再加载
执行pip install TTS时安装的transformers版本 4.53.2，出现AttributeError: 'GPT2InferenceModel' object has no attribute 'generate'， 降级到transformers==4.37.2 后成功
huggingface下载模型xtts_v2,放到/home/speech/.local/share/tts/tts_models--multilingual--multi-dataset--xtts_v2
# 代码修改
io.py  51/55 kwargs.setdefault("weights_only", False)  # added by gwm 2025.7.17  
systhesizer.py  93    tts_config_path = tts_checkpoint + "/config.json"      #added by gwm 2025.7.17
