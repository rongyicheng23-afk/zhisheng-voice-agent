"""Opt-in local integration check. Creates one sample transcription history.
Requires the local stack on 18080/10095; never enables a microphone.
"""
import asyncio
import json
from pathlib import Path
import wave
import argparse

import requests
import websockets

BASE = 'http://127.0.0.1:18080'
ORIGIN = 'http://127.0.0.1:8081'


async def main(chunk_interval=10):
    response = requests.post(BASE + '/api/realtime/ticket', headers={'Origin': ORIGIN}, timeout=5)
    assert response.status_code == 401, 'Ticket issuance must require login'
    auth = requests.post(BASE + '/user/login', data={'username': 'admin', 'password': '123456'}, timeout=5).json()
    assert auth.get('code') == 0, 'Local demo login failed'
    headers = {'Authorization': 'Bearer ' + auth['data'], 'Origin': ORIGIN}
    bad = requests.post(BASE + '/api/realtime/ticket', headers=headers | {'Origin': 'https://untrusted.example'}, timeout=5)
    assert bad.status_code == 403
    response = requests.post(BASE + '/api/realtime/ticket', headers=headers, timeout=5)
    assert response.status_code == 200 and response.headers.get('Cache-Control') == 'no-store'
    ticket = response.json()['data']['ticket']
    uri = 'ws://127.0.0.1:18080/ws/funasr?ticket=' + ticket

    async def rejected(url, origin, status):
        try:
            async with websockets.connect(url, origin=origin, open_timeout=5):
                raise AssertionError('Unexpected accepted handshake')
        except websockets.exceptions.InvalidStatusCode as error:
            assert error.status_code == status, 'Unexpected rejection status'

    await rejected(uri, 'https://untrusted.example', 403)
    await rejected('ws://127.0.0.1:18080/ws/funasr?token=legacy-not-a-real-token', ORIGIN, 401)
    async with websockets.connect(uri, origin=ORIGIN, open_timeout=5) as socket:
        ready = json.loads(await asyncio.wait_for(socket.recv(), 8))
        assert ready.get('event') == 'session.ready', 'Upstream not ready'
        await socket.send(json.dumps({'mode': '2pass', 'wav_name': 'ticket-sample', 'is_speaking': True,
                                      'chunk_size': [5, 10, 5], 'chunk_interval': chunk_interval}))
        path = Path(__file__).resolve().parents[3] / 'speech_campplus_sv_zh-cn_16k-common/examples/speaker1_a_cn_16k.wav'
        with wave.open(str(path)) as wav:
            assert (wav.getframerate(), wav.getsampwidth(), wav.getnchannels()) == (16000, 2, 1)
            while True:
                pcm = wav.readframes(960)
                if not pcm:
                    break
                await socket.send(pcm)
                await asyncio.sleep(0.06)
        await socket.send(json.dumps({'is_speaking': False}))
        final = ''
        async def collect():
            nonlocal final
            async for raw in socket:
                event = json.loads(raw)
                if 'offline' in event.get('mode', '') and event.get('text'):
                    final = event['text']
                if event.get('event') == 'asr.completed':
                    break
        await asyncio.wait_for(collect(), 15)
        assert final
        print('PASS authenticated ticket + sample PCM + stable transcription (content omitted)')
    await rejected(uri, ORIGIN, 401)
    print('PASS unauthenticated issuance, disallowed origins, legacy JWT URL, one-time replay rejection')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--chunk-interval', type=int, default=10)
    asyncio.run(main(parser.parse_args().chunk_interval))
