/* PCM16 little-endian mono playback primitive. Integration is not enabled yet.
 * The owning AudioContext must use the negotiated sampleRate.
 * Never pass Opus/WAV container bytes directly to this processor.
 */
class TurnPcmPlayer extends AudioWorkletProcessor {
  constructor() {
    super()
    this.samples = new Float32Array(sampleRate * 4)
    this.turnId = null
    this.readIndex = 0
    this.writeIndex = 0
    this.size = 0
    this.segmentSequence = 0
    this.chunkSequence = 0
    this.sequence = 0
    this.started = false
    this.ended = false
    this.underflow = false
    this.port.onmessage = ({ data }) => this.receive(data)
  }

  reset(turnId) {
    this.turnId = turnId
    this.readIndex = this.writeIndex = this.size = 0
    this.segmentSequence = this.chunkSequence = this.sequence = 0
    this.started = this.ended = this.underflow = false
  }

  notify(event, extra = {}) {
    this.port.postMessage({ event, turnId: this.turnId, ...extra })
  }

  fail(code) {
    this.notify('playback.failed', { code })
    this.reset(null)
  }

  receive(message) {
    if (!message || typeof message !== 'object') return
    // Only the trusted UI controller may send turn.start, never route arbitrary
    // remote event names here. It must reject stale session/turn events first.
    if (message.event === 'turn.start' && typeof message.turnId === 'string' && message.turnId) {
      this.reset(message.turnId)
      return
    }
    if (!this.turnId || message.turnId !== this.turnId) return
    if (message.event === 'turn.interrupt') {
      this.size = 0
      this.notify('playback.stopped')
      this.reset(null)
      return
    }
    if (message.event === 'audio.completed') {
      this.ended = true
      return
    }
    if (message.event !== 'audio.chunk' || this.ended) return
    if (!Number.isSafeInteger(message.sequence) || message.sequence <= this.sequence) return
    const segment = message.segmentSequence
    const chunk = message.chunkSequence
    if (!Number.isSafeInteger(segment) || !Number.isSafeInteger(chunk) ||
        !((segment === this.segmentSequence && chunk === this.chunkSequence + 1) ||
          (segment === this.segmentSequence + 1 && chunk === 1))) {
      this.fail('AUDIO_SEQUENCE_GAP')
      return
    }
    if (message.codec !== 'pcm16' || message.channels !== 1 || message.sampleRate !== sampleRate ||
        !(message.pcm instanceof ArrayBuffer) || !message.pcm.byteLength || message.pcm.byteLength % 2) {
      this.fail('UNSUPPORTED_AUDIO_FORMAT')
      return
    }
    const count = message.pcm.byteLength / 2
    if (count > this.samples.length - this.size) {
      // Upstream must pace delivery based on buffer status, never silently
      // overwrite queued speech or grow an unbounded array.
      this.fail('AUDIO_BUFFER_OVERFLOW')
      return
    }
    const pcm = new DataView(message.pcm)
    for (let index = 0; index < count; index++) {
      this.samples[this.writeIndex] = pcm.getInt16(index * 2, true) / 32768
      this.writeIndex = (this.writeIndex + 1) % this.samples.length
    }
    this.size += count
    this.sequence = message.sequence
    this.segmentSequence = segment
    this.chunkSequence = chunk
    this.notify('playback.buffer', { bufferedFrames: this.size, capacityFrames: this.samples.length })
  }

  process(_inputs, outputs) {
    const output = outputs[0] && outputs[0][0]
    if (!output) return true
    output.fill(0)
    if (!this.turnId) return true
    if (this.size && !this.started) {
      this.started = true
      this.notify('playback.started')
    }
    const count = Math.min(output.length, this.size)
    for (let index = 0; index < count; index++) {
      output[index] = this.samples[this.readIndex]
      this.readIndex = (this.readIndex + 1) % this.samples.length
    }
    this.size -= count
    if (this.ended && !this.size) {
      this.notify('playback.completed')
      this.reset(null)
    } else if (count < output.length && this.started) {
      if (!this.underflow) this.notify('playback.underflow')
      this.underflow = true
    } else {
      this.underflow = false
    }
    return true
  }
}

registerProcessor('turn-pcm-player', TurnPcmPlayer)
