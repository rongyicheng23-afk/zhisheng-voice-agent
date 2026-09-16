#!/usr/bin/env zsh
# 必须以 source 方式运行，才能把变量保留在当前终端：
# source "/Users/frank/Desktop/voice /scripts/import-local-env.zsh"

typeset script_path script_dir project_root legacy_env_file visible_env_file key raw_value value
script_path="${(%):-%N}"
script_dir="${script_path:A:h}"
project_root="${script_dir:h}"
visible_env_file="${project_root}/voice.local.env"
legacy_env_file="${project_root}/.env.local"

if [[ ! -f "$visible_env_file" && ! -f "$legacy_env_file" ]]; then
  print -u2 "未找到本地环境配置文件"
  return 1 2>/dev/null || exit 1
fi

# 兼容此前已填写的隐藏配置；其中的真实凭据不会被打印。
if [[ -f "$legacy_env_file" ]]; then
  set -a
  source "$legacy_env_file"
  set +a
fi

# 可见文件只覆盖“非空”项，因而新生成的内部校验值不会把旧凭据清空。
if [[ -f "$visible_env_file" ]]; then
  while IFS='=' read -r key raw_value; do
    [[ -z "$key" || "$key" == \#* ]] && continue
    value="$raw_value"
    value="${value#\'}"
    value="${value%\'}"
    [[ -z "$value" ]] && continue
    case "$key" in
      MYSQL_PASSWORD|REALTIME_GATEWAY_INTERNAL_KEY|DEEPSEEK_API_KEY|DEEPSEEK_MODEL|XFYUN_TTS_APP_ID|XFYUN_TTS_API_KEY|XFYUN_TTS_API_SECRET|XFYUN_TTS_VOICE|SPRING_BOOT_URL|SSL_CERT_FILE|APP_BOOTSTRAP_ADMIN_USERNAME)
        export "$key=$value"
        ;;
    esac
  done < "$visible_env_file"
fi

for name in MYSQL_PASSWORD REALTIME_GATEWAY_INTERNAL_KEY DEEPSEEK_API_KEY XFYUN_TTS_APP_ID XFYUN_TTS_API_KEY XFYUN_TTS_API_SECRET; do
  if [[ -z "${(P)name}" ]]; then
    print -u2 "提示：$name 尚未填写"
  fi
done
print "本地环境变量已导入（未显示任何密钥）。"
