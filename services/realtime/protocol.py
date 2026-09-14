"""Ordered realtime event protocol used by the future Python gateway.

The document requires every outbound event to be traceable, ordered and bound
to a turn.  This module keeps that rule independent from concrete ASR, LLM or
TTS providers.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Mapping
from uuid import UUID


class TurnState(str, Enum):
    ACTIVE = "ACTIVE"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"
    FAILED = "FAILED"


_TERMINAL_EVENTS: Mapping[str, TurnState] = {
    "turn.completed": TurnState.COMPLETED,
    "turn.cancelled": TurnState.CANCELLED,
    "turn.failed": TurnState.FAILED,
}


@dataclass(frozen=True)
class RealtimeEvent:
    session_id: str
    turn_id: str
    event: str
    sequence: int
    timestamp: str
    payload: Mapping[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "sessionId": self.session_id,
            "turnId": self.turn_id,
            "event": self.event,
            "sequence": self.sequence,
            "timestamp": self.timestamp,
            **self.payload,
        }


class SessionEventStream:
    """Creates one monotonic event stream and enforces one terminal turn state."""

    def __init__(self, session_id: str):
        self._validate_id(session_id, "sessionId")
        self.session_id = session_id
        self._next_sequence = 1
        self._states: dict[str, TurnState] = {}

    def start_turn(self, turn_id: str) -> RealtimeEvent:
        self._validate_id(turn_id, "turnId")
        if self._states.get(turn_id) == TurnState.ACTIVE:
            raise ValueError("turn already active")
        self._states[turn_id] = TurnState.ACTIVE
        return self.emit(turn_id, "turn.start")

    def emit(self, turn_id: str, event: str, **payload: Any) -> RealtimeEvent:
        self._validate_id(turn_id, "turnId")
        state = self._states.get(turn_id)
        if state != TurnState.ACTIVE:
            raise ValueError("cannot emit an event for a non-active turn")
        terminal = _TERMINAL_EVENTS.get(event)
        if terminal:
            self._states[turn_id] = terminal
        return self._new_event(turn_id, event, payload)

    def interrupt(self, turn_id: str) -> RealtimeEvent:
        return self.emit(turn_id, "turn.cancelled")

    def state_of(self, turn_id: str) -> TurnState | None:
        return self._states.get(turn_id)

    def _new_event(self, turn_id: str, event: str, payload: Mapping[str, Any]) -> RealtimeEvent:
        emitted = RealtimeEvent(
            session_id=self.session_id,
            turn_id=turn_id,
            event=event,
            sequence=self._next_sequence,
            timestamp=datetime.now(timezone.utc).isoformat(),
            payload=payload,
        )
        self._next_sequence += 1
        return emitted

    @staticmethod
    def _validate_id(value: str, field: str) -> None:
        try:
            UUID(value)
        except (TypeError, ValueError) as error:
            raise ValueError(f"{field} must be a UUID") from error
