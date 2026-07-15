<template>
  <div class="pb-root">
    <!-- 解锁 / 设置主密码（视觉还原 v1.2.1） -->
    <div v-if="mode === 'setup' || mode === 'unlock' || mode === 'change'" class="pb-unlock">
      <div class="pb-unlock-card">
        <div class="pb-unlock-badge">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="4" y="10" width="16" height="11" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
        </div>
        <h1>加密记事本</h1>
        <p class="pb-unlock-sub">{{ mode === 'setup' ? '首次使用，请设置你的主密码' : (mode === 'change' ? '出于安全，请修改你的主密码' : '输入主密码，解锁你的私人加密内容') }}</p>

        <div class="pb-form">
          <div class="pb-input-wrap" v-if="mode !== 'change'">
            <input :type="showPwd ? 'text' : 'password'" v-model="password" class="pb-input"
                   :placeholder="mode === 'setup' ? '设置主密码（至少 8 位）' : '主密码'"
                   @keyup.enter="mode === 'setup' ? doSetup() : doUnlock()" />
            <button class="pb-eye" type="button" :title="showPwd ? '隐藏' : '显示'" @click="showPwd = !showPwd">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg>
            </button>
          </div>
          <template v-else>
            <div class="pb-input-wrap">
              <input :type="showPwd ? 'text' : 'password'" v-model="password" class="pb-input" placeholder="原主密码" @keyup.enter="doChange()" />
              <button class="pb-eye" type="button" @click="showPwd = !showPwd">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
            <div class="pb-input-wrap">
              <input :type="showPwd2 ? 'text' : 'password'" v-model="newPassword" class="pb-input" placeholder="新主密码（至少 8 位）" @keyup.enter="doChange()" />
              <button class="pb-eye" type="button" @click="showPwd2 = !showPwd2">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
          </template>

          <div class="pb-meter" v-if="mode === 'setup' && password">
            <div class="pb-meter-bar"><span :style="{ width: (strength * 25) + '%', background: strengthColor }"></span></div>
            <div class="pb-meter-label" :style="{ color: strengthColor }">{{ strengthLabel }}</div>
          </div>

          <div class="pb-err" v-if="error">{{ error }}</div>

          <div class="pb-actions">
            <button class="pb-btn pb-btn-primary" :disabled="busy" @click="mode === 'setup' ? doSetup() : (mode === 'change' ? doChange() : doUnlock())">
              {{ busy ? '处理中…' : (mode === 'setup' ? '设置并进入' : (mode === 'change' ? '修改并进入' : '解锁')) }}
            </button>
          </div>

          <p class="pb-tip" v-if="mode === 'unlock' && !status.initialized">新账号请使用默认密码 <b>12345678</b> 解锁。</p>
        </div>
      </div>
    </div>

    <!-- 主界面 -->
    <div v-else-if="mode === 'main'" class="pb-main">
      <div class="pb-toolbar">
        <h2>加密记事本</h2>
        <div class="pb-spacer"></div>
        <button class="pb-btn" @click="openEditor()">+ 新建</button>
        <button class="pb-btn pb-btn-ghost" @click="doLock">锁定</button>
      </div>

      <!-- 分类标签栏 -->
      <div class="pb-tabs">
        <button class="pb-chip" :class="{ active: selectedCat === null }" @click="selectCat(null)">全部</button>
        <template v-for="(cat, idx) in categories" :key="cat.id || ('v'+cat.name)">
          <span class="pb-chip-wrap" :draggable="cat.managed" @dragstart="onDragStart(idx)" @dragover.prevent @drop="onDrop(idx)">
            <button class="pb-chip" :class="{ active: selectedCat === cat.name }" @click="selectCat(cat.name)">
              {{ cat.name }}
            </button>
            <span class="pb-chip-actions" v-if="editingId !== cat.name">
              <span class="pb-ico" title="重命名" @click="startRename(cat)">✎</span>
              <span class="pb-ico pb-ico-del" title="删除分类" @click="deleteCategory(cat)">✕</span>
              <span class="pb-ico pb-ico-grip" title="拖动排序" v-if="cat.managed">⋮⋮</span>
            </span>
            <input v-else class="pb-chip-input" v-model="renameValue" @keyup.enter="commitRename(cat)" @blur="commitRename(cat)" />
          </span>
        </template>
        <span class="pb-chip-wrap">
          <button class="pb-chip pb-chip-add" @click="adding = !adding">＋</button>
          <input v-if="adding" class="pb-chip-input" v-model="newCatName" placeholder="新分类名"
                 @keyup.enter="addCategory" @blur="adding = false" ref="addInput" />
        </span>
      </div>

      <!-- 笔记列表 -->
      <div class="pb-list" v-if="!detail">
        <div v-if="loading" class="pb-empty">加载中…</div>
        <div v-else-if="notes.length === 0" class="pb-empty">暂无笔记</div>
        <div v-for="n in notes" :key="n.id" class="pb-note" @click="viewDetail(n)">
          <div class="pb-note-title">{{ n.title }}</div>
          <div class="pb-note-meta">
            <span class="pb-tag" v-if="n.category">{{ n.category }}</span>
            <span class="pb-time">{{ formatTime(n.updatedAt) }}</span>
          </div>
          <div class="pb-note-actions" @click.stop>
            <span class="pb-ico" title="编辑" @click="openEditor(n)">✎</span>
            <span class="pb-ico pb-ico-del" title="删除" @click="deleteNote(n)">✕</span>
          </div>
        </div>
      </div>

      <!-- 笔记详情 -->
      <div class="pb-detail" v-else>
        <button class="pb-btn pb-btn-ghost" @click="detail = null">← 返回</button>
        <h3>{{ detail.title }}</h3>
        <div class="pb-note-meta">
          <span class="pb-tag" v-if="detail.category">{{ detail.category }}</span>
        </div>
        <div class="pb-content" v-if="detail.contentType === 'html'" v-html="detail.content"></div>
        <pre class="pb-content" v-else>{{ detail.content }}</pre>
      </div>
    </div>

    <!-- 编辑/新建弹窗 -->
    <div class="pb-modal-mask" v-if="showEditor" @click.self="closeEditor">
      <div class="pb-modal">
        <h3>{{ editing.id ? '编辑笔记' : '新建笔记' }}</h3>
        <input class="pb-input" v-model="editing.title" placeholder="标题" />
        <select class="pb-input" v-model="editing.category">
          <option value="">未分类</option>
          <option v-for="c in categories" :key="c.id || c.name" :value="c.name">{{ c.name }}</option>
        </select>
        <textarea class="pb-input pb-textarea" v-model="editing.content" placeholder="内容"></textarea>
        <div class="pb-err" v-if="editorError">{{ editorError }}</div>
        <div class="pb-modal-actions">
          <button class="pb-btn pb-btn-ghost" @click="closeEditor">取消</button>
          <button class="pb-btn pb-btn-primary" :disabled="busy" @click="saveNote">{{ busy ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";

const BASE = "/apis/passwordbook.halo.run/v1alpha1/passwordbook";
const axios: any = (window as any).axios;

const mode = ref<"loading" | "setup" | "unlock" | "change" | "main">("loading");
const status = reactive({ initialized: false, mustChange: false });
const token = ref("");
const password = ref("");
const newPassword = ref("");
const busy = ref(false);
const showPwd = ref(false);
const showPwd2 = ref(false);
const strength = computed(() => {
  const p = password.value;
  if (!p) return 0;
  let s = 0;
  if (p.length >= 8) s++;
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++;
  if (/\d/.test(p)) s++;
  if (/[^a-zA-Z0-9]/.test(p)) s++;
  return Math.min(4, s);
});
const strengthColor = computed(() => ["#ef4444", "#ef4444", "#f0883e", "#d9a514", "#18a058"][strength.value]);
const strengthLabel = computed(() => ["弱", "弱", "较弱", "中等", "强"][strength.value]);
const error = ref("");

const notes = ref<any[]>([]);
const categories = ref<any[]>([]);
const selectedCat = ref<string | null>(null);
const loading = ref(false);
const detail = ref<any>(null);

const showEditor = ref(false);
const editing = reactive({ id: "", title: "", content: "", category: "", contentType: "text" });
const editorError = ref("");

const adding = ref(false);
const newCatName = ref("");
const editingId = ref<string | null>(null);
const renameValue = ref("");
const dragIndex = ref<number>(-1);

function authHeaders() {
  return token.value ? { "X-PasswordBook-Token": token.value } : {};
}
function req(path: string, config: any = {}) {
  config.url = path;
  config.baseURL = BASE;
  config.headers = { ...(config.headers || {}), ...authHeaders() };
  return axios(config);
}

async function init() {
  try {
    const r = await req("/status");
    status.initialized = !!r.data.initialized;
    status.mustChange = !!r.data.mustChange;
    mode.value = status.initialized ? "unlock" : "setup";
  } catch (e: any) {
    error.value = "加载状态失败：" + (e?.message || e);
  }
}

async function doSetup() {
  if (password.value.length < 8) { error.value = "密码至少 8 位"; return; }
  busy.value = true; error.value = "";
  try {
    const r = await req("/setup", { method: "POST", data: { password: password.value } });
    token.value = r.data.token;
    mode.value = "main"; await loadAll();
  } catch (e: any) {
    error.value = e?.response?.data?.error || "设置失败";
  } finally { busy.value = false; }
}

async function doUnlock() {
  busy.value = true; error.value = "";
  try {
    const r = await req("/unlock", { method: "POST", data: { password: password.value } });
    token.value = r.data.token;
    if (r.data.mustChange) { mode.value = "change"; }
    else { mode.value = "main"; await loadAll(); }
  } catch (e: any) {
    error.value = e?.response?.data?.error || "解锁失败";
  } finally { busy.value = false; }
}

async function doChange() {
  if (newPassword.value.length < 8) { error.value = "新密码至少 8 位"; return; }
  busy.value = true; error.value = "";
  try {
    const r = await req("/change-password", { method: "POST", data: { oldPassword: password.value, newPassword: newPassword.value } });
    token.value = r.data.token;
    mode.value = "main"; await loadAll();
  } catch (e: any) {
    error.value = e?.response?.data?.error || "修改失败";
  } finally { busy.value = false; }
}

async function loadAll() {
  await loadCategories();
  await loadNotes();
}

async function loadNotes() {
  loading.value = true;
  try {
    const params: any = {};
    if (selectedCat.value) params.category = selectedCat.value;
    const r = await req("/notes", { method: "GET", params });
    notes.value = r.data || [];
  } catch (e: any) {
    notes.value = [];
  } finally { loading.value = false; }
}

async function loadCategories() {
  try {
    const r = await req("/categories", { method: "GET" });
    categories.value = r.data || [];
  } catch (e: any) {
    categories.value = [];
  }
}

function selectCat(name: string | null) {
  selectedCat.value = name;
  loadNotes();
}

async function viewDetail(n: any) {
  try {
    const r = await req("/notes/detail", { method: "GET", params: { id: n.id } });
    detail.value = r.data;
  } catch (e: any) { error.value = e?.response?.data?.error || "读取失败"; }
}

function openEditor(n?: any) {
  editing.id = n?.id || "";
  editing.title = n?.title || "";
  editing.content = n?.content || "";
  editing.category = n?.category || selectedCat.value || "";
  editing.contentType = n?.contentType || "text";
  editorError.value = "";
  showEditor.value = true;
}
function closeEditor() { showEditor.value = false; }

async function saveNote() {
  if (!editing.title.trim()) { editorError.value = "标题不能为空"; return; }
  busy.value = true; editorError.value = "";
  try {
    const body: any = { title: editing.title, content: editing.content, contentType: editing.contentType, category: editing.category };
    if (editing.id) body.id = editing.id;
    const r = await req("/notes", { method: editing.id ? "PUT" : "POST", data: body });
    if (r.status === 200) {
      showEditor.value = false;
      await loadNotes();
      await loadCategories();
    }
  } catch (e: any) {
    editorError.value = e?.response?.data?.error || "保存失败";
  } finally { busy.value = false; }
}

async function deleteNote(n: any) {
  if (!confirm("确定删除该笔记？")) return;
  try {
    await req("/notes", { method: "DELETE", params: { id: n.id } });
    await loadNotes();
  } catch (e: any) { error.value = e?.response?.data?.error || "删除失败"; }
}

async function doLock() {
  try { await req("/lock", { method: "POST" }); } catch (e) {}
  token.value = ""; password.value = ""; mode.value = "unlock"; notes.value = []; categories.value = []; detail.value = null;
}

// ===== 分类 =====
async function addCategory() {
  const name = newCatName.value.trim();
  if (!name) { adding.value = false; return; }
  try {
    await req("/categories", { method: "POST", data: { name } });
    newCatName.value = ""; adding.value = false;
    await loadCategories();
  } catch (e: any) { error.value = e?.response?.data?.error || "新增分类失败"; }
}

function startRename(cat: any) {
  editingId.value = cat.id || cat.name;
  renameValue.value = cat.name;
}
async function commitRename(cat: any) {
  const name = renameValue.value.trim();
  editingId.value = null;
  if (!name || name === cat.name) return;
  try {
    const body: any = { name };
    if (cat.id) body.id = cat.id; else body.oldName = cat.name;
    await req("/categories", { method: "PUT", data: body });
    await loadCategories();
  } catch (e: any) { error.value = e?.response?.data?.error || "重命名失败"; }
}

async function deleteCategory(cat: any) {
  if (!confirm("删除分类「" + cat.name + "」？其下笔记将归为未分类。")) return;
  try {
    const params: any = {};
    if (cat.id) params.id = cat.id; else params.name = cat.name;
    await req("/categories", { method: "DELETE", params });
    if (selectedCat.value === cat.name) selectedCat.value = null;
    await loadCategories();
    await loadNotes();
  } catch (e: any) { error.value = e?.response?.data?.error || "删除分类失败"; }
}

function onDragStart(idx: number) { dragIndex.value = idx; }
async function onDrop(idx: number) {
  const from = dragIndex.value;
  if (from < 0 || from === idx) return;
  const list = categories.value.slice();
  const [moved] = list.splice(from, 1);
  list.splice(idx, 0, moved);
  categories.value = list;
  dragIndex.value = -1;
  const ids = list.filter(c => c.id).map(c => c.id);
  if (ids.length) {
    try { await req("/categories/reorder", { method: "PUT", data: { ids } }); } catch (e) {}
  }
}

function formatTime(ts: number) {
  if (!ts) return "";
  const d = new Date(ts);
  return d.toLocaleString();
}

onMounted(init);
</script>

<style>
.pb-root { padding: 16px 20px; color: #1f2329; }
:root {
  --pb-primary: #6d5efc;
  --pb-primary-2: #6d5efc;
  --pb-primary-deep: #5246e0;
  --pb-primary-weak: #6d5efc1f;
  --pb-bg: #f5f6fb;
  --pb-card: #fff;
  --pb-border: #e9ebf2;
  --pb-text: #1c2230;
  --pb-muted: #8b93a7;
  --pb-danger: #ef4444;
  --pb-danger-weak: #ef44441f;
  --pb-success: #18a058;
  --pb-radius: 14px;
  --pb-shadow: 0 10px 30px #28265a14;
}
.pb-unlock { background: radial-gradient(900px 500px at 12% -8%, #6d5efc14 0%, #ece9ff00 60%), radial-gradient(800px 480px at 100% 0%, #6d5efc0f 0%, #e6f0ff00 55%), var(--pb-bg); justify-content: center; align-items: center; min-height: 62vh; padding: 3rem 1rem; display: flex; }
.pb-unlock-card { background: var(--pb-card); border: 1px solid var(--pb-border); text-align: center; width: 100%; max-width: 380px; box-shadow: var(--pb-shadow); border-radius: 22px; padding: 2.4rem 1.8rem 2rem; }
.pb-unlock-badge { color: #fff; background: linear-gradient(135deg, var(--pb-primary), var(--pb-primary-2)); border-radius: 20px; justify-content: center; align-items: center; width: 64px; height: 64px; margin: 0 auto 1.1rem; display: flex; box-shadow: 0 10px 24px #6d5efc61; }
.pb-unlock-badge svg { width: 32px; height: 32px; }
.pb-unlock-card h1 { margin: 0 0 .35rem; font-size: 1.4rem; font-weight: 700; color: var(--pb-text); }
.pb-unlock-sub { color: var(--pb-muted); margin: 0 0 1.6rem; font-size: .86rem; }
.pb-form { flex-direction: column; gap: .9rem; width: 100%; display: flex; }
.pb-input-wrap { position: relative; }
.pb-eye { color: var(--pb-muted); cursor: pointer; background: 0 0; border: none; border-radius: 8px; padding: 4px; display: inline-flex; position: absolute; top: 50%; right: 6px; transform: translateY(-50%); }
.pb-eye svg { width: 18px; height: 18px; }
.pb-eye:hover { color: var(--pb-primary); }
.pb-actions { flex-wrap: wrap; align-items: center; gap: .5rem; display: flex; }
.pb-actions .pb-btn-primary { background: linear-gradient(135deg, var(--pb-primary), var(--pb-primary-2)); color: #fff; border: none; box-shadow: 0 6px 16px #6d5efc4d; }
.pb-actions .pb-btn-primary:hover { filter: brightness(1.05); }
.pb-unlock .pb-input { padding: .7rem 2.7rem .7rem .95rem; border-radius: 11px; border: 1px solid var(--pb-border); }
.pb-unlock .pb-input:focus { border-color: var(--pb-primary); box-shadow: 0 0 0 3px var(--pb-primary-weak); }
.pb-meter { align-items: center; gap: .6rem; margin-top: .5rem; display: flex; }
.pb-meter-bar { background: var(--pb-border); border-radius: 99px; flex: 1; height: 6px; overflow: hidden; }
.pb-meter-bar span { border-radius: 99px; height: 100%; transition: width .25s, background .25s; display: block; }
.pb-meter-label { min-width: 2em; font-size: .76rem; font-weight: 600; }
.pb-tip { color: var(--pb-muted); margin: 1.1rem 0 0; font-size: .76rem; line-height: 1.6; }
.pb-tip b { color: var(--pb-primary-deep); }
.pb-hint { font-size: 13px; color: #86909c; margin: 0 0 12px; }
.pb-input { width: 100%; box-sizing: border-box; padding: 9px 11px; margin-bottom: 10px; border: 1px solid #e5e6eb; border-radius: 8px; font-size: 14px; outline: none; }
.pb-input:focus { border-color: #667eea; }
.pb-textarea { min-height: 120px; resize: vertical; font-family: inherit; }
.pb-btn { padding: 8px 14px; border: 1px solid #e5e6eb; border-radius: 8px; background: #f2f3f5; cursor: pointer; font-size: 13px; }
.pb-btn:hover { background: #e8eaed; }
.pb-btn-primary { background: #667eea; color: #fff; border-color: #667eea; }
.pb-btn-primary:hover { background: #5a6fd6; }
.pb-btn-ghost { background: transparent; }
.pb-btn:disabled { opacity: .6; cursor: default; }
.pb-err { color: #f53f3f; font-size: 13px; margin: 4px 0 10px; }
.pb-main { max-width: 820px; margin: 0 auto; }
.pb-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.pb-toolbar h2 { margin: 0; font-size: 18px; }
.pb-spacer { flex: 1; }
.pb-tabs { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f1f3; }
.pb-chip { padding: 6px 14px; border: 1px solid #e5e6eb; border-radius: 999px; background: #fff; cursor: pointer; font-size: 13px; color: #4e5969; }
.pb-chip.active { background: #667eea; color: #fff; border-color: #667eea; }
.pb-chip-add { color: #667eea; border-style: dashed; }
.pb-chip-wrap { display: inline-flex; align-items: center; gap: 2px; }
.pb-chip-input { width: 110px; padding: 5px 8px; border: 1px solid #667eea; border-radius: 999px; font-size: 13px; outline: none; }
.pb-chip-actions { display: inline-flex; gap: 1px; opacity: 0; transition: opacity .15s; }
.pb-chip-wrap:hover .pb-chip-actions { opacity: 1; }
.pb-ico { cursor: pointer; font-size: 12px; color: #86909c; padding: 0 3px; }
.pb-ico:hover { color: #1f2329; }
.pb-ico-del:hover { color: #f53f3f; }
.pb-ico-grip { cursor: grab; }
.pb-list { display: flex; flex-direction: column; gap: 10px; }
.pb-empty { color: #86909c; text-align: center; padding: 40px 0; }
.pb-note { position: relative; background: #fff; border: 1px solid #e5e6eb; border-radius: 10px; padding: 14px 16px; cursor: pointer; transition: box-shadow .15s; }
.pb-note:hover { box-shadow: 0 2px 10px rgba(0,0,0,.08); }
.pb-note-title { font-size: 15px; font-weight: 600; margin-bottom: 6px; }
.pb-note-meta { display: flex; gap: 8px; align-items: center; font-size: 12px; color: #86909c; }
.pb-tag { background: #f2f3f5; border-radius: 4px; padding: 1px 6px; }
.pb-time { margin-left: auto; }
.pb-note-actions { position: absolute; top: 10px; right: 12px; display: none; gap: 4px; }
.pb-note:hover .pb-note-actions { display: inline-flex; }
.pb-detail { max-width: 820px; margin: 0 auto; }
.pb-content { white-space: pre-wrap; word-break: break-word; background: #fff; border: 1px solid #e5e6eb; border-radius: 10px; padding: 14px 16px; font-size: 14px; line-height: 1.6; }
.pb-modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.35); display: flex; justify-content: center; align-items: center; z-index: 1000; }
.pb-modal { width: 460px; background: #fff; border-radius: 12px; padding: 22px; box-shadow: 0 8px 30px rgba(0,0,0,.2); }
.pb-modal h3 { margin: 0 0 14px; }
.pb-modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
</style>
