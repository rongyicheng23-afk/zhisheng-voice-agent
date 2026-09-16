import unittest
from services.evaluation.diarization import score

def seg(start, end, speaker): return dict(start=start, end=end, speaker=speaker)

class DerTests(unittest.TestCase):
    def test_anonymous_label_permutation_is_not_error(self):
        self.assertEqual(score([seg(0, 1, 'A'), seg(1, 2, 'B')], [seg(0, 1, '2'), seg(1, 2, '1')], 2)['der'], 0)
    def test_merging_two_speakers_is_confusion(self):
        self.assertEqual(score([seg(0, 1, 'A'), seg(1, 2, 'B')], [seg(0, 2, '1')], 2)['der'], .5)
    def test_miss_and_false_alarm(self):
        result = score([seg(0, 1, 'A')], [seg(1, 2, '1')], 2)
        self.assertEqual(result['missedSpeakerSeconds'], 1)
        self.assertEqual(result['falseAlarmSpeakerSeconds'], 1)
        self.assertEqual(result['der'], 2)  # DER can exceed 100%.
    def test_overlap_counts_speaker_time(self):
        result = score([seg(0, 1, 'A'), seg(0, 1, 'B')], [seg(0, 1, '1')], 1)
        self.assertEqual(result['referenceSpeakerSeconds'], 2)
        self.assertEqual(result['der'], .5)
    def test_no_reference_is_not_claimed_as_zero_error(self):
        self.assertIsNone(score([], [], 1)['der'])
    def test_invalid_timestamps_rejected(self):
        for item in [seg(-1, 1, 'A'), seg(0, float('nan'), 'A'), seg(0, 2, 'A')]:
            with self.assertRaises(ValueError): score([item], [], 1)
    def test_duplicate_same_speaker_intervals_not_double_counted(self):
        result = score([seg(0, 1, 'A'), seg(0, 1, 'A')], [seg(0, 1, '1')], 1)
        self.assertEqual(result['referenceSpeakerSeconds'], 1)
        self.assertEqual(result['der'], 0)

if __name__ == '__main__': unittest.main()
