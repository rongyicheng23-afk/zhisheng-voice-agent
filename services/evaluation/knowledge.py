"""Offline, independently annotated knowledge-QA evaluation; no model requests.

Input contains opaque source IDs and human labels, not document text or API keys.
Run: python -m services.evaluation.knowledge --input reviewed.json --k 3
"""
import argparse
import json
import math
from pathlib import Path


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def source_ids(value):
    if (not isinstance(value, list) or len(value) > 100 or
            any(not isinstance(item, str) or not item.strip() or len(item) > 256 for item in value) or
            len(set(value)) != len(value)):
        raise ValueError('source IDs must be a unique bounded list')
    return value


def evaluate(payload, k=3):
    if type(k) is not int or not 1 <= k <= 100:
        raise ValueError('k must be between 1 and 100')
    if not isinstance(payload, dict) or not isinstance(payload.get('cases'), list):
        raise ValueError('cases required')
    cases = payload['cases']
    if not 1 <= len(cases) <= 10000:
        raise ValueError('require 1 to 10000 cases')
    seen, recalls, reciprocal_ranks, timings = set(), [], [], []
    unanswerable = correct_refusals = answered = failed = supported = reviewed = claim_count = 0
    answerable_count = answerable_answers = 0
    for case in cases:
        if not isinstance(case, dict):
            raise ValueError('invalid case')
        case_id = case.get('id')
        if not isinstance(case_id, str) or not case_id.strip() or len(case_id) > 256 or case_id in seen:
            raise ValueError('case IDs must be unique')
        seen.add(case_id)
        relevant = set(source_ids(case.get('relevantSourceIds')))
        retrieved = source_ids(case.get('retrievedSourceIds'))[:k]
        answerable, outcome, claims = case.get('answerable'), case.get('outcome'), case.get('claims')
        if type(answerable) is not bool or outcome not in ('answered', 'refused', 'failed'):
            raise ValueError('invalid outcome or human answerability label')
        if answerable and not relevant:
            raise ValueError('answerable cases need independently labelled relevant sources')
        if not isinstance(claims, list) or len(claims) > 6 or bool(claims) != (outcome == 'answered'):
            raise ValueError('answered cases require claims; other outcomes must not have claims')
        for claim in claims:
            if not isinstance(claim, dict) or 'supported' not in claim:
                raise ValueError('human support label required (null if not reviewed)')
            label = claim['supported']
            if label is not None and type(label) is not bool:
                raise ValueError('support label must be boolean or null')
            claim_count += 1
            reviewed += label is not None
            supported += label is True
        if relevant:
            recalls.append(len(relevant.intersection(retrieved)) / len(relevant))
            reciprocal_ranks.append(next((1 / (i + 1) for i, item in enumerate(retrieved) if item in relevant), 0))
        unanswerable += not answerable
        correct_refusals += not answerable and outcome == 'refused'
        answerable_count += answerable
        answerable_answers += answerable and outcome == 'answered'
        answered += outcome == 'answered'
        failed += outcome == 'failed'
        timing = case.get('firstAudioMs')
        if timing is not None:
            if type(timing) not in (int, float) or not math.isfinite(timing) or not 0 <= timing <= 600000:
                raise ValueError('invalid firstAudioMs')
            if outcome == 'failed':
                raise ValueError('failed cases cannot contribute success latency')
            timings.append(timing)
    timings.sort()
    # Nearest-rank percentiles; small samples remain visible, not production claims.
    percentile = lambda p: timings[math.ceil(len(timings) * p) - 1] if timings else None
    return {
        'caseCount': len(cases), 'k': k, 'retrievalCaseCount': len(recalls),
        'macroRecallAtK': ratio(sum(recalls), len(recalls)),
        'meanReciprocalRankAtK': ratio(sum(reciprocal_ranks), len(reciprocal_ranks)),
        'unanswerableCaseCount': unanswerable,
        'correctRefusalRate': ratio(correct_refusals, unanswerable),
        'answerableCaseCount': answerable_count,
        'answerableAnswerRate': ratio(answerable_answers, answerable_count),
        'answeredCaseCount': answered, 'failedCaseCount': failed,
        'claimCount': claim_count, 'reviewedClaimCount': reviewed,
        'humanReviewCoverage': ratio(reviewed, claim_count),
        'humanSupportedClaimRate': ratio(supported, reviewed),
        'latencySampleCount': len(timings),
        'firstAudioP50Ms': percentile(.5), 'firstAudioP95Ms': percentile(.95),
        'latencyMethod': 'nearest rank; optional successful answered/refused cases only',
        'referenceRequirement': 'independent human labels, not model self-grading; no claims of production quality',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--k', type=int, default=3)
    args = parser.parse_args()
    try:
        with args.input.open('rb') as handle:
            raw = handle.read(5 * 1024 * 1024 + 1)
        if len(raw) > 5 * 1024 * 1024:
            raise ValueError('input exceeds 5 MiB')
        result = evaluate(json.loads(raw), args.k)
    except (OSError, ValueError, TypeError):
        parser.exit(2, 'Invalid evaluation input; check file format, bounds and independent labels.\n')
    print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
