<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  userAdminApi,
  type ManagedUser,
  type UserRole,
} from './api'

const rows = ref<ManagedUser[]>([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const dialogOpen = ref(false)
const submitting = ref(false)
const editing = ref<ManagedUser | null>(null)
const form = reactive({ username: '', displayName: '', password: '', role: 'USER' as UserRole })
const errors = reactive({ username: '', displayName: '', password: '' })

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await userAdminApi.list(page.value, size.value)
    rows.value = result.items
    total.value = result.total
  }
  catch {
    error.value = '用户列表加载失败，请重试'
  }
  finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, { username: '', displayName: '', password: '', role: 'USER' })
  clearErrors()
  dialogOpen.value = true
}

function openEdit(user: ManagedUser): void {
  editing.value = user
  Object.assign(form, {
    username: user.username,
    displayName: user.displayName,
    password: '',
    role: user.role,
  })
  clearErrors()
  dialogOpen.value = true
}

function clearErrors(): void {
  errors.username = ''
  errors.displayName = ''
  errors.password = ''
}

function validate(): boolean {
  clearErrors()
  const username = form.username.trim().toLowerCase()
  const displayName = form.displayName.trim()
  const passwordRequired = !editing.value
  if (passwordRequired && !/^[a-z][a-z0-9_.-]{2,31}$/.test(username)) {
    errors.username = '使用 3–32 位小写字母、数字或 . _ -'
  }
  if (!displayName || displayName.length > 100) errors.displayName = '请输入 1–100 个字符'
  if ((passwordRequired || form.password) && !isStrongPassword(form.password)) {
    errors.password = '至少 8 位，需包含大写、小写字母和数字'
  }
  return !errors.username && !errors.displayName && !errors.password
}

function isStrongPassword(value: string): boolean {
  return value.length >= 8
    && value.length <= 72
    && /[a-z]/.test(value)
    && /[A-Z]/.test(value)
    && /\d/.test(value)
}

async function submit(): Promise<void> {
  if (!validate() || submitting.value) return
  submitting.value = true
  try {
    if (editing.value) {
      await userAdminApi.update(editing.value.id, {
        displayName: form.displayName.trim(),
        role: form.role,
        ...(form.password ? { password: form.password } : {}),
      })
      ElMessage.success('用户资料已更新')
    }
    else {
      await userAdminApi.create({
        username: form.username.trim().toLowerCase(),
        displayName: form.displayName.trim(),
        password: form.password,
        role: form.role,
      })
      ElMessage.success('用户已创建')
    }
    dialogOpen.value = false
    page.value = 1
    await load()
  }
  catch {
    ElMessage.error(editing.value ? '更新失败，请检查管理员规则' : '创建失败，用户名可能已存在')
  }
  finally {
    submitting.value = false
  }
}

async function toggleStatus(user: ManagedUser): Promise<void> {
  const enabling = user.status === 'DISABLED'
  const action = enabling ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `${action}“${user.displayName}”的账号吗？${enabling ? '' : '停用后已登录会话将失效。'}`,
      `${action}用户`,
      { type: enabling ? 'info' : 'warning', confirmButtonText: action, cancelButtonText: '取消' },
    )
    await userAdminApi.changeStatus(user.id, enabling ? 'ENABLED' : 'DISABLED')
    ElMessage.success(`用户已${action}`)
    await load()
  }
  catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') {
      ElMessage.error('操作失败，系统至少需保留一位可用管理员')
    }
  }
}

function changePage(value: number): void {
  page.value = value
  void load()
}

function formatTime(value: string | null): string {
  if (!value) return '尚未登录'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="admin-page" aria-labelledby="user-admin-heading">
    <header class="page-heading">
      <div>
        <h2 id="user-admin-heading">用户管理</h2>
        <p>创建成员账号，维护角色与使用状态。</p>
      </div>
      <el-button data-test="create-user" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建用户
      </el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <template #default><el-button text type="primary" @click="load">重新加载</el-button></template>
    </el-alert>

    <div class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无用户">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-avatar">{{ row.displayName.slice(0, 1).toUpperCase() }}</span>
              <div><strong>{{ row.displayName }}</strong><span>@{{ row.username }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="92">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'primary' : 'info'" effect="light">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <span class="status-dot" :class="{ 'is-disabled': row.status === 'DISABLED' }">
              {{ row.status === 'ENABLED' ? '已启用' : '已停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" min-width="140">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="140">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text @click="openEdit(row)">编辑</el-button>
            <el-button :type="row.status === 'ENABLED' ? 'danger' : 'success'" text @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <span>共 {{ total }} 位用户</span>
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="changePage"
        />
      </div>
    </div>

    <el-dialog v-model="dialogOpen" :title="editing ? '编辑用户' : '新建用户'" width="500px" destroy-on-close>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" :error="errors.username">
          <el-input v-model="form.username" aria-label="用户名" :disabled="Boolean(editing)" maxlength="32" />
        </el-form-item>
        <el-form-item label="显示名称" :error="errors.displayName">
          <el-input v-model="form.displayName" aria-label="显示名称" maxlength="100" />
        </el-form-item>
        <el-form-item :label="editing ? '重置密码（可选）' : '初始密码'" :error="errors.password">
          <el-input v-model="form.password" aria-label="用户密码" type="password" show-password maxlength="72" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" aria-label="用户角色" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-page { min-width: 0; }

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-heading h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
}

.page-heading p { margin: 6px 0 0; color: var(--color-muted); }

.table-panel {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.table-panel :deep(.el-table__header th) {
  height: 48px;
  color: var(--color-muted);
  background: #f8fafc;
  font-weight: 550;
}

.table-panel :deep(.el-table__cell) { padding-block: 13px; }

.user-cell { display: flex; align-items: center; gap: 11px; min-width: 0; }

.user-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  border-radius: 50%;
  font-weight: 650;
}

.user-cell strong,
.user-cell span { display: block; }
.user-cell strong { color: var(--color-heading); font-weight: 550; }
.user-cell div span { color: var(--color-muted); font-size: 12px; }

.status-dot { color: var(--color-success); font-size: 13px; }
.status-dot::before {
  width: 7px;
  height: 7px;
  display: inline-block;
  margin-right: 7px;
  background: currentColor;
  border-radius: 50%;
  content: '';
}
.status-dot.is-disabled { color: var(--color-muted); }

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-top: 1px solid var(--color-border);
}

.pagination-row > span { color: var(--color-muted); font-size: 13px; }
</style>
