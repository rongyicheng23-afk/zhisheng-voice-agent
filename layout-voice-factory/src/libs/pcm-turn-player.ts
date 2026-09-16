/**
 * Ordered PCM16 player backed by an AudioWorklet ring buffer.
 *
 * The gateway sends short PCM chunks while TTS is still generating later
 * chunks. Feeding every chunk to a separate AudioBufferSource makes it easy
 * for old or out-of-order audio to leak into the next turn. This player owns
 * one turn at a time, accepts only contiguous segment/chunk sequences and
 * writes Float32 samples into an AudioWorklet-side ring buffer.
 */
type PlaybackMetric = {
  turnId: string
  event: 'playback.started' | 'playback.stopped' | 'playback.buffer_underrun'
  segmentSequence?: number
  timestamp: string
}

type PlayerOptions = {
  onTurnPlaybackEnded?: (turnId: string) => void
  onMetric?: (metric: PlaybackMetric) => void
  onError?: (turnId: string, message: string) => void
}

const WORKLET_SOURCE = `
class PcmRingBufferProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.capacity = sampleRate * 18;
    this.buffer = new Float32Array(this.capacity);
    this.readIndex = 0;
    this.writeIndex = 0;
    this.length = 0;
    this.finished = false;
    this.wasPlaying = false;
    this.ready = false;
    // The first provider packet is not guaranteed to contain enough audio for
    // a clean utterance onset.  A longer one-time startup reserve avoids
    // swallowing the first word while an AudioWorklet/context is warming up.
    // It affects only the beginning of a turn; subsequent data continues to
    // stream through the same ring buffer.
    this.prebufferSamples = Math.max(1, Math.round(sampleRate * 0.8));
    this.underrunReported = false;
    this.consumed = 0;
    this.port.onmessage = event => {
      const message = event.data || {};
      if (message.type === 'samples' && message.samples) {
        const samples = message.samples;
        // Main-thread credits guarantee space. Never overwrite unplayed
        // samples if the transport violates that contract.
        if (samples.length > this.capacity - this.length) {
          this.port.postMessage({ type: 'overflow' });
          return;
        }
        for (let i = 0; i < samples.length; i += 1) {
          this.buffer[this.writeIndex] = samples[i];
          this.writeIndex = (this.writeIndex + 1) % this.capacity;
          this.length += 1;
        }
      }
      if (message.type === 'complete') this.finished = true;
      if (message.type === 'clear') {
        this.readIndex = 0; this.writeIndex = 0; this.length = 0;
        this.finished = false; this.wasPlaying = false; this.ready = false; this.underrunReported = false;
        this.consumed = 0;
      }
    };
  }
  process(_inputs, outputs) {
    const output = outputs[0][0];
    // Starting on the very first tiny WebSocket packet makes even a small
    // network jitter audible and can clip the first spoken word. Keep an
    // 800 ms startup reserve; after the final turn marker, play a short tail.
    if (!this.ready && this.length > 0 && (this.length >= this.prebufferSamples || this.finished)) {
      this.ready = true;
      this.underrunReported = false;
    }
    let copied = 0;
    for (let i = 0; i < output.length; i += 1) {
      if (this.ready && this.length > 0) {
        output[i] = this.buffer[this.readIndex];
        this.readIndex = (this.readIndex + 1) % this.capacity;
        this.length -= 1;
        copied += 1;
      } else output[i] = 0;
    }
    if (copied > 0 && !this.wasPlaying) {
      this.wasPlaying = true;
      this.port.postMessage({ type: 'started' });
    }
    this.consumed += copied;
    if (this.consumed >= 2048 || (this.length === 0 && this.consumed > 0)) {
      this.port.postMessage({ type: 'consumed', count: this.consumed });
      this.consumed = 0;
    }
    if (this.wasPlaying && this.length === 0 && this.finished) {
      this.wasPlaying = false;
      this.finished = false;
      this.port.postMessage({ type: 'ended' });
    } else if (this.wasPlaying && copied < output.length && !this.finished && !this.underrunReported) {
      this.underrunReported = true;
      this.ready = false;
      this.port.postMessage({ type: 'underrun' });
    }
    return true;
  }
}
registerProcessor('pcm-ring-buffer', PcmRingBufferProcessor);
`

export class PcmTurnPlayer {
  private context: AudioContext | null = null
  private node: AudioWorkletNode | null = null
  private activeTurnId: string | null = null
  private completedTurnId: string | null = null
  private outputSampleRate = 16000
  private expectedSegmentSequence = 0
  private expectedChunkSequence = 0
  private queuedSegmentSequence = 0
  private readonly pending = new Map<number, Map<number, Float32Array>>()
  // A later segment can finish TTS before the preceding one.  Keep its
  // completion marker until playback reaches it; otherwise the old player
  // would never advance past that segment and silently skip following speech.
  private readonly completedSegments = new Map<number, number>()
  private pendingSamples = 0
  private inFlightSamples = 0
  private chunkOffset = 0
  private completionSent = false
  private readonly onTurnPlaybackEnded?: (turnId: string) => void
  private readonly onMetric?: (metric: PlaybackMetric) => void
  private readonly onError?: (turnId: string, message: string) => void

  constructor(options: PlayerOptions = {}) {
    this.onTurnPlaybackEnded = options.onTurnPlaybackEnded
    this.onMetric = options.onMetric
    this.onError = options.onError
  }

  async start(turnId: string): Promise<void> {
    this.stop(false)
    this.activeTurnId = turnId
    this.completedTurnId = null
    this.expectedSegmentSequence = 0
    this.expectedChunkSequence = 0
    this.queuedSegmentSequence = 0
    this.completedSegments.clear()
    const context = new AudioContext({ sampleRate: 16000 })
    this.context = context
    // Browsers are allowed to choose the device rate (often 44.1/48 kHz),
    // even when 16 kHz was requested.  PCM from TTS must be resampled to the
    // rate the AudioWorklet actually runs at, otherwise speech becomes fast,
    // high-pitched, or unintelligible on some devices.
    this.outputSampleRate = context.sampleRate
    const workletUrl = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'application/javascript' }))
    try {
      await context.audioWorklet.addModule(workletUrl)
    } finally {
      URL.revokeObjectURL(workletUrl)
    }
    if (this.context !== context || this.activeTurnId !== turnId) return
    this.node = new AudioWorkletNode(context, 'pcm-ring-buffer')
    this.node.port.onmessage = event => this.handleWorkletMessage(turnId, event.data)
    this.node.connect(context.destination)
    await context.resume()
  }

  play(
    turnId: string,
    base64: string,
    sampleRate: number,
    channels: number,
    segmentSequence = 0,
    chunkSequence = 0,
    codec = 'pcm_s16le'
  ): void {
    if (!this.node || this.activeTurnId !== turnId) return
    if (channels !== 1 || !Number.isFinite(sampleRate) || sampleRate <= 0 || codec !== 'pcm_s16le') {
      this.fail('语音格式不受支持，已停止播报')
      return
    }
    if (segmentSequence < this.expectedSegmentSequence) return
    if (segmentSequence === this.expectedSegmentSequence && chunkSequence < this.expectedChunkSequence) return
    if (this.pending.get(segmentSequence)?.has(chunkSequence)) return
    const samples = this.decodePcm16(base64, sampleRate)
    if (!samples) {
      this.fail('收到的语音数据不完整，已停止播报，请重试')
      return
    }
    // Bounded local backlog: stop with an explicit error instead of silently
    // losing speech or growing memory indefinitely on a suspended device.
    if (this.pendingSamples + this.inFlightSamples + samples.length > this.outputSampleRate * 180) {
      this.fail('待播语音过长，已停止播报，请缩短问题后重试')
      return
    }
    const segment = this.pending.get(segmentSequence) || new Map<number, Float32Array>()
    segment.set(chunkSequence, samples)
    this.pending.set(segmentSequence, segment)
    this.pendingSamples += samples.length
    this.flushContiguousChunks()
  }

  completeSegment(turnId: string, segmentSequence: number, chunkCount: number): void {
    if (this.activeTurnId !== turnId || segmentSequence < this.expectedSegmentSequence) return
    if (!Number.isInteger(chunkCount) || chunkCount < 0) {
      this.fail('语音片段缺少完整性信息，请刷新页面后重试')
      return
    }
    this.completedSegments.set(segmentSequence, chunkCount)
    this.flushContiguousChunks()
  }

  markTurnCompleted(turnId: string): void {
    if (this.activeTurnId !== turnId || !this.node) return
    this.completedTurnId = turnId
    this.flushContiguousChunks()
  }

  stop(notify = true): void {
    const stoppedTurn = this.activeTurnId
    this.pending.clear()
    this.activeTurnId = null
    this.completedTurnId = null
    this.expectedSegmentSequence = 0
    this.expectedChunkSequence = 0
    this.queuedSegmentSequence = 0
    this.completedSegments.clear()
    this.pendingSamples = 0
    this.inFlightSamples = 0
    this.chunkOffset = 0
    this.completionSent = false
    if (this.node) {
      this.node.port.postMessage({ type: 'clear' })
      this.node.disconnect()
      this.node = null
    }
    if (this.context) {
      void this.context.close().catch(() => undefined)
      this.context = null
    }
    if (notify && stoppedTurn) this.metric(stoppedTurn, 'playback.stopped')
  }

  private flushContiguousChunks(): void {
    while (this.node) {
      const segment = this.pending.get(this.expectedSegmentSequence)
      const samples = segment?.get(this.expectedChunkSequence)
      if (!samples) {
        if (this.completedSegments.get(this.expectedSegmentSequence) === this.expectedChunkSequence) {
          this.completedSegments.delete(this.expectedSegmentSequence)
          this.expectedSegmentSequence += 1
          this.expectedChunkSequence = 0
          continue
        }
        if (this.completedTurnId && (this.pending.size || this.completedSegments.size || this.expectedChunkSequence)) {
          this.fail('语音片段缺失，已停止播报，请重试')
          return
        }
        break
      }
      const available = this.outputSampleRate * 18 - this.inFlightSamples
      if (available <= 0) break
      const count = Math.min(available, samples.length - this.chunkOffset, 8192)
      const packet = samples.slice(this.chunkOffset, this.chunkOffset + count)
      this.inFlightSamples += count
      this.pendingSamples -= count
      this.chunkOffset += count
      this.node.port.postMessage({ type: 'samples', samples: packet }, [packet.buffer])
      this.queuedSegmentSequence = this.expectedSegmentSequence
      if (this.chunkOffset === samples.length) {
        segment!.delete(this.expectedChunkSequence)
        this.expectedChunkSequence += 1
        this.chunkOffset = 0
        if (!segment!.size) this.pending.delete(this.expectedSegmentSequence)
      }
    }
    if (this.completedTurnId && this.node && !this.completionSent && !this.pending.size && !this.completedSegments.size) {
      this.completionSent = true
      this.node.port.postMessage({ type: 'complete' })
    }
  }

  private handleWorkletMessage(turnId: string, message: { type?: string, count?: number }): void {
    if (this.activeTurnId !== turnId) return
    if (message.type === 'consumed' && typeof message.count === 'number') {
      this.inFlightSamples = Math.max(0, this.inFlightSamples - message.count)
      this.flushContiguousChunks()
    }
    if (message.type === 'overflow') this.fail('语音播放缓冲异常，已停止播报，请重试')
    if (message.type === 'started') this.metric(turnId, 'playback.started', this.queuedSegmentSequence)
    if (message.type === 'underrun') this.metric(turnId, 'playback.buffer_underrun', this.queuedSegmentSequence)
    if (message.type === 'ended' && this.completedTurnId === turnId) {
      this.metric(turnId, 'playback.stopped', this.queuedSegmentSequence)
      this.completedTurnId = null
      this.onTurnPlaybackEnded?.(turnId)
    }
  }

  private fail(message: string): void {
    const turnId = this.activeTurnId
    this.stop()
    if (turnId) this.onError?.(turnId, message)
  }

  private metric(turnId: string, event: PlaybackMetric['event'], segmentSequence?: number): void {
    this.onMetric?.({ turnId, event, segmentSequence, timestamp: new Date().toISOString() })
  }

  private decodePcm16(base64: string, sourceSampleRate: number): Float32Array | null {
    try {
      const bytes = Uint8Array.from(atob(base64), char => char.charCodeAt(0))
      if (!bytes.byteLength || bytes.byteLength % 2) return null
      const pcm = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
      const samples = new Float32Array(bytes.byteLength / 2)
      for (let index = 0; index < samples.length; index += 1) samples[index] = pcm.getInt16(index * 2, true) / 32768
      return this.resample(samples, sourceSampleRate)
    } catch (_) {
      return null
    }
  }

  private resample(samples: Float32Array, sourceSampleRate: number): Float32Array {
    const targetSampleRate = this.outputSampleRate
    if (sourceSampleRate === targetSampleRate || samples.length < 2) return samples
    const targetLength = Math.max(1, Math.round(samples.length * targetSampleRate / sourceSampleRate))
    const converted = new Float32Array(targetLength)
    const ratio = sourceSampleRate / targetSampleRate
    for (let index = 0; index < targetLength; index += 1) {
      const position = index * ratio
      const lower = Math.min(samples.length - 1, Math.floor(position))
      const upper = Math.min(samples.length - 1, lower + 1)
      const fraction = position - lower
      converted[index] = samples[lower] + (samples[upper] - samples[lower]) * fraction
    }
    return converted
  }
}
