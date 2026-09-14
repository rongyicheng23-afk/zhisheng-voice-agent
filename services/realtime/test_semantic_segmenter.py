import unittest

from semantic_segmenter import AdaptiveSemanticChunker


class AdaptiveSemanticChunkerTests(unittest.TestCase):
    def test_emits_at_a_strong_sentence_boundary(self):
        chunker = AdaptiveSemanticChunker(min_chars=6, max_chars=30)

        emitted = chunker.feed("报名材料已经准备完成。下一步提交", 0)

        self.assertEqual(["报名材料已经准备完成。"], [item.text for item in emitted])
        self.assertEqual("semantic_boundary", emitted[0].reason)
        self.assertEqual(["下一步提交"], [item.text for item in chunker.finish()])

    def test_does_not_cut_inside_unclosed_brackets(self):
        chunker = AdaptiveSemanticChunker(min_chars=6, max_chars=18)

        self.assertEqual([], chunker.feed("请准备（身份证、", 0))
        emitted = chunker.feed("学生证和报名表）。", 100)

        self.assertEqual(["请准备（身份证、学生证和报名表）。"], [item.text for item in emitted])

    def test_wait_releases_a_stable_tail_without_punctuation(self):
        chunker = AdaptiveSemanticChunker(min_chars=6, max_chars=30, max_wait_ms=500)

        self.assertEqual([], chunker.feed("系统正在查询竞赛资料", 0))
        emitted = chunker.feed("", 500)

        self.assertEqual(["系统正在查询竞赛资料"], [item.text for item in emitted])
        self.assertEqual("max_wait", emitted[0].reason)

    def test_short_tail_waits_until_turn_finished(self):
        chunker = AdaptiveSemanticChunker(min_chars=8, max_chars=30)

        self.assertEqual([], chunker.feed("好的", 0))
        self.assertEqual(["好的"], [item.text for item in chunker.finish()])


if __name__ == "__main__":
    unittest.main()
