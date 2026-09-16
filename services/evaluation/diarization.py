"""Offline interval DER: optimal one-to-one mapping, collar=0, overlap included.

Input JSON: {durationSeconds, reference: [{start,end,speaker}], hypothesis: [...]}.
Reference labels must come from human annotation, not the system's own output.
No audio, identities, credentials, or network calls are needed. Requires scipy.
Metric convention: https://pyannote.github.io/pyannote-metrics/reference.html
"""
import argparse
import json
import math
from pathlib import Path


def score(reference, hypothesis, duration):
    from scipy.optimize import linear_sum_assignment
    import numpy as np
    if type(duration) not in (int, float) or not math.isfinite(duration) or duration <= 0:
        raise ValueError('durationSeconds must be positive and finite')
    def validate(items):
        if not isinstance(items, list) or len(items) > 10000:
            raise ValueError('segments must be a list of at most 10000 entries')
        result = []
        for entry in items:
            if not isinstance(entry, dict) or not {'start', 'end', 'speaker'} <= entry.keys():
                raise ValueError('segment must contain start, end and speaker')
            start, end, speaker = entry['start'], entry['end'], entry['speaker']
            if (type(start) not in (int, float) or type(end) not in (int, float)
                    or not math.isfinite(start) or not math.isfinite(end) or not 0 <= start < end <= duration
                    or not isinstance(speaker, str) or not speaker.strip()):
                raise ValueError('invalid segment time or speaker label')
            result.append((start, end, speaker))
        return result
    ref, hyp = validate(reference), validate(hypothesis)
    rlabels, hlabels = sorted({s for _, _, s in ref}), sorted({s for _, _, s in hyp})
    if max(len(rlabels), len(hlabels)) > 100:
        raise ValueError('at most 100 speaker labels per recording')
    # Sweep exact boundaries rather than quantizing timestamps into frames.
    events = {}
    for side, segments in enumerate((ref, hyp)):
        for start, end, label in segments:
            events.setdefault(start, []).append((side, label, 1))
            events.setdefault(end, []).append((side, label, -1))
    events.setdefault(0, []); events.setdefault(duration, [])
    active = [{}, {}]
    intervals = []
    previous = 0
    weights = np.zeros((len(rlabels), len(hlabels)))
    ri, hi = {s: i for i, s in enumerate(rlabels)}, {s: i for i, s in enumerate(hlabels)}
    for time in sorted(events):
        if time > previous:
            r, h = set(active[0]), set(active[1])
            span = time - previous
            intervals.append((span, r, h))
            for a in r:
                for b in h:
                    weights[ri[a], hi[b]] += span
        for side, label, change in events[time]:
            active[side][label] = active[side].get(label, 0) + change
            if active[side][label] == 0: del active[side][label]
        previous = time
    rows, cols = linear_sum_assignment(-weights)
    mapping = {hlabels[h]: rlabels[r] for r, h in zip(rows, cols)}
    total = missed = false_alarm = confused = 0.0
    for span, r, h in intervals:
        correct = len(r & {mapping[label] for label in h if label in mapping})
        total += span * len(r)
        missed += span * max(len(r) - len(h), 0)
        false_alarm += span * max(len(h) - len(r), 0)
        confused += span * (min(len(r), len(h)) - correct)
    return dict(der=None if total == 0 else (missed + false_alarm + confused) / total,
                referenceSpeakerSeconds=total, missedSpeakerSeconds=missed,
                falseAlarmSpeakerSeconds=false_alarm, confusionSpeakerSeconds=confused,
                collarSeconds=0, overlapIncluded=True, mappingMethod='optimal-one-to-one',
                evaluatedDurationSeconds=duration)


def score_corpus(recordings):
    """Aggregate speaker-seconds, never average per-recording DER percentages.

    Silent references still contribute false alarms to the corpus numerator.
    Speaker mapping is computed independently for each recording.
    """
    if not isinstance(recordings, list) or not 1 <= len(recordings) <= 1000:
        raise ValueError('recordings must contain 1 to 1000 recordings')
    totals = dict.fromkeys(('referenceSpeakerSeconds', 'missedSpeakerSeconds',
                           'falseAlarmSpeakerSeconds', 'confusionSpeakerSeconds',
                           'evaluatedDurationSeconds'), 0.0)
    silent = 0
    for recording in recordings:
        if not isinstance(recording, dict) or not {'reference', 'hypothesis', 'durationSeconds'} <= recording.keys():
            raise ValueError('recording must contain reference, hypothesis and durationSeconds')
        result = score(recording['reference'], recording['hypothesis'], recording['durationSeconds'])
        silent += result['referenceSpeakerSeconds'] == 0
        for key in totals:
            totals[key] += result[key]
    denominator = totals['referenceSpeakerSeconds']
    errors = sum(totals[key] for key in ('missedSpeakerSeconds', 'falseAlarmSpeakerSeconds', 'confusionSpeakerSeconds'))
    return dict(totals, der=None if denominator == 0 else errors / denominator,
                recordingCount=len(recordings), recordingsWithoutReferenceSpeech=silent,
                collarSeconds=0, overlapIncluded=True, mappingMethod='optimal-one-to-one-per-recording')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('annotations', type=Path)
    args = parser.parse_args()
    if args.annotations.stat().st_size > 10 * 1024 * 1024:
        parser.error('annotation file exceeds 10 MiB')
    data = json.loads(args.annotations.read_text(encoding='utf-8'))
    try:
        if not isinstance(data, dict):
            raise ValueError('input must be an object')
        result = (score_corpus(data['recordings']) if 'recordings' in data
                  else score(data['reference'], data['hypothesis'], data['durationSeconds']))
    except (ValueError, KeyError) as error:
        parser.error(str(error))
    print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
