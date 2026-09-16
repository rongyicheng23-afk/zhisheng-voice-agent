"""Opt-in local regression. Uses demo admin, saves one explicit-consent sample.
Run while no other workflow is creating ASR histories. Never prints user data.
"""
import asyncio
import requests
from services.realtime.tests.verify_ticket_connection import main as verify_stream

BASE = 'http://127.0.0.1:18080'

async def main():
    for path in ['/api/users', '/api/user/1', '/api/download/1']:
        assert requests.get(BASE + path, timeout=5).status_code == 401
    session = requests.Session()
    login = session.post(BASE + '/user/login', data={'username': 'admin', 'password': '123456'}, timeout=10).json()
    assert login['code'] == 0
    session.headers['Authorization'] = 'Bearer ' + login['data']
    users = session.get(BASE + '/api/users', timeout=5).json()['data']
    assert len(users) == 1 and 'password' not in users[0]
    user_id = users[0]['id']
    assert session.get(BASE + f'/api/user/{user_id + 100000}', timeout=5).status_code == 403
    denied = session.options(BASE + '/api/users', headers={'Origin': 'https://untrusted.example',
                              'Access-Control-Request-Method': 'GET'}, timeout=5)
    assert denied.status_code == 403 and 'Access-Control-Allow-Origin' not in denied.headers
    def count():
        response = session.get(BASE + '/api/funasr/history', timeout=5).json()
        assert response['code'] == 200 and isinstance(response['data'], list)
        return len(response['data'])
    before = count()
    await verify_stream(save_audio=False)
    await asyncio.sleep(.3)
    assert count() == before, 'Default session unexpectedly persisted a history'
    await verify_stream(save_audio=True)
    for _ in range(20):
        if count() == before + 1: break
        await asyncio.sleep(.1)
    assert count() == before + 1, 'Explicit consent did not create exactly one history'
    print('PASS user isolation, password omission, CORS, default no-save and explicit consent')

if __name__ == '__main__': asyncio.run(main())
