const state = { knowledgeBases: [], activeId: null, documents: [] };

function syncViewportHeight() {
    document.documentElement.style.setProperty('--viewport-height', `${window.innerHeight}px`);
}

syncViewportHeight();
window.addEventListener('resize', syncViewportHeight);

const elements = {
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
    modelStatus: document.querySelector('#modelStatus'),
    toast: document.querySelector('#toast')
};

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

async function loadKnowledgeBases(preferredId) {
    state.knowledgeBases = await api('/api/v1/knowledge-bases');
    state.activeId = preferredId || state.activeId || state.knowledgeBases[0]?.id || null;
    renderKnowledgeBases();
    await loadDocuments();
}

function renderKnowledgeBases() {
    elements.knowledgeList.innerHTML = state.knowledgeBases.map(item => `
        <button class="knowledge-item ${item.id === state.activeId ? 'active' : ''}" data-id="${item.id}" type="button">
            <strong>${escapeHtml(item.name)}</strong>
            <span>${escapeHtml(item.description || '暂无描述')}</span>
        </button>`).join('');
    const active = state.knowledgeBases.find(item => item.id === state.activeId);
    elements.activeKnowledgeName.textContent = active?.name || '暂无知识库';
}

async function loadDocuments() {
    if (!state.activeId) {
        state.documents = [];
        renderDocuments();
        return;
    }
    state.documents = await api(`/api/v1/knowledge-bases/${state.activeId}/documents`);
    renderDocuments();
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
                <button class="delete-button" data-document-id="${item.id}" type="button">删除</button>
            </div>
            <div class="document-row document-meta">
                <span>${item.chunkCount} 个片段 · ${formatBytes(item.sizeBytes)}</span>
                <span class="document-status ${item.status === 'FAILED' ? 'failed' : ''}">${statusText(item.status)}</span>
            </div>
        </article>`).join('');
}

function statusText(status) {
    return ({ PROCESSING: '处理中', READY: '已就绪', FAILED: '失败' })[status] || status;
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
    const sourceMarkup = sources.length ? `
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
});

elements.chatForm.addEventListener('submit', async event => {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!state.activeId || !question) return;
    appendMessage('user', question);
    elements.questionInput.value = '';
    elements.sendButton.disabled = true;
    elements.sendButton.textContent = '思考中';
    try {
        const result = await api('/api/v1/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ knowledgeBaseId: state.activeId, question })
        });
        appendMessage('assistant', result.answer, result.sources);
        elements.modelStatus.textContent = result.mode === 'model' ? '模型已连接' : '本地演示模式';
    } catch (error) {
        showToast(error.message);
        appendMessage('assistant', '回答生成失败，请稍后重试。');
    } finally {
        elements.sendButton.disabled = false;
        elements.sendButton.textContent = '发送';
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
        showToast('文档已完成索引');
    } catch (error) {
        showToast(error.message);
    } finally {
        elements.fileInput.value = '';
    }
});

elements.documentList.addEventListener('click', async event => {
    const button = event.target.closest('[data-document-id]');
    if (!button || !window.confirm('确认删除这个文档及其索引吗？')) return;
    try {
        await api(`/api/v1/knowledge-bases/${state.activeId}/documents/${button.dataset.documentId}`, { method: 'DELETE' });
        await loadDocuments();
        showToast('文档已删除');
    } catch (error) { showToast(error.message); }
});

document.querySelector('#openCreateButton').addEventListener('click', () => elements.createDialog.showModal());
document.querySelector('#closeCreateButton').addEventListener('click', () => elements.createDialog.close());
document.querySelector('#cancelCreateButton').addEventListener('click', () => elements.createDialog.close());

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
        await loadKnowledgeBases(created.id);
        showToast('知识库已创建');
    } catch (error) { showToast(error.message); }
});

loadKnowledgeBases().catch(error => showToast(error.message));
