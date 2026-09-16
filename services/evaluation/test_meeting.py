import json
import unittest
from services.evaluation.meeting import hypothesis_from_meeting, evaluate


class MeetingEvaluationTests(unittest.TestCase):
    def segment(self, start, name='匿名1', profile=None):
        return dict(startMs=start, endMs=start + 1000, speakerName=name,
                    speakerProfileId=profile, transcript='private transcript')

    def test_api_response_and_direct_segments_are_supported(self):
        value = dict(speakerSegments=[self.segment(0)])
        self.assertEqual(hypothesis_from_meeting(value), hypothesis_from_meeting(dict(code=200, data=value)))

    def test_unknown_and_same_named_profiles_stay_separate(self):
        segments = [self.segment(0, '未知发言人'), self.segment(1000, '未知发言人'),
                    self.segment(2000, '王', 1), self.segment(3000, '王', 2), self.segment(4000, '王', 1)]
        hyp, unknown = hypothesis_from_meeting(dict(speakerSegments=segments))
        self.assertEqual(2, unknown)
        self.assertEqual(4, len({s['speaker'] for s in hyp}))
        self.assertEqual(hyp[2]['speaker'], hyp[4]['speaker'])
        self.assertNotIn('private', json.dumps(hyp))

    def test_result_excludes_names_and_transcript(self):
        result = evaluate(dict(speakerSegments=[self.segment(0, 'private person')]),
                          dict(durationSeconds=1, reference=[dict(start=0, end=1, speaker='private reference')]))
        self.assertEqual(0, result['metrics']['der'])
        self.assertNotIn('private', json.dumps(result))

    def test_invalid_api_and_times_fail(self):
        for payload in [dict(code=401, data={}), {}, dict(speakerSegments=[dict(startMs=True, endMs=1)]),
                        dict(speakerSegments=[dict(startMs=1, endMs=0)])]:
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                hypothesis_from_meeting(payload)

    def test_unknown_fragmentation_is_not_reported_as_perfect_grouping(self):
        result = evaluate(dict(speakerSegments=[self.segment(0, '未知发言人'), self.segment(1000, '未知发言人')]),
                          dict(durationSeconds=2, reference=[dict(start=0, end=2, speaker='A')]))
        self.assertEqual(0.5, result['metrics']['der'])
        self.assertEqual(2, result['unknownSegmentCount'])


if __name__ == '__main__':
    unittest.main()
