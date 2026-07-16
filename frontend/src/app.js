const isStaticPreview = window.location.protocol === 'file:';
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const state = { currentUser: null, knowledgeBases: [], activeId: null, documents: [], conversationId: null,
    conversations: [], memories: [], documentEvents: null, documentEventKnowledgeBaseId: null,
    feishuConnections: [], feishuSources: [], feishuTasks: [] };

function iconMarkup(name, className = 'ui-icon') {
    return `<svg class="${className}" aria-hidden="true"><use href="#icon-${name}"/></svg>`;
}

function syncViewportHeight() {
    document.documentElement.style.setProperty('--viewport-height', `${window.innerHeight}px`);
}

syncViewportHeight();
window.addEventListener('resize', syncViewportHeight);

const elements = {
    authPage: document.querySelector('#authPage'),
    authForm: document.querySelector('#authForm'),
    authEmail: document.querySelector('#authEmail'),
    authPassword: document.querySelector('#authPassword'),
    authConfirmPassword: document.querySelector('#authConfirmPassword'),
    authError: document.querySelector('#authError'),
    authSubmit: document.querySelector('#authSubmit'),
    appShell: document.querySelector('#appShell'),
    chatPanel: document.querySelector('.chat-panel'),
    settingsPage: document.querySelector('#settingsPage'),
    knowledgeList: document.querySelector('#knowledgeList'),
    documentList: document.querySelector('#documentList'),
    activeKnowledgeName: document.querySelector('#activeKnowledgeName'),
    messages: document.querySelector('#messages'),
    emptyState: document.querySelector('#emptyState'),
    chatForm: document.querySelector('#chatForm'),
    questionInput: document.querySelector('#questionInput'),
    sendButton: document.querySelector('#sendButton'),
    fileInput: document.querySelector('#fileInput'),
    createDialog: document.querySelector('#createDialog'),
    createForm: document.querySelector('#createForm'),
    nameInput: document.querySelector('#nameInput'),
    descriptionInput: document.querySelector('#descriptionInput'),
    createKnowledgeButton: document.querySelector('#createKnowledgeButton'),
    historyDialog: document.querySelector('#historyDialog'),
    historySearchInput: document.querySelector('#historySearchInput'),
    historyList: document.querySelector('#historyList'),
    historyEmpty: document.querySelector('#historyEmpty'),
    modelStatus: document.querySelector('#modelStatus'),
    toast: document.querySelector('#toast'),
    feishuConnectionDialog: document.querySelector('#feishuConnectionDialog'),
    feishuConnectionForm: document.querySelector('#feishuConnectionForm'),
    feishuSourceDialog: document.querySelector('#feishuSourceDialog'),
    feishuSourceForm: document.querySelector('#feishuSourceForm'),
    feishuSourceList: document.querySelector('#feishuSourceList'),
    feishuTaskList: document.querySelector('#feishuTaskList')
};

const defaultSettings = {
    profileName: '',
    workRole: '',
    colorMode: 'auto',
    chatBackground: 'none',
    language: 'zh-CN',
    topK: 6,
    showCitations: true,
    autoScroll: true,
    smoothStreaming: true,
    collapseLargePastes: true,
    personalInstructions: '',
    referenceMemories: true,
    updateMemories: false
};

let appSettings = { ...defaultSettings };
let authMode = 'login';

function saveSettings(applyImmediately = true) {
    if (applyImmediately) applySettings();
    if (isStaticPreview) return;
    window.clearTimeout(saveSettings.timer);
    saveSettings.timer = window.setTimeout(async () => {
        try {
            const saved = await api('/api/v1/settings', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    ...appSettings,
                    colorMode: appSettings.colorMode.toUpperCase(),
                    chatBackground: appSettings.chatBackground.toUpperCase(),
                    language: appSettings.language === 'en' ? 'EN' : 'ZH_CN'
                })
            });
            appSettings = { ...defaultSettings, ...saved };
            applySettings();
        } catch (error) {
            showToast(`设置保存失败：${error.message}`);
        }
    }, 350);
}

async function loadServerSettings() {
    if (isStaticPreview) return;
    appSettings = { ...defaultSettings, ...await api('/api/v1/settings') };
    applySettings();
}

function applySettings() {
    document.documentElement.dataset.theme = appSettings.colorMode;
    elements.chatPanel.dataset.chatBackground = appSettings.chatBackground;
    document.querySelector('#profileNameInput').value = state.currentUser?.displayName || appSettings.profileName;
    document.querySelector('#workRoleInput').value = appSettings.workRole;
    document.querySelector('#colorModeSelect').value = appSettings.colorMode;
    document.querySelector('#languageSelect').value = appSettings.language;
    document.querySelector('#topKInput').value = appSettings.topK;
    document.querySelector('#citationsToggle').checked = appSettings.showCitations;
    document.querySelector('#autoScrollToggle').checked = appSettings.autoScroll;
    document.querySelector('#smoothStreamingToggle').checked = appSettings.smoothStreaming;
    document.querySelector('#collapseLargePastesToggle').checked = appSettings.collapseLargePastes;
    document.querySelector('#personalInstructionsInput').value = appSettings.personalInstructions;
    document.querySelector('#personalInstructionsCount').textContent = appSettings.personalInstructions.length;
    document.querySelector('#referenceMemoriesToggle').checked = appSettings.referenceMemories;
    document.querySelector('#updateMemoriesToggle').checked = appSettings.updateMemories;
    document.querySelector('#settingsModelName').textContent = elements.modelStatus.textContent;
    document.querySelectorAll('[data-background]').forEach(button => {
        button.classList.toggle('active', button.dataset.background === appSettings.chatBackground);
    });
}

function setModelStatus(value) {
    document.querySelectorAll('[data-model-status]').forEach(element => { element.textContent = value; });
    document.querySelector('#settingsModelName').textContent = value;
}

function userLabel() {
    if (state.currentUser?.displayName) return state.currentUser.displayName;
    return state.currentUser?.email?.split('@')[0] || '用户';
}

function renderCurrentUser() {
    if (!state.currentUser) return;
    document.querySelector('#accountDisplayName').textContent = userLabel();
    document.querySelector('#accountEmail').textContent = state.currentUser.email;
    document.querySelector('#settingsAccountEmail').textContent = state.currentUser.email;
    document.querySelector('#settingsAccountRole').textContent = state.currentUser.role === 'owner' ? '所有者' : '成员';
    document.querySelector('#profileNameInput').value = state.currentUser.displayName || appSettings.profileName;
    document.querySelectorAll('[data-display-name-prompt]').forEach(prompt => {
        prompt.hidden = Boolean(state.currentUser.displayName);
        const input = prompt.querySelector('[data-welcome-name]');
        if (input && !state.currentUser.displayName) input.value = '';
    });
}

function setAuthMode(mode) {
    authMode = mode;
    const registering = mode === 'register';
    document.querySelector('#authTitle').textContent = registering ? '创建 JadeBase 账号' : '欢迎使用 JadeBase';
    document.querySelector('#authSubtitle').textContent = registering ? '加入你的企业 AI 工作空间' : '你的开源企业 AI 工作空间';
    document.querySelector('#authSubmitLabel').textContent = registering ? '创建账号' : '登录';
    document.querySelector('#authSwitchHint').textContent = registering ? '已经有账号？' : '还没有账号？';
    document.querySelector('#authSwitchButton').textContent = registering ? '返回登录' : '创建账号';
    document.querySelectorAll('.auth-confirm-field').forEach(element => { element.hidden = !registering; });
    elements.authConfirmPassword.required = registering;
    elements.authPassword.autocomplete = registering ? 'new-password' : 'current-password';
    elements.authError.hidden = true;
}

function showAuthPage(message = '') {
    state.documentEvents?.close();
    state.documentEvents = null;
    state.currentUser = null;
    elements.appShell.hidden = true;
    elements.authPage.hidden = false;
    elements.authError.textContent = message;
    elements.authError.hidden = !message;
    elements.authPassword.value = '';
    elements.authConfirmPassword.value = '';
    window.setTimeout(() => elements.authEmail.focus());
}

function showWorkspace() {
    elements.authPage.hidden = true;
    elements.appShell.hidden = false;
    elements.chatPanel.classList.add('welcome-mode');
    renderCurrentUser();
}

function openSettings() {
    closeAccountMenu();
    elements.chatPanel.hidden = true;
    elements.settingsPage.hidden = false;
    elements.settingsPage.scrollTop = 0;
}

function closeSettings() {
    elements.settingsPage.hidden = true;
    elements.chatPanel.hidden = false;
}

function setAccountMenuExpanded(expanded) {
    const popover = document.querySelector('#accountPopover');
    popover.hidden = !expanded;
    document.querySelector('#accountMenuButton').setAttribute('aria-expanded', String(expanded));
    document.querySelector('#mobileAccountButton').setAttribute('aria-expanded', String(expanded));
}

function toggleAccountMenu() {
    setAccountMenuExpanded(document.querySelector('#accountPopover').hidden);
}

function closeAccountMenu() {
    setAccountMenuExpanded(false);
}

function emptyStateMarkup() {
    return `
        <div class="empty-state" id="emptyState">
            <div class="welcome-heading-row">
                <div class="welcome-heading">
                    <span class="jade-logo large" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
                    <h1>有什么可以帮你？</h1>
                </div>
                <div class="welcome-actions">
                    <button class="welcome-add-button" type="button" data-welcome-add aria-label="创建知识库">${iconMarkup('add')}</button>
                    <span class="welcome-separator" aria-hidden="true"></span>
                    <span class="model-pill"><span class="model-mark" aria-hidden="true">◆</span><span data-model-status>${escapeHtml(elements.modelStatus.textContent)}</span></span>
                </div>
            </div>
            <form class="display-name-prompt" data-display-name-prompt ${state.currentUser?.displayName ? 'hidden' : ''}>
                <div class="display-name-copy">
                    ${iconMarkup('user')}
                    <span><strong>JadeBase 应该如何称呼你？</strong><small>这个名称会显示在应用中。</small></span>
                </div>
                <div class="display-name-action">
                    <label class="sr-only">你的名称</label>
                    <input data-welcome-name maxlength="40" autocomplete="name" placeholder="你的名称">
                    <button type="submit" disabled>保存</button>
                </div>
            </form>
        </div>`;
}

function resetConversation() {
    state.conversationId = null;
    elements.messages.innerHTML = emptyStateMarkup();
    elements.emptyState = elements.messages.querySelector('#emptyState');
    elements.chatPanel.classList.add('welcome-mode');
    renderCurrentUser();
    elements.questionInput.value = '';
    elements.questionInput.focus();
}

async function api(path, options = {}) {
    const { skipAuthRedirect = false, ...requestOptions } = options;
    const response = await fetch(`${apiBaseUrl}${path}`, { credentials: 'same-origin', ...requestOptions });
    if (!response.ok) {
        let message = `请求失败 (${response.status})`;
        try { message = (await response.json()).message || message; } catch (_) { /* no-op */ }
        const error = new Error(message);
        error.status = response.status;
        if (response.status === 401 && !skipAuthRedirect
                && !path.endsWith('/login') && !path.endsWith('/register')) showAuthPage('登录已过期，请重新登录');
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

async function loadMemories() {
    state.memories = isStaticPreview ? [] : await api('/api/v1/memories');
    renderMemories();
}

function renderMemories() {
    const list = document.querySelector('#memoryList');
    if (!state.memories.length) {
        list.innerHTML = '<p class="memory-empty">还没有保存的记忆</p>';
        return;
    }
    list.innerHTML = state.memories.map(memory => `
        <div class="memory-item">
            <span>${escapeHtml(memory.content)}</span>
            <button class="memory-delete" type="button" data-memory-delete="${memory.id}" aria-label="删除记忆">
                ${iconMarkup('close')}
            </button>
        </div>`).join('');
}

function escapeHtml(value) {
    return String(value).replace(/[&<>'"]/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    })[character]);
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.classList.add('visible');
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => elements.toast.classList.remove('visible'), 2600);
}

function updateNotificationBadges(count) {
    ['desktopNotificationBadge', 'menuNotificationBadge'].forEach(id => {
        const badge = document.querySelector(`#${id}`);
        badge.hidden = count === 0;
        badge.textContent = count > 99 ? '99+' : String(count);
    });
}

async function loadNotifications() {
    if (isStaticPreview) {
        updateNotificationBadges(2);
        return { unreadCount: 2, items: [] };
    }
    const result = await api('/api/v1/notifications');
    updateNotificationBadges(result.unreadCount);
    return result;
}

function formatConversationTime(value) {
    return new Intl.DateTimeFormat('zh-CN', {
        month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(new Date(value));
}

function renderConversations() {
    elements.historyEmpty.hidden = state.conversations.length > 0;
    elements.historyList.innerHTML = state.conversations.map(item => `
        <div class="history-item">
            <button class="history-open" type="button" data-conversation-open="${item.id}">
                <strong>${escapeHtml(item.title)}</strong>
                <small>${item.messageCount} 条消息 · ${formatConversationTime(item.updatedAt)}</small>
            </button>
            <button class="history-delete" type="button" data-conversation-delete="${item.id}" aria-label="删除对话">
                ${iconMarkup('close')}
            </button>
        </div>`).join('');
}

async function loadConversations(query = '') {
    if (isStaticPreview) {
        state.conversations = [];
    } else {
        const suffix = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
        state.conversations = await api(`/api/v1/conversations${suffix}`);
    }
    renderConversations();
}

async function openConversation(conversationId) {
    const conversation = await api(`/api/v1/conversations/${conversationId}`);
    state.conversationId = conversation.id;
    state.activeId = conversation.knowledgeBaseId;
    renderKnowledgeBases();
    await loadDocuments();
    elements.messages.innerHTML = '';
    elements.emptyState = null;
    conversation.messages.forEach(message => appendMessage(message.role, message.content, message.sources));
    elements.historyDialog.close();
    closeSettings();
}

async function loadKnowledgeBases(preferredId) {
    state.knowledgeBases = await api('/api/v1/knowledge-bases');
    state.activeId = preferredId || state.activeId || state.knowledgeBases[0]?.id || null;
    renderKnowledgeBases();
    await loadDocuments();
}

function renderKnowledgeBases() {
    elements.knowledgeList.innerHTML = state.knowledgeBases.map(item => `
        <button class="knowledge-item ${item.id === state.activeId ? 'active' : ''}" data-id="${item.id}" type="button">
            ${iconMarkup('folder')}
            <strong>${escapeHtml(item.name)}</strong>
            <span>${escapeHtml(item.description || '暂无描述')}</span>
        </button>`).join('');
    const active = state.knowledgeBases.find(item => item.id === state.activeId);
    elements.activeKnowledgeName.textContent = active?.name || '暂无知识库';
}

async function loadDocuments() {
    if (isStaticPreview) {
        state.documents = [];
        renderDocuments();
        return;
    }
    if (!state.activeId) {
        state.documents = [];
        renderDocuments();
        return;
    }
    state.documents = await api(`/api/v1/knowledge-bases/${state.activeId}/documents`);
    renderDocuments();
    connectDocumentEvents();
    scheduleDocumentPolling();
}

function connectDocumentEvents() {
    if (isStaticPreview || !state.activeId || typeof EventSource === 'undefined') return;
    if (state.documentEvents && state.documentEventKnowledgeBaseId === state.activeId) return;
    state.documentEvents?.close();
    state.documentEventKnowledgeBaseId = state.activeId;
    const events = new EventSource(`${apiBaseUrl}/api/v1/knowledge-bases/${state.activeId}/documents/events`);
    state.documentEvents = events;
    events.addEventListener('snapshot', event => {
        state.documents = JSON.parse(event.data);
        renderDocuments();
    });
    events.addEventListener('document', event => {
        const document = JSON.parse(event.data);
        const index = state.documents.findIndex(item => item.id === document.id);
        if (index >= 0) state.documents[index] = document;
        else state.documents.unshift(document);
        renderDocuments();
    });
    events.onopen = () => window.clearTimeout(scheduleDocumentPolling.timer);
    events.onerror = () => scheduleDocumentPolling();
}

function scheduleDocumentPolling() {
    window.clearTimeout(scheduleDocumentPolling.timer);
    const pending = state.documents.some(item => item.status === 'QUEUED' || item.status === 'PROCESSING');
    if (!pending || isStaticPreview) return;
    if (state.documentEvents?.readyState === EventSource.OPEN) return;
    scheduleDocumentPolling.timer = window.setTimeout(() => {
        loadDocuments().catch(error => showToast(error.message));
    }, 1200);
}

function renderDocuments() {
    if (!state.documents.length) {
        elements.documentList.innerHTML = '<div class="document-item"><div class="document-meta">当前知识库还没有文档</div></div>';
        return;
    }
    elements.documentList.innerHTML = state.documents.map(item => `
        <article class="document-item">
            <div class="document-row">
                ${item.sourceUrl ? `<a class="document-name source-document-name" href="${escapeHtml(safeHttpUrl(item.sourceUrl))}" target="_blank" rel="noopener noreferrer" title="在飞书中打开 ${escapeHtml(item.name)}">${escapeHtml(item.name)}</a>`
                    : `<div class="document-name" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</div>`}
                <div class="document-actions">
                    ${item.status === 'FAILED' ? `<button class="retry-button" data-document-retry="${item.id}" type="button" aria-label="重试索引">${iconMarkup('sync')}</button>` : ''}
                    <button class="delete-button" data-document-delete="${item.id}" type="button" aria-label="删除文档">${iconMarkup('close')}</button>
                </div>
            </div>
            <div class="document-row document-meta">
                <span>${item.chunkCount} 个片段 · ${formatBytes(item.sizeBytes)}</span>
                <span class="document-status ${item.status === 'FAILED' ? 'failed' : ''}" title="${escapeHtml(item.errorMessage || '')}">${statusText(item.status, item.progress)}</span>
            </div>
            ${item.sourceType === 'FEISHU' ? `<div class="document-source-meta"><span>飞书同步</span>${item.sourceAuthor ? `<span>${escapeHtml(item.sourceAuthor)}</span>` : ''}</div>` : ''}
        </article>`).join('');
}

function safeHttpUrl(value) {
    try {
        const url = new URL(value);
        return ['http:', 'https:'].includes(url.protocol) ? url.href : '#';
    } catch { return '#'; }
}

function statusText(status, progress = 0) {
    return ({ QUEUED: '排队中', PROCESSING: `处理中 ${progress}%`, READY: '已就绪', FAILED: '失败' })[status] || status;
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function loadFeishuConnector() {
    if (isStaticPreview) {
        document.querySelector('#feishuConnectionStatus').textContent = '静态预览中不可配置';
        return;
    }
    const [connections, sources, tasks] = await Promise.all([
        api('/api/v1/connectors/feishu/connections'),
        api('/api/v1/connectors/feishu/sources'),
        api('/api/v1/connectors/feishu/sync-tasks')
    ]);
    state.feishuConnections = connections;
    state.feishuSources = sources;
    state.feishuTasks = tasks;
    renderFeishuConnector();
    scheduleFeishuRefresh();
}

function renderFeishuConnector() {
    const status = document.querySelector('#feishuConnectionStatus');
    const manager = document.querySelector('#feishuManager');
    const connection = state.feishuConnections[0];
    manager.hidden = !connection;
    document.querySelector('#configureFeishuButton').textContent = connection ? '管理连接' : '配置';
    status.textContent = !connection ? '尚未配置'
        : connection.status === 'CONNECTED'
            ? `${connection.name} · ${connection.sourceCount} 个来源`
            : `${connection.name} · ${connection.statusMessage || '连接异常'}`;
    document.querySelector('#feishuConnectorCard').classList.toggle('connector-error', connection?.status === 'ERROR');

    if (!state.feishuSources.length) {
        elements.feishuSourceList.innerHTML = '<p class="connector-empty">还没有同步来源</p>';
    } else {
        elements.feishuSourceList.innerHTML = state.feishuSources.map(source => {
            const task = source.latestTask;
            const running = task && ['QUEUED', 'RUNNING'].includes(task.status);
            return `<article class="connector-source-item">
                <span class="connector-source-icon">${iconMarkup(source.sourceType === 'WIKI' ? 'folder' : 'attachment')}</span>
                <div class="connector-source-copy">
                    <div><strong>${escapeHtml(source.remoteName)}</strong><span class="connector-type-label">${source.sourceType === 'WIKI' ? 'Wiki' : '文件夹'}</span></div>
                    <small>${escapeHtml(source.knowledgeBaseName)} · ${source.enabled ? `每 ${formatInterval(source.syncIntervalMinutes)}同步` : '已暂停'}</small>
                    <span class="connector-source-status ${source.lastSyncStatus === 'FAILED' ? 'failed' : ''}">${escapeHtml(source.lastSyncMessage || '等待首次同步')}</span>
                </div>
                <div class="connector-source-actions">
                    <button class="icon-button ${running ? 'is-spinning' : ''}" data-feishu-sync="${source.id}" type="button" title="立即增量同步" aria-label="立即同步" ${running || !source.enabled ? 'disabled' : ''}>${iconMarkup('sync')}</button>
                    <button class="secondary-button" data-feishu-toggle="${source.id}" data-enabled="${source.enabled}" type="button">${source.enabled ? '暂停' : '启用'}</button>
                    <button class="icon-button connector-delete-action" data-feishu-source-delete="${source.id}" type="button" title="删除来源" aria-label="删除来源">${iconMarkup('close')}</button>
                </div>
            </article>`;
        }).join('');
    }

    if (!state.feishuTasks.length) {
        elements.feishuTaskList.innerHTML = '<p class="connector-empty">暂无同步任务</p>';
    } else {
        elements.feishuTaskList.innerHTML = state.feishuTasks.slice(0, 8).map(task => {
            const source = state.feishuSources.find(item => item.id === task.sourceId);
            return `<article class="connector-task-item">
                <span class="task-status-dot ${task.status.toLowerCase()}"></span>
                <div><strong>${escapeHtml(source?.remoteName || '已删除来源')}</strong><small>${task.mode === 'FULL' ? '全量同步' : '增量同步'} · ${connectorTaskStatus(task.status)}</small></div>
                <div class="task-counts"><span>扫描 ${task.scannedCount}</span><span>+${task.createdCount} / ~${task.updatedCount} / -${task.deletedCount}</span></div>
                <time>${formatConnectorDate(task.completedAt || task.startedAt || task.createdAt)}</time>
                ${task.status === 'FAILED' ? `<button class="secondary-button" data-feishu-task-retry="${task.id}" type="button">重试</button>` : ''}
            </article>`;
        }).join('');
    }
}

function connectorTaskStatus(status) {
    return ({ QUEUED: '等待执行', RUNNING: '同步中', SUCCEEDED: '已完成', FAILED: '失败' })[status] || status;
}

function formatInterval(minutes) {
    return minutes < 60 ? `${minutes} 分钟` : `${minutes / 60} 小时`;
}

function formatConnectorDate(value) {
    if (!value) return '--';
    return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
        .format(new Date(value));
}

function scheduleFeishuRefresh() {
    window.clearTimeout(scheduleFeishuRefresh.timer);
    const active = state.feishuTasks.some(task => ['QUEUED', 'RUNNING'].includes(task.status));
    if (!active || document.querySelector('[data-settings-section="connectors"]:not(.active)')) return;
    scheduleFeishuRefresh.timer = window.setTimeout(() => {
        loadFeishuConnector().catch(error => showToast(error.message));
    }, 1800);
}

function connectionPayload() {
    return {
        name: document.querySelector('#feishuConnectionName').value,
        appId: document.querySelector('#feishuAppId').value,
        appSecret: document.querySelector('#feishuAppSecret').value
    };
}

function showConnectorResult(id, message, failed = false) {
    const result = document.querySelector(id);
    result.textContent = message;
    result.classList.toggle('failed', failed);
    result.hidden = false;
}

function openFeishuConnectionDialog() {
    const connection = state.feishuConnections[0];
    elements.feishuConnectionForm.reset();
    elements.feishuConnectionForm.dataset.connectionId = connection?.id || '';
    document.querySelector('#feishuConnectionName').value = connection?.name || '';
    document.querySelector('#feishuAppId').value = connection?.appId || '';
    document.querySelector('#feishuConnectionResult').hidden = true;
    elements.feishuConnectionDialog.showModal();
    document.querySelector(connection ? '#feishuConnectionName' : '#feishuAppId').focus();
}

async function discoverFeishuSpaces() {
    const connectionId = document.querySelector('#feishuSourceConnection').value;
    if (!connectionId) throw new Error('请先选择飞书连接');
    const button = document.querySelector('#discoverFeishuSpacesButton');
    button.disabled = true;
    try {
        const spaces = await api(`/api/v1/connectors/feishu/connections/${connectionId}/spaces`);
        const select = document.querySelector('#feishuWikiSpace');
        select.innerHTML = spaces.map(space => `<option value="${escapeHtml(space.id)}">${escapeHtml(space.name)}</option>`).join('');
        showConnectorResult('#feishuSourceResult', spaces.length ? `发现 ${spaces.length} 个可访问空间` : '没有发现可访问的 Wiki 空间', !spaces.length);
    } finally { button.disabled = false; }
}

function openFeishuSourceDialog() {
    elements.feishuSourceForm.reset();
    document.querySelector('#feishuSourceResult').hidden = true;
    document.querySelector('#feishuSourceConnection').innerHTML = state.feishuConnections
        .map(connection => `<option value="${connection.id}">${escapeHtml(connection.name)}</option>`).join('');
    document.querySelector('#feishuTargetKnowledgeBase').innerHTML = state.knowledgeBases
        .map(item => `<option value="${item.id}" ${item.id === state.activeId ? 'selected' : ''}>${escapeHtml(item.name)}</option>`).join('');
    setFeishuSourceType('WIKI');
    elements.feishuSourceDialog.showModal();
    discoverFeishuSpaces().catch(error => showConnectorResult('#feishuSourceResult', error.message, true));
}

function setFeishuSourceType(type) {
    elements.feishuSourceForm.dataset.sourceType = type;
    document.querySelectorAll('[data-feishu-source-type]').forEach(button => button.classList.toggle('active', button.dataset.feishuSourceType === type));
    document.querySelector('#feishuWikiFields').hidden = type !== 'WIKI';
    document.querySelector('#feishuFolderFields').hidden = type !== 'FOLDER';
}

function appendMessage(role, text, sources = []) {
    elements.emptyState?.remove();
    elements.chatPanel.classList.remove('welcome-mode');
    const article = document.createElement('article');
    article.className = `message ${role}`;
    if (role === 'assistant' && appSettings.smoothStreaming) article.classList.add('smooth-enter');
    const sourceMarkup = sources.length && appSettings.showCitations ? `
        <div class="sources">
            ${sources.map((source, index) => `
                <div class="source-item">
                    <div class="source-title">
                        <span>[资料${index + 1}] ${escapeHtml(source.documentName)}</span>
                        <span class="source-score">${Math.round(source.score * 100)}%</span>
                    </div>
                    <p class="source-snippet">${escapeHtml(source.snippet)}</p>
                </div>`).join('')}
        </div>` : '';
    article.innerHTML = `
        <div class="message-label">${role === 'user' ? '你' : 'JadeBase'}</div>
        <div class="message-body">${escapeHtml(text)}</div>
        ${sourceMarkup}`;
    elements.messages.appendChild(article);
    if (appSettings.autoScroll) elements.messages.scrollTop = elements.messages.scrollHeight;
}

elements.knowledgeList.addEventListener('click', async event => {
    const button = event.target.closest('[data-id]');
    if (!button) return;
    state.activeId = button.dataset.id;
    renderKnowledgeBases();
    await loadDocuments();
    resetConversation();
});

elements.chatForm.addEventListener('submit', async event => {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!state.activeId || !question) return;
    appendMessage('user', question);
    elements.questionInput.value = '';
    if (isStaticPreview) {
        appendMessage('assistant', '这是 JadeBase 静态预览。连接本地服务后即可检索知识库并生成带引用的回答。');
        return;
    }
    elements.sendButton.disabled = true;
    elements.sendButton.innerHTML = iconMarkup('hourglass');
    try {
        const result = await api('/api/v1/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ knowledgeBaseId: state.activeId, conversationId: state.conversationId,
                question, topK: appSettings.topK, language: appSettings.language })
        });
        state.conversationId = result.conversationId;
        appendMessage('assistant', result.answer, result.sources);
        setModelStatus(result.mode === 'model' ? '模型已连接' : '本地演示');
        if (appSettings.updateMemories && /^记住[：:]/.test(question)) await loadMemories();
    } catch (error) {
        showToast(error.message);
        appendMessage('assistant', '回答生成失败，请稍后重试。');
    } finally {
        elements.sendButton.disabled = false;
        elements.sendButton.innerHTML = iconMarkup('send');
        elements.questionInput.focus();
    }
});

elements.fileInput.addEventListener('change', async () => {
    if (!state.activeId || !elements.fileInput.files.length) return;
    const files = [...elements.fileInput.files];
    showToast(`正在处理 ${files.length} 个文档`);
    try {
        for (const file of files) {
            const form = new FormData();
            form.append('file', file);
            await api(`/api/v1/knowledge-bases/${state.activeId}/documents`, { method: 'POST', body: form });
        }
        await loadDocuments();
        showToast('文档已进入索引队列');
    } catch (error) {
        showToast(error.message);
    } finally {
        elements.fileInput.value = '';
    }
});

elements.documentList.addEventListener('click', async event => {
    const retryButton = event.target.closest('[data-document-retry]');
    const deleteButton = event.target.closest('[data-document-delete]');
    try {
        if (retryButton) {
            await api(`/api/v1/knowledge-bases/${state.activeId}/documents/${retryButton.dataset.documentRetry}/retry`, { method: 'POST' });
            await loadDocuments();
            showToast('索引任务已重新入队');
        }
        if (deleteButton && window.confirm('确认删除这个文档及其索引吗？')) {
            await api(`/api/v1/knowledge-bases/${state.activeId}/documents/${deleteButton.dataset.documentDelete}`, { method: 'DELETE' });
            await loadDocuments();
            showToast('文档已删除');
        }
    } catch (error) { showToast(error.message); }
});

function syncCreateButton() {
    elements.createKnowledgeButton.disabled = !elements.nameInput.value.trim();
}

document.querySelector('#openCreateButton').addEventListener('click', () => {
    elements.createForm.reset();
    syncCreateButton();
    elements.createDialog.showModal();
    elements.nameInput.focus();
});
document.querySelector('#closeCreateButton').addEventListener('click', () => elements.createDialog.close());
document.querySelector('#cancelCreateButton').addEventListener('click', () => elements.createDialog.close());
elements.nameInput.addEventListener('input', syncCreateButton);
document.querySelector('#newSessionButton').addEventListener('click', resetConversation);
document.querySelector('#searchChatsButton').addEventListener('click', async () => {
    elements.historySearchInput.value = '';
    elements.historyDialog.showModal();
    elements.historySearchInput.focus();
    try { await loadConversations(); } catch (error) { showToast(error.message); }
});
document.querySelector('#closeHistoryButton').addEventListener('click', () => elements.historyDialog.close());
elements.historySearchInput.addEventListener('input', () => {
    window.clearTimeout(elements.historySearchInput.searchTimer);
    elements.historySearchInput.searchTimer = window.setTimeout(() => {
        loadConversations(elements.historySearchInput.value).catch(error => showToast(error.message));
    }, 250);
});
elements.historyList.addEventListener('click', async event => {
    const openButton = event.target.closest('[data-conversation-open]');
    const deleteButton = event.target.closest('[data-conversation-delete]');
    try {
        if (openButton) await openConversation(openButton.dataset.conversationOpen);
        if (deleteButton && window.confirm('确认删除这个历史对话吗？')) {
            await api(`/api/v1/conversations/${deleteButton.dataset.conversationDelete}`, { method: 'DELETE' });
            if (state.conversationId === deleteButton.dataset.conversationDelete) resetConversation();
            await loadConversations(elements.historySearchInput.value);
            showToast('历史对话已删除');
        }
    } catch (error) { showToast(error.message); }
});
document.querySelector('#retrievalSettingsButton').addEventListener('click', () => showToast('混合检索 · 向量 65% · 关键词 35%'));
document.querySelector('#sidebarToggle').addEventListener('click', event => {
    const collapsed = elements.appShell.classList.toggle('sidebar-collapsed');
    event.currentTarget.setAttribute('aria-label', collapsed ? '展开侧栏' : '收起侧栏');
});

document.querySelector('#openSettingsButton').addEventListener('click', openSettings);
document.querySelector('#accountMenuButton').addEventListener('click', toggleAccountMenu);
document.querySelector('#mobileAccountButton').addEventListener('click', toggleAccountMenu);
document.querySelector('#adminPanelButton').addEventListener('click', () => showToast('管理后台将在企业权限阶段开放'));
document.querySelector('#notificationsButton').addEventListener('click', async () => {
    closeAccountMenu();
    try {
        const result = await loadNotifications();
        const unread = result.items.filter(item => !item.read);
        if (!unread.length) {
            showToast('暂无未读通知');
            return;
        }
        await api('/api/v1/notifications/read-all', { method: 'POST' });
        updateNotificationBadges(0);
        showToast(unread.map(item => item.title).slice(0, 2).join(' · '));
    } catch (error) { showToast(error.message); }
});
document.querySelector('#helpButton').addEventListener('click', () => {
    closeAccountMenu();
    showToast('帮助中心内容正在整理中');
});
document.querySelector('#logoutButton').addEventListener('click', async () => {
    closeAccountMenu();
    try {
        await api('/api/v1/auth/logout', { method: 'POST' });
    } catch (error) {
        if (error.status !== 401) showToast(error.message);
    } finally {
        setAuthMode('login');
        showAuthPage();
    }
});
document.querySelector('#closeSettingsButton').addEventListener('click', closeSettings);

document.addEventListener('click', event => {
    const popover = document.querySelector('#accountPopover');
    if (popover.hidden) return;
    if (popover.contains(event.target) || document.querySelector('#accountMenuButton').contains(event.target)
            || document.querySelector('#mobileAccountButton').contains(event.target)) return;
    closeAccountMenu();
});

document.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeAccountMenu();
});

document.querySelectorAll('[data-settings-target]').forEach(button => {
    button.addEventListener('click', () => {
        document.querySelectorAll('[data-settings-target]').forEach(item => item.classList.toggle('active', item === button));
        document.querySelectorAll('[data-settings-section]').forEach(section => {
            section.classList.toggle('active', section.dataset.settingsSection === button.dataset.settingsTarget);
        });
        if (button.dataset.settingsTarget === 'connectors') {
            loadFeishuConnector().catch(error => showToast(error.message));
        } else {
            window.clearTimeout(scheduleFeishuRefresh.timer);
        }
    });
});

document.querySelector('#profileNameInput').addEventListener('input', event => {
    appSettings.profileName = event.target.value;
    saveSettings();
    window.clearTimeout(renderCurrentUser.profileTimer);
    renderCurrentUser.profileTimer = window.setTimeout(async () => {
        try {
            state.currentUser = await api('/api/v1/auth/me', {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ displayName: appSettings.profileName })
            });
            renderCurrentUser();
        } catch (error) { showToast(`名称保存失败：${error.message}`); }
    }, 450);
});
document.querySelector('#workRoleInput').addEventListener('input', event => {
    appSettings.workRole = event.target.value;
    saveSettings();
});
document.querySelector('#colorModeSelect').addEventListener('change', event => {
    appSettings.colorMode = event.target.value;
    saveSettings();
});
document.querySelector('#languageSelect').addEventListener('change', event => {
    appSettings.language = event.target.value;
    saveSettings();
});
document.querySelector('#topKInput').addEventListener('change', event => {
    appSettings.topK = Math.min(12, Math.max(1, Number(event.target.value) || 6));
    saveSettings();
});
document.querySelector('#citationsToggle').addEventListener('change', event => {
    appSettings.showCitations = event.target.checked;
    saveSettings();
});
document.querySelector('#autoScrollToggle').addEventListener('change', event => {
    appSettings.autoScroll = event.target.checked;
    saveSettings();
});
document.querySelector('#smoothStreamingToggle').addEventListener('change', event => {
    appSettings.smoothStreaming = event.target.checked;
    saveSettings();
});
document.querySelector('#collapseLargePastesToggle').addEventListener('change', event => {
    appSettings.collapseLargePastes = event.target.checked;
    if (!event.target.checked) elements.questionInput.classList.remove('collapsed-paste');
    saveSettings();
});
document.querySelector('#personalInstructionsInput').addEventListener('input', event => {
    appSettings.personalInstructions = event.target.value;
    document.querySelector('#personalInstructionsCount').textContent = event.target.value.length;
    saveSettings(false);
});
document.querySelector('#referenceMemoriesToggle').addEventListener('change', event => {
    appSettings.referenceMemories = event.target.checked;
    saveSettings();
});
document.querySelector('#updateMemoriesToggle').addEventListener('change', event => {
    appSettings.updateMemories = event.target.checked;
    saveSettings();
});

elements.questionInput.addEventListener('paste', () => {
    window.setTimeout(() => {
        const value = elements.questionInput.value;
        if (appSettings.collapseLargePastes && (value.length > 200 || value.split('\n').length > 3)) {
            elements.questionInput.classList.add('collapsed-paste');
            showToast(`已折叠 ${value.length} 字的长文本，点击输入框可展开`);
        }
    });
});
elements.questionInput.addEventListener('focus', () => elements.questionInput.classList.remove('collapsed-paste'));

const memoryInput = document.querySelector('#memoryInput');
const memoryAddButton = document.querySelector('#memoryForm button[type="submit"]');
memoryInput.addEventListener('input', () => {
    memoryAddButton.disabled = !memoryInput.value.trim();
});
document.querySelector('#memoryForm').addEventListener('submit', async event => {
    event.preventDefault();
    if (!memoryInput.value.trim()) return;
    try {
        await api('/api/v1/memories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content: memoryInput.value })
        });
        memoryInput.value = '';
        memoryAddButton.disabled = true;
        await loadMemories();
        showToast('记忆已保存');
    } catch (error) { showToast(error.message); }
});
document.querySelector('#memoryList').addEventListener('click', async event => {
    const button = event.target.closest('[data-memory-delete]');
    if (!button) return;
    try {
        await api(`/api/v1/memories/${button.dataset.memoryDelete}`, { method: 'DELETE' });
        await loadMemories();
        showToast('记忆已删除');
    } catch (error) { showToast(error.message); }
});

elements.messages.addEventListener('input', event => {
    if (!event.target.matches('[data-welcome-name]')) return;
    const submit = event.target.closest('form').querySelector('button[type="submit"]');
    submit.disabled = !event.target.value.trim();
});

elements.messages.addEventListener('submit', async event => {
    const form = event.target.closest('[data-display-name-prompt]');
    if (!form) return;
    event.preventDefault();
    const input = form.querySelector('[data-welcome-name]');
    const button = form.querySelector('button[type="submit"]');
    const displayName = input.value.trim();
    if (!displayName) return;
    button.disabled = true;
    try {
        state.currentUser = await api('/api/v1/auth/me', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ displayName })
        });
        appSettings.profileName = displayName;
        saveSettings();
        renderCurrentUser();
        elements.questionInput.focus();
        showToast('显示名称已保存');
    } catch (error) {
        showToast(error.message);
        button.disabled = false;
    }
});

elements.messages.addEventListener('click', event => {
    if (event.target.closest('[data-welcome-add]')) document.querySelector('#openCreateButton').click();
});
document.querySelectorAll('[data-background]').forEach(button => {
    button.addEventListener('click', () => {
        appSettings.chatBackground = button.dataset.background;
        saveSettings();
    });
});
document.querySelector('#clearConversationButton').addEventListener('click', async () => {
    if (!window.confirm('确认清空当前页面中的全部对话吗？')) return;
    try {
        if (state.conversationId && !isStaticPreview) {
            await api(`/api/v1/conversations/${state.conversationId}`, { method: 'DELETE' });
        }
        resetConversation();
        closeSettings();
        showToast('当前对话已清空');
    } catch (error) { showToast(error.message); }
});
document.querySelectorAll('.connector-button').forEach(button => {
    if (button.dataset.connector) button.addEventListener('click', () => showToast(`${button.dataset.connector}将在后续连接器阶段开放`));
});

document.querySelector('#configureFeishuButton').addEventListener('click', openFeishuConnectionDialog);
document.querySelector('#closeFeishuConnectionButton').addEventListener('click', () => elements.feishuConnectionDialog.close());
document.querySelector('#testFeishuConnectionButton').addEventListener('click', async event => {
    const button = event.currentTarget;
    button.disabled = true;
    document.querySelector('#feishuConnectionResult').hidden = true;
    try {
        const connectionId = elements.feishuConnectionForm.dataset.connectionId;
        const payload = connectionPayload();
        const result = connectionId && !payload.appSecret
            ? await api(`/api/v1/connectors/feishu/connections/${connectionId}/test`, { method: 'POST' })
            : await api('/api/v1/connectors/feishu/connections/test', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
            });
        showConnectorResult('#feishuConnectionResult', result.message);
    } catch (error) { showConnectorResult('#feishuConnectionResult', error.message, true); }
    finally { button.disabled = false; }
});

elements.feishuConnectionForm.addEventListener('submit', async event => {
    event.preventDefault();
    const connectionId = event.currentTarget.dataset.connectionId;
    const payload = connectionPayload();
    if (!connectionId && !payload.appSecret) {
        showConnectorResult('#feishuConnectionResult', '首次配置需要填写 App Secret', true);
        return;
    }
    const button = document.querySelector('#saveFeishuConnectionButton');
    button.disabled = true;
    try {
        await api(`/api/v1/connectors/feishu/connections${connectionId ? `/${connectionId}` : ''}`, {
            method: connectionId ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        elements.feishuConnectionDialog.close();
        await loadFeishuConnector();
        showToast('飞书连接已保存');
    } catch (error) { showConnectorResult('#feishuConnectionResult', error.message, true); }
    finally { button.disabled = false; }
});

document.querySelector('#addFeishuSourceButton').addEventListener('click', openFeishuSourceDialog);
document.querySelector('#closeFeishuSourceButton').addEventListener('click', () => elements.feishuSourceDialog.close());
document.querySelector('#cancelFeishuSourceButton').addEventListener('click', () => elements.feishuSourceDialog.close());
document.querySelectorAll('[data-feishu-source-type]').forEach(button => {
    button.addEventListener('click', () => setFeishuSourceType(button.dataset.feishuSourceType));
});
document.querySelector('#discoverFeishuSpacesButton').addEventListener('click', () => {
    discoverFeishuSpaces().catch(error => showConnectorResult('#feishuSourceResult', error.message, true));
});
document.querySelector('#browseFeishuFolderButton').addEventListener('click', async event => {
    const button = event.currentTarget;
    const connectionId = document.querySelector('#feishuSourceConnection').value;
    const input = document.querySelector('#feishuFolderToken');
    button.disabled = true;
    try {
        const token = extractFeishuToken(input.value);
        const suffix = token ? `?folderToken=${encodeURIComponent(token)}` : '';
        const result = await api(`/api/v1/connectors/feishu/connections/${connectionId}/folders${suffix}`);
        input.value = result.folder.id;
        if (!document.querySelector('#feishuFolderName').value) document.querySelector('#feishuFolderName').value = result.folder.name;
        showConnectorResult('#feishuSourceResult', `文件夹可访问，发现 ${result.items.length} 个直接子项`);
    } catch (error) { showConnectorResult('#feishuSourceResult', error.message, true); }
    finally { button.disabled = false; }
});

function extractFeishuToken(value) {
    const trimmed = value.trim();
    if (!trimmed.includes('/')) return trimmed;
    try {
        const url = new URL(trimmed);
        return url.pathname.split('/').filter(Boolean).at(-1) || '';
    } catch { return trimmed; }
}

elements.feishuSourceForm.addEventListener('submit', async event => {
    event.preventDefault();
    const type = event.currentTarget.dataset.sourceType || 'WIKI';
    const wiki = document.querySelector('#feishuWikiSpace');
    const folderToken = extractFeishuToken(document.querySelector('#feishuFolderToken').value);
    const remoteId = type === 'WIKI' ? wiki.value : folderToken;
    const remoteName = type === 'WIKI' ? wiki.selectedOptions[0]?.textContent : document.querySelector('#feishuFolderName').value;
    if (!remoteId) {
        showConnectorResult('#feishuSourceResult', type === 'WIKI' ? '请先发现并选择 Wiki 空间' : '请填写或验证文件夹 Token', true);
        return;
    }
    const button = document.querySelector('#saveFeishuSourceButton');
    button.disabled = true;
    try {
        await api('/api/v1/connectors/feishu/sources', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                connectionId: document.querySelector('#feishuSourceConnection').value,
                knowledgeBaseId: document.querySelector('#feishuTargetKnowledgeBase').value,
                sourceType: type, remoteId, remoteName,
                syncIntervalMinutes: Number(document.querySelector('#feishuSyncInterval').value)
            })
        });
        elements.feishuSourceDialog.close();
        await loadFeishuConnector();
        showToast('同步来源已添加，首次全量同步已入队');
    } catch (error) { showConnectorResult('#feishuSourceResult', error.message, true); }
    finally { button.disabled = false; }
});

elements.feishuSourceList.addEventListener('click', async event => {
    const syncButton = event.target.closest('[data-feishu-sync]');
    const toggleButton = event.target.closest('[data-feishu-toggle]');
    const deleteButton = event.target.closest('[data-feishu-source-delete]');
    try {
        if (syncButton) {
            await api(`/api/v1/connectors/feishu/sources/${syncButton.dataset.feishuSync}/sync`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode: 'INCREMENTAL' })
            });
            showToast('增量同步已入队');
        }
        if (toggleButton) {
            await api(`/api/v1/connectors/feishu/sources/${toggleButton.dataset.feishuToggle}`, {
                method: 'PATCH', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: toggleButton.dataset.enabled !== 'true' })
            });
        }
        if (deleteButton) {
            if (!window.confirm('确认删除这个同步来源及其已导入文档吗？')) return;
            await api(`/api/v1/connectors/feishu/sources/${deleteButton.dataset.feishuSourceDelete}`, { method: 'DELETE' });
            await loadDocuments();
            showToast('同步来源已删除');
        }
        await loadFeishuConnector();
    } catch (error) { showToast(error.message); }
});

elements.feishuTaskList.addEventListener('click', async event => {
    const button = event.target.closest('[data-feishu-task-retry]');
    if (!button) return;
    try {
        await api(`/api/v1/connectors/feishu/sync-tasks/${button.dataset.feishuTaskRetry}/retry`, { method: 'POST' });
        await loadFeishuConnector();
        showToast('同步任务已重新入队');
    } catch (error) { showToast(error.message); }
});
document.querySelector('#refreshFeishuButton').addEventListener('click', () => {
    loadFeishuConnector().catch(error => showToast(error.message));
});

document.querySelector('#changePasswordForm').addEventListener('submit', async event => {
    event.preventDefault();
    const currentPassword = document.querySelector('#currentPasswordInput').value;
    const newPassword = document.querySelector('#newPasswordInput').value;
    const confirmation = document.querySelector('#confirmNewPasswordInput').value;
    if (newPassword !== confirmation) {
        showToast('两次输入的新密码不一致');
        return;
    }
    const button = event.currentTarget.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
        await api('/api/v1/auth/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ currentPassword, newPassword })
        });
        event.currentTarget.reset();
        showToast('密码已更新');
    } catch (error) { showToast(error.message); }
    finally { button.disabled = false; }
});

elements.createForm.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        const created = await api('/api/v1/knowledge-bases', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: elements.nameInput.value, description: elements.descriptionInput.value })
        });
        elements.createDialog.close();
        elements.createForm.reset();
        syncCreateButton();
        await loadKnowledgeBases(created.id);
        showToast('知识库已创建');
    } catch (error) { showToast(error.message); }
});

applySettings();
if (isStaticPreview) {
    state.currentUser = { email: 'preview@jadebase.local', displayName: '', role: 'owner' };
    showWorkspace();
    state.knowledgeBases = [
        { id: 'preview-product', name: '产品知识库', description: '产品资料与使用说明' },
        { id: 'preview-engineering', name: '研发知识库', description: '团队研发规范与架构文档' }
    ];
    state.activeId = state.knowledgeBases[0].id;
    setModelStatus('静态预览');
    renderKnowledgeBases();
    renderDocuments();
    renderMemories();
    loadNotifications();
} else {
    bootstrap();
}

async function bootstrap() {
    try {
        state.currentUser = await api('/api/v1/auth/me', { skipAuthRedirect: true });
        showWorkspace();
        await Promise.all([loadServerSettings(), loadNotifications(), loadKnowledgeBases(), loadMemories()]);
        renderCurrentUser();
    } catch (error) {
        if (error.status === 401) showAuthPage();
        else showAuthPage(`无法连接 JadeBase：${error.message}`);
    }
}

document.querySelector('#authSwitchButton').addEventListener('click', () => {
    setAuthMode(authMode === 'login' ? 'register' : 'login');
    elements.authPassword.value = '';
    elements.authConfirmPassword.value = '';
    elements.authEmail.focus();
});

elements.authForm.addEventListener('submit', async event => {
    event.preventDefault();
    const registering = authMode === 'register';
    if (registering && elements.authPassword.value !== elements.authConfirmPassword.value) {
        elements.authError.textContent = '两次输入的密码不一致';
        elements.authError.hidden = false;
        return;
    }
    elements.authSubmit.disabled = true;
    elements.authError.hidden = true;
    try {
        state.currentUser = await api(`/api/v1/auth/${registering ? 'register' : 'login'}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: elements.authEmail.value, password: elements.authPassword.value }),
            skipAuthRedirect: true
        });
        elements.authForm.reset();
        showWorkspace();
        await Promise.all([loadServerSettings(), loadNotifications(), loadKnowledgeBases(), loadMemories()]);
        resetConversation();
        renderCurrentUser();
    } catch (error) {
        elements.authError.textContent = error.message;
        elements.authError.hidden = false;
    } finally {
        elements.authSubmit.disabled = false;
    }
});
