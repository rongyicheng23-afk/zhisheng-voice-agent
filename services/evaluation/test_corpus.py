import unittest
from services.evaluation.diarization import score, score_corpus


def recording(duration, reference, hypothesis):
    return dict(durationSeconds=duration, reference=reference, hypothesis=hypothesis)


def segment(end, speaker='anonymous'):
    return dict(start=0, end=end, speaker=speaker)


class CorpusTests(unittest.TestCase):
    def test_weighted_by_speaker_time_not_recording_count(self):
        result = score_corpus([recording(9, [segment(9)], [segment(9)]),
                               recording(1, [segment(1)], [])])
        self.assertAlmostEqual(result['der'], .1)

    def test_silent_recording_false_alarms_are_not_discarded(self):
        result = score_corpus([recording(2, [segment(2)], [segment(2)]),
                               recording(1, [], [segment(1)])])
        self.assertEqual(result['der'], .5)
        self.assertEqual(result['recordingsWithoutReferenceSpeech'], 1)

    def test_mapping_is_independent_for_each_recording(self):
        result = score_corpus([recording(1, [segment(1, 'A')], [segment(1, '1')]),
                               recording(1, [segment(1, 'B')], [segment(1, '1')])])
        self.assertEqual(result['der'], 0)

    def test_all_silent_reference_has_undefined_der(self):
        result = score_corpus([recording(1, [], [segment(1)])])
        self.assertIsNone(result['der'])
        self.assertEqual(result['falseAlarmSpeakerSeconds'], 1)

    def test_invalid_corpus(self):
        for value in ([], {}, [None], [{}], [{}] * 1001):
            with self.subTest(value=type(value)), self.assertRaises(ValueError):
                score_corpus(value)

    def test_malformed_segments_raise_validation_error(self):
        for value in (None, {}, {'start': 0}, dict(start=False, end=1, speaker='A')):
            with self.subTest(value=value), self.assertRaises(ValueError):
                score([value], [], 1)

    def test_boolean_duration_rejected(self):
        with self.assertRaises(ValueError):
            score([], [], True)


if __name__ == '__main__':
    unittest.main()
