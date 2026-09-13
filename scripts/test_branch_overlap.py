import unittest
from unittest.mock import patch
from scripts.check_branch_overlap import report


class OverlapTests(unittest.TestCase):
    def test_covers_committed_dirty_and_untracked_paths(self):
        responses = [b'peer\n', b'head\n', b'base\n',
                     b'committed\0dirty\0new\0peer-only\0',
                     b'committed\0local-only\0', b'dirty\0', b'new\0']
        with patch('scripts.check_branch_overlap.git', side_effect=responses) as command:
            result = report('origin/feature/frank-dev')
        self.assertEqual(result['sharedPaths'], ['committed', 'dirty', 'new'])
        self.assertEqual(result['peerCommit'], 'peer')
        self.assertTrue(all(call.args[0] in ('rev-parse', 'merge-base', 'diff', 'ls-files')
                            for call in command.call_args_list))

    def test_disjoint_paths(self):
        with patch('scripts.check_branch_overlap.git', side_effect=[
                b'peer', b'head', b'base', b'gateway.py\0', b'evaluation.py\0', b'', b'']):
            self.assertEqual(report('peer')['sharedPaths'], [])

    def test_paths_with_spaces_and_newlines_are_not_split(self):
        value = 'folder/name with\nnewline.py\0'.encode()
        with patch('scripts.check_branch_overlap.git', side_effect=[
                b'peer', b'head', b'base', value, value, b'', b'']):
            self.assertEqual(report('peer')['sharedPaths'], ['folder/name with\nnewline.py'])


if __name__ == '__main__':
    unittest.main()
