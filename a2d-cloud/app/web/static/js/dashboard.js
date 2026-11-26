// A2D Cloud Dashboard - task list with expandable activities + export/import
const A2D_API_BASE = '/v1/sync';
const A2D_TOKEN_KEY = 'a2d_token';

function getToken() {
    return localStorage.getItem(A2D_TOKEN_KEY);
}

function getUserIdFromToken(token) {
    try {
        const payload = token.split('.')[1];
        const base64Url = payload.replace(/-/g, '+').replace(/_/g, '/');
        // Add padding if needed
        const pad = 4 - (base64Url.length % 4);
        if (pad > 0) base64Url += '='.repeat(pad);
        const decoded = atob(base64Url);
        const obj = JSON.parse(decoded);
        return obj.sub || '';
    } catch (e) {
        console.warn('Unable to decode token for user id', e);
        return '';
    }
}

function updateWelcomeText() {
    const token = getToken();
    if (!token) return;
    const payload = token.split('.')[1];
    let base64Url = payload.replace(/-/g, '+').replace(/_/g, '/');
    const pad = 4 - (base64Url.length % 4);
    if (pad > 0) base64Url += '='.repeat(pad);
    const decoded = atob(base64Url);
    let obj;
    try { obj = JSON.parse(decoded); } catch (e) { obj = {}; }
    const userId = obj.sub || '';
    const span = document.getElementById('userId');
    span.textContent = `Welcome ${userId}!!`;
}

function exportSampleJson() {
    const now = Date.now();
    const sampleData = {
        "format": "a2d.tasks",
        "formatVersion": 1,
        "tasks": [
            {
                "syncId": "1",
                "taskName": "Sample Task 1",
                "description": "This is a sample task description.",
                "priority": 1,
                "context": "Sample context",
                "achieved": false,
                "creationTime": "2023-01-01T00:00:00Z",
                "updatedAt": now,
                "deleted": false,
                "activities": [
                    {
                        "syncId": "101",
                        "taskId": "1",
                        "description": "Sample activity",
                        "achieved": false,
                        "updatedAt": now,
                        "deleted": false
                    }
                ]
            },
            {
                "syncId": "2",
                "taskName": "Sample Task 2",
                "description": "Another sample task.",
                "priority": 2,
                "context": "Sample context 2",
                "achieved": true,
                "creationTime": "2023-01-02T00:00:00Z",
                "updatedAt": now,
                "deleted": false,
                "activities": []
            }
        ]
    };
    const blob = new Blob([JSON.stringify(sampleData, null, 2)], {type: 'application/json'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'sample_tasks.json';
    a.click();
    URL.revokeObjectURL(url);
}

function logout() {
    localStorage.removeItem('a2d_token');
    window.location.href = './login.html';
}

async function apiGet(path, params) {
    const qs = params ? '?' + new URLSearchParams(params).toString() : '';
    const resp = await fetch(`${A2D_API_BASE}${path}${qs}`, {
        headers: { 'Authorization': `Bearer ${getToken()}` }
    });
    if (!resp.ok) {
        throw new Error(`API error ${resp.status}`);
    }
    return resp.json();
}

async function apiDownload(path) {
    const resp = await fetch(`${A2D_API_BASE}${path}`, {
        headers: { 'Authorization': `Bearer ${getToken()}` }
    });
    if (!resp.ok) {
        throw new Error(`API error ${resp.status}`);
    }
    const blob = await resp.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `a2d-tasks-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
}

async function apiPostImport(file) {
    const formData = new FormData();
    formData.append('file', file);
    const resp = await fetch(`${A2D_API_BASE}/import?dry_run=false&on_conflict=skip`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${getToken()}` },
        body: formData
    });
    if (!resp.ok) {
        const txt = await resp.text();
        throw new Error(`Import failed: ${txt}`);
    }
    return resp.json();
}

async function loadTasks() {
    const subtitle = document.getElementById('subtitle');
    const err = document.getElementById('errorMsg');
    try {
        const data = await apiGet('/tasks');
        const tasks = data.tasks || [];
        renderTasks(tasks);
        subtitle.textContent = `${data.total ?? tasks.length} task(s)`;
        if (err) { err.style.display = 'none'; }
    } catch (e) {
        if (err) { err.textContent = `Failed to load tasks: ${e.message}`; err.style.display = 'block'; }
        else { subtitle.textContent = 'Failed to load tasks'; }
        toast(`Failed to load tasks: ${e.message}`);
    }
}

function renderTasks(tasks) {
    const tbody = document.getElementById('taskTableBody');
    tbody.innerHTML = '';
    for (const task of tasks) {
        const tr = document.createElement('tr');
        tr.className = 'task-row';
        tr.dataset.taskSyncId = task.syncId;
        tr.innerHTML = `
            <td class="task-name"><strong>${escapeHtml(task.taskName)}</strong></td>
            <td>${escapeHtml(task.description || '')}</td>
            <td>${task.priority ?? 1}</td>
            <td>${escapeHtml(task.context || 'No Context')}</td>
            <td>${task.achieved ? 'Yes' : 'No'}</td>
            <td>${formatDate(task.updatedAt)}</td>
        `;
        tr.querySelector('.task-name').style.cursor = 'pointer';
        tr.addEventListener('click', () => toggleActivities(task.syncId));
        tbody.appendChild(tr);
    }
}

async function toggleActivities(taskSyncId) {
    const existing = document.querySelector('.activity-subtable');
    if (existing && existing.dataset.taskId === taskSyncId) {
        existing.remove();
        return;
    }
    try {
        const task = await apiGet(`/tasks/${taskSyncId}`);
        if (task.activities && task.activities.length > 0) {
            const sub = document.createElement('tr');
            sub.className = 'activity-subrow';
            sub.dataset.taskId = taskSyncId;
            sub.innerHTML = `
                <td colspan="6">
                    <div class="activity-detail">
                        <div class="activity-title">Activities</div>
                        <table class="activity-subtable">
                            <thead>
                                <tr>
                                    <th>Activity</th>
                                    <th>Achieved</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${task.activities.map(a => `
                                    <tr>
                                        <td>${escapeHtml(a.description)}</td>
                                        <td>${a.achieved ? 'Yes' : 'No'}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                </td>
            `;
            task.parentElement.insertBefore(sub, task.nextElementSibling);
        }
    } catch (e) {
        toast(`Failed to load activities: ${e.message}`);
    }
}

function formatDate(ts) {
    if (!ts) return '—';
    const d = new Date(ts);
    return isNaN(d.getTime()) ? '—' : d.toLocaleDateString();
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str ?? '';
    return div.innerHTML;
}

async function doExport() {
    try {
        await apiDownload('/export?format=json&includeDeleted=false');
        toast('Export downloaded');
    } catch (e) {
        toast(`Export failed: ${e.message}`);
    }
}

async function doImportFile(file) {
    try {
        const report = await apiPostImport(file);
        toast(`Import complete — created: ${report.wouldCreate}, updated: ${report.wouldUpdate}, skipped: ${report.wouldSkip}. Android will sync on next sync.`);
        loadTasks();
    } catch (e) {
        toast(`Import failed: ${e.message}`);
    }
}

function toast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 4000);
}

// Wire controls
document.getElementById('exportBtn').addEventListener('click', doExport);
document.getElementById('sampleBtn').addEventListener('click', exportSampleJson);
document.getElementById('logoutBtn').addEventListener('click', logout);

const importFile = document.getElementById('importFile');
const importBtn = document.getElementById('importBtn');
importBtn.addEventListener('click', () => {
    importFile.value = '';
    importFile.click();
});
importFile.addEventListener('change', async (e) => {
    const file = e.target.files && e.target.files[0];
    if (file) {
        await doImportFile(file);
    }
});

// Init
document.addEventListener('DOMContentLoaded', () => {
    const token = getToken();
    if (!token) {
        window.location.href = '/login';
        return;
    }
    loadTasks();
    updateWelcomeText();
});
