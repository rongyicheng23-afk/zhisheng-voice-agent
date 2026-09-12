import unittest
from uuid import uuid4

from protocol import SessionEventStream, TurnState


class SessionEventStreamTests(unittest.TestCase):
    def setUp(self):
        self.session_id = str(uuid4())
        self.turn_id = str(uuid4())
        self.events = SessionEventStream(self.session_id)

    def test_events_have_shared_ids_and_monotonic_sequences(self):
        started = self.events.start_turn(self.turn_id)
        delta = self.events.emit(self.turn_id, "llm.delta", text="你好")

        self.assertEqual(self.session_id, started.to_dict()["sessionId"])
        self.assertEqual(self.turn_id, delta.to_dict()["turnId"])
        self.assertEqual([1, 2], [started.sequence, delta.sequence])

    def test_cancel_is_the_only_terminal_state_and_rejects_late_events(self):
        self.events.start_turn(self.turn_id)
        cancelled = self.events.interrupt(self.turn_id)

        self.assertEqual("turn.cancelled", cancelled.event)
        self.assertEqual(TurnState.CANCELLED, self.events.state_of(self.turn_id))
        with self.assertRaisesRegex(ValueError, "non-active"):
            self.events.emit(self.turn_id, "audio.chunk", chunkSequence=1)

    def test_a_turn_cannot_have_two_terminal_events(self):
        self.events.start_turn(self.turn_id)
        self.events.emit(self.turn_id, "turn.completed")

        with self.assertRaisesRegex(ValueError, "non-active"):
            self.events.emit(self.turn_id, "turn.failed", code="late_error")


if __name__ == "__main__":
    unittest.main()
