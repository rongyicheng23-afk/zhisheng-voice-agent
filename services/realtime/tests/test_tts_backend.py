import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import types
import struct
import unittest
from unittest.mock import patch
import numpy as np


class TtsBackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.loads = 0
        owner = self

        class Chunk:
            def __init__(self, data): self.data = np.asarray(data, dtype=np.float32)
            def detach(self): return self
            def cpu(self): return self.data

        class XttsFixture:
            args = types.SimpleNamespace(output_sample_rate=24000)
            def get_conditioning_latents(self, audio_path):
                assert Path(audio_path).is_file()
                return 'conditioning', 'speaker'
            def inference_stream(self, text, language, conditioning, speaker, stream_chunk_size):
                owner.stream_progress.append('first')
                yield Chunk([0.5] * 2400)
                owner.stream_progress.append('second')
                yield Chunk([-0.5] * 2400)
                owner.stream_progress.append('done')

        self.stream_progress = []

        class ModelFixture:
            def __init__(self, **kwargs):
                owner.loads += 1
                self.synthesizer = types.SimpleNamespace(tts_model=XttsFixture())

            def tts_to_file(self, **kwargs):
                Path(kwargs['file_path']).write_bytes(b'RIFF' + b'\0' * 100)

        fake = types.ModuleType('TTS.api')
        fake.TTS = ModelFixture
        path = Path(__file__).resolve().parents[3] / 'layout-tts-0.22.0/layout/simple_tts_backend.py'
        spec = importlib.util.spec_from_file_location('tts_test_backend', path)
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {'TTS': types.ModuleType('TTS'), 'TTS.api': fake}), patch.dict('os.environ', {
            'TTS_UPLOAD_DIR': str(self.root / 'uploads'), 'TTS_OUTPUT_DIR': str(self.root / 'outputs')
        }):
            spec.loader.exec_module(self.module)
        self.client = self.module.app.test_client()

    def tearDown(self):
        self.temp.cleanup()

    def post(self, **overrides):
        return self.client.post('/synthesize', data=dict(
            text='测试', language='zh-cn', audio=(io.BytesIO(b'fixture'), 'sample.wav'), **overrides))

    def post_stream(self, **overrides):
        data = dict(text='测试', language='zh-cn', audio=(io.BytesIO(b'fixture'), 'sample.wav'))
        data.update(overrides)
        return self.client.post('/synthesize-stream', data=data, buffered=False)

    def assert_clean(self):
        self.assertEqual(list(self.root.glob('*/*')), [])

    def test_model_reused_and_files_cleaned(self):
        for _ in range(2):
            response = self.post()
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.headers['X-Synthesis-Mode'], 'segmented')
            response.close()
        self.assertEqual(self.loads, 1)
        self.assert_clean()

    def test_validation_before_files(self):
        response = self.client.post('/synthesize', data={'text': '', 'audio': (io.BytesIO(b'a'), 'a.wav')})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(self.loads, 0)
        self.assert_clean()

    def test_busy_returns_retry_not_unbounded_queue(self):
        with self.module._synthesis_lock:
            self.assertEqual(self.post().status_code, 503)
        self.assert_clean()

    def test_failure_is_sanitized_and_cleaned(self):
        with patch.object(self.module, 'TTS', side_effect=RuntimeError('secret-key/internal/path')):
            response = self.post()
        self.assertEqual(response.status_code, 500)
        self.assertNotIn(b'secret-key', response.data)
        self.assertFalse(self.module._synthesis_lock.locked())
        self.assert_clean()
        self.assertEqual(self.post().status_code, 200)

    def test_health_exposes_streaming_route_without_claiming_model_loaded(self):
        data = self.client.get('/health').json
        self.assertTrue(data['supportsStreaming'])
        self.assertEqual('/synthesize-stream', data['streamingEndpoint'])
        self.assertEqual(['segmented', 'streaming'], data['availableSynthesisModes'])
        self.assertFalse(data['modelLoaded'])

    def test_preload_makes_model_ready_and_does_not_reload_on_synthesis(self):
        self.module.preload_model()
        self.module.preload_model()
        self.assertTrue(self.client.get('/health').json['modelLoaded'])
        response = self.post_stream()
        self.assertEqual(200, response.status_code)
        response.close()
        self.assertEqual(1, self.loads)

    def test_streaming_emits_audio_before_model_finishes_and_releases_files(self):
        response = self.post_stream()
        first = next(iter(response.response))
        self.assertEqual('streaming', response.headers['X-Synthesis-Mode'])
        self.assertEqual(4800, struct.unpack('>I', first[:4])[0])
        self.assertEqual(['first'], self.stream_progress)
        body = first + b''.join(response.response)
        self.assertEqual(b'\0\0\0\0', body[-4:])
        self.assertEqual(['first', 'second', 'done'], self.stream_progress)
        response.close()
        self.assert_clean()

    def test_streaming_close_and_failure_cleanup_without_false_completion(self):
        response = self.post_stream()
        next(iter(response.response))
        response.close()
        self.assertFalse(self.module._synthesis_lock.locked())
        self.assert_clean()
        def interrupted(*args):
            yield struct.pack('>I', 2) + b'\0\0'
            raise RuntimeError('private model error')
        with patch.object(self.module, 'stream_speech', interrupted):
            response = self.post_stream()
            data = response.get_data()
            self.assertFalse(data.endswith(b'\0\0\0\0'))
            self.assertNotIn(b'private model error', data)
            response.close()
        self.assert_clean()

    def test_streaming_validation_and_busy_do_not_leak_lock(self):
        with self.module._synthesis_lock:
            self.assertEqual(503, self.post_stream().status_code)
        self.assertEqual(400, self.post_stream(text='').status_code)
        self.assert_clean()

    def test_request_limit_remains_413(self):
        self.module.app.config['MAX_CONTENT_LENGTH'] = 10
        self.assertEqual(self.post().status_code, 413)
        self.assert_clean()


if __name__ == '__main__':
    unittest.main()
