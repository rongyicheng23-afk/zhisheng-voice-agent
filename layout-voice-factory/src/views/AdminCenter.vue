<template>
  <FlowBackgroundPanel>
    <main class="admin-page">
      <section class="admin-shell">
        <header class="page-heading"><p>ADMIN CENTER</p><h1>管理中心</h1><span>管理用户角色、资料生命周期和系统观察。后台操作由服务端再次校验，不能只依赖页面按钮。</span></header>

        <section v-if="loading" class="admin-card"><el-skeleton :rows="4" animated /></section>
        <section v-else-if="!profile || (!profile.canManageUsers && !profile.canManageKnowledge && !profile.canObserveSystem)" class="admin-card empty-state"><strong>你没有管理端访问权限</strong><p>请联系超级管理员为账号分配资料管理员或运维管理员角色。</p></section>

        <template v-else>
          <section class="capability-grid">
            <article class="admin-card capability"><span>我的角色</span><div class="tags"><el-tag v-for="role in profile.roles" :key="role" effect="plain">{{ roleLabel(role) }}</el-tag></div></article>
            <router-link v-if="profile.canManageKnowledge" class="admin-card capability clickable" to="/knowledge"><span>资料管理</span><strong>审核、发布与下架</strong><small>进入知识库管理页 →</small></router-link>
            <router-link v-if="profile.canObserveSystem" class="admin-card capability clickable" to="/system-observation"><span>系统观察</span><strong>服务健康与指标</strong><small>查看脱敏汇总 →</small></router-link>
          </section>

          <section v-if="profile.canManageUsers" class="admin-card users-card">
            <div class="section-head"><div><strong>用户与角色</strong><small>普通用户只使用业务功能；高级角色按职责授予，至少保留一名超级管理员。</small></div><el-button text :loading="usersLoading" @click="loadUsers">刷新</el-button></div>
            <el-table :data="users" empty-text="暂无用户">
              <el-table-column prop="username" label="账号" min-width="140" />
              <el-table-column prop="nickname" label="昵称" min-width="140" />
              <el-table-column label="角色" min-width="360"><template #default="scope"><el-select v-model="scope.row.roles" multiple collapse-tags collapse-tags-tooltip placeholder="选择角色" @change="saveRoles(scope.row)"><el-option v-for="role in availableRoles" :key="role" :label="roleLabel(role)" :value="role" /></el-select></template></el-table-column>
            </el-table>
          </section>
        </template>
      </section>
    </main>
  </FlowBackgroundPanel>
</template>

<script lang="ts">
import { defineComponent, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import { AdminProfile, getAdminProfile, getManagedUsers, ManagedUser, updateManagedUserRoles } from '@/api/admin'

export default defineComponent({
  name: 'AdminCenter', components: { FlowBackgroundPanel },
  setup () {
    const profile = ref<AdminProfile | null>(null); const users = ref<ManagedUser[]>([]); const loading = ref(true); const usersLoading = ref(false)
    const availableRoles = ['USER', 'KNOWLEDGE_ADMIN', 'OPS_ADMIN', 'SUPER_ADMIN']
    const unwrap = <T,>(response: { code: number, msg: string, data: T }) => { if (response.code !== 200) throw new Error(response.msg || '请求失败'); return response.data }
    const roleLabel = (role: string) => ({ USER: '普通用户', KNOWLEDGE_ADMIN: '资料管理员', OPS_ADMIN: '运维管理员', SUPER_ADMIN: '超级管理员' }[role] || role)
    const loadUsers = async () => { usersLoading.value = true; try { users.value = unwrap(await getManagedUsers()) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '加载用户失败') } finally { usersLoading.value = false } }
    const saveRoles = async (user: ManagedUser) => { try { const result = unwrap(await updateManagedUserRoles(user.id, user.roles)); user.roles = result.roles; ElMessage.success(`已更新 ${user.nickname} 的角色`) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '角色更新失败'); await loadUsers() } }
    onMounted(async () => { try { profile.value = unwrap(await getAdminProfile()); if (profile.value.canManageUsers) await loadUsers() } catch (error) { ElMessage.error('获取管理权限失败') } finally { loading.value = false } })
    return { profile, users, loading, usersLoading, availableRoles, roleLabel, loadUsers, saveRoles }
  }
})
</script>

<style scoped>
.admin-page { padding: 18px 24px 66px; }.admin-shell { max-width: 1180px; margin: 0 auto; }.page-heading { margin: 6px 0 26px; max-width: 750px; }.page-heading p { color: #476ef7; font-size: 12px; font-weight: 700; letter-spacing: .14em; margin: 0 0 8px; }.page-heading h1 { color: #1d3155; margin: 0 0 10px; font-size: 34px; }.page-heading span, small { color: #71819e; line-height: 1.6; }.admin-card { border: 1px solid rgba(112, 103, 255, .2); background: rgba(255,255,255,.84); border-radius: 20px; padding: 22px; box-shadow: 0 18px 45px rgba(85,109,172,.1); backdrop-filter: blur(14px); }.capability-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }.capability span, .capability small { display: block; }.capability strong { display: block; color: #273959; margin: 8px 0 4px; font-size: 18px; }.clickable { text-decoration: none; transition: transform .2s ease, border-color .2s ease; }.clickable:hover { transform: translateY(-3px); border-color: rgba(71,110,247,.5); }.tags { margin-top: 11px; display: flex; gap: 8px; flex-wrap: wrap; }.users-card { margin-top: 17px; }.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }.section-head strong, .section-head small { display: block; }.section-head strong { color: #273959; font-size: 18px; }.empty-state { text-align: center; padding: 60px 24px; }.empty-state strong { color: #273959; font-size: 19px; }.empty-state p { color: #71819e; } @media (max-width: 760px) { .admin-page { padding: 18px 14px 44px; }.capability-grid { grid-template-columns: 1fr; } }
</style>
