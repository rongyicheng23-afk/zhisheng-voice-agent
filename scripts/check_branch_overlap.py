"""Read-only path overlap guard. Does not fetch, merge, stage or change branches.

Exit 1 means shared paths require review, NOT proof of a Git merge conflict.
Includes committed, staged, unstaged and non-ignored untracked local paths.
Renames are treated as deletion + addition to include both affected paths.
"""
import argparse
import json
from pathlib import Path
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args], cwd=Path(__file__).resolve().parents[1])


def paths(*args):
    return set(git(*args).decode('utf-8').rstrip('\0').split('\0')) - {''}


def report(peer):
    peer_commit = git('rev-parse', '--verify', '--end-of-options', peer + '^{commit}').decode().strip()
    head = git('rev-parse', 'HEAD').decode().strip()
    base = git('merge-base', head, peer_commit).decode().strip()
    other = paths('diff', '--no-renames', '--name-only', '-z', base, peer_commit)
    local = paths('diff', '--no-renames', '--name-only', '-z', base, 'HEAD')
    local |= paths('diff', '--no-renames', '--name-only', '-z', 'HEAD')
    local |= paths('ls-files', '--others', '--exclude-standard', '-z')
    return dict(head=head, peerCommit=peer_commit, mergeBase=base,
                sharedPaths=sorted(local & other),
                note='Path overlap only; no semantic compatibility or mergeability guarantee. Remote ref is not refreshed automatically.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('peer', nargs='?', default='origin/feature/frank-dev')
    args = parser.parse_args()
    try:
        result = report(args.peer)
    except subprocess.CalledProcessError:
        parser.exit(2, 'Unable to resolve branches or common ancestor. Fetch the intended branch and retry.\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(1 if result['sharedPaths'] else 0)
