import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch


class TtsBackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.loads = 0
        owner = self

        class ModelFixture:
            def __init__(self, **kwargs):
                owner.loads += 1

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

    def test_health_does_not_claim_streaming(self):
        data = self.client.get('/health').json
        self.assertFalse(data['supportsStreaming'])
        self.assertFalse(data['modelLoaded'])

    def test_request_limit_remains_413(self):
        self.module.app.config['MAX_CONTENT_LENGTH'] = 10
        self.assertEqual(self.post().status_code, 413)
        self.assert_clean()


if __name__ == '__main__':
    unittest.main()
