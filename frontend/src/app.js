const isStaticPreview = window.location.protocol === 'file:';
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const state = { currentUser: null, knowledgeBases: [], activeId: null, documents: [], conversationId: null,
    conversations: [], memories: [], documentEvents: null, documentEventKnowledgeBaseId: null,
    feishuConnections: [], feishuSources: [], feishuTasks: [], modelCatalog: [], modelProviders: [],
    currentModel: null, adminPage: 'language-models', knowledgeSummary: null, adminDocuments: [],
    documentSets: [], indexSettings: null, userAdmin: null, userPage: 0,
    userFilters: { query: '', role: 'all', status: 'all' },
    agents: [], availableAgents: [], activeAgentId: null,
    agentFilters: { query: '', access: 'all', status: 'all' },
    registrationPolicy: { restrictOpenSignup: false, invitationValid: false, invitationEmail: null },
    thinkMode: false };

const inviteToken = new URLSearchParams(location.search).get('invite') || '';

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
    adminShell: document.querySelector('#adminShell'),
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
    thinkModeButton: document.querySelector('#thinkModeButton'),
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
    feishuTaskList: document.querySelector('#feishuTaskList'),
    providerCatalogGrid: document.querySelector('#providerCatalogGrid'),
    configuredProviderList: document.querySelector('#configuredProviderList'),
    defaultModelSelect: document.querySelector('#defaultModelSelect'),
    modelProviderDialog: document.querySelector('#modelProviderDialog'),
    modelProviderForm: document.querySelector('#modelProviderForm'),
    modelOptionList: document.querySelector('#modelOptionList'),
    availableAgentList: document.querySelector('#availableAgentList'),
    chatAgentSelect: document.querySelector('#chatAgentSelect'),
    agentTableBody: document.querySelector('#agentTableBody'),
    agentEditorForm: document.querySelector('#agentEditorForm'),
    adminConnectorList: document.querySelector('#adminConnectorList'),
    adminSourceList: document.querySelector('#adminSourceList'),
    adminTaskList: document.querySelector('#adminTaskList'),
    documentSetList: document.querySelector('#documentSetList'),
    documentSetDialog: document.querySelector('#documentSetDialog'),
    documentSetForm: document.querySelector('#documentSetForm'),
    documentPickerList: document.querySelector('#documentPickerList'),
    indexSettingsForm: document.querySelector('#indexSettingsForm'),
    userTableBody: document.querySelector('#userTableBody'),
    inviteUsersDialog: document.querySelector('#inviteUsersDialog'),
    inviteUsersForm: document.querySelector('#inviteUsersForm')
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
    document.querySelector('#adminUserName').textContent = userLabel();
    document.querySelector('#adminPanelButton').hidden = state.currentUser.role !== 'owner';
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
    elements.authEmail.readOnly = registering && state.registrationPolicy.invitationValid;
    if (registering && state.registrationPolicy.invitationValid) {
        elements.authEmail.value = state.registrationPolicy.invitationEmail || '';
    }
    elements.authError.hidden = true;
}

function applyRegistrationPolicy() {
    const policy = state.registrationPolicy;
    const switchRow = document.querySelector('.auth-switch');
    switchRow.hidden = policy.restrictOpenSignup && !policy.invitationValid;
    elements.authEmail.readOnly = Boolean(policy.invitationValid);
    if (policy.invitationValid) {
        setAuthMode('register');
        elements.authEmail.value = policy.invitationEmail || '';
        document.querySelector('#authSubtitle').textContent = '完成注册以加入 JadeBase 工作区';
    } else if (policy.restrictOpenSignup) {
        setAuthMode('login');
    }
}

async function loadRegistrationPolicy() {
    if (isStaticPreview) return;
    const suffix = inviteToken ? `?inviteToken=${encodeURIComponent(inviteToken)}` : '';
    state.registrationPolicy = await api(`/api/v1/auth/registration-policy${suffix}`, { skipAuthRedirect: true });
    applyRegistrationPolicy();
}

function showAuthPage(message = '') {
    state.documentEvents?.close();
    state.documentEvents = null;
    state.currentUser = null;
    elements.appShell.hidden = true;
    elements.adminShell.hidden = true;
    elements.authPage.hidden = false;
    elements.authError.textContent = message;
    elements.authError.hidden = !message;
    elements.authPassword.value = '';
    elements.authConfirmPassword.value = '';
    window.setTimeout(() => elements.authEmail.focus());
}

function showWorkspace() {
    elements.authPage.hidden = true;
    elements.adminShell.hidden = true;
    elements.appShell.hidden = false;
    elements.chatPanel.classList.add('welcome-mode');
    renderCurrentUser();
}

async function loadCurrentModel() {
    if (isStaticPreview) return;
    state.currentModel = await api('/api/v1/models/current');
    setModelStatus(state.currentModel.configured ? state.currentModel.modelId : '本地演示');
}

const adminPages = new Set(['language-models', 'agents', 'existing-connectors', 'add-connector', 'document-sets', 'index-settings', 'users']);

function adminPageFromHash() {
    const page = location.hash.startsWith('#admin/') ? location.hash.slice(7) : '';
    return adminPages.has(page) ? page : 'language-models';
}

async function showAdminPage(page, updateLocation = true) {
    const target = adminPages.has(page) ? page : 'language-models';
    state.adminPage = target;
    document.querySelectorAll('[data-admin-section]').forEach(section => { section.hidden = section.dataset.adminSection !== target; });
    document.querySelectorAll('[data-admin-page]').forEach(button => button.classList.toggle('active', button.dataset.adminPage === target));
    document.querySelector('#adminContent').scrollTop = 0;
    elements.adminShell.classList.remove('sidebar-open');
    if (updateLocation && !isStaticPreview) history.replaceState(null, '', `#admin/${target}`);
    if (target === 'language-models') await loadModelAdmin();
    if (target === 'agents') await loadAgentAdmin();
    if (target === 'existing-connectors' || target === 'add-connector') await loadFeishuConnector();
    if (target === 'document-sets') await loadDocumentKnowledge();
    if (target === 'index-settings') await loadIndexAdmin();
    if (target === 'users') await loadUserAdmin();
}

async function openAdmin(updateLocation = true, requestedPage = adminPageFromHash()) {
    closeAccountMenu();
    if (state.currentUser?.role !== 'owner') {
        showToast('只有工作区所有者可以进入管理后台');
        return;
    }
    elements.appShell.hidden = true;
    elements.adminShell.hidden = false;
    elements.adminShell.classList.remove('sidebar-open');
    try { await showAdminPage(requestedPage, updateLocation); }
    catch (error) { showToast(`管理页加载失败：${error.message}`); }
}

function closeAdmin() {
    window.clearTimeout(scheduleFeishuRefresh.timer);
    elements.adminShell.hidden = true;
    elements.adminShell.classList.remove('sidebar-open');
    elements.appShell.hidden = false;
    if (!isStaticPreview) history.replaceState(null, '', `${location.pathname}${location.search}`);
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
            <div class="agent-starter-suggestions" id="agentStarterSuggestions" aria-label="Agent 对话开场白" hidden></div>
        </div>`;
}

function resetConversation() {
    state.conversationId = null;
    elements.messages.innerHTML = emptyStateMarkup();
    elements.emptyState = elements.messages.querySelector('#emptyState');
    elements.chatPanel.classList.add('welcome-mode');
    renderCurrentUser();
    renderAgentStarterSuggestions();
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
    state.activeAgentId = null;
    state.activeId = conversation.knowledgeBaseId;
    renderAvailableAgents();
    renderKnowledgeBases();
    await loadDocuments();
    elements.messages.innerHTML = '';
    elements.emptyState = null;
    conversation.messages.forEach(message => appendMessage(message.role, message.content, message.sources, {
        reasoning: message.reasoning, durationMs: message.thinkingDurationMs, steps: message.thinkingSteps
    }));
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
        renderFeishuConnector();
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
    renderAdminConnectors();
}

function sourceMarkup(source) {
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
}

function taskMarkup(task) {
    const source = state.feishuSources.find(item => item.id === task.sourceId);
    return `<article class="connector-task-item">
        <span class="task-status-dot ${task.status.toLowerCase()}"></span>
        <div><strong>${escapeHtml(source?.remoteName || '已删除来源')}</strong><small>${task.mode === 'FULL' ? '全量同步' : '增量同步'} · ${connectorTaskStatus(task.status)}</small></div>
        <div class="task-counts"><span>扫描 ${task.scannedCount}</span><span>+${task.createdCount} / ~${task.updatedCount} / -${task.deletedCount}</span></div>
        <time>${formatConnectorDate(task.completedAt || task.startedAt || task.createdAt)}</time>
        ${task.status === 'FAILED' ? `<button class="secondary-button" data-feishu-task-retry="${task.id}" type="button">重试</button>` : ''}
    </article>`;
}

function renderAdminConnectors() {
    if (!elements.adminConnectorList) return;
    document.querySelector('#connectorConnectionCount').textContent = state.feishuConnections.length;
    document.querySelector('#connectorSourceCount').textContent = state.feishuSources.length;
    document.querySelector('#connectorRunningCount').textContent = state.feishuTasks.filter(task => ['QUEUED', 'RUNNING'].includes(task.status)).length;
    elements.adminConnectorList.innerHTML = state.feishuConnections.length ? state.feishuConnections.map(connection => `
        <article class="admin-connector-row">
            <span class="connector-brand feishu">飞</span>
            <div><strong>${escapeHtml(connection.name)}</strong><small>${escapeHtml(connection.appId)} · ${connection.sourceCount} 个来源</small><span class="connector-state ${connection.status === 'ERROR' ? 'failed' : ''}">${connection.status === 'CONNECTED' ? '连接正常' : escapeHtml(connection.statusMessage || '待验证')}</span></div>
            <div class="admin-row-actions"><button class="secondary-button" data-feishu-connection-test="${connection.id}" type="button">测试</button><button class="icon-button" data-feishu-connection-edit="${connection.id}" type="button" title="配置" aria-label="配置">${iconMarkup('settings')}</button><button class="icon-button connector-delete-action" data-feishu-connection-delete="${connection.id}" type="button" title="删除" aria-label="删除">${iconMarkup('close')}</button></div>
        </article>`).join('') : '<p class="admin-empty-state">尚未配置任何连接器</p>';
    elements.adminSourceList.innerHTML = state.feishuSources.length
        ? state.feishuSources.map(sourceMarkup).join('') : '<p class="admin-empty-state">连接企业知识源后，可在这里添加同步目录</p>';
    elements.adminTaskList.innerHTML = state.feishuTasks.length
        ? state.feishuTasks.slice(0, 12).map(taskMarkup).join('') : '<p class="admin-empty-state">暂无同步任务</p>';
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
    const settingsVisible = document.querySelector('[data-settings-section="connectors"].active');
    const adminVisible = !elements.adminShell.hidden && state.adminPage === 'existing-connectors';
    if (!active || (!settingsVisible && !adminVisible)) return;
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

function openFeishuConnectionDialog(connectionId) {
    const connection = connectionId === null ? null
        : connectionId ? state.feishuConnections.find(item => item.id === connectionId) : state.feishuConnections[0];
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

async function loadDocumentKnowledge() {
    if (isStaticPreview) {
        state.knowledgeSummary = { knowledgeBaseCount: 2, documentCount: 3, documentSetCount: 1, readyCount: 3,
            indexingCount: 0, failedCount: 0, chunkCount: 42, sizeBytes: 186400 };
        state.adminDocuments = [
            { id: 'preview-doc-1', knowledgeBaseName: '产品知识库', name: '产品使用手册.md', status: 'READY', sourceType: 'UPLOAD', chunkCount: 18, sizeBytes: 72800 },
            { id: 'preview-doc-2', knowledgeBaseName: '研发知识库', name: '服务架构设计.md', status: 'READY', sourceType: 'FEISHU', chunkCount: 24, sizeBytes: 113600 }
        ];
        state.documentSets = [{ id: 'preview-set', name: '新人必读', description: '产品与研发的核心资料', documentCount: 2, readyCount: 2, chunkCount: 42, documents: state.adminDocuments }];
        renderDocumentSets();
        return;
    }
    const [summary, documents, sets] = await Promise.all([
        api('/api/v1/admin/knowledge/summary'), api('/api/v1/admin/knowledge/documents'),
        api('/api/v1/admin/knowledge/document-sets')
    ]);
    state.knowledgeSummary = summary;
    state.adminDocuments = documents;
    state.documentSets = sets;
    renderDocumentSets();
}

function renderDocumentSets() {
    const summary = state.knowledgeSummary || {};
    document.querySelector('#documentSetCount').textContent = summary.documentSetCount || 0;
    document.querySelector('#inventoryDocumentCount').textContent = summary.documentCount || 0;
    document.querySelector('#inventoryReadyCount').textContent = summary.readyCount || 0;
    document.querySelector('#inventoryChunkCount').textContent = summary.chunkCount || 0;
    elements.documentSetList.innerHTML = state.documentSets.length ? state.documentSets.map(set => `
        <article class="document-set-row">
            <span class="document-set-icon">${iconMarkup('files')}</span>
            <div class="document-set-copy"><div><strong>${escapeHtml(set.name)}</strong><span>${set.readyCount}/${set.documentCount} 已就绪</span></div><p>${escapeHtml(set.description || '暂无描述')}</p><small>${set.chunkCount} 个索引片段 · ${escapeHtml(set.documents.map(item => item.knowledgeBaseName).filter((name, index, values) => values.indexOf(name) === index).join(' / ') || '尚未选择文档')}</small></div>
            <button class="icon-button" data-document-set-edit="${set.id}" type="button" title="编辑文档集" aria-label="编辑文档集">${iconMarkup('settings')}</button>
        </article>`).join('') : '<p class="admin-empty-state">还没有文档集。创建后可以跨知识库组织文档。</p>';
}

let selectedDocumentIds = new Set();

function renderDocumentPicker() {
    const query = document.querySelector('#documentPickerSearch').value.trim().toLowerCase();
    const visible = state.adminDocuments.filter(item => `${item.name} ${item.knowledgeBaseName}`.toLowerCase().includes(query));
    document.querySelector('#documentSetSelectionCount').textContent = `已选择 ${selectedDocumentIds.size} 个文档`;
    if (!visible.length) {
        elements.documentPickerList.innerHTML = '<p class="admin-empty-state">没有找到匹配文档</p>';
        return;
    }
    const groups = visible.reduce((result, item) => {
        if (!result.has(item.knowledgeBaseName)) result.set(item.knowledgeBaseName, []);
        result.get(item.knowledgeBaseName).push(item);
        return result;
    }, new Map());
    elements.documentPickerList.innerHTML = [...groups.entries()].map(([knowledgeBase, documents]) => `
        <section class="document-picker-group"><h4>${escapeHtml(knowledgeBase)}<span>${documents.length}</span></h4>${documents.map(item => `
            <label class="document-picker-item"><input type="checkbox" value="${item.id}" ${selectedDocumentIds.has(item.id) ? 'checked' : ''}><span><strong>${escapeHtml(item.name)}</strong><small>${item.sourceType === 'FEISHU' ? '飞书同步' : '本地上传'} · ${item.chunkCount} 个片段 · ${formatBytes(item.sizeBytes)}</small></span><em class="document-status ${item.status === 'FAILED' ? 'failed' : ''}">${statusText(item.status, item.progress)}</em></label>`).join('')}</section>`).join('');
}

function openDocumentSetDialog(setId = '') {
    const set = state.documentSets.find(item => item.id === setId);
    elements.documentSetForm.reset();
    document.querySelector('#documentSetId').value = set?.id || '';
    document.querySelector('#documentSetDialogTitle').textContent = set ? '编辑文档集' : '新建文档集';
    document.querySelector('#documentSetNameInput').value = set?.name || '';
    document.querySelector('#documentSetDescriptionInput').value = set?.description || '';
    document.querySelector('#deleteDocumentSetButton').hidden = !set;
    selectedDocumentIds = new Set(set?.documents.map(item => item.id) || []);
    renderDocumentPicker();
    elements.documentSetDialog.showModal();
    document.querySelector('#documentSetNameInput').focus();
}

async function loadIndexAdmin() {
    if (isStaticPreview) {
        state.knowledgeSummary ||= { knowledgeBaseCount: 2, documentCount: 3, failedCount: 0, sizeBytes: 186400 };
        state.indexSettings = { chunkSize: 700, chunkOverlap: 100, topK: 6, candidateK: 40, rrfK: 60,
            rerankEnabled: true, queryRewriteEnabled: true, reindexRequired: false };
        renderIndexAdmin();
        return;
    }
    const [summary, settings] = await Promise.all([
        api('/api/v1/admin/knowledge/summary'), api('/api/v1/admin/knowledge/index-settings')
    ]);
    state.knowledgeSummary = summary;
    state.indexSettings = settings;
    renderIndexAdmin();
}

function renderIndexAdmin() {
    const summary = state.knowledgeSummary || {};
    const settings = state.indexSettings || {};
    document.querySelector('#indexKnowledgeBaseCount').textContent = summary.knowledgeBaseCount || 0;
    document.querySelector('#indexDocumentCount').textContent = summary.documentCount || 0;
    document.querySelector('#indexFailedCount').textContent = summary.failedCount || 0;
    document.querySelector('#indexSizeTotal').textContent = formatBytes(summary.sizeBytes || 0);
    document.querySelector('#chunkSizeInput').value = settings.chunkSize || 700;
    document.querySelector('#chunkOverlapInput').value = settings.chunkOverlap ?? 100;
    document.querySelector('#indexTopKInput').value = settings.topK || 6;
    document.querySelector('#candidateKInput').value = settings.candidateK || 40;
    document.querySelector('#rrfKInput').value = settings.rrfK || 60;
    document.querySelector('#rerankEnabledInput').checked = Boolean(settings.rerankEnabled);
    document.querySelector('#queryRewriteEnabledInput').checked = Boolean(settings.queryRewriteEnabled);
    document.querySelector('#reindexRequiredBadge').hidden = !settings.reindexRequired;
}

function previewAgents() {
    return [{ id: 'preview-agent', name: '研发知识助手', description: '基于研发知识库回答架构与工程规范问题',
        systemPrompt: '你是研发知识助手。回答应简洁、准确，并标注知识库证据。',
        conversationStarters: ['梳理这个系统的核心架构', '总结研发规范中的关键要求'], useKnowledge: true,
        knowledgeBaseId: 'preview-engineering', knowledgeBaseName: '研发知识库', modelProviderId: null,
        modelId: null, modelName: '工作区默认模型', accessLevel: 'everyone', status: 'published', enabled: true,
        thinkMode: true, maxIterations: 4, featured: true, labels: ['研发', '架构'],
        enabledActions: ['OPEN_URL', 'CODING_AGENT'], knowledgeCutoffDate: null,
        currentVersion: 1, hasUnpublishedChanges: false,
        createdBy: 'preview-owner', createdByName: 'JadeBase 所有者', createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(), publishedAt: new Date().toISOString() }];
}

async function loadAgentAdmin() {
    if (isStaticPreview) {
        state.agents = state.agents.length ? state.agents : previewAgents();
        if (!state.modelProviders.length) state.modelProviders = [];
        renderAgentAdmin();
        return;
    }
    const [agents, providers, knowledgeBases] = await Promise.all([
        api('/api/v1/admin/agents'), api('/api/v1/admin/model-providers'), api('/api/v1/knowledge-bases')
    ]);
    state.agents = agents;
    state.modelProviders = providers;
    state.knowledgeBases = knowledgeBases;
    renderAgentAdmin();
}

function agentDisplayStatus(agent) {
    if (!agent.enabled) return { value: 'disabled', label: '已停用' };
    if (!agent.currentVersion) return { value: 'draft', label: '草稿' };
    if (agent.hasUnpublishedChanges) return { value: 'draft', label: `有未发布修改 · v${agent.currentVersion}` };
    return { value: 'published', label: `已发布 · v${agent.currentVersion}` };
}

function filteredAgents() {
    const query = state.agentFilters.query.toLowerCase();
    return state.agents.filter(agent => {
        const status = agentDisplayStatus(agent).value;
        return (!query || `${agent.name} ${agent.description || ''} ${agent.createdByName}`.toLowerCase().includes(query))
            && (state.agentFilters.access === 'all' || agent.accessLevel === state.agentFilters.access)
            && (state.agentFilters.status === 'all' || status === state.agentFilters.status);
    });
}

function renderAgentAdmin() {
    const visible = filteredAgents();
    const empty = document.querySelector('#agentEmptyState');
    empty.hidden = Boolean(visible.length);
    document.querySelector('#agentPaginationSummary').textContent = visible.length
        ? `显示 1-${visible.length}，共 ${visible.length} 个 Agents` : '0 个 Agents';
    elements.agentTableBody.innerHTML = visible.map(agent => {
        const status = agentDisplayStatus(agent);
        return `<tr data-agent-edit="${agent.id}">
            <td><div class="agent-name-cell"><span class="agent-row-icon">${iconMarkup('bot')}</span><span><strong>${escapeHtml(agent.name)}</strong><small>${escapeHtml(agent.knowledgeBaseName)}</small></span></div></td>
            <td class="agent-description-cell" title="${escapeHtml(agent.description || '')}">${escapeHtml(agent.description || '暂无描述')}</td>
            <td class="agent-created-cell">${escapeHtml(agent.createdByName)}</td>
            <td><span class="agent-access-badge">${agent.accessLevel === 'private' ? '仅创建者' : '所有人'}</span></td>
            <td><span class="agent-status-badge ${status.value}">${escapeHtml(status.label)}</span></td>
            <td><div class="agent-row-actions"><button class="icon-button" data-agent-enabled="${agent.id}" data-next-enabled="${!agent.enabled}" type="button" title="${agent.enabled ? '停用 Agent' : '启用 Agent'}" aria-label="${agent.enabled ? '停用' : '启用'} ${escapeHtml(agent.name)}">${iconMarkup(agent.enabled ? 'eye' : 'sync')}</button><button class="icon-button" data-agent-open="${agent.id}" type="button" title="编辑 Agent" aria-label="编辑 ${escapeHtml(agent.name)}">${iconMarkup('settings')}</button></div></td>
        </tr>`;
    }).join('');
}

function agentModelOptions(selectedValue = '') {
    const options = [{ value: '', label: '工作区默认模型' }];
    state.modelProviders.forEach(provider => provider.models.filter(model => model.enabled).forEach(model => {
        options.push({ value: `${provider.id}|${encodeURIComponent(model.modelId)}`,
            label: `${model.displayName} · ${provider.displayName}` });
    }));
    return options.map(option => `<option value="${option.value}" ${option.value === selectedValue ? 'selected' : ''}>${escapeHtml(option.label)}</option>`).join('');
}

function showAgentBuilder() {
    state.adminPage = 'agents';
    document.querySelectorAll('[data-admin-section]').forEach(section => {
        section.hidden = section.dataset.adminSection !== 'agent-editor';
    });
    document.querySelectorAll('[data-admin-page]').forEach(button => {
        button.classList.toggle('active', button.dataset.adminPage === 'agents');
    });
    document.querySelector('#adminContent').scrollTop = 0;
    elements.adminShell.classList.remove('sidebar-open');
    if (!isStaticPreview) history.replaceState(null, '', '#admin/agents');
}

function closeAgentBuilder() {
    return showAdminPage('agents');
}

function renderAgentStarterEditor(starters = ['']) {
    const values = starters.length ? starters.slice(0, 6) : [''];
    document.querySelector('#agentStarterEditor').innerHTML = values.map((value, index) => `
        <div class="agent-starter-row">
            <input data-agent-starter maxlength="240" value="${escapeHtml(value)}" placeholder="例如：帮我总结最近更新的产品文档">
            <button class="icon-button" data-remove-agent-starter="${index}" type="button" title="移除开场白" aria-label="移除开场白">${iconMarkup('close')}</button>
        </div>`).join('');
}

function agentStarterValues() {
    return [...document.querySelectorAll('[data-agent-starter]')]
        .map(input => input.value.trim()).filter((value, index, values) => value && values.indexOf(value) === index);
}

function syncAgentKnowledgeControl() {
    const enabled = document.querySelector('#agentUseKnowledgeInput').checked;
    const picker = document.querySelector('#agentKnowledgePicker');
    picker.classList.toggle('disabled', !enabled);
    picker.querySelector('select').disabled = !enabled;
}

function syncAgentShareControls() {
    const featured = document.querySelector('#agentFeaturedInput');
    const canFeature = document.querySelector('#agentAccessSelect').value === 'EVERYONE';
    featured.disabled = !canFeature;
    if (!canFeature) featured.checked = false;
    featured.closest('.agent-setting-row').classList.toggle('disabled', !canFeature);
}

function syncAgentSubmitState() {
    document.querySelector('#publishAgentButton').disabled = !document.querySelector('#agentNameInput').value.trim();
}

async function openAgentEditor(agentId = '') {
    const agent = state.agents.find(item => item.id === agentId);
    elements.agentEditorForm.reset();
    showAgentBuilder();
    document.querySelector('#agentIdInput').value = agent?.id || '';
    document.querySelector('#agentEditorTitle').textContent = agent ? agent.name : '创建 Agent';
    document.querySelector('#agentEditorSubtitle').textContent = agent
        ? `当前${agent.currentVersion ? `发布版本 v${agent.currentVersion}` : '尚未发布'}${agent.hasUnpublishedChanges ? '，包含未发布修改' : ''}`
        : '配置一个可复用的智能助手。';
    document.querySelector('#agentNameInput').value = agent?.name || '';
    document.querySelector('#agentDescriptionInput').value = agent?.description || '';
    document.querySelector('#agentSystemPromptInput').value = agent?.systemPrompt || '';
    renderAgentStarterEditor(agent?.conversationStarters || ['']);
    document.querySelector('#agentUseKnowledgeInput').checked = agent?.useKnowledge ?? false;
    document.querySelector('#agentKnowledgeBaseSelect').innerHTML = state.knowledgeBases.length
        ? state.knowledgeBases.map(item => `<option value="${item.id}" ${item.id === agent?.knowledgeBaseId ? 'selected' : ''}>${escapeHtml(item.name)}</option>`).join('')
        : '<option value="">请先创建知识库</option>';
    const modelValue = agent?.modelProviderId && agent?.modelId
        ? `${agent.modelProviderId}|${encodeURIComponent(agent.modelId)}` : '';
    document.querySelector('#agentModelSelect').innerHTML = agentModelOptions(modelValue);
    document.querySelector('#agentAccessSelect').value = agent?.accessLevel === 'everyone' ? 'EVERYONE' : 'PRIVATE';
    document.querySelector('#agentFeaturedInput').checked = Boolean(agent?.featured);
    document.querySelector('#agentLabelsInput').value = (agent?.labels || []).join('，');
    document.querySelectorAll('[data-agent-action]').forEach(input => {
        input.checked = (agent?.enabledActions || []).includes(input.dataset.agentAction);
    });
    document.querySelector('#agentKnowledgeCutoffInput').value = agent?.knowledgeCutoffDate || '';
    document.querySelector('#agentMaxIterationsInput').value = agent?.maxIterations || 4;
    document.querySelector('#agentThinkModeInput').checked = Boolean(agent?.thinkMode);
    document.querySelector('#agentHistorySection').hidden = !agent;
    document.querySelector('#agentDangerZone').hidden = !agent;
    document.querySelector('#saveAgentDraftButton').hidden = !agent;
    document.querySelector('#publishAgentButton').textContent = agent ? '发布更新' : '创建';
    syncAgentKnowledgeControl();
    syncAgentShareControls();
    syncAgentSubmitState();
    document.querySelector('#agentNameInput').focus();
    if (agent) await loadAgentHistory(agent.id);
}

async function loadAgentHistory(agentId) {
    if (isStaticPreview) {
        renderAgentVersions([{ version: 1, name: '研发知识助手', modelName: '工作区默认模型', accessLevel: 'everyone', publishedBy: 'JadeBase 所有者', publishedAt: new Date().toISOString() }]);
        renderAgentRuns([]);
        return;
    }
    const [versions, runs] = await Promise.all([
        api(`/api/v1/admin/agents/${agentId}/versions`), api(`/api/v1/admin/agents/${agentId}/runs`)
    ]);
    renderAgentVersions(versions);
    renderAgentRuns(runs);
}

function renderAgentVersions(versions) {
    document.querySelector('#agentVersionList').innerHTML = versions.length ? versions.map(version => `
        <article class="agent-history-row"><span><strong>版本 v${version.version} · ${escapeHtml(version.name)}</strong><small>${escapeHtml(version.modelName)} · ${version.accessLevel === 'private' ? '仅创建者' : '工作区所有人'}</small></span><em>${escapeHtml(version.publishedBy)}<br>${formatUserTime(version.publishedAt)}</em></article>`).join('')
        : '<p class="admin-empty-state">尚未发布版本</p>';
}

function renderAgentRuns(runs) {
    document.querySelector('#agentRunList').innerHTML = runs.length ? runs.map(run => `
        <article class="agent-history-row"><span><strong>${run.status === 'completed' ? '运行成功' : run.status === 'failed' ? '运行失败' : '运行中'} · v${run.version}</strong><small>${escapeHtml(run.question)}${run.errorMessage ? ` · ${escapeHtml(run.errorMessage)}` : ''}</small></span><em>${escapeHtml(run.user)}<br>${run.durationMs} ms</em></article>`).join('')
        : '<p class="admin-empty-state">尚无运行记录</p>';
}

function agentPayload() {
    const model = document.querySelector('#agentModelSelect').value;
    const [modelProviderId, encodedModelId] = model ? model.split('|') : [null, null];
    const useKnowledge = document.querySelector('#agentUseKnowledgeInput').checked;
    return {
        name: document.querySelector('#agentNameInput').value.trim(),
        description: document.querySelector('#agentDescriptionInput').value.trim(),
        systemPrompt: document.querySelector('#agentSystemPromptInput').value.trim(),
        conversationStarters: agentStarterValues(),
        useKnowledge,
        knowledgeBaseId: useKnowledge ? document.querySelector('#agentKnowledgeBaseSelect').value || null : null,
        modelProviderId, modelId: encodedModelId ? decodeURIComponent(encodedModelId) : null,
        accessLevel: document.querySelector('#agentAccessSelect').value,
        featured: document.querySelector('#agentFeaturedInput').checked,
        labels: document.querySelector('#agentLabelsInput').value.split(/[,，]/).map(value => value.trim()).filter(Boolean),
        enabledActions: [...document.querySelectorAll('[data-agent-action]:checked')].map(input => input.dataset.agentAction),
        knowledgeCutoffDate: document.querySelector('#agentKnowledgeCutoffInput').value || null,
        thinkMode: document.querySelector('#agentThinkModeInput').checked,
        maxIterations: Number(document.querySelector('#agentMaxIterationsInput').value)
    };
}

async function saveAgentDefinition() {
    const agentId = document.querySelector('#agentIdInput').value;
    const payload = agentPayload();
    if (!payload.name) throw new Error('请填写 Agent 名称');
    if (payload.useKnowledge && !payload.knowledgeBaseId) throw new Error('开启知识库后需要选择一个知识库');
    if (payload.conversationStarters.length > 6) throw new Error('对话开场白最多添加 6 条');
    if (payload.labels.length > 8) throw new Error('标签最多添加 8 个');
    let saved;
    if (isStaticPreview) {
        const existing = state.agents.find(item => item.id === agentId);
        saved = { ...(existing || {}), ...payload, id: existing?.id || `preview-agent-${Date.now()}`,
            knowledgeBaseName: payload.useKnowledge
                ? state.knowledgeBases.find(item => item.id === payload.knowledgeBaseId)?.name || '知识库'
                : '未使用知识库',
            modelName: payload.modelId || '工作区默认模型', accessLevel: payload.accessLevel.toLowerCase(),
            status: existing?.status || 'draft', enabled: existing?.enabled ?? true,
            currentVersion: existing?.currentVersion || 0, hasUnpublishedChanges: Boolean(existing?.currentVersion),
            createdBy: existing?.createdBy || state.currentUser.id, createdByName: existing?.createdByName || state.currentUser.email,
            createdAt: existing?.createdAt || new Date().toISOString(), updatedAt: new Date().toISOString() };
        state.agents = existing ? state.agents.map(item => item.id === agentId ? saved : item) : [saved, ...state.agents];
    } else {
        saved = await api(`/api/v1/admin/agents${agentId ? `/${agentId}` : ''}`, {
            method: agentId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
        });
        state.agents = agentId ? state.agents.map(item => item.id === agentId ? saved : item) : [saved, ...state.agents];
    }
    document.querySelector('#agentIdInput').value = saved.id;
    renderAgentAdmin();
    return saved;
}

async function loadAvailableAgents() {
    state.availableAgents = isStaticPreview
        ? state.agents.filter(agent => agent.enabled && agent.currentVersion).map(agent => ({ ...agent, version: agent.currentVersion }))
        : await api('/api/v1/agents');
    renderAvailableAgents();
}

function renderAvailableAgents() {
    const selected = state.activeAgentId || '';
    elements.chatAgentSelect.innerHTML = '<option value="">默认知识助手</option>' + state.availableAgents.map(agent =>
        `<option value="${agent.id}" ${agent.id === selected ? 'selected' : ''}>${escapeHtml(agent.name)}</option>`).join('');
    elements.availableAgentList.innerHTML = state.availableAgents.map(agent => `
        <button class="knowledge-item agent-sidebar-item ${agent.id === selected ? 'active' : ''}" data-sidebar-agent="${agent.id}" type="button">${iconMarkup('bot')}<span>${escapeHtml(agent.name)}</span></button>`).join('');
    renderAgentStarterSuggestions();
}

function renderAgentStarterSuggestions() {
    const container = document.querySelector('#agentStarterSuggestions');
    if (!container) return;
    const agent = state.availableAgents.find(item => item.id === state.activeAgentId);
    const starters = agent?.conversationStarters || [];
    container.hidden = !starters.length;
    container.innerHTML = starters.map(starter => `
        <button type="button" data-agent-starter-question="${escapeHtml(starter)}">${escapeHtml(starter)}${iconMarkup('chevron-right')}</button>`).join('');
}

async function selectAgent(agentId) {
    state.activeAgentId = agentId || null;
    const agent = state.availableAgents.find(item => item.id === agentId);
    if (agent) {
        state.activeId = agent.knowledgeBaseId || state.activeId || state.knowledgeBases[0]?.id || null;
        state.thinkMode = Boolean(agent.thinkMode);
        elements.thinkModeButton.classList.toggle('active', state.thinkMode);
        elements.thinkModeButton.setAttribute('aria-pressed', String(state.thinkMode));
    } else {
        state.thinkMode = false;
        elements.thinkModeButton.classList.remove('active');
        elements.thinkModeButton.setAttribute('aria-pressed', 'false');
    }
    renderAvailableAgents();
    renderKnowledgeBases();
    if (state.activeId) await loadDocuments();
    resetConversation();
    showToast(agent ? `已切换到 ${agent.name}` : '已切换到默认知识助手');
}

async function loadUserAdmin() {
    if (isStaticPreview) {
        state.userAdmin = {
            users: [
                { id: 'preview-owner', email: 'owner@jadebase.local', displayName: 'JadeBase 所有者', role: 'owner', status: 'active', updatedAt: new Date().toISOString(), lastLoginAt: new Date().toISOString() },
                { id: 'preview-member', email: 'member@jadebase.local', displayName: '产品成员', role: 'member', status: 'active', updatedAt: new Date(Date.now() - 86400000).toISOString(), lastLoginAt: null }
            ], invitations: [{ id: 'preview-invite', email: 'new.member@jadebase.local', role: 'member', invitedBy: 'JadeBase 所有者', expiresAt: new Date(Date.now() + 6 * 86400000).toISOString(), createdAt: new Date().toISOString() }],
            totalElements: 2, totalPages: 1, page: 0, size: 20, activeUsers: 2, pendingInvites: 1, restrictOpenSignup: true
        };
        renderUserAdmin();
        return;
    }
    const params = new URLSearchParams({ ...state.userFilters, page: String(state.userPage), size: '20' });
    state.userAdmin = await api(`/api/v1/admin/users?${params}`);
    renderUserAdmin();
}

function formatUserTime(value) {
    if (!value) return '从未登录';
    return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' }).format(new Date(value));
}

function roleLabel(role) {
    return role === 'owner' ? '所有者' : '成员';
}

function renderUserAdmin() {
    const result = state.userAdmin || { users: [], invitations: [] };
    document.querySelector('#activeUserCount').textContent = result.activeUsers || 0;
    document.querySelector('#pendingInviteCount').textContent = result.pendingInvites || 0;
    document.querySelector('#restrictSignupToggle').checked = Boolean(result.restrictOpenSignup);
    document.querySelector('#userPaginationSummary').textContent = result.totalElements
        ? `显示 ${result.page * result.size + 1}-${Math.min((result.page + 1) * result.size, result.totalElements)}，共 ${result.totalElements} 个用户`
        : '0 个用户';
    document.querySelector('#userPageNumber').textContent = String((result.page || 0) + 1);
    document.querySelector('#previousUserPageButton').disabled = !result.page;
    document.querySelector('#nextUserPageButton').disabled = (result.page || 0) + 1 >= (result.totalPages || 1);
    elements.userTableBody.innerHTML = result.users.length ? result.users.map(user => {
        const label = user.displayName || user.email;
        const secondary = user.displayName ? user.email : (user.lastLoginAt ? `最近登录 ${formatUserTime(user.lastLoginAt)}` : '尚未登录');
        const currentUser = user.id === state.currentUser?.id;
        const nextStatus = user.status === 'active' ? 'suspended' : 'active';
        return `<tr data-user-id="${user.id}">
            <td><div class="user-identity-cell"><span class="user-avatar">${iconMarkup('user')}</span><span class="user-identity-copy"><strong>${escapeHtml(label)}</strong><small>${escapeHtml(secondary)}</small></span></div></td>
            <td><select class="user-role-select" data-user-role="${user.id}" aria-label="设置 ${escapeHtml(label)} 的账号类型" ${currentUser ? 'disabled' : ''}><option value="owner" ${user.role === 'owner' ? 'selected' : ''}>所有者</option><option value="member" ${user.role === 'member' ? 'selected' : ''}>成员</option></select></td>
            <td><span class="user-status-badge ${user.status === 'suspended' ? 'suspended' : ''}">${user.status === 'active' ? '有效' : '已停用'}</span></td>
            <td class="user-updated-cell">${formatUserTime(user.updatedAt)}</td>
            <td><button class="icon-button user-row-action ${user.status === 'active' ? 'danger' : ''}" data-user-status="${user.id}" data-next-status="${nextStatus}" type="button" ${currentUser ? 'disabled' : ''} title="${user.status === 'active' ? '停用用户' : '恢复用户'}" aria-label="${user.status === 'active' ? '停用' : '恢复'} ${escapeHtml(label)}">${iconMarkup('more')}</button></td>
        </tr>`;
    }).join('') : '<tr><td colspan="5" class="admin-empty-state">没有找到匹配用户</td></tr>';

    const invitationSection = document.querySelector('#pendingInvitationsSection');
    invitationSection.hidden = !result.invitations.length;
    document.querySelector('#pendingInvitationList').innerHTML = result.invitations.map(invitation => `
        <article class="pending-invitation-row">
            <span><strong>${escapeHtml(invitation.email)}</strong><small>由 ${escapeHtml(invitation.invitedBy)} 邀请</small></span>
            <em>${roleLabel(invitation.role)}</em>
            <em>${formatUserTime(invitation.expiresAt)} 到期</em>
            <button class="secondary-button" data-invitation-revoke="${invitation.id}" type="button">撤销</button>
        </article>`).join('');
}

function openInviteUsersDialog() {
    elements.inviteUsersForm.reset();
    document.querySelector('#inviteLinkResult').hidden = true;
    document.querySelector('#createInvitationButton').textContent = '创建邀请';
    elements.inviteUsersDialog.showModal();
    document.querySelector('#inviteEmailInput').focus();
}

function invitationUrl(token) {
    if (isStaticPreview) return `https://jadebase.local/?invite=${encodeURIComponent(token)}`;
    const url = new URL(location.href);
    url.hash = '';
    url.search = '';
    url.searchParams.set('invite', token);
    return url.toString();
}

function exportCurrentUsers() {
    const users = state.userAdmin?.users || [];
    if (!users.length) return showToast('当前没有可导出的用户');
    const quote = value => `"${String(value ?? '').replaceAll('"', '""')}"`;
    const rows = [['名称', '邮箱', '账号类型', '状态', '最近登录'], ...users.map(user => [
        user.displayName, user.email, roleLabel(user.role), user.status === 'active' ? '有效' : '已停用',
        user.lastLoginAt ? formatUserTime(user.lastLoginAt) : '从未登录'
    ])];
    const blob = new Blob([`\ufeff${rows.map(row => row.map(quote).join(',')).join('\n')}`], { type: 'text/csv;charset=utf-8' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `jadebase-users-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
}

function formatThinkingDuration(durationMs) {
    if (!durationMs) return '不到 1 秒';
    if (durationMs < 1000) return '不到 1 秒';
    return `${Math.max(1, Math.round(durationMs / 1000))} 秒`;
}

function thinkingMarkup(thinking = {}) {
    if (!thinking.pending && !thinking.reasoning) return '';
    const status = thinking.pending ? escapeHtml(thinking.status || '正在分析问题并检索知识库…')
        : escapeHtml(thinking.reasoning);
    const title = thinking.pending ? '正在思考' : `思考了 ${formatThinkingDuration(thinking.durationMs)}`;
    const steps = Math.max(1, thinking.steps || 1);
    return `
        <section class="thinking-trace ${thinking.pending ? 'pending' : ''}" data-thinking-trace>
            <button class="thinking-header" type="button" data-thinking-toggle aria-expanded="true">
                <span>${escapeHtml(title)}</span>
                <span class="thinking-step-count">${thinking.pending ? '处理中' : `${steps} 步`}</span>
                ${iconMarkup('chevron-down')}
            </button>
            <div class="thinking-content" data-thinking-content>
                <p>${status}</p>
                <span class="thinking-done">${thinking.pending ? iconMarkup('hourglass') + ' 推理中' : iconMarkup('check') + ' 完成'}</span>
            </div>
        </section>`;
}

function renderMessageArticle(article, role, text, sources = [], thinking = {}) {
    article.className = `message ${role}`;
    article.dataset.messageText = text || '';
    if (role === 'assistant' && appSettings.smoothStreaming) article.classList.add('smooth-enter');
    elements.emptyState?.remove();
    elements.chatPanel.classList.remove('welcome-mode');
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
    if (role === 'user') {
        article.innerHTML = `<div class="message-body">${escapeHtml(text)}</div>`;
        return;
    }
    const actions = thinking.pending ? '' : `
        <div class="message-actions" aria-label="回答操作">
            <button type="button" data-message-action="copy" title="复制回答" aria-label="复制回答">${iconMarkup('copy')}</button>
            <button type="button" data-message-action="like" title="有帮助" aria-label="有帮助">${iconMarkup('thumbs-up')}</button>
            <button type="button" data-message-action="dislike" title="需要改进" aria-label="需要改进">${iconMarkup('thumbs-down')}</button>
            <button type="button" data-message-action="regenerate" title="重新生成" aria-label="重新生成">${iconMarkup('sync')}</button>
        </div>`;
    article.innerHTML = `
        <span class="assistant-avatar jade-logo small" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <div class="assistant-message-content">
            ${thinkingMarkup(thinking)}
            ${text ? `<div class="message-body">${escapeHtml(text)}</div>` : ''}
            ${sourceMarkup}
            ${actions}
        </div>`;
}

function appendMessage(role, text, sources = [], thinking = {}) {
    const article = document.createElement('article');
    renderMessageArticle(article, role, text, sources, thinking);
    elements.messages.appendChild(article);
    if (appSettings.autoScroll) elements.messages.scrollTop = elements.messages.scrollHeight;
    return article;
}

async function streamChat(payload, onEvent) {
    const response = await fetch(`${apiBaseUrl}/api/v1/chat/stream`, {
        method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!response.ok) {
        let message = `请求失败 (${response.status})`;
        try { message = (await response.json()).message || message; } catch (_) { /* no-op */ }
        throw new Error(message);
    }
    if (!response.body) throw new Error('浏览器不支持流式回答');
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
        const { value, done } = await reader.read();
        buffer += decoder.decode(value || new Uint8Array(), { stream: !done }).replaceAll('\r\n', '\n');
        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
            const block = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            let eventName = 'message';
            const data = [];
            block.split('\n').forEach(line => {
                if (line.startsWith('event:')) eventName = line.slice(6).trim();
                if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
            });
            if (data.length) onEvent(eventName, JSON.parse(data.join('\n')));
        }
        if (done) break;
    }
}

let modelDialogModels = [];

function modelPreset(type) {
    return state.modelCatalog.find(item => item.type === type) || {
        type, name: type, description: '', defaultBaseUrl: '', mark: 'AI', apiKeyRequired: true,
        local: false, suggestedModels: []
    };
}

async function loadModelAdmin() {
    if (isStaticPreview) {
        state.modelCatalog = previewModelCatalog();
        state.modelProviders = [];
        state.currentModel = { modelId: '本地演示', providerName: '未配置', configured: false, source: 'fallback' };
    } else {
        [state.modelCatalog, state.modelProviders, state.currentModel] = await Promise.all([
            api('/api/v1/admin/model-providers/catalog'),
            api('/api/v1/admin/model-providers'),
            api('/api/v1/models/current')
        ]);
    }
    renderModelAdmin();
}

function previewModelCatalog() {
    return [
        ['DEEPSEEK', 'DeepSeek', '深度求索', 'https://api.deepseek.com', 'DS'],
        ['DASHSCOPE', '阿里云百炼', '通义千问 Qwen', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'QW'],
        ['ZHIPU', '智谱 AI', 'GLM', 'https://open.bigmodel.cn/api/paas/v4', 'GLM'],
        ['MOONSHOT', 'Kimi', '月之暗面', 'https://api.moonshot.cn/v1', 'K'],
        ['VOLCENGINE', '火山方舟', '豆包与第三方模型', 'https://ark.cn-beijing.volces.com/api/v3', 'DB'],
        ['QIANFAN', '百度千帆', '文心与第三方模型', 'https://qianfan.baidubce.com/v2', 'QF'],
        ['SILICONFLOW', '硅基流动', '国产模型聚合平台', 'https://api.siliconflow.cn/v1', 'SF'],
        ['OPENAI', 'OpenAI', 'GPT 系列', 'https://api.openai.com/v1', 'AI'],
        ['OPENAI_COMPATIBLE', 'OpenAI 通用接口', '任意兼容服务', 'https://your-provider.example/v1', '<>'],
        ['OLLAMA', 'Ollama', '本地模型', 'http://host.docker.internal:11434/v1', 'OL'],
        ['VLLM', 'vLLM', 'OpenAI 兼容推理服务', 'http://host.docker.internal:8000/v1', 'VL'],
        ['LM_STUDIO', 'LM Studio', '桌面本地模型', 'http://host.docker.internal:1234/v1', 'LM'],
        ['LOCALAI', 'LocalAI', '自托管兼容服务', 'http://host.docker.internal:8080/v1', 'LA']
    ].map(([type, name, description, defaultBaseUrl, mark]) => ({ type, name, description, defaultBaseUrl, mark,
        apiKeyRequired: !['OPENAI_COMPATIBLE', 'OLLAMA', 'VLLM', 'LM_STUDIO', 'LOCALAI'].includes(type),
        local: ['OLLAMA', 'VLLM', 'LM_STUDIO', 'LOCALAI'].includes(type), suggestedModels: [] }));
}

function renderModelAdmin() {
    elements.providerCatalogGrid.innerHTML = state.modelCatalog.map(preset => `
        <button class="provider-catalog-item" data-provider-type="${preset.type}" type="button">
            <span class="provider-mark">${escapeHtml(preset.mark)}</span>
            <span class="provider-catalog-copy"><strong>${escapeHtml(preset.name)}</strong><small>${escapeHtml(preset.description)}</small></span>
            <span class="provider-connect-label">连接</span>
        </button>`).join('');

    if (!state.modelProviders.length) {
        elements.configuredProviderList.innerHTML = '<p class="admin-empty-state">尚未连接模型供应商，当前继续使用环境变量或本地演示模式。</p>';
    } else {
        elements.configuredProviderList.innerHTML = state.modelProviders.map(provider => {
            const preset = modelPreset(provider.providerType);
            const isDefault = provider.models.some(model => model.defaultModel);
            return `<article class="configured-provider">
                <span class="provider-mark">${escapeHtml(preset.mark)}</span>
                <span class="configured-provider-copy"><div><strong>${escapeHtml(provider.displayName)}</strong>${isDefault ? '<span class="default-provider-badge">默认</span>' : ''}</div><small>${provider.models.length} 个模型 · ${escapeHtml(provider.baseUrl)}</small></span>
                <span class="provider-status ${provider.status === 'ERROR' ? 'error' : ''}">${escapeHtml(provider.statusMessage || '等待测试')}</span>
                <button class="icon-button" data-configure-provider="${provider.id}" type="button" title="配置供应商" aria-label="配置 ${escapeHtml(provider.displayName)}">${iconMarkup('settings')}</button>
            </article>`;
        }).join('');
    }

    const options = [];
    state.modelProviders.forEach(provider => provider.models.filter(model => model.enabled).forEach(model => {
        options.push({ value: `${provider.id}|${encodeURIComponent(model.modelId)}`,
            label: `${model.displayName} · ${provider.displayName}`, selected: model.defaultModel });
    }));
    if (!options.length) {
        elements.defaultModelSelect.innerHTML = `<option value="">${escapeHtml(state.currentModel?.configured ? `${state.currentModel.modelId} · 环境变量` : '本地演示')}</option>`;
        elements.defaultModelSelect.disabled = true;
    } else {
        elements.defaultModelSelect.disabled = false;
        elements.defaultModelSelect.innerHTML = options.map(option => `<option value="${option.value}" ${option.selected ? 'selected' : ''}>${escapeHtml(option.label)}</option>`).join('');
    }
    if (state.currentModel?.configured) setModelStatus(state.currentModel.modelId);
}

function openModelProviderDialog(type, providerId = '') {
    const provider = providerId ? state.modelProviders.find(item => item.id === providerId) : null;
    const preset = modelPreset(provider?.providerType || type);
    document.querySelector('#modelProviderId').value = provider?.id || '';
    document.querySelector('#modelProviderType').value = preset.type;
    document.querySelector('#modelProviderMark').textContent = preset.mark;
    document.querySelector('#modelProviderDialogTitle').textContent = `配置 ${provider?.displayName || preset.name}`;
    document.querySelector('#modelProviderDialogSubtitle').textContent = preset.description || '连接模型服务并选择工作区可用模型。';
    document.querySelector('#modelProviderBaseUrl').value = provider?.baseUrl || preset.defaultBaseUrl;
    const apiKeyInput = document.querySelector('#modelProviderApiKey');
    apiKeyInput.type = 'password';
    apiKeyInput.value = '';
    apiKeyInput.required = preset.apiKeyRequired && !provider?.apiKeyConfigured;
    document.querySelector('#modelProviderKeyOptional').textContent = preset.apiKeyRequired
        ? (provider?.apiKeyConfigured ? '（已保存，留空保留）' : '') : '（可选）';
    document.querySelector('#modelProviderDisplayName').value = provider?.displayName || '';
    document.querySelector('#deleteModelProviderButton').hidden = !provider;
    document.querySelector('#modelProviderResult').hidden = true;
    document.querySelector('#modelProviderResult').classList.remove('error');
    document.querySelector('#manualModelId').value = '';
    modelDialogModels = provider
        ? provider.models.map(model => ({ id: model.modelId, selected: model.enabled }))
        : (preset.suggestedModels || []).map(id => ({ id, selected: true }));
    renderModelOptions();
    elements.modelProviderDialog.showModal();
    document.querySelector('#modelProviderBaseUrl').focus();
}

function captureModelSelections() {
    document.querySelectorAll('[data-model-option]').forEach(input => {
        const model = modelDialogModels.find(item => item.id === input.dataset.modelOption);
        if (model) model.selected = input.checked;
    });
}

function renderModelOptions() {
    if (!modelDialogModels.length) {
        elements.modelOptionList.innerHTML = '<p>点击“发现模型”读取可用模型，或手动添加模型 ID。</p>';
        return;
    }
    elements.modelOptionList.innerHTML = modelDialogModels.map(model => `
        <label class="model-option"><input type="checkbox" data-model-option="${escapeHtml(model.id)}" ${model.selected ? 'checked' : ''}><span>${escapeHtml(model.id)}</span></label>`).join('');
}

function modelProviderPayload() {
    captureModelSelections();
    return {
        providerType: document.querySelector('#modelProviderType').value,
        displayName: document.querySelector('#modelProviderDisplayName').value.trim(),
        baseUrl: document.querySelector('#modelProviderBaseUrl').value.trim(),
        apiKey: document.querySelector('#modelProviderApiKey').value.trim(),
        modelIds: modelDialogModels.filter(model => model.selected).map(model => model.id)
    };
}

function showModelProviderResult(message, error = false) {
    const result = document.querySelector('#modelProviderResult');
    result.textContent = message;
    result.hidden = false;
    result.classList.toggle('error', error);
}

async function discoverProviderModels() {
    const payload = modelProviderPayload();
    const providerId = document.querySelector('#modelProviderId').value;
    const path = providerId
        ? `/api/v1/admin/model-providers/${providerId}/discover`
        : '/api/v1/admin/model-providers/discover';
    const result = await api(path, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ providerType: payload.providerType, baseUrl: payload.baseUrl, apiKey: payload.apiKey })
    });
    captureModelSelections();
    const selected = new Set(modelDialogModels.filter(model => model.selected).map(model => model.id));
    modelDialogModels = [...new Set([...modelDialogModels.map(model => model.id), ...result.models])]
        .sort((a, b) => a.localeCompare(b))
        .map(id => ({ id, selected: selected.has(id) || result.models.includes(id) }));
    renderModelOptions();
    showModelProviderResult(result.message);
}

async function refreshModelConfiguration() {
    await Promise.all([loadModelAdmin(), loadCurrentModel()]);
    renderModelAdmin();
}

elements.knowledgeList.addEventListener('click', async event => {
    const button = event.target.closest('[data-id]');
    if (!button) return;
    state.activeAgentId = null;
    state.activeId = button.dataset.id;
    renderAvailableAgents();
    renderKnowledgeBases();
    await loadDocuments();
    resetConversation();
});

elements.availableAgentList.addEventListener('click', event => {
    const button = event.target.closest('[data-sidebar-agent]');
    if (button) selectAgent(button.dataset.sidebarAgent).catch(error => showToast(error.message));
});
elements.chatAgentSelect.addEventListener('change', event => {
    selectAgent(event.target.value).catch(error => showToast(error.message));
});

elements.chatForm.addEventListener('submit', async event => {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!state.activeId || !question) return;
    appendMessage('user', question);
    elements.questionInput.value = '';
    if (isStaticPreview) {
        appendMessage('assistant', '这是 JadeBase 静态预览。连接本地服务后即可检索知识库并生成带引用的回答。', [],
            state.thinkMode ? { reasoning: '已理解问题，并检查当前知识库与模型连接状态。', durationMs: 620, steps: 1 } : {});
        return;
    }
    elements.sendButton.disabled = true;
    elements.sendButton.innerHTML = iconMarkup('hourglass');
    let pendingMessage = state.thinkMode
        ? appendMessage('assistant', '', [], { pending: true, status: '正在分析问题并检索知识库…', steps: 1 })
        : null;
    try {
        let result = null;
        await streamChat({ knowledgeBaseId: state.activeId, conversationId: state.conversationId,
            question, topK: appSettings.topK, language: appSettings.language, thinkMode: state.thinkMode,
            agentId: state.activeAgentId },
        (eventName, data) => {
            if (eventName === 'thinking' && pendingMessage) {
                renderMessageArticle(pendingMessage, 'assistant', '', [], {
                    pending: true, status: data.message, steps: 1
                });
            }
            if (eventName === 'result') result = data;
            if (eventName === 'error') throw new Error(data.message || '回答生成失败');
        });
        if (!result) throw new Error('模型未返回完整回答');
        state.conversationId = result.conversationId;
        if (pendingMessage) {
            renderMessageArticle(pendingMessage, 'assistant', result.answer, result.sources, {
                reasoning: result.reasoning, durationMs: result.thinkingDurationMs, steps: result.thinkingSteps
            });
        } else {
            appendMessage('assistant', result.answer, result.sources);
        }
        setModelStatus(result.mode === 'model' ? result.model : '本地演示');
        if (appSettings.updateMemories && /^记住[：:]/.test(question)) await loadMemories();
    } catch (error) {
        showToast(error.message);
        if (pendingMessage) renderMessageArticle(pendingMessage, 'assistant', '回答生成失败，请稍后重试。');
        else appendMessage('assistant', '回答生成失败，请稍后重试。');
    } finally {
        elements.sendButton.disabled = false;
        elements.sendButton.innerHTML = iconMarkup('send');
        elements.questionInput.focus();
    }
});

elements.thinkModeButton.addEventListener('click', () => {
    state.thinkMode = !state.thinkMode;
    elements.thinkModeButton.classList.toggle('active', state.thinkMode);
    elements.thinkModeButton.setAttribute('aria-pressed', String(state.thinkMode));
    showToast(state.thinkMode ? '已开启深度思考' : '已关闭深度思考');
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
document.querySelector('#adminPanelButton').addEventListener('click', () => openAdmin());
document.querySelector('#exitAdminButton').addEventListener('click', closeAdmin);
document.querySelector('#openAdminSidebarButton').addEventListener('click', () => elements.adminShell.classList.add('sidebar-open'));
document.querySelector('#closeAdminSidebarButton').addEventListener('click', () => elements.adminShell.classList.remove('sidebar-open'));
document.querySelectorAll('.admin-nav-item.planned').forEach(button => {
    button.addEventListener('click', () => showToast(`${button.dataset.adminLabel}将在后续管理阶段开放`));
});
document.querySelectorAll('[data-admin-page]').forEach(button => {
    button.addEventListener('click', () => showAdminPage(button.dataset.adminPage).catch(error => showToast(error.message)));
});
document.querySelector('#adminMenuSearch').addEventListener('input', event => {
    const keyword = event.target.value.trim().toLowerCase();
    document.querySelectorAll('.admin-nav-item').forEach(button => {
        button.hidden = Boolean(keyword) && !button.textContent.toLowerCase().includes(keyword);
    });
    document.querySelectorAll('[data-admin-group]').forEach(group => {
        group.hidden = ![...group.querySelectorAll('.admin-nav-item')].some(button => !button.hidden);
    });
});
document.querySelector('#createAgentButton').addEventListener('click', () => openAgentEditor().catch(error => showToast(error.message)));
document.querySelector('#refreshAgentsButton').addEventListener('click', () => loadAgentAdmin().catch(error => showToast(error.message)));
document.querySelector('#agentSearchInput').addEventListener('input', event => {
    state.agentFilters.query = event.target.value.trim();
    renderAgentAdmin();
});
document.querySelector('#agentAccessFilter').addEventListener('change', event => {
    state.agentFilters.access = event.target.value;
    renderAgentAdmin();
});
document.querySelector('#agentStatusFilter').addEventListener('change', event => {
    state.agentFilters.status = event.target.value;
    renderAgentAdmin();
});
elements.agentTableBody.addEventListener('click', async event => {
    const enabledButton = event.target.closest('[data-agent-enabled]');
    const openButton = event.target.closest('[data-agent-open]');
    const row = event.target.closest('[data-agent-edit]');
    try {
        if (enabledButton) {
            event.stopPropagation();
            const agentId = enabledButton.dataset.agentEnabled;
            const enabled = enabledButton.dataset.nextEnabled === 'true';
            let updated;
            if (isStaticPreview) {
                updated = { ...state.agents.find(item => item.id === agentId), enabled };
            } else {
                updated = await api(`/api/v1/admin/agents/${agentId}/enabled`, {
                    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
                });
            }
            state.agents = state.agents.map(item => item.id === agentId ? updated : item);
            renderAgentAdmin();
            await loadAvailableAgents();
            showToast(enabled ? 'Agent 已启用' : 'Agent 已停用');
            return;
        }
        if (openButton || row) await openAgentEditor((openButton || row).dataset.agentOpen || row.dataset.agentEdit);
    } catch (error) { showToast(error.message); }
});
document.querySelector('#backAgentEditorButton').addEventListener('click', () => closeAgentBuilder().catch(error => showToast(error.message)));
document.querySelector('#cancelAgentEditorButton').addEventListener('click', () => closeAgentBuilder().catch(error => showToast(error.message)));
document.querySelector('#agentNameInput').addEventListener('input', syncAgentSubmitState);
document.querySelector('#agentUseKnowledgeInput').addEventListener('change', syncAgentKnowledgeControl);
document.querySelector('#agentAccessSelect').addEventListener('change', syncAgentShareControls);
document.querySelector('#addAgentStarterButton').addEventListener('click', () => {
    const values = [...document.querySelectorAll('[data-agent-starter]')].map(input => input.value);
    if (values.length >= 6) {
        showToast('对话开场白最多添加 6 条');
        return;
    }
    renderAgentStarterEditor([...values, '']);
    document.querySelectorAll('[data-agent-starter]')[values.length].focus();
});
document.querySelector('#agentStarterEditor').addEventListener('click', event => {
    const button = event.target.closest('[data-remove-agent-starter]');
    if (!button) return;
    const inputs = [...document.querySelectorAll('[data-agent-starter]')];
    const values = inputs.filter((_, index) => index !== Number(button.dataset.removeAgentStarter)).map(input => input.value);
    renderAgentStarterEditor(values.length ? values : ['']);
});
document.querySelector('#saveAgentDraftButton').addEventListener('click', async () => {
    const button = document.querySelector('#saveAgentDraftButton');
    button.disabled = true;
    try {
        const saved = await saveAgentDefinition();
        await openAgentEditor(saved.id);
        showToast(`${saved.name} 的草稿已保存`);
    } catch (error) { showToast(error.message); }
    finally { button.disabled = false; }
});
elements.agentEditorForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = document.querySelector('#publishAgentButton');
    button.disabled = true;
    try {
        const saved = await saveAgentDefinition();
        let published;
        if (isStaticPreview) {
            published = { ...saved, status: 'published', currentVersion: (saved.currentVersion || 0) + 1,
                hasUnpublishedChanges: false, enabled: true, publishedAt: new Date().toISOString() };
        } else published = await api(`/api/v1/admin/agents/${saved.id}/publish`, { method: 'POST' });
        state.agents = state.agents.map(item => item.id === published.id ? published : item);
        renderAgentAdmin();
        await loadAvailableAgents();
        await closeAgentBuilder();
        showToast(`${published.name} v${published.currentVersion} 已发布`);
    } catch (error) { showToast(error.message); }
    finally { syncAgentSubmitState(); }
});
document.querySelector('#deleteAgentButton').addEventListener('click', async () => {
    const agentId = document.querySelector('#agentIdInput').value;
    const agent = state.agents.find(item => item.id === agentId);
    if (!agent || !window.confirm(`确认删除 Agent“${agent.name}”及其发布版本和运行记录吗？`)) return;
    try {
        if (!isStaticPreview) await api(`/api/v1/admin/agents/${agentId}`, { method: 'DELETE' });
        state.agents = state.agents.filter(item => item.id !== agentId);
        if (state.activeAgentId === agentId) state.activeAgentId = null;
        renderAgentAdmin();
        await loadAvailableAgents();
        await closeAgentBuilder();
        showToast('Agent 已删除');
    } catch (error) { showToast(error.message); }
});
document.querySelector('#refreshModelProvidersButton').addEventListener('click', () => {
    refreshModelConfiguration().catch(error => showToast(error.message));
});
elements.providerCatalogGrid.addEventListener('click', event => {
    const button = event.target.closest('[data-provider-type]');
    if (button) openModelProviderDialog(button.dataset.providerType);
});
elements.configuredProviderList.addEventListener('click', event => {
    const button = event.target.closest('[data-configure-provider]');
    if (button) {
        const provider = state.modelProviders.find(item => item.id === button.dataset.configureProvider);
        if (provider) openModelProviderDialog(provider.providerType, provider.id);
    }
});
elements.defaultModelSelect.addEventListener('change', async event => {
    if (!event.target.value) return;
    const [providerId, encodedModelId] = event.target.value.split('|');
    event.target.disabled = true;
    try {
        state.currentModel = await api('/api/v1/admin/model-providers/default', {
            method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ providerId, modelId: decodeURIComponent(encodedModelId) })
        });
        await refreshModelConfiguration();
        showToast(`默认模型已切换为 ${state.currentModel.modelId}`);
    } catch (error) { showToast(error.message); }
    finally { event.target.disabled = false; }
});

document.querySelector('#adminAddConnectorButton').addEventListener('click', () => showAdminPage('add-connector').catch(error => showToast(error.message)));
document.querySelector('#catalogFeishuButton').addEventListener('click', () => openFeishuConnectionDialog(null));
document.querySelector('#adminRefreshConnectorsButton').addEventListener('click', () => loadFeishuConnector().catch(error => showToast(error.message)));
document.querySelector('#adminAddSourceButton').addEventListener('click', () => {
    if (!state.feishuConnections.length) {
        showToast('请先配置飞书连接');
        showAdminPage('add-connector').catch(error => showToast(error.message));
        return;
    }
    openFeishuSourceDialog();
});

elements.adminConnectorList.addEventListener('click', async event => {
    const edit = event.target.closest('[data-feishu-connection-edit]');
    const test = event.target.closest('[data-feishu-connection-test]');
    const remove = event.target.closest('[data-feishu-connection-delete]');
    if (edit) return openFeishuConnectionDialog(edit.dataset.feishuConnectionEdit);
    try {
        if (test) {
            test.disabled = true;
            const result = await api(`/api/v1/connectors/feishu/connections/${test.dataset.feishuConnectionTest}/test`, { method: 'POST' });
            showToast(result.message);
        }
        if (remove) {
            const connection = state.feishuConnections.find(item => item.id === remove.dataset.feishuConnectionDelete);
            if (!window.confirm(`确认删除「${connection?.name || '飞书连接'}」及其同步来源吗？`)) return;
            await api(`/api/v1/connectors/feishu/connections/${remove.dataset.feishuConnectionDelete}`, { method: 'DELETE' });
            await loadDocuments();
            showToast('连接器已删除');
        }
        await loadFeishuConnector();
    } catch (error) { showToast(error.message); }
    finally { if (test) test.disabled = false; }
});

document.querySelector('#createDocumentSetButton').addEventListener('click', () => openDocumentSetDialog());
elements.documentSetList.addEventListener('click', event => {
    const button = event.target.closest('[data-document-set-edit]');
    if (button) openDocumentSetDialog(button.dataset.documentSetEdit);
});
document.querySelector('#closeDocumentSetDialogButton').addEventListener('click', () => elements.documentSetDialog.close());
document.querySelector('#cancelDocumentSetButton').addEventListener('click', () => elements.documentSetDialog.close());
document.querySelector('#documentPickerSearch').addEventListener('input', renderDocumentPicker);
elements.documentPickerList.addEventListener('change', event => {
    if (!event.target.matches('input[type="checkbox"]')) return;
    if (event.target.checked) selectedDocumentIds.add(event.target.value);
    else selectedDocumentIds.delete(event.target.value);
    renderDocumentPicker();
});
document.querySelector('#selectAllDocumentsButton').addEventListener('click', () => {
    const query = document.querySelector('#documentPickerSearch').value.trim().toLowerCase();
    const visible = state.adminDocuments.filter(item => `${item.name} ${item.knowledgeBaseName}`.toLowerCase().includes(query));
    const allSelected = visible.length && visible.every(item => selectedDocumentIds.has(item.id));
    visible.forEach(item => allSelected ? selectedDocumentIds.delete(item.id) : selectedDocumentIds.add(item.id));
    renderDocumentPicker();
});
elements.documentSetForm.addEventListener('submit', async event => {
    event.preventDefault();
    const setId = document.querySelector('#documentSetId').value;
    const button = document.querySelector('#saveDocumentSetButton');
    button.disabled = true;
    try {
        const payload = { name: document.querySelector('#documentSetNameInput').value,
            description: document.querySelector('#documentSetDescriptionInput').value,
            documentIds: [...selectedDocumentIds] };
        if (isStaticPreview) {
            const members = state.adminDocuments.filter(item => selectedDocumentIds.has(item.id));
            const preview = { id: setId || `preview-set-${Date.now()}`, ...payload, documents: members,
                documentCount: members.length, readyCount: members.filter(item => item.status === 'READY').length,
                chunkCount: members.reduce((total, item) => total + item.chunkCount, 0) };
            const index = state.documentSets.findIndex(item => item.id === setId);
            if (index >= 0) state.documentSets[index] = preview;
            else state.documentSets.unshift(preview);
            state.knowledgeSummary.documentSetCount = state.documentSets.length;
        } else {
            await api(`/api/v1/admin/knowledge/document-sets${setId ? `/${setId}` : ''}`, {
                method: setId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
            });
        }
        elements.documentSetDialog.close();
        if (isStaticPreview) renderDocumentSets();
        else await loadDocumentKnowledge();
        showToast(setId ? '文档集已更新' : '文档集已创建');
    } catch (error) { showToast(error.message); }
    finally { button.disabled = false; }
});
document.querySelector('#deleteDocumentSetButton').addEventListener('click', async () => {
    const setId = document.querySelector('#documentSetId').value;
    if (!setId || !window.confirm('确认删除这个文档集吗？原始文档不会被删除。')) return;
    try {
        if (isStaticPreview) {
            state.documentSets = state.documentSets.filter(item => item.id !== setId);
            state.knowledgeSummary.documentSetCount = state.documentSets.length;
        } else await api(`/api/v1/admin/knowledge/document-sets/${setId}`, { method: 'DELETE' });
        elements.documentSetDialog.close();
        if (isStaticPreview) renderDocumentSets();
        else await loadDocumentKnowledge();
        showToast('文档集已删除');
    } catch (error) { showToast(error.message); }
});

elements.indexSettingsForm.addEventListener('submit', async event => {
    event.preventDefault();
    const payload = { chunkSize: Number(document.querySelector('#chunkSizeInput').value),
        chunkOverlap: Number(document.querySelector('#chunkOverlapInput').value),
        topK: Number(document.querySelector('#indexTopKInput').value),
        candidateK: Number(document.querySelector('#candidateKInput').value),
        rrfK: Number(document.querySelector('#rrfKInput').value),
        rerankEnabled: document.querySelector('#rerankEnabledInput').checked,
        queryRewriteEnabled: document.querySelector('#queryRewriteEnabledInput').checked };
    if (payload.chunkOverlap >= payload.chunkSize) return showToast('重叠大小必须小于分块大小');
    const button = document.querySelector('#saveIndexSettingsButton');
    button.disabled = true;
    try {
        state.indexSettings = isStaticPreview ? { ...payload, reindexRequired: true }
            : await api('/api/v1/admin/knowledge/index-settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        appSettings.topK = state.indexSettings.topK;
        renderIndexAdmin();
        showToast('索引设置已保存');
    } catch (error) { showToast(error.message); }
    finally { button.disabled = false; }
});
document.querySelector('#reindexAllButton').addEventListener('click', async event => {
    if (!window.confirm('将所有可用文档加入重建队列，继续吗？')) return;
    event.currentTarget.disabled = true;
    try {
        const result = isStaticPreview ? { message: '已将 3 个文档加入重建队列' }
            : await api('/api/v1/admin/knowledge/reindex', { method: 'POST' });
        await loadIndexAdmin();
        showToast(result.message);
    } catch (error) { showToast(error.message); }
    finally { event.currentTarget.disabled = false; }
});
document.querySelector('#inviteUsersButton').addEventListener('click', openInviteUsersDialog);
document.querySelector('#closeInviteUsersDialogButton').addEventListener('click', () => elements.inviteUsersDialog.close());
document.querySelector('#cancelInviteUsersButton').addEventListener('click', () => elements.inviteUsersDialog.close());
document.querySelector('#refreshUsersButton').addEventListener('click', () => loadUserAdmin().catch(error => showToast(error.message)));
document.querySelector('#exportUsersButton').addEventListener('click', exportCurrentUsers);
document.querySelector('#userSearchInput').addEventListener('input', event => {
    window.clearTimeout(event.currentTarget.searchTimer);
    event.currentTarget.searchTimer = window.setTimeout(() => {
        state.userFilters.query = event.currentTarget.value.trim();
        state.userPage = 0;
        loadUserAdmin().catch(error => showToast(error.message));
    }, 250);
});
document.querySelector('#userRoleFilter').addEventListener('change', event => {
    state.userFilters.role = event.currentTarget.value;
    state.userPage = 0;
    loadUserAdmin().catch(error => showToast(error.message));
});
document.querySelector('#userStatusFilter').addEventListener('change', event => {
    state.userFilters.status = event.currentTarget.value;
    state.userPage = 0;
    loadUserAdmin().catch(error => showToast(error.message));
});
document.querySelector('#previousUserPageButton').addEventListener('click', () => {
    if (!state.userPage) return;
    state.userPage -= 1;
    loadUserAdmin().catch(error => showToast(error.message));
});
document.querySelector('#nextUserPageButton').addEventListener('click', () => {
    if (state.userPage + 1 >= (state.userAdmin?.totalPages || 1)) return;
    state.userPage += 1;
    loadUserAdmin().catch(error => showToast(error.message));
});
document.querySelector('#restrictSignupToggle').addEventListener('change', async event => {
    event.currentTarget.disabled = true;
    try {
        if (isStaticPreview) state.userAdmin.restrictOpenSignup = event.currentTarget.checked;
        else await api('/api/v1/admin/users/registration-policy', {
            method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ restrictOpenSignup: event.currentTarget.checked })
        });
        showToast(event.currentTarget.checked ? '已限制开放注册' : '已允许开放注册');
    } catch (error) {
        event.currentTarget.checked = !event.currentTarget.checked;
        showToast(error.message);
    } finally { event.currentTarget.disabled = false; }
});
elements.userTableBody.addEventListener('change', async event => {
    const select = event.target.closest('[data-user-role]');
    if (!select) return;
    select.disabled = true;
    try {
        if (isStaticPreview) {
            const user = state.userAdmin.users.find(item => item.id === select.dataset.userRole);
            if (user) user.role = select.value;
        } else await api(`/api/v1/admin/users/${select.dataset.userRole}`, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: select.value })
        });
        await loadUserAdmin();
        showToast('用户账号类型已更新');
    } catch (error) {
        await loadUserAdmin();
        showToast(error.message);
    }
});
elements.userTableBody.addEventListener('click', async event => {
    const button = event.target.closest('[data-user-status]');
    if (!button) return;
    const user = state.userAdmin?.users.find(item => item.id === button.dataset.userStatus);
    const suspending = button.dataset.nextStatus === 'suspended';
    if (suspending && !window.confirm(`确认停用「${user?.displayName || user?.email || '该用户'}」吗？其现有登录会话将立即失效。`)) return;
    button.disabled = true;
    try {
        if (isStaticPreview && user) user.status = button.dataset.nextStatus;
        else await api(`/api/v1/admin/users/${button.dataset.userStatus}`, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: button.dataset.nextStatus })
        });
        await loadUserAdmin();
        showToast(suspending ? '用户已停用' : '用户已恢复');
    } catch (error) { showToast(error.message); button.disabled = false; }
});
elements.inviteUsersForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = document.querySelector('#createInvitationButton');
    button.disabled = true;
    try {
        const email = document.querySelector('#inviteEmailInput').value.trim();
        const role = document.querySelector('#inviteRoleSelect').value;
        const result = isStaticPreview ? {
            invitation: { id: `preview-${Date.now()}`, email, role, invitedBy: userLabel(), expiresAt: new Date(Date.now() + 7 * 86400000).toISOString(), createdAt: new Date().toISOString() },
            token: `preview-${Date.now()}`
        } : await api('/api/v1/admin/users/invitations', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, role })
        });
        if (isStaticPreview) {
            state.userAdmin.invitations.unshift(result.invitation);
            state.userAdmin.pendingInvites = state.userAdmin.invitations.length;
            renderUserAdmin();
        } else await loadUserAdmin();
        document.querySelector('#inviteResultEmail').textContent = result.invitation.email;
        document.querySelector('#inviteLinkOutput').value = invitationUrl(result.token);
        document.querySelector('#inviteLinkResult').hidden = false;
        button.textContent = '重新创建邀请';
        showToast('邀请已创建');
    } catch (error) { showToast(error.message); }
    finally { button.disabled = false; }
});
document.querySelector('#copyInviteLinkButton').addEventListener('click', async () => {
    const input = document.querySelector('#inviteLinkOutput');
    try { await navigator.clipboard.writeText(input.value); }
    catch { input.select(); document.execCommand('copy'); }
    showToast('邀请链接已复制');
});
document.querySelector('#pendingInvitationList').addEventListener('click', async event => {
    const button = event.target.closest('[data-invitation-revoke]');
    if (!button || !window.confirm('确认撤销这个邀请吗？')) return;
    button.disabled = true;
    try {
        if (isStaticPreview) {
            state.userAdmin.invitations = state.userAdmin.invitations.filter(item => item.id !== button.dataset.invitationRevoke);
            state.userAdmin.pendingInvites = state.userAdmin.invitations.length;
            renderUserAdmin();
        } else {
            await api(`/api/v1/admin/users/invitations/${button.dataset.invitationRevoke}`, { method: 'DELETE' });
            await loadUserAdmin();
        }
        showToast('邀请已撤销');
    } catch (error) { showToast(error.message); button.disabled = false; }
});
document.querySelector('#closeModelProviderButton').addEventListener('click', () => elements.modelProviderDialog.close());
document.querySelector('#cancelModelProviderButton').addEventListener('click', () => elements.modelProviderDialog.close());
document.querySelector('#toggleModelProviderKey').addEventListener('click', () => {
    const input = document.querySelector('#modelProviderApiKey');
    input.type = input.type === 'password' ? 'text' : 'password';
});
document.querySelector('#addManualModelButton').addEventListener('click', () => {
    const input = document.querySelector('#manualModelId');
    const id = input.value.trim();
    if (!id) return;
    captureModelSelections();
    const existing = modelDialogModels.find(model => model.id === id);
    if (existing) existing.selected = true;
    else modelDialogModels.push({ id, selected: true });
    modelDialogModels.sort((a, b) => a.id.localeCompare(b.id));
    input.value = '';
    renderModelOptions();
});
document.querySelector('#selectAllModelsButton').addEventListener('click', () => {
    captureModelSelections();
    const shouldSelect = modelDialogModels.some(model => !model.selected);
    modelDialogModels.forEach(model => { model.selected = shouldSelect; });
    renderModelOptions();
});
document.querySelector('#discoverModelsButton').addEventListener('click', async event => {
    event.currentTarget.disabled = true;
    event.currentTarget.classList.add('is-spinning');
    try { await discoverProviderModels(); }
    catch (error) { showModelProviderResult(error.message, true); }
    finally { event.currentTarget.disabled = false; event.currentTarget.classList.remove('is-spinning'); }
});
document.querySelector('#testModelProviderButton').addEventListener('click', async event => {
    const payload = modelProviderPayload();
    const firstModel = payload.modelIds[0];
    if (!firstModel) {
        showModelProviderResult('请先选择或添加一个模型', true);
        return;
    }
    event.currentTarget.disabled = true;
    try {
        const result = await api('/api/v1/admin/model-providers/test', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ providerId: document.querySelector('#modelProviderId').value || null,
                providerType: payload.providerType, baseUrl: payload.baseUrl,
                apiKey: payload.apiKey, modelId: firstModel })
        });
        showModelProviderResult(result.message);
    } catch (error) { showModelProviderResult(error.message, true); }
    finally { event.currentTarget.disabled = false; }
});
elements.modelProviderForm.addEventListener('submit', async event => {
    event.preventDefault();
    const payload = modelProviderPayload();
    if (!payload.modelIds.length) {
        showModelProviderResult('请至少选择或添加一个模型', true);
        return;
    }
    const providerId = document.querySelector('#modelProviderId').value;
    const saveButton = document.querySelector('#saveModelProviderButton');
    saveButton.disabled = true;
    try {
        await api(`/api/v1/admin/model-providers${providerId ? `/${providerId}` : ''}`, {
            method: providerId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        elements.modelProviderDialog.close();
        await refreshModelConfiguration();
        showToast(providerId ? '模型供应商已更新' : '模型供应商已连接');
    } catch (error) { showModelProviderResult(error.message, true); }
    finally { saveButton.disabled = false; }
});
document.querySelector('#deleteModelProviderButton').addEventListener('click', async event => {
    const providerId = document.querySelector('#modelProviderId').value;
    if (!providerId || !window.confirm('确认删除这个模型供应商及其模型配置吗？')) return;
    event.currentTarget.disabled = true;
    try {
        await api(`/api/v1/admin/model-providers/${providerId}`, { method: 'DELETE' });
        elements.modelProviderDialog.close();
        await refreshModelConfiguration();
        showToast('模型供应商已删除');
    } catch (error) { showModelProviderResult(error.message, true); }
    finally { event.currentTarget.disabled = false; }
});
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
    if (event.key === 'Escape') {
        closeAccountMenu();
        elements.adminShell.classList.remove('sidebar-open');
    }
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
    if (event.target.closest('[data-welcome-add]')) {
        document.querySelector('#openCreateButton').click();
        return;
    }
    const starter = event.target.closest('[data-agent-starter-question]');
    if (starter) {
        elements.questionInput.value = starter.dataset.agentStarterQuestion;
        elements.chatForm.requestSubmit();
        return;
    }
    const toggle = event.target.closest('[data-thinking-toggle]');
    if (toggle) {
        const trace = toggle.closest('[data-thinking-trace]');
        const collapsed = trace.classList.toggle('collapsed');
        toggle.setAttribute('aria-expanded', String(!collapsed));
        return;
    }
    const action = event.target.closest('[data-message-action]');
    if (!action) return;
    const article = action.closest('.message.assistant');
    if (action.dataset.messageAction === 'copy') {
        navigator.clipboard.writeText(article.dataset.messageText || '').then(() => showToast('回答已复制'));
    }
    if (action.dataset.messageAction === 'like' || action.dataset.messageAction === 'dislike') {
        article.querySelectorAll('[data-message-action="like"], [data-message-action="dislike"]')
            .forEach(button => button.classList.toggle('active', button === action && !action.classList.contains('active')));
    }
    if (action.dataset.messageAction === 'regenerate') {
        const userMessage = article.previousElementSibling;
        if (userMessage?.classList.contains('user')) {
            elements.questionInput.value = userMessage.dataset.messageText || '';
            elements.chatForm.requestSubmit();
        }
    }
});
document.querySelector('.composer-context [data-welcome-add]').addEventListener('click', () => {
    document.querySelector('#openCreateButton').click();
});
document.querySelector('#shareConversationButton').addEventListener('click', async () => {
    if (!state.conversationId) return showToast('发送消息后即可分享当前对话');
    const shareData = { title: 'JadeBase 对话', text: 'JadeBase 对话分享', url: location.href };
    try {
        if (navigator.share) await navigator.share(shareData);
        else {
            await navigator.clipboard.writeText(location.href);
            showToast('对话链接已复制');
        }
    } catch (error) {
        if (error.name !== 'AbortError') showToast('暂时无法分享对话');
    }
});
document.querySelector('#conversationMoreButton').addEventListener('click', () => {
    showToast('更多对话操作将在会话管理阶段开放');
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

document.querySelector('#configureFeishuButton').addEventListener('click', () => openFeishuConnectionDialog());
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
        if (!elements.adminShell.hidden && state.adminPage === 'add-connector') await showAdminPage('existing-connectors');
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

async function handleFeishuSourceAction(event) {
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
}
elements.feishuSourceList.addEventListener('click', handleFeishuSourceAction);
elements.adminSourceList.addEventListener('click', handleFeishuSourceAction);

async function handleFeishuTaskAction(event) {
    const button = event.target.closest('[data-feishu-task-retry]');
    if (!button) return;
    try {
        await api(`/api/v1/connectors/feishu/sync-tasks/${button.dataset.feishuTaskRetry}/retry`, { method: 'POST' });
        await loadFeishuConnector();
        showToast('同步任务已重新入队');
    } catch (error) { showToast(error.message); }
}
elements.feishuTaskList.addEventListener('click', handleFeishuTaskAction);
elements.adminTaskList.addEventListener('click', handleFeishuTaskAction);
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
    state.currentUser = { id: 'preview-owner', email: 'preview@jadebase.local', displayName: '', role: 'owner', status: 'active' };
    showWorkspace();
    state.knowledgeBases = [
        { id: 'preview-product', name: '产品知识库', description: '产品资料与使用说明' },
        { id: 'preview-engineering', name: '研发知识库', description: '团队研发规范与架构文档' }
    ];
    state.agents = previewAgents();
    state.activeId = state.knowledgeBases[0].id;
    setModelStatus('静态预览');
    renderKnowledgeBases();
    renderDocuments();
    renderMemories();
    loadAvailableAgents();
    loadNotifications();
    if (location.hash.startsWith('#admin/')) openAdmin(false);
} else {
    bootstrap();
}

async function bootstrap() {
    try {
        state.currentUser = await api('/api/v1/auth/me', { skipAuthRedirect: true });
        showWorkspace();
        await Promise.all([loadServerSettings(), loadNotifications(), loadKnowledgeBases(), loadMemories(), loadCurrentModel(), loadAvailableAgents()]);
        renderCurrentUser();
        if (location.hash.startsWith('#admin/')) await openAdmin(false);
    } catch (error) {
        if (error.status === 401) {
            try { await loadRegistrationPolicy(); } catch (_) { /* login remains available */ }
            showAuthPage();
            applyRegistrationPolicy();
        }
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
            body: JSON.stringify({ email: elements.authEmail.value, password: elements.authPassword.value,
                inviteToken: registering ? inviteToken || null : null }),
            skipAuthRedirect: true
        });
        elements.authForm.reset();
        showWorkspace();
        await Promise.all([loadServerSettings(), loadNotifications(), loadKnowledgeBases(), loadMemories(), loadCurrentModel(), loadAvailableAgents()]);
        resetConversation();
        renderCurrentUser();
        if (registering && inviteToken) history.replaceState(null, '', location.pathname);
    } catch (error) {
        elements.authError.textContent = error.message;
        elements.authError.hidden = false;
    } finally {
        elements.authSubmit.disabled = false;
    }
});
