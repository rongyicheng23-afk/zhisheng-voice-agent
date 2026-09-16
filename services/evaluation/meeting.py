"""Compare saved meeting segments with independently human-labelled reference.

Run from repository root:
python -m services.evaluation.meeting --meeting detail.json --reference manual.json
manual.json: {"durationSeconds": 10, "reference": [{"start":0,"end":2,"speaker":"A"}]}
detail.json: authorized detail API response or downloaded anonymous evaluation data.
Outputs metrics only; never exports transcripts, personal names or audio.
"""
import argparse
import json
import math
from pathlib import Path
from services.evaluation.diarization import score


def hypothesis_from_meeting(payload):
    if not isinstance(payload, dict):
        raise ValueError('meeting must be an object')
    if 'data' in payload:
        if payload.get('code') != 200 or not isinstance(payload['data'], dict):
            raise ValueError('meeting API response must be successful')
        payload = payload['data']
    segments = payload.get('speakerSegments')
    if not isinstance(segments, list) or len(segments) > 10000:
        raise ValueError('meeting must contain at most 10000 speakerSegments')
    groups, result = {}, []
    unknown_count = 0
    for index, segment in enumerate(segments):
        if not isinstance(segment, dict):
            raise ValueError('invalid meeting segment')
        start, end = segment.get('startMs'), segment.get('endMs')
        if (type(start) not in (int, float) or type(end) not in (int, float)
                or not math.isfinite(start) or not math.isfinite(end) or not 0 <= start < end):
            raise ValueError('invalid meeting segment times')
        name, profile = segment.get('speakerName') or '', segment.get('speakerProfileId')
        if not isinstance(name, str) or (profile is not None and (type(profile) is not int or profile <= 0)):
            raise ValueError('invalid meeting speaker metadata')
        name = name.strip()
        if not name or name == '未知发言人':
            key = ('unknown', index)
            unknown_count += 1
        else:
            key = ('profile', profile) if profile is not None else ('local', name)
        if key not in groups:
            groups[key] = f'cluster-{len(groups) + 1}'
        result.append(dict(start=start / 1000, end=end / 1000, speaker=groups[key]))
    return result, unknown_count


def evaluate(meeting, manual):
    if not isinstance(manual, dict) or 'reference' not in manual or 'durationSeconds' not in manual:
        raise ValueError('manual reference requires reference and durationSeconds')
    hypothesis, unknown = hypothesis_from_meeting(meeting)
    metrics = score(manual['reference'], hypothesis, manual['durationSeconds'])
    # Never include a label mapping if a future metric version provides one.
    metrics.pop('mapping', None)
    return dict(metrics=metrics, segmentCount=len(hypothesis), unknownSegmentCount=unknown,
                unknownPolicy='each unknown segment is a separate hypothesis cluster',
                referenceRequirement='independent human annotation; never copy system output as reference')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--meeting', required=True, type=Path)
    parser.add_argument('--reference', required=True, type=Path)
    args = parser.parse_args()
    try:
        meeting = json.loads(args.meeting.read_text(encoding='utf-8'))
        manual = json.loads(args.reference.read_text(encoding='utf-8'))
        print(json.dumps(evaluate(meeting, manual), ensure_ascii=False, indent=2))
    except (ValueError, OSError):
        parser.exit(2, 'Evaluation failed; check JSON structure, readable files and valid time ranges.\n')


if __name__ == '__main__':
    main()
