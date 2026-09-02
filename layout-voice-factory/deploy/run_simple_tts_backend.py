import importlib.util
import os
from pathlib import Path


TTS_DIR = Path("/Users/rongyicheng/Desktop/voice.baconboat/layout-tts-0.22.0/layout")
TTS_SCRIPT = TTS_DIR / "simple_tts_backend.py"


def load_tts_app():
    spec = importlib.util.spec_from_file_location("simple_tts_backend", TTS_SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载 TTS 服务脚本: {TTS_SCRIPT}")

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.app


def main():
    os.chdir(TTS_DIR)
    app = load_tts_app()
    app.run(host="0.0.0.0", port=8003, debug=False, use_reloader=False)


if __name__ == "__main__":
    main()
