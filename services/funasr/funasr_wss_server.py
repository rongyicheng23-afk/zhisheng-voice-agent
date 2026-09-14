import asyncio
import json
import websockets
import time
import logging
import tracemalloc
import numpy as np
import argparse
import ssl

# 创建命令行参数解析器
parser = argparse.ArgumentParser()
# 添加主机参数
parser.add_argument(
    "--host", type=str, default="127.0.0.1", required=False, help="host ip, localhost, 0.0.0.0"
)
# 添加端口参数
parser.add_argument("--port", type=int, default=10095, required=False, help="grpc server port")
# 添加ASR模型参数   
parser.add_argument(
    "--asr_model",
    type=str,
    default="iic/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-pytorch",
    help="model from modelscope",
)
# 添加ASR模型版本参数
parser.add_argument("--asr_model_revision", type=str, default="v2.0.4", help="")
# 添加在线ASR模型参数
parser.add_argument(
    "--asr_model_online",
    type=str,
    default="iic/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online",
    help="model from modelscope",
)
# 添加在线ASR模型版本参数
parser.add_argument("--asr_model_online_revision", type=str, default="v2.0.4", help="")
# 添加VAD模型参数  vad 语音活动检测模型
parser.add_argument(
    "--vad_model",
    type=str,
    default="iic/speech_fsmn_vad_zh-cn-16k-common-pytorch",
    help="model from modelscope",
)
# 添加VAD模型版本参数
parser.add_argument("--vad_model_revision", type=str, default="v2.0.4", help="")
# 添加PUNC模型参数  punc 标点符号预测模型
parser.add_argument(
    "--punc_model",
    type=str,
    default="iic/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727",
    help="model from modelscope",
)
# 添加PUNC模型版本参数
parser.add_argument("--punc_model_revision", type=str, default="v2.0.4", help="")
# 添加GPU参数
parser.add_argument("--ngpu", type=int, default=1, help="0 for cpu, 1 for gpu")
# 添加设备参数
parser.add_argument("--device", type=str, default="cuda", help="cuda, cpu")
# 添加CPU参数
parser.add_argument("--ncpu", type=int, default=4, help="cpu cores")
# 添加SSL证书文件参数
parser.add_argument(
    "--certfile",
    type=str,
    default="../../ssl_key/server.crt",
    required=False,
    help="certfile for ssl",
)
# 添加SSL密钥文件参数
parser.add_argument(
    "--keyfile",
    type=str,
    default="../../ssl_key/server.key",
    required=False,
    help="keyfile for ssl",
)
# 命令行参数存储在args中
args = parser.parse_args()
# 创建一个集合用于存储WebSocket用户
websocket_users = set()

print("model loading")
from funasr import AutoModel

# asr
model_asr = AutoModel(
    model=args.asr_model,
    model_revision=args.asr_model_revision,
    ngpu=args.ngpu,
    ncpu=args.ncpu,
    device=args.device,
    disable_pbar=True,
    disable_log=True,
)
# asr 在线ASR模型
model_asr_streaming = AutoModel(
    model=args.asr_model_online,
    model_revision=args.asr_model_online_revision,
    ngpu=args.ngpu,
    ncpu=args.ncpu,
    device=args.device,
    disable_pbar=True,
    disable_log=True,
)
# vad 语音活动检测模型
model_vad = AutoModel(
    model=args.vad_model,
    model_revision=args.vad_model_revision,
    ngpu=args.ngpu,
    ncpu=args.ncpu,
    device=args.device,
    disable_pbar=True,
    disable_log=True,
    # chunk_size=60,
)
# punc 标点符号预测模型
if args.punc_model != "":
    model_punc = AutoModel(
        model=args.punc_model,
        model_revision=args.punc_model_revision,
        ngpu=args.ngpu,
        ncpu=args.ncpu,
        device=args.device,
        disable_pbar=True,
        disable_log=True,
    )
else:
    model_punc = None


print("model loaded! only support one client at the same time now!!!!")


async def ws_reset(websocket):
    print("ws reset now, total num is ", len(websocket_users))
    # 重置WebSocket用户的状态
    websocket.status_dict_asr_online["cache"] = {}
    # 设置在线ASR模型的状态为最终状态
    websocket.status_dict_asr_online["is_final"] = True
    # 重置语音活动检测模型的状态
    websocket.status_dict_vad["cache"] = {}
    # 设置语音活动检测模型的状态为最终状态
    websocket.status_dict_vad["is_final"] = True
    # 设置标点符号预测模型的状态为最终状态
    websocket.status_dict_punc["cache"] = {}

    await websocket.close()

# 清除WebSocket用户
async def clear_websocket():
    for websocket in websocket_users:
        await ws_reset(websocket)
    websocket_users.clear()

# 处理WebSocket请求
async def ws_serve(websocket, path):
    
    frames = [] #存储所有接收到的音频帧，用于VAD处理和离线ASR处理
    frames_asr = [] #存储离线ASR处理后的音频帧
    frames_asr_online = [] #存储在线ASR处理后的音频帧
    global websocket_users #存储所有WebSocket用户
    # await clear_websocket()
    websocket_users.add(websocket) #将当前WebSocket用户添加到集合中
    websocket.status_dict_asr = {} #初始化离线ASR模型的状态
    websocket.status_dict_asr_online = {"cache": {}, "is_final": False} #初始化在线ASR模型的状态
    websocket.status_dict_vad = {"cache": {}, "is_final": False} #初始化语音活动检测模型的状态
    websocket.status_dict_punc = {"cache": {}} #初始化标点符号预测模型的状态
    websocket.chunk_interval = 10 #初始化音频帧间隔
    websocket.vad_pre_idx = 0 # 初始化VAD预处理索引
    speech_start = False # 初始化语音开始标志
    speech_end_i = -1 # 初始化语音结束索引
    websocket.wav_name = "microphone" # 初始化音频文件名
    websocket.mode = "2pass" # 初始化模式
    print("new user connected", flush=True) # 打印新用户连接信息 flush=True表示立即输出到控制台

    # 处理WebSocket请求
    try:
        async for message in websocket:
            if isinstance(message, str): # 如果消息是字符串
                messagejson = json.loads(message) # 将消息转换为JSON对象
                if "is_speaking" in messagejson: # 如果消息包含is_speaking字段
                    websocket.is_speaking = messagejson["is_speaking"] # 设置当前是否在说话的标志
                    websocket.status_dict_asr_online["is_final"] = not websocket.is_speaking # 如果不再说话，则设置在线ASR模型的状态为最终状态
                if "chunk_interval" in messagejson: # 如果消息包含chunk_interval字段
                    websocket.chunk_interval = messagejson["chunk_interval"] # 设置音频帧间隔
                if "wav_name" in messagejson: # 如果消息包含wav_name字段
                    websocket.wav_name = messagejson.get("wav_name") # 设置音频来源名称
                if "chunk_size" in messagejson: 
                    chunk_size = messagejson["chunk_size"] # 设置音频帧大小
                    if isinstance(chunk_size, str): # 如果音频帧大小是字符串
                        chunk_size = chunk_size.split(",") # 按逗号分割
                    websocket.status_dict_asr_online["chunk_size"] = [int(x) for x in chunk_size] # 转换为整数列表并存储到在线ASR状态中
                if "encoder_chunk_look_back" in messagejson: # 如果消息包含encoder_chunk_look_back字段
                    websocket.status_dict_asr_online["encoder_chunk_look_back"] = messagejson[
                        "encoder_chunk_look_back"
                    ]# 设置编码器回看的音频块数量
                if "decoder_chunk_look_back" in messagejson:
                    websocket.status_dict_asr_online["decoder_chunk_look_back"] = messagejson[
                        "decoder_chunk_look_back"
                    ]# 设置解码器回看的音频块数量
                if "hotwords" in messagejson:
                    websocket.status_dict_asr["hotword"] = messagejson["hotwords"]# 设置热词，提高特定词汇的识别准确率
                if "mode" in messagejson:
                    websocket.mode = messagejson["mode"] # 设置识别模式：online、offline或2pass

            websocket.status_dict_vad["chunk_size"] = int(
                websocket.status_dict_asr_online["chunk_size"][1] * 60 / websocket.chunk_interval
            ) # 根据在线ASR的chunk_size和chunk_interval计算VAD的chunk_size
            # 如果有在线ASR帧、离线ASR帧或消息不是字符串（即音频数据）
            if len(frames_asr_online) > 0 or len(frames_asr) >= 0 or not isinstance(message, str):
                if not isinstance(message, str): # 如果消息不是字符串（即音频数据）
                    frames.append(message) # 将音频帧添加到总帧列表
                    duration_ms = len(message) // 32 # 计算音频帧的持续时间（毫秒）
                    websocket.vad_pre_idx += duration_ms # 更新VAD预处理索引

                    # asr online
                    frames_asr_online.append(message)
                    websocket.status_dict_asr_online["is_final"] = speech_end_i != -1 # speech_end_i是VAD检测到的语音结束时间索引，如果为-1，则表示没有检测到语音结束
                    if (
                        len(frames_asr_online) % websocket.chunk_interval == 0 
                        or websocket.status_dict_asr_online["is_final"] #当累积的音频帧数达到chunk_interval的倍数时触发 或 语音结束标志为True时触发
                    ):
                        if websocket.mode == "2pass" or websocket.mode == "online": # 如果模式为2pass或online
                            audio_in = b"".join(frames_asr_online) # 将所有的音频帧连接成一个完整的字节串
                            try:
                                await async_asr_online(websocket, audio_in) # 调用在线ASR处理函数
                            except:
                                print("error in asr streaming (request contents omitted)")
                        frames_asr_online = [] # 清空在线ASR帧列表
                    if speech_start:
                        frames_asr.append(message) # 将音频帧添加到离线ASR帧列表
                    # vad online
                    try:
                        speech_start_i, speech_end_i = await async_vad(websocket, message) # 调用语音活动检测函数
                    except:
                        print("error in vad")
                    if speech_start_i != -1:
                        speech_start = True
                        # 计算音频帧的偏移量 vad检测到语音开始的时间索引与当前音频帧的开始时间索引的差值
                        beg_bias = (websocket.vad_pre_idx - speech_start_i) // duration_ms
                        frames_pre = frames[-beg_bias:] # 获取从音频帧偏移量开始的音频帧
                        frames_asr = [] # 清空离线ASR帧列表
                        frames_asr.extend(frames_pre) # 将回退帧加入离线 ASR 列表，保证从语音开头开始处理
                # asr punc offline 
                if speech_end_i != -1 or not websocket.is_speaking:
                    # print("vad end point")
                    if websocket.mode == "2pass" or websocket.mode == "offline":
                        audio_in = b"".join(frames_asr)
                        try:
                            await async_asr(websocket, audio_in)
                        except:
                            print("error in asr offline")
                    frames_asr = []
                    speech_start = False
                    frames_asr_online = []
                    websocket.status_dict_asr_online["cache"] = {}
                    if not websocket.is_speaking:
                        websocket.vad_pre_idx = 0
                        frames = []
                        websocket.status_dict_vad["cache"] = {}
                        # All results for this stop request have been sent. The
                        # browser must drain to this marker, not a fixed delay.
                        await websocket.send(json.dumps({"event": "asr.completed"}))
                    else:
                        frames = frames[-20:] # 如果还在说话 只保留最后20个音频帧

    except websockets.ConnectionClosed:
        print("ConnectionClosed", flush=True)
        await ws_reset(websocket)
        websocket_users.remove(websocket)
    except websockets.InvalidState:
        print("InvalidState...")
    except Exception as e:
        print("WebSocket processing failed:", type(e).__name__)

# 语音活动检测函数
async def async_vad(websocket, audio_in):
    # 调用VAD模型生成 语音活动检测结果
    # 返回语音段信息，格式：[[start_time, end_time], ...]
    segments_result = model_vad.generate(input=audio_in, **websocket.status_dict_vad)[0]["value"]
    # print(segments_result)

    speech_start = -1
    speech_end = -1
    # 如果语音活动检测结果为空或长度大于1，则返回-1
    if len(segments_result) == 0 or len(segments_result) > 1:
        return speech_start, speech_end
    # 如果语音活动检测结果不为空或长度为1，则返回语音开始时间
    if segments_result[0][0] != -1:
        speech_start = segments_result[0][0]
    # 如果语音活动检测结果不为空或长度为1，则返回语音结束时间
    if segments_result[0][1] != -1:
        speech_end = segments_result[0][1]
    return speech_start, speech_end

# 离线ASR处理函数
async def async_asr(websocket, audio_in):
    # 如果音频帧长度大于0，则调用离线ASR模型生成识别结果
    if len(audio_in) > 0:
        # print(len(audio_in))
        # 调用离线ASR模型生成识别结果
        # 返回识别结果，格式：{"text": "识别文本", "timestamp": [...]}
        rec_result = model_asr.generate(input=audio_in, **websocket.status_dict_asr)[0]
        # print("offline_asr, ", rec_result)
        if model_punc is not None and len(rec_result["text"]) > 0: # 如果标点符号预测模型存在 且识别结果不为空
            # print("offline, before punc", rec_result, "cache", websocket.status_dict_punc)
            # 调用标点符号预测模型生成识别结果
            rec_result = model_punc.generate(
                input=rec_result["text"], **websocket.status_dict_punc
            )[0]
            # print("offline, after punc", rec_result)
        if len(rec_result["text"]) > 0: # 如果识别结果不为空
            # print("offline", rec_result)
            mode = "2pass-offline" if "2pass" in websocket.mode else websocket.mode # 如果模式为2pass，则设置模式为2pass-offline
            # json.dumps()：将Python对象序列化为JSON字符串
            message = json.dumps(
                {
                    "mode": mode,
                    "text": rec_result["text"],
                    "wav_name": websocket.wav_name,
                    "is_final": websocket.is_speaking,
                }
            )
            await websocket.send(message) # 异步发送识别结果到客户端

    else:# 处理音频帧长度为0的情况
        mode = "2pass-offline" if "2pass" in websocket.mode else websocket.mode
        message = json.dumps(
            {
                "mode": mode,
                "text": "",
                "wav_name": websocket.wav_name,
                "is_final": websocket.is_speaking,
            }
        )
        await websocket.send(message)    

# 在线ASR处理函数
async def async_asr_online(websocket, audio_in):
    # 如果音频帧长度大于0，则调用在线ASR模型生成识别结果
    if len(audio_in) > 0:
        # print(websocket.status_dict_asr_online.get("is_final", False))
        # 调用在线ASR模型生成识别结果 model_asr_streaming 低延迟 
        rec_result = model_asr_streaming.generate(
            input=audio_in, **websocket.status_dict_asr_online
        )[0]
        # print("online, ", rec_result)
        if websocket.mode == "2pass" and websocket.status_dict_asr_online.get("is_final", False):
            return
            #     websocket.status_dict_asr_online["cache"] = dict()
        if len(rec_result["text"]):
            mode = "2pass-online" if "2pass" in websocket.mode else websocket.mode
            message = json.dumps(
                {
                    "mode": mode,
                    "text": rec_result["text"],
                    "wav_name": websocket.wav_name,
                    "is_final": websocket.is_speaking,
                }
            )
            await websocket.send(message)

async def main():
    ssl_context = None
    # 如果证书文件存在 则使用SSL协议
    if len(args.certfile) > 0:
        # 创建SSL上下文
        ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        # Generate with Lets Encrypt, copied to this location, chown to current user and 400 permissions
        ssl_cert = args.certfile # 证书文件
        ssl_key = args.keyfile # 密钥文件
        # 加载证书和密钥
        ssl_context.load_cert_chain(ssl_cert, keyfile=ssl_key)

    # websockets 新版本要求在运行中的事件循环里启动 serve
    async with websockets.serve(
        ws_serve,
        args.host,
        args.port,
        subprotocols=["binary"],
        ping_interval=None,
        ssl=ssl_context,
    ):
        await asyncio.Future()


asyncio.run(main())

# python funasr_wss_server.py --certfile "" --keyfile ""  运行来跳过ssl
