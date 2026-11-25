// A2D Cloud Dashboard - task list with expandable activities + export/import
const A2D_API_BASE = '/v1/sync';
const A2D_TOKEN_KEY = 'a2d_token';

function getToken() {
    return localStorage.getItem(A2D_TOKEN_KEY);
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
    try {
        const data = await apiGet('/tasks');
        const tasks = data.tasks || [];
        renderTasks(tasks);
        subtitle.textContent = `${data.total ?? tasks.length} task(s)`;
    } catch (e) {
        subtitle.textContent = 'Failed to load tasks';
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
    loadTasks();
});
