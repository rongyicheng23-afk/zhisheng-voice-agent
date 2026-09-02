<template>
  <div class="auth-stage" :class="themeClass">
    <div class="sky" aria-hidden="true">
      <span class="cloud cloud--1"></span>
      <span class="cloud cloud--2"></span>
      <span class="wave-band wave-band--1"></span>
      <span class="wave-band wave-band--2"></span>
      <span class="wave-band wave-band--3"></span>
      <span class="wave-band wave-band--4"></span>
      <div class="sky-grain"></div>
    </div>

    <main class="auth-center">
      <div class="auth-logo reveal" style="--d: 0ms">
        <span class="ripple"></span>
        <span class="ripple ripple--2"></span>
        <img class="auth-logo-img" src="/logo.png" alt="Voice Factory" />
      </div>

      <h1 class="auth-headline reveal" style="--d: 80ms">创建账号</h1>
      <p class="auth-tagline reveal" style="--d: 160ms">加入 Voice Factory，几分钟搭好你的声音中枢</p>

      <section class="auth-panel reveal" style="--d: 240ms">
        <el-form class="auth-form" @submit.prevent>
          <div class="auth-row">
            <el-input v-model="form.nickName" class="auth-input" size="large" placeholder="昵称" />
            <el-input v-model="form.username" class="auth-input" size="large" placeholder="用户名" />
          </div>
          <el-input v-model="form.email" class="auth-input" size="large" placeholder="邮箱（可选）" />
          <el-input
            v-model="form.password"
            class="auth-input"
            size="large"
            type="password"
            show-password
            placeholder="密码"
          />
          <el-input
            v-model="form.confirmPassword"
            class="auth-input"
            size="large"
            type="password"
            show-password
            placeholder="确认密码"
            @keyup.enter="handleRegister"
          />

          <button
            type="button"
            class="auth-submit"
            :disabled="loading"
            @click="handleRegister"
          >
            <span>{{ loading ? '注册中…' : '创建账号' }}</span>
            <span class="auth-submit-arrow" aria-hidden="true">→</span>
          </button>
        </el-form>
      </section>

      <p class="auth-switch reveal" style="--d: 320ms">
        已经有账号了？<router-link to="/login">返回登录</router-link>
      </p>
    </main>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { GlobalDataProps } from '@/store/types'
import { useAuthThemeMode } from '@/composables/useAuthThemeMode'
import { getUserInfo, login, register } from '@/api/auth'

export default defineComponent({
  name: 'Register',
  setup () {
    const router = useRouter()
    const store = useStore<GlobalDataProps>()
    const { themeClass } = useAuthThemeMode()
    const loading = ref(false)
    const form = reactive({
      nickName: '',
      username: '',
      email: '',
      password: '',
      confirmPassword: ''
    })

    const handleRegister = async () => {
      if (!form.nickName.trim() || !form.username.trim() || !form.password.trim()) {
        ElMessage.warning('请先填写完整注册信息')
        return
      }
      if (form.password !== form.confirmPassword) {
        ElMessage.error('两次输入的密码不一致')
        return
      }

      loading.value = true
      try {
        const registerRes = await register(
          form.username.trim(),
          form.password.trim(),
          form.nickName.trim(),
          form.email.trim()
        )
        if (registerRes.code !== 0) {
          ElMessage.error(registerRes.message || '注册失败')
          return
        }
        const loginRes = await login(form.username.trim(), form.password.trim())
        if (loginRes.code !== 0) {
          ElMessage.error(loginRes.message || '注册成功，但自动登录失败')
          router.push('/login')
          return
        }
        const token = loginRes.data
        localStorage.setItem('token', token)
        const userInfoRes = await getUserInfo()
        const userInfo = userInfoRes.data
        store.commit('setAuthState', {
          token,
          user: {
            _id: String(userInfo.id),
            username: userInfo.username,
            email: userInfo.email || form.email.trim() || '',
            nickName: userInfo.nickname || userInfo.username,
            description: '已接入 springboot-minio 注册服务。',
            isLogin: true
          }
        })
        ElMessage.success('注册成功，已自动登录')
        router.push('/')
      } catch (error: any) {
        localStorage.removeItem('token')
        ElMessage.error(error?.response?.data?.message || '注册失败，请检查后端服务和数据库配置')
      } finally {
        loading.value = false
      }
    }

    return { themeClass, form, loading, handleRegister }
  }
})
</script>

<style scoped>
.auth-stage {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  --easing: cubic-bezier(0.16, 1, 0.3, 1);
  --ink: #2a2550;
  --ink-soft: rgba(42, 37, 80, 0.62);
  --brand: #7c5cff;
  background: #eef1ff;
}

.sky {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    linear-gradient(165deg, #eef1ff 0%, #ece8ff 32%, #f2ecff 58%, #e8f0ff 100%);
  background-size: 200% 200%;
  animation: sky-shift 20s ease-in-out infinite;
}

.cloud {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  will-change: transform;
}

.cloud--1 {
  width: 60vw;
  height: 60vw;
  top: -20%;
  left: -12%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.75) 0%, transparent 60%);
  animation: cloud-a 16s ease-in-out infinite;
}

.cloud--2 {
  width: 52vw;
  height: 52vw;
  bottom: -18%;
  right: -10%;
  background: radial-gradient(circle, rgba(214, 224, 255, 0.7) 0%, transparent 62%);
  animation: cloud-b 20s ease-in-out infinite;
}

.wave-band {
  position: absolute;
  left: 0;
  width: 100%;
  background-repeat: repeat-x;
  will-change: background-position;
}

.wave-band--1 {
  top: 26%;
  height: 120px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='520' height='120'%3E%3Cpath d='M0 60 Q130 18 260 60 T520 60' fill='none' stroke='%237c5cff' stroke-width='2.5'/%3E%3C/svg%3E");
  background-size: 520px 120px;
  opacity: 0.32;
  animation: wave1 9s linear infinite;
}

.wave-band--2 {
  top: 40%;
  height: 150px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='640' height='150'%3E%3Cpath d='M0 75 Q160 15 320 75 T640 75' fill='none' stroke='%235a8bff' stroke-width='2.5'/%3E%3C/svg%3E");
  background-size: 640px 150px;
  opacity: 0.28;
  animation: wave2 13s linear infinite;
}

.wave-band--3 {
  top: 56%;
  height: 110px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='460' height='110'%3E%3Cpath d='M0 55 Q115 16 230 55 T460 55' fill='none' stroke='%2322b4d8' stroke-width='2.5'/%3E%3C/svg%3E");
  background-size: 460px 110px;
  opacity: 0.24;
  animation: wave3 7s linear infinite;
}

.wave-band--4 {
  top: 70%;
  height: 160px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='720' height='160'%3E%3Cpath d='M0 80 Q180 14 360 80 T720 80' fill='none' stroke='%23a78bfa' stroke-width='2.5'/%3E%3C/svg%3E");
  background-size: 720px 160px;
  opacity: 0.22;
  animation: wave4 17s linear infinite;
}

.sky-grain {
  position: absolute;
  inset: 0;
  opacity: 0.05;
  mix-blend-mode: multiply;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.55'/%3E%3C/svg%3E");
}

@keyframes sky-shift {
  0%, 100% { background-position: 0% 0%; }
  50% { background-position: 100% 100%; }
}
@keyframes cloud-a {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(10vw, 8vh, 0) scale(1.15); }
}
@keyframes cloud-b {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(-12vw, -10vh, 0) scale(1.2); }
}
@keyframes wave1 { to { background-position-x: -520px; } }
@keyframes wave2 { to { background-position-x: 640px; } }
@keyframes wave3 { to { background-position-x: -460px; } }
@keyframes wave4 { to { background-position-x: 720px; } }

.auth-center {
  position: relative;
  z-index: 1;
  width: min(440px, calc(100% - 40px));
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.auth-logo {
  position: relative;
  width: 88px;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 26px;
}

.auth-logo-img {
  width: 88px;
  height: 88px;
  object-fit: contain;
  filter: drop-shadow(0 14px 28px rgba(96, 110, 230, 0.32));
}

.wave-bar {
  transform-box: fill-box;
  transform-origin: center;
  animation: bar-bounce 1.3s ease-in-out infinite;
}

.wave-bar--1 { animation-delay: 0s; }
.wave-bar--2 { animation-delay: 0.12s; }
.wave-bar--3 { animation-delay: 0.24s; }
.wave-bar--4 { animation-delay: 0.12s; }
.wave-bar--5 { animation-delay: 0s; }

.ripple {
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 1.5px solid rgba(124, 92, 255, 0.45);
  animation: ripple-out 2.8s ease-out infinite;
}

.ripple--2 { animation-delay: 1.4s; }

@keyframes bar-bounce {
  0%, 100% { transform: scaleY(0.55); }
  50% { transform: scaleY(1); }
}
@keyframes ripple-out {
  0% { transform: scale(1); opacity: 0.55; }
  100% { transform: scale(2.6); opacity: 0; }
}

.auth-headline {
  margin: 0 0 10px;
  font-size: 42px;
  line-height: 1.08;
  font-weight: 600;
  letter-spacing: -0.035em;
  color: var(--ink);
}

.auth-tagline {
  margin: 0 0 36px;
  font-size: 16px;
  color: var(--ink-soft);
}

.auth-panel {
  width: 100%;
  padding: 28px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.75);
  box-shadow: 0 20px 50px rgba(70, 80, 160, 0.16), inset 0 1px 0 rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(22px) saturate(150%);
  -webkit-backdrop-filter: blur(22px) saturate(150%);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.auth-input :deep(.el-input__wrapper) {
  height: 52px;
  padding: 0 16px;
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: none;
  border: 1px solid rgba(120, 130, 200, 0.16);
  transition: border-color 0.2s var(--easing), background 0.2s var(--easing);
}

.auth-input :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.96);
}

.auth-input :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  border-color: rgba(124, 92, 255, 0.55);
  box-shadow: 0 0 0 4px rgba(124, 92, 255, 0.14);
}

.auth-input :deep(.el-input__inner) {
  font-size: 15px;
  color: #1f1a3d;
}

.auth-submit {
  position: relative;
  height: 52px;
  margin-top: 4px;
  border: none;
  border-radius: 13px;
  background: #1a1535;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: transform 0.2s var(--easing), opacity 0.2s var(--easing), box-shadow 0.3s var(--easing);
  box-shadow: 0 10px 24px rgba(26, 21, 53, 0.28);
}

.auth-submit::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 30%, rgba(255, 255, 255, 0.22) 50%, transparent 70%);
  transform: translateX(-110%);
  transition: transform 0.7s var(--easing);
}

.auth-submit:hover:not(:disabled)::before { transform: translateX(110%); }
.auth-submit:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 16px 32px rgba(26, 21, 53, 0.36); }
.auth-submit:active:not(:disabled) { transform: translateY(0); }
.auth-submit:disabled { opacity: 0.65; cursor: not-allowed; }

.auth-submit-arrow { transition: transform 0.25s var(--easing); }
.auth-submit:hover:not(:disabled) .auth-submit-arrow { transform: translateX(4px); }

.auth-switch {
  margin: 28px 0 0;
  font-size: 14px;
  color: var(--ink-soft);
}

.auth-switch a {
  color: var(--ink);
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid rgba(42, 37, 80, 0.3);
  padding-bottom: 1px;
  transition: border-color 0.2s var(--easing);
}

.auth-switch a:hover { border-color: var(--ink); }

.reveal {
  opacity: 0;
  transform: translateY(16px);
  animation: reveal-up 0.8s var(--easing) forwards;
  animation-delay: var(--d, 0ms);
}

@keyframes reveal-up {
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .cloud, .sky, .wave-band, .ripple, .wave-bar { animation: none; }
  .reveal { animation: none; opacity: 1; transform: none; }
}

@media (max-width: 480px) {
  .auth-headline { font-size: 34px; }
  .auth-panel { padding: 22px; }
  .auth-row { grid-template-columns: 1fr; }
}
</style>
