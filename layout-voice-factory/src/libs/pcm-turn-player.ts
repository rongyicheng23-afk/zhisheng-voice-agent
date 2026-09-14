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
    this.underrunReported = false;
    this.port.onmessage = event => {
      const message = event.data || {};
      if (message.type === 'samples' && message.samples) {
        const samples = message.samples;
        for (let i = 0; i < samples.length; i += 1) {
          if (this.length >= this.capacity) {
            this.readIndex = (this.readIndex + 1) % this.capacity;
            this.length -= 1;
          }
          this.buffer[this.writeIndex] = samples[i];
          this.writeIndex = (this.writeIndex + 1) % this.capacity;
          this.length += 1;
        }
        this.underrunReported = false;
      }
      if (message.type === 'complete') this.finished = true;
      if (message.type === 'clear') {
        this.readIndex = 0; this.writeIndex = 0; this.length = 0;
        this.finished = false; this.wasPlaying = false; this.underrunReported = false;
      }
    };
  }
  process(_inputs, outputs) {
    const output = outputs[0][0];
    let copied = 0;
    for (let i = 0; i < output.length; i += 1) {
      if (this.length > 0) {
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
    if (this.wasPlaying && this.length === 0 && this.finished) {
      this.wasPlaying = false;
      this.finished = false;
      this.port.postMessage({ type: 'ended' });
    } else if (this.wasPlaying && copied < output.length && !this.finished && !this.underrunReported) {
      this.underrunReported = true;
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
  private expectedSegmentSequence = 0
  private expectedChunkSequence = 0
  private queuedSegmentSequence = 0
  private readonly pending = new Map<number, Map<number, Float32Array>>()
  private readonly onTurnPlaybackEnded?: (turnId: string) => void
  private readonly onMetric?: (metric: PlaybackMetric) => void

  constructor(options: PlayerOptions = {}) {
    this.onTurnPlaybackEnded = options.onTurnPlaybackEnded
    this.onMetric = options.onMetric
  }

  async start(turnId: string): Promise<void> {
    this.stop(false)
    this.activeTurnId = turnId
    this.completedTurnId = null
    this.expectedSegmentSequence = 0
    this.expectedChunkSequence = 0
    this.queuedSegmentSequence = 0
    this.context = new AudioContext({ sampleRate: 16000 })
    const workletUrl = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'application/javascript' }))
    try {
      await this.context.audioWorklet.addModule(workletUrl)
    } finally {
      URL.revokeObjectURL(workletUrl)
    }
    if (!this.context || this.activeTurnId !== turnId) return
    this.node = new AudioWorkletNode(this.context, 'pcm-ring-buffer')
    this.node.port.onmessage = event => this.handleWorkletMessage(turnId, event.data)
    this.node.connect(this.context.destination)
    await this.context.resume()
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
    if (!this.node || this.activeTurnId !== turnId || channels !== 1 || sampleRate !== 16000 || codec !== 'pcm_s16le') return
    if (segmentSequence < this.expectedSegmentSequence) return
    if (segmentSequence === this.expectedSegmentSequence && chunkSequence < this.expectedChunkSequence) return
    const samples = this.decodePcm16(base64)
    if (!samples) return
    const segment = this.pending.get(segmentSequence) || new Map<number, Float32Array>()
    segment.set(chunkSequence, samples)
    this.pending.set(segmentSequence, segment)
    this.flushContiguousChunks()
  }

  completeSegment(turnId: string, segmentSequence: number): void {
    if (this.activeTurnId !== turnId || segmentSequence < this.expectedSegmentSequence) return
    if (segmentSequence === this.expectedSegmentSequence && !this.pending.get(segmentSequence)?.size) {
      this.expectedSegmentSequence += 1
      this.expectedChunkSequence = 0
      this.flushContiguousChunks()
    }
  }

  markTurnCompleted(turnId: string): void {
    if (this.activeTurnId !== turnId || !this.node) return
    this.completedTurnId = turnId
    this.node.port.postMessage({ type: 'complete' })
  }

  stop(notify = true): void {
    const stoppedTurn = this.activeTurnId
    this.pending.clear()
    this.activeTurnId = null
    this.completedTurnId = null
    this.expectedSegmentSequence = 0
    this.expectedChunkSequence = 0
    this.queuedSegmentSequence = 0
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
    while (true) {
      const segment = this.pending.get(this.expectedSegmentSequence)
      const samples = segment?.get(this.expectedChunkSequence)
      if (!samples || !this.node) return
      this.node.port.postMessage({ type: 'samples', samples }, [samples.buffer])
      this.queuedSegmentSequence = this.expectedSegmentSequence
      segment!.delete(this.expectedChunkSequence)
      this.expectedChunkSequence += 1
      if (!segment!.size) this.pending.delete(this.expectedSegmentSequence)
    }
  }

  private handleWorkletMessage(turnId: string, message: { type?: string }): void {
    if (this.activeTurnId !== turnId) return
    if (message.type === 'started') this.metric(turnId, 'playback.started', this.queuedSegmentSequence)
    if (message.type === 'underrun') this.metric(turnId, 'playback.buffer_underrun', this.queuedSegmentSequence)
    if (message.type === 'ended' && this.completedTurnId === turnId) {
      this.metric(turnId, 'playback.stopped', this.queuedSegmentSequence)
      this.completedTurnId = null
      this.onTurnPlaybackEnded?.(turnId)
    }
  }

  private metric(turnId: string, event: PlaybackMetric['event'], segmentSequence?: number): void {
    this.onMetric?.({ turnId, event, segmentSequence, timestamp: new Date().toISOString() })
  }

  private decodePcm16(base64: string): Float32Array | null {
    try {
      const bytes = Uint8Array.from(atob(base64), char => char.charCodeAt(0))
      if (!bytes.byteLength || bytes.byteLength % 2) return null
      const pcm = new Int16Array(bytes.buffer, bytes.byteOffset, bytes.byteLength / 2)
      const samples = new Float32Array(pcm.length)
      for (let index = 0; index < pcm.length; index += 1) samples[index] = pcm[index] / 32768
      return samples
    } catch (_) {
      return null
    }
  }
}
