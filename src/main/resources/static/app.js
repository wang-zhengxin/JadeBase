const isStaticPreview = window.location.protocol === 'file:';
const state = { knowledgeBases: [], activeId: null, documents: [], conversationId: null, conversations: [] };

function iconMarkup(name, className = 'ui-icon') {
    return `<svg class="${className}" aria-hidden="true"><use href="#icon-${name}"/></svg>`;
}

function syncViewportHeight() {
    document.documentElement.style.setProperty('--viewport-height', `${window.innerHeight}px`);
}

syncViewportHeight();
window.addEventListener('resize', syncViewportHeight);

const elements = {
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
    toast: document.querySelector('#toast')
};

const defaultSettings = {
    profileName: '',
    workRole: '',
    colorMode: 'auto',
    chatBackground: 'none',
    language: 'zh-CN',
    topK: 6,
    showCitations: true
};

let appSettings = { ...defaultSettings };

function saveSettings() {
    applySettings();
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
    document.querySelector('#profileNameInput').value = appSettings.profileName;
    document.querySelector('#workRoleInput').value = appSettings.workRole;
    document.querySelector('#colorModeSelect').value = appSettings.colorMode;
    document.querySelector('#languageSelect').value = appSettings.language;
    document.querySelector('#topKInput').value = appSettings.topK;
    document.querySelector('#citationsToggle').checked = appSettings.showCitations;
    document.querySelectorAll('[data-background]').forEach(button => {
        button.classList.toggle('active', button.dataset.background === appSettings.chatBackground);
    });
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
            <span class="jade-logo large" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
            <h1>有什么可以帮你？</h1>
            <p>从当前知识库中检索可靠答案</p>
        </div>`;
}

function resetConversation() {
    state.conversationId = null;
    elements.messages.innerHTML = emptyStateMarkup();
    elements.emptyState = elements.messages.querySelector('#emptyState');
    elements.questionInput.value = '';
    elements.questionInput.focus();
}

async function api(path, options = {}) {
    const response = await fetch(path, options);
    if (!response.ok) {
        let message = `请求失败 (${response.status})`;
        try { message = (await response.json()).message || message; } catch (_) { /* no-op */ }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
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
    scheduleDocumentPolling();
}

function scheduleDocumentPolling() {
    window.clearTimeout(scheduleDocumentPolling.timer);
    const pending = state.documents.some(item => item.status === 'QUEUED' || item.status === 'PROCESSING');
    if (!pending || isStaticPreview) return;
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
                <div class="document-name" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</div>
                <div class="document-actions">
                    ${item.status === 'FAILED' ? `<button class="retry-button" data-document-retry="${item.id}" type="button" aria-label="重试索引">${iconMarkup('sync')}</button>` : ''}
                    <button class="delete-button" data-document-delete="${item.id}" type="button" aria-label="删除文档">${iconMarkup('close')}</button>
                </div>
            </div>
            <div class="document-row document-meta">
                <span>${item.chunkCount} 个片段 · ${formatBytes(item.sizeBytes)}</span>
                <span class="document-status ${item.status === 'FAILED' ? 'failed' : ''}" title="${escapeHtml(item.errorMessage || '')}">${statusText(item.status, item.progress)}</span>
            </div>
        </article>`).join('');
}

function statusText(status, progress = 0) {
    return ({ QUEUED: '排队中', PROCESSING: `处理中 ${progress}%`, READY: '已就绪', FAILED: '失败' })[status] || status;
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function appendMessage(role, text, sources = []) {
    elements.emptyState?.remove();
    const article = document.createElement('article');
    article.className = `message ${role}`;
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
    elements.messages.scrollTop = elements.messages.scrollHeight;
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
        elements.modelStatus.textContent = result.mode === 'model' ? '模型已连接' : '本地演示';
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
document.querySelector('#logoutButton').addEventListener('click', () => {
    closeAccountMenu();
    showToast('本地演示模式未启用账户登录');
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
    });
});

document.querySelector('#profileNameInput').addEventListener('input', event => {
    appSettings.profileName = event.target.value;
    saveSettings();
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
    button.addEventListener('click', () => showToast(`${button.dataset.connector}将在连接器阶段开放`));
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
    state.knowledgeBases = [
        { id: 'preview-product', name: '产品知识库', description: '产品资料与使用说明' },
        { id: 'preview-engineering', name: '研发知识库', description: '团队研发规范与架构文档' }
    ];
    state.activeId = state.knowledgeBases[0].id;
    elements.modelStatus.textContent = '静态预览';
    renderKnowledgeBases();
    renderDocuments();
    loadNotifications();
} else {
    Promise.all([loadServerSettings(), loadNotifications(), loadKnowledgeBases()])
            .catch(error => showToast(error.message));
}
