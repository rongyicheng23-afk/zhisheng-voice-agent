/**
 * 音频录制管理器
 * 封装了音频录制、处理和发送的功能
 * 采用FunASR示例的分块办法和音频参数配置
 */
export class AudioRecorder {
  /** AudioContext实例，用于音频处理 */
  private audioContext: AudioContext | null = null
  /** ScriptProcessor节点，用于音频数据处理 */
  private processor: ScriptProcessorNode | null = null
  /** MediaStreamSource节点，用于音频流输入 */
  private source: MediaStreamAudioSourceNode | null = null
  /** 音频数据缓冲区 */
  private sampleBuf = new Int16Array()
  /** 音频数据块大小 - 使用FunASR的标准块大小 */
  private readonly chunkSize = 960
  /** 浏览器处理缓冲区，越大越稳但延迟越高 */
  private readonly processorBufferSize = 1024
  /** 是否正在录音 */
  private isRecording = false
  /** 是否已连接WebSocket */
  private isConnected = false
  /** 音频数据发送回调函数 */
  private onAudioDataCallback?: (data: ArrayBuffer) => void

  /**
   * 开始录音
   * 采用FunASR示例的音频参数配置和分块逻辑
   * @param onAudioData 音频数据发送回调函数
   */
  async startRecording(onAudioData: (data: ArrayBuffer) => void): Promise<void> {
    try {
      this.onAudioDataCallback = onAudioData
      this.isRecording = true

      // 获取麦克风音频流，采用FunASR示例的音频参数配置
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          sampleRate: 16000, // 16kHz采样率
          channelCount: 1,   // 单声道
          echoCancellation: true,   // 开启回声消除，提高语音质量
          noiseSuppression: true,   // 开启噪声抑制，减少背景噪音
          autoGainControl: true     // 开启自动增益控制，保持音量稳定
        }
      })
      
      // 创建AudioContext音频处理上下文
      this.audioContext = new AudioContext({ sampleRate: 16000 })
      this.source = this.audioContext.createMediaStreamSource(stream)
      this.processor = this.audioContext.createScriptProcessor(this.processorBufferSize, 1, 1)
      
      // 清空音频缓冲区
      this.sampleBuf = new Int16Array()
      
      // 音频数据处理函数，采用FunASR示例的分块逻辑
      this.processor.onaudioprocess = (event: AudioProcessingEvent) => {
        if (this.isRecording && this.isConnected) {
          const inputData = event.inputBuffer.getChannelData(0)
          
          // 转换为16位PCM格式
          const pcmData = new Int16Array(inputData.length)
          for (let i = 0; i < inputData.length; i++) {
            // 确保音频数据在有效范围内
            const sample = Math.max(-1, Math.min(1, inputData[i]))
            pcmData[i] = Math.round(sample * 32767)
          }
          
          // 采用FunASR示例的分块逻辑：先添加到缓冲区
          const newBuffer = new Int16Array(this.sampleBuf.length + pcmData.length)
          newBuffer.set(this.sampleBuf)
          newBuffer.set(pcmData, this.sampleBuf.length)
          this.sampleBuf = newBuffer
          
          // 分块发送音频数据，完全按照FunASR示例的方式
          while (this.sampleBuf.length >= this.chunkSize) {
            const sendBuf = this.sampleBuf.slice(0, this.chunkSize)
            this.sampleBuf = this.sampleBuf.slice(this.chunkSize, this.sampleBuf.length)
            
            if (this.onAudioDataCallback) {
              this.onAudioDataCallback(sendBuf.buffer)
            }
          }
        }
      }
      
      // 连接音频处理节点
      this.source.connect(this.processor)
      this.processor.connect(this.audioContext.destination)
      
      console.log('开始录音 - 采用FunASR分块方式')
      
    } catch (error) {
      console.error('录音失败:', error)
      throw new Error('无法访问麦克风，请检查权限设置')
    }
  }

  /**
   * 停止录音
   * 采用FunASR示例的停止逻辑
   * @returns 剩余的音频数据（如果有的话）
   */
  stopRecording(): ArrayBuffer | null {
    this.isRecording = false
    
    // 断开音频处理节点
    if (this.processor) {
      this.processor.disconnect()
    }
    if (this.source) {
      this.source.disconnect()
    }
    if (this.audioContext) {
      this.audioContext.close()
    }
    
    // 返回剩余的音频数据，按照FunASR示例的方式
    let remainingData: ArrayBuffer | null = null
    if (this.sampleBuf.length > 0) {
      remainingData = this.sampleBuf.buffer
      console.log('剩余音频数据，大小:', this.sampleBuf.buffer.byteLength, '字节')
      // 注意：这里不清空sampleBuf，让调用方处理
    }
    
    console.log('录音已停止 - FunASR方式')
    return remainingData
  }

  /**
   * 设置连接状态
   * @param connected 是否已连接
   */
  setConnected(connected: boolean): void {
    this.isConnected = connected
  }

  /**
   * 检查是否正在录音
   * @returns 是否正在录音
   */
  isRecordingActive(): boolean {
    return this.isRecording
  }

  /**
   * 清理资源
   */
  dispose(): void {
    if (this.isRecording) {
      this.stopRecording()
    }
  }
} 
