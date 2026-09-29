# 资料问答离线评测

工具不联网、不调用模型、不修改数据库，只输出聚合指标。需先用有权评测的资料实际提问，独立人工标注后准备 JSON。

```bash
python -m services.evaluation.knowledge --input /absolute/path/to/reviewed.json --k 3
```

输入最多 5 MiB、10000 条。建议不存问题、正文、姓名、音频或密钥，只保存不透明编号与人工标签。以下仅为格式示例，不是项目实测成绩：

```json
{"cases": [
  {"id": "case-001", "answerable": true,
   "relevantSourceIds": ["document:version:paragraph"],
   "retrievedSourceIds": ["document:version:paragraph"],
   "outcome": "answered", "claims": [{"supported": true}, {"supported": null}],
   "firstAudioMs": 1800},
  {"id": "case-002", "answerable": false,
   "relevantSourceIds": [], "retrievedSourceIds": [],
   "outcome": "refused", "claims": []}
]}
```

- answerable：人工判断当前授权有效资料是否足以回答。冲突、缺失关键条件可为 false，即便有相关资料。true 必须提供人工标注的相关来源。
- relevantSourceIds：独立标注的应召回片段 ID，不得复制检索输出当标准答案。retrievedSourceIds 保留系统实际排序。两者各最多 100 个不同 ID。
- outcome：实际 answered / refused / failed；运行失败不是正确拒答。回答须记录 1–6 条结论，拒答或失败则为空。
- claims[].supported：人工核对结论是否被引用原文完整支持，true / false；尚未核对用 null。不能默认 true，程序无法验证标注真实性。
- firstAudioMs：可选，从发送到播放器启动的实测时延；失败样本不允许填。每种服务器配置、冷/热启动、回答模式、网络条件分别评测；不要混合普通问答和先检查后播报的归纳模式。

| 指标 | 分母与限制 |
|---|---|
| macroRecallAtK | 每个有相关来源的样本计算 top-K 命中 / 全部相关来源，再求平均；无命中为 0 |
| meanReciprocalRankAtK | 有相关来源样本中首个相关来源排名的倒数；top-K 无命中为 0 |
| correctRefusalRate | 不可回答样本中明确拒答的比例；失败计入分母 |
| answerableAnswerRate | 可回答样本给出答案的比例，不代表答案正确 |
| humanSupportedClaimRate | 已人工核对结论中被支持的比例；同时报告 humanReviewCoverage，未核对项不冒充正确 |
| firstAudioP50Ms / firstAudioP95Ms | 有时延的非失败样本，nearest-rank 方法；报告样本数，不混入失败假造的 0 ms |

无分母或时延样本时返回 null；总例数、回答数、失败数、人工核对数一起保留。当前测试使用合成 fixture 验证算法，没有正式实测成绩。最终验收需覆盖过期资料、冲突、无依据提问、相似词误召回、用户隔离与长回答，并保存独立运行条件记录。
