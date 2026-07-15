<template>
  <div class="pb-root">
    <!-- 解锁 / 设置主密码 -->
    <div v-if="mode === 'setup' || mode === 'unlock' || mode === 'change'" class="pb-auth">
      <div class="pb-auth-card">
        <h2>{{ mode === 'setup' ? '设置主密码' : (mode === 'change' ? '修改主密码' : '解锁加密记事本') }}</h2>
        <p class="pb-hint" v-if="mode === 'setup'">首次使用，请设置主密码（至少 8 位）。该密码仅你知晓，遗忘将无法恢复。</p>
        <p class="pb-hint" v-if="mode === 'unlock' && !status.initialized">新账号请使用默认密码 <b>12345678</b> 解锁。</p>
        <p class="pb-hint" v-if="mode === 'change'">出于安全，首次使用需修改主密码。</p>

        <input v-if="mode !== 'change'" type="password" v-model="password" class="pb-input"
               :placeholder="mode === 'setup' ? '设置主密码' : '输入主密码'" @keyup.enter="mode === 'setup' ? doSetup() : doUnlock()" />
        <template v-else>
          <input type="password" v-model="password" class="pb-input" placeholder="原主密码" />
          <input type="password" v-model="newPassword" class="pb-input" placeholder="新主密码（至少 8 位）" />
        </template>

        <div class="pb-err" v-if="error">{{ error }}</div>
        <button class="pb-btn pb-btn-primary" :disabled="busy" @click="mode === 'setup' ? doSetup() : (mode === 'change' ? doChange() : doUnlock())">
          {{ busy ? '处理中…' : (mode === 'setup' ? '设置并进入' : (mode === 'change' ? '修改并进入' : '解锁')) }}
        </button>
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
import { ref, reactive, onMounted } from "vue";

const BASE = "/apis/passwordbook.halo.run/v1alpha1/passwordbook";
const axios: any = (window as any).axios;

const mode = ref<"loading" | "setup" | "unlock" | "change" | "main">("loading");
const status = reactive({ initialized: false, mustChange: false });
const token = ref("");
const password = ref("");
const newPassword = ref("");
const busy = ref(false);
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
.pb-auth { display: flex; justify-content: center; align-items: center; min-height: 60vh; }
.pb-auth-card { width: 340px; background: #fff; border: 1px solid #e5e6eb; border-radius: 12px; padding: 24px; box-shadow: 0 2px 12px rgba(0,0,0,.06); }
.pb-auth-card h2 { margin: 0 0 12px; font-size: 18px; }
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
