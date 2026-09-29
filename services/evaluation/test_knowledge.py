import unittest
from copy import deepcopy
from services.evaluation.knowledge import evaluate


def sample():
    return {'cases': [
        {'id': 'a', 'answerable': True, 'relevantSourceIds': ['a', 'b'],
         'retrievedSourceIds': ['x', 'a'], 'outcome': 'answered',
         'claims': [{'supported': True}, {'supported': False}, {'supported': None}], 'firstAudioMs': 100},
        {'id': 'b', 'answerable': False, 'relevantSourceIds': [],
         'retrievedSourceIds': [], 'outcome': 'refused', 'claims': [], 'firstAudioMs': 500},
        {'id': 'c', 'answerable': False, 'relevantSourceIds': [],
         'retrievedSourceIds': [], 'outcome': 'failed', 'claims': []},
    ]}


class KnowledgeEvaluationTests(unittest.TestCase):
    def test_metrics_and_denominators(self):
        result = evaluate(sample())
        self.assertEqual(.5, result['macroRecallAtK'])
        self.assertEqual(.5, result['meanReciprocalRankAtK'])
        self.assertEqual(.5, result['correctRefusalRate'])
        self.assertEqual(1, result['failedCaseCount'])
        self.assertEqual(.5, result['humanSupportedClaimRate'])
        self.assertEqual(2 / 3, result['humanReviewCoverage'])
        self.assertEqual(100, result['firstAudioP50Ms'])
        self.assertEqual(500, result['firstAudioP95Ms'])
        self.assertEqual(2, result['latencySampleCount'])

    def test_top_k_is_ranked_and_misses_count_zero(self):
        result = evaluate(sample(), 1)
        self.assertEqual(0, result['macroRecallAtK'])
        self.assertEqual(0, result['meanReciprocalRankAtK'])

    def test_no_denominator_is_unknown_not_perfect_score(self):
        data = sample()
        data['cases'] = [data['cases'][2]]
        result = evaluate(data)
        for key in ('macroRecallAtK', 'humanSupportedClaimRate', 'humanReviewCoverage', 'firstAudioP95Ms'):
            self.assertIsNone(result[key])
        self.assertEqual(0, result['correctRefusalRate'])

    def test_rejects_duplicate_ids_sources_and_missing_human_labels(self):
        data = sample()
        duplicate = deepcopy(data)
        duplicate['cases'].append(duplicate['cases'][0])
        with self.assertRaises(ValueError): evaluate(duplicate)
        for key, value in [('relevantSourceIds', ['a', 'a']), ('retrievedSourceIds', ['x', 'x']),
                           ('claims', [{}]), ('claims', [{'supported': 1}]),
                           ('answerable', None), ('firstAudioMs', float('nan')),
                           ('firstAudioMs', True), ('firstAudioMs', -1), ('claims', [])]:
            changed = deepcopy(data)
            changed['cases'][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): evaluate(changed)

    def test_rejects_failed_latency_and_unlabelled_answerable_cases(self):
        data = sample()
        data['cases'][2]['firstAudioMs'] = 42
        with self.assertRaises(ValueError): evaluate(data)
        data = sample()
        data['cases'][0]['relevantSourceIds'] = []
        with self.assertRaises(ValueError): evaluate(data)

    def test_bounds(self):
        for data in (None, {}, {'cases': []}, {'cases': sample()['cases'] * 4000}):
            with self.assertRaises(ValueError): evaluate(data)
        for k in (0, 101, True, 1.5):
            with self.assertRaises(ValueError): evaluate(sample(), k)
