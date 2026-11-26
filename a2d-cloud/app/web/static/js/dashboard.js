// A2D Cloud Dashboard - task list with expandable activities + export/import
const A2D_API_BASE = '/v1/sync';
const A2D_TOKEN_KEY = 'tokens';

function getToken() {
    return localStorage.getItem(A2D_TOKEN_KEY);
}

// Decode JWT payload safely (handles padding)
function b64urlDecode(str) {
    const s = str.replace(/-/g, '+').replace(/_/g, '/');
    s += '='.repeat((4 - (s.length % 4)) % 4);
    return atob(s);
}

// Fetch user email from /me endpoint
async function fetchUserInfo() {
    const token = getToken();
    if (!token) return;
    try {
        const data = await apiGet('/me');
        const email = data.email || '';
        const span = document.getElementById('userId');
        span.textContent = `Welcome ${email}!!`;
    } catch (e) {
        console.warn('Failed to fetch user info', e);
    }
}

// Export sample JSON with correct schema for import
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

// Logout function
function logout() {
    localStorage.removeItem(A2D_TOKEN_KEY);
    window.location.href = '/login';
}

// API helpers
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

// Export JSON file
async function doExport() {
    try {
        await apiDownload('/export?format=json&includeDeleted=false');
        toast('Export downloaded');
    } catch (e) {
        toast(`Export failed: ${e.message}`);
    }
}

// Import JSON file
async function doImportFile(file) {
    try {
        const report = await apiPostImport(file);
        toast(`Import complete — created: ${report.wouldCreate}, updated: ${report.wouldUpdate}, skipped: ${report.wouldSkip}. Android will sync on next sync.`);
        loadTasks();
    } catch (e) {
        toast(`Import failed: ${e.message}`);
    }
}

// Toast notification
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

// Import file handling
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

// Init page
document.addEventListener('DOMContentLoaded', async () => {
    const token = getToken();
    if (!token) {
        window.location.href = '/login';
        return;
    }
    await loadTasks();
    await fetchUserInfo();
});