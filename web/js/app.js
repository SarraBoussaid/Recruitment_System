const API = '/api';

let jobs = [];
let companies = [];
let currentUser = null;
let activeType = 'all';
let searchKeyword = '';
let searchLocation = '';

const jobsGrid = document.getElementById('jobs-grid');
const companiesGrid = document.getElementById('companies-grid');
const emptyState = document.getElementById('empty-state');
const modalOverlay = document.getElementById('modal-overlay');
const modalDialog = document.querySelector('.modal');
const modalBody = document.getElementById('modal-body');
const toast = document.getElementById('toast');
const navActions = document.getElementById('nav-actions');
const navNotifications = document.getElementById('nav-notifications');
const notifBell = document.getElementById('notif-bell');
const notifBadge = document.getElementById('notif-badge');
const notifDropdown = document.getElementById('notif-dropdown');
const notifList = document.getElementById('notif-list');
const homeView = document.getElementById('home-view');
const dashboardView = document.getElementById('dashboard-view');
const dashboardContent = document.getElementById('dashboard-content');
const dashboardTitle = document.getElementById('dashboard-title');
const messagesView = document.getElementById('messages-view');
const messagesInbox = document.getElementById('messages-inbox');
const messagesThread = document.getElementById('messages-thread');

let notifications = [];
let unreadCount = 0;
let activeThread = { applicationId: null, conversationId: null };
let inboxItems = [];

function threadKey(thread) {
    if (!thread) return '';
    if (thread.applicationId) return `app:${thread.applicationId}`;
    if (thread.conversationId) return `conv:${thread.conversationId}`;
    return '';
}

function isSameThread(a, b) {
    return threadKey(a) === threadKey(b) && threadKey(a) !== '';
}

function parseThreadFromElement(el) {
    if (el.dataset.applicationId) {
        return { applicationId: Number(el.dataset.applicationId), conversationId: null };
    }
    if (el.dataset.conversationId) {
        return { applicationId: null, conversationId: Number(el.dataset.conversationId) };
    }
    if (el.dataset.viewThread) {
        return { applicationId: Number(el.dataset.viewThread), conversationId: null };
    }
    return null;
}

const PHONE_PATTERN = /^\+216\d{8}$/;
const PHONE_MESSAGE = 'Phone must be +216 followed by 8 digits (e.g. +21612345678)';

function isValidPhone(phone) {
    if (!phone) return true;
    return PHONE_PATTERN.test(phone.trim());
}

function normalizePhoneInput(value) {
    let digits = value.replace(/\D/g, '');
    if (digits.startsWith('216')) {
        digits = digits.slice(3);
    }
    digits = digits.slice(0, 8);
    return digits.length > 0 ? `+216${digits}` : '';
}
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
}

function getInitials(name) {
    return name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}

function formatType(type) {
    return type.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join('-');
}

function showToast(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle('toast-error', isError);
    toast.classList.remove('hidden');
    setTimeout(() => toast.classList.add('hidden'), 3500);
}

function showLoading(container, message = 'Loading...') {
    container.innerHTML = `<p class="loading-state">${message}</p>`;
}

async function apiFetch(path, options = {}) {
    let response;
    try {
        response = await fetch(`${API}${path}`, {
            credentials: 'include',
            headers: { 'Content-Type': 'application/json', ...options.headers },
            ...options
        });
    } catch {
        showServerBanner();
        throw new Error('Cannot connect to server. Start RecruitmentSystemApplication.java and open http://localhost:8082');
    }

    if (response.status === 204) {
        hideServerBanner();
        return null;
    }

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(data.error || 'Request failed.');
    }

    hideServerBanner();
    return data;
}

function showServerBanner() {
    document.getElementById('server-banner')?.classList.remove('hidden');
}

function hideServerBanner() {
    document.getElementById('server-banner')?.classList.add('hidden');
}

async function apiUpload(path, formData) {
    let response;
    try {
        response = await fetch(`${API}${path}`, {
            method: 'POST',
            credentials: 'include',
            body: formData
        });
    } catch {
        showServerBanner();
        throw new Error('Cannot connect to server.');
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || 'Upload failed.');
    hideServerBanner();
    return data;
}

function resumeLinkHtml(url, label = 'View resume') {
    if (!url) return '<span class="text-muted">No CV uploaded yet</span>';
    const text = url.startsWith('/uploads/') ? 'View PDF resume' : label;
    return `<a href="${escapeHtml(url)}" target="_blank" rel="noopener">${text}</a>`;
}

function isDashboardRoute() {
    return location.hash === '#dashboard';
}

function isMessagesRoute() {
    return location.hash === '#messages';
}

function showHomeView() {
    homeView?.classList.remove('hidden');
    dashboardView?.classList.add('hidden');
    messagesView?.classList.add('hidden');
}

function showDashboardView() {
    if (!currentUser) {
        openModal(loginFormHtml('Please log in to access your dashboard.'));
        location.hash = '#top';
        return;
    }
    homeView?.classList.add('hidden');
    dashboardView?.classList.remove('hidden');
    messagesView?.classList.add('hidden');
    loadDashboard();
}

function showMessagesView() {
    if (!currentUser) {
        openModal(loginFormHtml('Please log in to view your messages.'));
        location.hash = '#top';
        return;
    }
    homeView?.classList.add('hidden');
    dashboardView?.classList.add('hidden');
    messagesView?.classList.remove('hidden');
    loadMessagesPage();
}

function handleRoute() {
    const isCompany = currentUser?.role === 'COMPANY';
    const isCandidate = currentUser?.role === 'CANDIDATE';

    document.querySelectorAll('.nav-candidate-only').forEach(el => {
        el.classList.toggle('hidden', isCompany);
    });
    document.querySelector('.nav-company-only')?.classList.toggle('hidden', !isCompany);
    document.querySelector('.nav-dashboard-link')?.classList.toggle('hidden', !currentUser || isCompany);
    document.querySelector('.nav-messages-link')?.classList.toggle('hidden', !currentUser);

    if (isCompany) {
        const browseHashes = ['', '#top', '#jobs', '#companies', '#how-it-works', '#employers'];
        if (browseHashes.includes(location.hash)) {
            location.hash = '#dashboard';
            return;
        }
    }

    if (isDashboardRoute()) {
        showDashboardView();
    } else if (isMessagesRoute()) {
        showMessagesView();
    } else {
        showHomeView();
    }
}

async function loadNotifications() {
    if (!currentUser) {
        notifications = [];
        unreadCount = 0;
        renderNotificationBell();
        return;
    }
    try {
        const [list, countData] = await Promise.all([
            apiFetch('/notifications'),
            apiFetch('/notifications/unread-count')
        ]);
        notifications = list;
        unreadCount = countData.count;
        renderNotificationBell();
    } catch {
        notifications = [];
        unreadCount = 0;
        renderNotificationBell();
    }
}

function renderNotificationBell() {
    if (!navNotifications) return;
    navNotifications.classList.toggle('hidden', !currentUser);
    if (!currentUser) return;

    if (unreadCount > 0) {
        notifBadge.textContent = unreadCount > 9 ? '9+' : unreadCount;
        notifBadge.classList.remove('hidden');
    } else {
        notifBadge.classList.add('hidden');
    }

    if (notifications.length === 0) {
        notifList.innerHTML = '<p class="notif-empty">No notifications yet.</p>';
        return;
    }

    notifList.innerHTML = notifications.map(n => `
        <button class="notif-item ${n.read ? '' : 'unread'}" data-notif-id="${n.id}" data-notif-related="${n.relatedId ?? ''}" data-notif-type="${escapeHtml(n.type)}">
            <strong>${escapeHtml(n.title)}</strong>
            <p>${escapeHtml(n.body)}</p>
            <span class="notif-time">${escapeHtml(n.createdAt)}</span>
        </button>
    `).join('');
}

async function markNotificationRead(id) {
    try {
        await apiFetch(`/notifications/${id}/read`, { method: 'PATCH' });
        await loadNotifications();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function markAllNotificationsRead() {
    try {
        await apiFetch('/notifications/read-all', { method: 'PATCH' });
        await loadNotifications();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function uploadResumeFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    const result = await apiUpload('/candidates/resume', formData);
    currentUser = await apiFetch('/auth/me');
    return result;
}

async function loadDashboard() {
    if (!currentUser) return;
    showLoading(dashboardContent);
    try {
        if (currentUser.role === 'CANDIDATE') {
            dashboardTitle.textContent = `Hi, ${currentUser.displayName}`;
            const data = await apiFetch('/dashboard/candidate');
            dashboardContent.innerHTML = renderCandidateDashboard(data);
        } else {
            dashboardTitle.textContent = 'Candidates for your company';
            const data = await apiFetch('/dashboard/company');
            dashboardContent.innerHTML = renderCompanyDashboard(data);
        }
        bindDashboardEvents();
    } catch (err) {
        dashboardContent.innerHTML = `<p class="loading-state">${escapeHtml(err.message)}</p>`;
    }
}

function renderCandidateDashboard(data) {
    const statusIcons = {
        pending: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
        reviewed: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>',
        interview: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>',
        accepted: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
        rejected: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>'
    };
    const statusCards = ['pending', 'reviewed', 'interview', 'accepted', 'rejected'].map(status => `
        <div class="dash-stat-card dash-stat-${status}">
            <div class="dsc-icon">${statusIcons[status]}</div>
            <span class="dash-stat-num">${data.statusCounts[status] || 0}</span>
            <span class="dash-stat-label">${escapeHtml(status)}</span>
        </div>
    `).join('');

    const appsHtml = data.applications.length === 0
        ? `<div class="empty-state-block"><div class="esb-icon"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div><p>No applications yet. Browse jobs and apply!</p></div>`
        : `<div class="app-list">${data.applications.map(app => `
            <div class="app-item">
                <div class="app-item-left">
                    <div class="app-avatar">${escapeHtml(getInitials(app.company))}</div>
                    <div>
                        <p class="app-title">${escapeHtml(app.jobTitle)}</p>
                        <p class="app-company">${escapeHtml(app.company)} · ${escapeHtml(app.appliedAt)}</p>
                    </div>
                </div>
                <div class="app-item-right">
                    <span class="badge badge-${escapeHtml(app.status)}">${escapeHtml(app.status)}</span>
                    <button class="btn btn-ghost btn-sm" data-application-id="${app.id}">Messages</button>
                </div>
            </div>
        `).join('')}</div>`;

    return `
        <div class="dash-stats">${statusCards}</div>
        <div class="dash-grid">
            <section class="dash-panel">
                <div class="dp-header">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                    <h3>My Profile &amp; CV</h3>
                </div>
                <p class="dp-meta">${escapeHtml(currentUser.email)}</p>
                <form id="profile-form" class="profile-form">
                    <div class="form-group">
                        <label>Phone</label>
                        <input type="tel" name="phone" value="${escapeHtml(data.phone || '')}" placeholder="+216 XX XXX XXX">
                    </div>
                    <button type="submit" class="btn btn-primary btn-sm">Save changes</button>
                </form>
                <div class="cv-upload-box">
                    <div class="cv-row">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                        <span>${resumeLinkHtml(data.resumeUrl)}</span>
                    </div>
                    <label class="file-upload-label">
                        <input type="file" id="cv-upload" accept=".pdf,application/pdf" hidden>
                        <span class="btn btn-secondary btn-sm">Upload PDF (max 5 MB)</span>
                    </label>
                </div>
            </section>
            <section class="dash-panel">
                <div class="dp-header">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                    <h3>My Applications</h3>
                </div>
                ${appsHtml}
            </section>
            <section class="dash-panel dp-messages-prompt">
                <div class="dp-header">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                    <h3>Messages ${data.unreadMessages > 0 ? `<span class="badge-count">${data.unreadMessages}</span>` : ''}</h3>
                </div>
                <p class="dp-meta">Read and reply to employer messages from your inbox.</p>
                <a href="#messages" class="btn btn-primary btn-sm">Open Inbox</a>
            </section>
        </div>
    `;
}

function suggestedCandidatesHtml(candidates) {
    if (candidates.length === 0) {
        return `<div class="empty-state-block">
            <div class="esb-icon">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </div>
            <p>No candidate suggestions yet. Post a job to receive applicants.</p>
        </div>`;
    }
    return `<div class="candidate-grid">${candidates.map(c => `
        <article class="candidate-card">
            <div class="cand-card-top">
                <div class="cand-avatar">${escapeHtml(getInitials(c.name))}</div>
                <div class="cand-info">
                    <h4 class="cand-name">${escapeHtml(c.name)}</h4>
                    <p class="cand-reason">${escapeHtml(c.suggestionReason)}</p>
                </div>
                ${c.status ? `<span class="badge badge-${escapeHtml(c.status)}">${escapeHtml(c.status)}</span>` : ''}
            </div>
            <div class="cand-meta">
                ${c.matchedJobTitle ? `<span class="cand-meta-item"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>${escapeHtml(c.matchedJobTitle)}</span>` : ''}
                <span class="cand-meta-item"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>${escapeHtml(c.email)}</span>
                ${c.phone ? `<span class="cand-meta-item"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 13a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.6 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.91 9.56a16 16 0 0 0 6.29 6.29l1.93-1.94a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/></svg>${escapeHtml(c.phone)}</span>` : ''}
                ${c.resumeUrl ? `<span class="cand-meta-item">${resumeLinkHtml(c.resumeUrl)}</span>` : ''}
            </div>
            <div class="cand-actions">
                ${c.applicationId
                    ? `<button class="btn btn-primary btn-sm" data-application-id="${c.applicationId}"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>Message</button>`
                    : `<button class="btn btn-primary btn-sm" data-contact-candidate="${c.candidateId}" data-candidate-name="${escapeHtml(c.name)}"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>Contact</button>`}
            </div>
        </article>
    `).join('')}</div>`;
}

function renderCompanyDashboard(data) {
    const suggestionsHtml = suggestedCandidatesHtml(data.suggestedCandidates);

    return `
        <div class="dash-stats">
            <div class="dash-stat-card">
                <div class="dsc-icon" style="--ico-clr:#4f46e5"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></div>
                <span class="dash-stat-num">${data.suggestedCandidates.length}</span>
                <span class="dash-stat-label">Candidates</span>
            </div>
            <div class="dash-stat-card">
                <div class="dsc-icon" style="--ico-clr:#0d9488"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg></div>
                <span class="dash-stat-num">${data.openJobs}</span>
                <span class="dash-stat-label">Open Jobs</span>
            </div>
            <div class="dash-stat-card">
                <div class="dsc-icon" style="--ico-clr:#f59e0b"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <span class="dash-stat-num">${data.pendingApplicants}</span>
                <span class="dash-stat-label">Pending Review</span>
            </div>
            <div class="dash-stat-card">
                <div class="dsc-icon" style="--ico-clr:#6366f1"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg></div>
                <span class="dash-stat-num">${data.unreadNotifications}</span>
                <span class="dash-stat-label">Notifications</span>
            </div>
            <div class="dash-stat-card">
                <div class="dsc-icon" style="--ico-clr:#10b981"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg></div>
                <span class="dash-stat-num">${data.unreadMessages}</span>
                <span class="dash-stat-label">New Messages</span>
            </div>
        </div>
        <div class="dash-quick-actions">
            <button class="btn btn-primary" data-open-modal="post-job">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                Post a Job
            </button>
            <button class="btn btn-secondary" data-open-modal="manage">Manage Applicants</button>
            <a href="#messages" class="btn btn-secondary">
                Messages
                ${data.unreadMessages > 0 ? `<span class="badge-count">${data.unreadMessages}</span>` : ''}
            </a>
        </div>
        <section class="dash-section">
            <div class="dp-header">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                <h3>Suggested Candidates</h3>
            </div>
            <p class="dp-meta">People you can hire — click <strong>Contact</strong> to message anyone, even without a prior application.</p>
            ${suggestionsHtml}
        </section>
    `;
}

async function loadInboxList() {
    try {
        inboxItems = await apiFetch('/messages/inbox');
        return inboxItems;
    } catch (err) {
        inboxItems = [];
        showToast(err.message, true);
        return [];
    }
}

function renderInboxList(container, items, selectedThread = null) {
    if (!container) return;
    if (items.length === 0) {
        const emptyText = currentUser?.role === 'COMPANY'
            ? 'No messages yet. Contact a suggested candidate or someone from Manage applicants.'
            : 'No messages yet. Employers will contact you here after reviewing your application.';
        container.innerHTML = `<div class="inbox-empty"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg><p>${emptyText}</p></div>`;
        return;
    }
    container.innerHTML = `<ul class="inbox-list">${items.map(item => {
        const thread = { applicationId: item.applicationId, conversationId: item.conversationId };
        const active = isSameThread(thread, selectedThread);
        const dataAttrs = item.applicationId
            ? `data-application-id="${item.applicationId}"`
            : `data-conversation-id="${item.conversationId}"`;
        const initials = getInitials(item.contactName);
        return `
        <li>
            <button type="button" class="inbox-btn ${active ? 'active' : ''}" ${dataAttrs}>
                <div class="ib-avatar">${escapeHtml(initials)}</div>
                <div class="ib-content">
                    <div class="ib-row">
                        <span class="ib-name">${escapeHtml(item.contactName)}</span>
                        <span class="ib-time">${escapeHtml(item.lastMessageAt)}</span>
                    </div>
                    <p class="ib-job">${escapeHtml(item.jobTitle)}</p>
                    <p class="ib-preview">${escapeHtml(item.lastMessage)}</p>
                </div>
            </button>
        </li>`;
    }).join('')}</ul>`;
}

function renderThreadPanel(thread, messages) {
    if (!messagesThread) return;
    if (!thread || !threadKey(thread)) {
        messagesThread.innerHTML = `<div class="messages-empty-state"><svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg><p>Select a conversation to start reading.</p></div>`;
        return;
    }
    const headerInfo = messages.length > 0 ? messages[0] : null;
    const headerHtml = headerInfo
        ? `<div class="thread-header">
            <div class="th-avatar">${escapeHtml(getInitials(headerInfo.senderRole === currentUser.role ? (messages.find(m=>m.senderRole!==currentUser.role)?.senderName||'?') : headerInfo.senderName))}</div>
            <div>
                <p class="th-name">${escapeHtml(headerInfo.jobTitle)}</p>
                <p class="th-sub">${escapeHtml(headerInfo.companyName)}</p>
            </div>
           </div>`
        : `<div class="thread-header"><p class="th-name">Conversation</p></div>`;

    const threadHtml = messages.length === 0
        ? '<div class="thread-no-msgs"><p>No messages yet. Send the first message below.</p></div>'
        : `<div class="thread-bubbles">${messages.map(m => {
            const mine = m.senderRole === currentUser.role;
            return `<div class="bubble-row ${mine ? 'bubble-mine' : 'bubble-theirs'}">
                <div class="bubble-meta"><span>${escapeHtml(m.senderName)}</span><span>${escapeHtml(m.sentAt)}</span></div>
                <div class="bubble ${mine ? 'bubble-sent' : 'bubble-received'}">${escapeHtml(m.message)}</div>
            </div>`;
        }).join('')}</div>`;

    const formAttrs = thread.applicationId
        ? `data-application-id="${thread.applicationId}"`
        : `data-conversation-id="${thread.conversationId}"`;

    messagesThread.innerHTML = `
        ${headerHtml}
        <div class="thread-scroll-area">${threadHtml}</div>
        <form class="reply-form" id="reply-form" ${formAttrs}>
            <input type="text" name="message" placeholder="Write a reply…" required autocomplete="off">
            <button type="submit" class="btn btn-primary btn-sm">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                Send
            </button>
        </form>
    `;
}

async function loadThread(thread, { inModal = false } = {}) {
    if (!thread || !threadKey(thread)) return;
    activeThread = thread;
    const target = inModal ? document.getElementById('thread-content') : messagesThread;
    if (!target) return;

    if (inModal) {
        target.innerHTML = '<p class="loading-state">Loading...</p>';
    }

    const path = thread.applicationId
        ? `/messages/application/${thread.applicationId}`
        : `/messages/conversation/${thread.conversationId}`;

    try {
        const messages = await apiFetch(path);
        if (inModal) {
            const formAttrs = thread.applicationId
                ? `data-application-id="${thread.applicationId}"`
                : `data-conversation-id="${thread.conversationId}"`;
            target.innerHTML = `
                <div id="modal-thread-body"></div>
                <form class="reply-form" id="reply-form" ${formAttrs}>
                    <input type="text" name="message" placeholder="Write your reply..." required autocomplete="off">
                    <button type="submit" class="btn btn-primary btn-sm">Send</button>
                </form>
            `;
            const body = document.getElementById('modal-thread-body');
            body.innerHTML = messages.length === 0
                ? '<p class="loading-state">No messages yet.</p>'
                : `<p class="subtitle">${escapeHtml(messages[0]?.jobTitle || '')} · ${escapeHtml(messages[0]?.companyName || '')}</p>
                   <ul class="thread-list">${messages.map(m => `
                       <li class="thread-item ${m.senderRole === currentUser.role ? 'thread-mine' : 'thread-theirs'}">
                           <strong>${escapeHtml(m.senderName)}</strong>
                           <span class="notif-time">${escapeHtml(m.sentAt)}</span>
                           <p>${escapeHtml(m.message)}</p>
                       </li>
                   `).join('')}</ul>`;
        } else {
            renderThreadPanel(thread, messages);
            renderInboxList(messagesInbox, inboxItems, thread);
        }
    } catch (err) {
        target.innerHTML = `<p class="loading-state">${escapeHtml(err.message)}</p>`;
    }
}

async function loadMessagesPage() {
    showLoading(messagesInbox, 'Loading conversations...');
    messagesThread.innerHTML = '<p class="messages-placeholder">Select a conversation to read and reply.</p>';
    const items = await loadInboxList();
    renderInboxList(messagesInbox, items, activeThread);
    if (threadKey(activeThread)) {
        await loadThread(activeThread);
    }
}

function openThread(thread) {
    if (isMessagesRoute()) {
        activeThread = thread;
        loadThread(thread);
    } else {
        location.hash = '#messages';
        activeThread = thread;
    }
}

async function openMessageThread(applicationId) {
    const thread = { applicationId, conversationId: null };
    if (isMessagesRoute()) {
        activeThread = thread;
        await loadMessagesPage();
        return;
    }
    openModal(`<h2 id="modal-title">Messages</h2><div id="thread-content"><p class="loading-state">Loading...</p></div>`, true);
    await loadThread(thread, { inModal: true });
}

function contactCandidateFormHtml(candidateId, candidateName) {
    return `
        <h2 id="modal-title">Contact ${escapeHtml(candidateName)}</h2>
        <p class="subtitle">Send a message even if they have not applied to your jobs yet.</p>
        <form id="outreach-form" data-candidate-id="${candidateId}">
            <div class="form-group">
                <label>Your message</label>
                <textarea name="message" rows="4" required placeholder="Introduce your company and the opportunity..."></textarea>
            </div>
            <button type="submit" class="btn btn-primary">Send message</button>
        </form>
    `;
}

async function sendReply(thread, message) {
    const path = thread.applicationId
        ? `/messages/application/${thread.applicationId}`
        : `/messages/conversation/${thread.conversationId}`;
    await apiFetch(path, {
        method: 'POST',
        body: JSON.stringify({ message })
    });
}

function bindDashboardEvents() {
    dashboardContent.querySelectorAll('[data-open-modal]').forEach(btn => {
        btn.addEventListener('click', () => handleModalOpen(btn.dataset.openModal));
    });

    dashboardContent.querySelectorAll('[data-application-id]').forEach(btn => {
        btn.addEventListener('click', () => {
            openThread({ applicationId: Number(btn.dataset.applicationId), conversationId: null });
        });
    });

    dashboardContent.querySelectorAll('[data-contact-candidate]').forEach(btn => {
        btn.addEventListener('click', () => {
            openModal(contactCandidateFormHtml(
                Number(btn.dataset.contactCandidate),
                btn.dataset.candidateName
            ));
        });
    });
}

async function checkServer() {
    try {
        await apiFetch('/health');
        hideServerBanner();
    } catch {
        showServerBanner();
    }
}

function renderNav() {
    if (!navActions) return;

    if (!currentUser) {
        navActions.innerHTML = `
            <button class="btn btn-ghost" data-open-modal="login">Login</button>
            <button class="btn btn-primary" data-open-modal="register">Create account</button>
        `;
    } else if (currentUser.role === 'CANDIDATE') {
        navActions.innerHTML = `
            <span class="user-pill">Hi, ${escapeHtml(currentUser.displayName)}</span>
            <a href="#dashboard" class="btn btn-ghost">Dashboard</a>
            <a href="#messages" class="btn btn-ghost">Messages</a>
            <button class="btn btn-primary" data-action="logout">Logout</button>
        `;
    } else {
        navActions.innerHTML = `
            <span class="user-pill">${escapeHtml(currentUser.displayName)}</span>
            <button class="btn btn-ghost" data-open-modal="post-job">Post job</button>
            <a href="#dashboard" class="btn btn-ghost">Find candidates</a>
            <a href="#messages" class="btn btn-ghost">Messages</a>
            <button class="btn btn-primary" data-action="logout">Logout</button>
        `;
    }

    navActions.querySelectorAll('[data-open-modal]').forEach(btn => {
        btn.addEventListener('click', () => handleModalOpen(btn.dataset.openModal));
    });
    navActions.querySelector('[data-action="logout"]')?.addEventListener('click', logout);
    handleRoute();
    loadNotifications();
}

async function loadUser() {
    try {
        currentUser = await apiFetch('/auth/me');
    } catch {
        currentUser = null;
    }
    renderNav();
}

async function logout() {
    try {
        await apiFetch('/auth/logout', { method: 'POST' });
        currentUser = null;
        notifications = [];
        unreadCount = 0;
        renderNav();
        showHomeView();
        location.hash = '#top';
        showToast('Logged out successfully.');
    } catch (err) {
        showToast(err.message, true);
    }
}

function requireCandidate() {
    if (!currentUser || currentUser.role !== 'CANDIDATE') {
        openModal(loginFormHtml('Please log in as a candidate to apply.'));
        return false;
    }
    return true;
}

function requireCompany() {
    if (!currentUser || currentUser.role !== 'COMPANY') {
        openModal(loginFormHtml('Please log in as a company to access this feature.'));
        return false;
    }
    return true;
}

async function loadStats() {
    try {
        const stats = await apiFetch('/stats');
        animateStat('stat-jobs', stats.openJobs);
        animateStat('stat-companies', stats.companies);
        animateStat('stat-applications', stats.applicationsThisWeek);
        const heroApps = document.getElementById('hero-applications');
        if (heroApps) heroApps.textContent = stats.applicationsThisWeek;
    } catch {}
}

function animateStat(id, target) {
    const el = document.getElementById(id);
    if (!el) return;
    const duration = 1200;
    const start = performance.now();
    const step = (now) => {
        const progress = Math.min((now - start) / duration, 1);
        const ease = 1 - Math.pow(1 - progress, 4);
        el.textContent = Math.round(ease * target);
        if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
}

async function loadJobs() {
    showLoading(jobsGrid);
    try {
        const params = new URLSearchParams();
        if (searchKeyword) params.set('keyword', searchKeyword);
        if (searchLocation) params.set('location', searchLocation);
        if (activeType !== 'all') params.set('type', activeType);

        const query = params.toString();
        jobs = await apiFetch(`/jobs${query ? `?${query}` : ''}`);
        renderJobs();
        loadStats();
    } catch (err) {
        jobsGrid.innerHTML = '';
        emptyState.innerHTML = `<strong>Cannot connect to server.</strong><br>Open <a href="http://localhost:8082">http://localhost:8082</a> after starting the app.`;
        emptyState.classList.remove('hidden');
        showToast(err.message, true);
    }
}

async function loadCompanies() {
    showLoading(companiesGrid);
    try {
        companies = await apiFetch('/companies');
        renderCompanies();
    } catch {
        companiesGrid.innerHTML = `<p class="loading-state">Could not load companies.</p>`;
    }
}

function renderJobs() {
    jobsGrid.innerHTML = '';
    if (jobs.length === 0) {
        emptyState.textContent = 'No jobs match your search filters. Try searching with different keywords or locations.';
        emptyState.classList.remove('hidden');
        return;
    }
    emptyState.classList.add('hidden');

    jobs.forEach(job => {
        const card = document.createElement('article');
        card.className = 'job-card';
        const initials = getInitials(job.company);
        card.innerHTML = `
            <div class="jc-top">
                <div class="jc-avatar">${escapeHtml(initials)}</div>
                <div class="jc-company-meta">
                    <span class="jc-company-name">${escapeHtml(job.company)}</span>
                    <span class="jc-location">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 10c0 6-8 12-8 12s-8-6-8-10a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>
                        ${escapeHtml(job.location)}
                    </span>
                </div>
                <span class="jc-type-tag">${escapeHtml(formatType(job.type))}</span>
            </div>
            <h3 class="jc-title">${escapeHtml(job.title)}</h3>
            <p class="jc-desc">${escapeHtml(job.description)}</p>
            <div class="jc-footer">
                <div class="jc-salary">
                    <span class="jc-salary-amount">${escapeHtml(job.salary || 'Negotiable')}</span>
                </div>
                <div class="jc-actions">
                    <button class="btn btn-secondary btn-sm" data-details="${job.id}">Details</button>
                    <button class="btn btn-primary btn-sm" data-apply="${job.id}">Apply</button>
                </div>
            </div>
        `;
        jobsGrid.appendChild(card);
    });
}

function renderCompanies() {
    companiesGrid.innerHTML = '';
    companies.forEach(company => {
        const card = document.createElement('div');
        card.className = 'company-card';
        const initials = getInitials(company.name);
        card.innerHTML = `
            <div class="co-card-inner">
                <div class="co-avatar">${escapeHtml(initials)}</div>
                <div class="co-info">
                    <h3 class="co-name">${escapeHtml(company.name)}</h3>
                    <p class="co-industry">${escapeHtml(company.industry || 'Technology Solutions')}</p>
                </div>
            </div>
            <div class="co-footer">
                <span class="co-jobs-badge">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
                    ${company.jobs} open ${company.jobs !== 1 ? 'positions' : 'position'}
                </span>
                <button class="btn btn-ghost btn-sm co-view-btn" data-company-name="${escapeHtml(company.name)}">View →</button>
            </div>
        `;
        companiesGrid.appendChild(card);
    });
}

function openModal(content, wide = false) {
    modalBody.innerHTML = content;
    modalDialog.classList.toggle('wide', wide);
    modalOverlay.classList.remove('hidden');
    modalOverlay.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
}

function closeModal() {
    modalOverlay.classList.add('hidden');
    modalOverlay.setAttribute('aria-hidden', 'true');
    modalDialog.classList.remove('wide');
    document.body.style.overflow = '';
}

function loginFormHtml(subtitle = 'Sign in to your account.') {
    return `
        <h2 id="modal-title">Welcome back</h2>
        <p class="subtitle">${subtitle}</p>
        <form id="login-form">
            <div class="form-group">
                <label for="login-email">Email</label>
                <input type="email" id="login-email" name="email" required placeholder="you@email.com">
            </div>
            <div class="form-group">
                <label for="login-password">Password</label>
                <input type="password" id="login-password" name="password" required placeholder="Your password">
            </div>
            <button type="submit" class="btn btn-primary">Sign in</button>
        </form>
        <p class="subtitle" style="margin-top:1rem">No account? <button type="button" class="btn btn-ghost btn-sm" data-switch-modal="register">Create one</button></p>
    `;
}

function registerFormHtml() {
    return `
        <h2 id="modal-title">Create an account</h2>
        <p class="subtitle">Join as a candidate or register your company.</p>
        <div class="auth-tabs">
            <button type="button" class="auth-tab active" data-register-tab="candidate">Candidate</button>
            <button type="button" class="auth-tab" data-register-tab="company">Company</button>
        </div>
        <div id="register-panel">
            ${candidateRegisterFormHtml()}
        </div>
        <p class="subtitle" style="margin-top:1rem">Already have an account? <button type="button" class="btn btn-ghost btn-sm" data-switch-modal="login">Sign in</button></p>
    `;
}

function candidateRegisterFormHtml() {
    return `
        <form id="register-candidate-form">
            <div class="form-row">
                <div class="form-group">
                    <label>First name</label>
                    <input type="text" name="firstName" required placeholder="Sarra">
                </div>
                <div class="form-group">
                    <label>Last name</label>
                    <input type="text" name="lastName" required placeholder="Ben Salem">
                </div>
            </div>
            <div class="form-group">
                <label>Email</label>
                <input type="email" name="email" required placeholder="sarra@example.com">
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" name="password" required minlength="6" placeholder="At least 6 characters">
            </div>
            <div class="form-group">
                <label>Phone (optional)</label>
                <input type="tel" name="phone" placeholder="+21612345678" pattern="\\+216[0-9]{8}" maxlength="12" title="${PHONE_MESSAGE}">
                <small class="field-hint">Format: +216 then 8 digits</small>
            </div>
            <button type="submit" class="btn btn-primary">Create candidate account</button>
        </form>
    `;
}

function companyRegisterFormHtml() {
    return `
        <form id="register-company-form">
            <div class="form-group">
                <label>Company name</label>
                <input type="text" name="companyName" required placeholder="Carthage Data Systems">
            </div>
            <div class="form-group">
                <label>Industry (optional)</label>
                <input type="text" name="industry" placeholder="Software Engineering, Telecommunications...">
            </div>
            <div class="form-group">
                <label>Work email</label>
                <input type="email" name="email" required placeholder="careers@carthagedata.tn">
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" name="password" required minlength="6" placeholder="At least 6 characters">
            </div>
            <button type="submit" class="btn btn-primary">Create company account</button>
        </form>
    `;
}

function applyFormHtml(job) {
    const jobSelect = job
        ? `<input type="hidden" name="jobId" value="${job.id}">`
        : `<div class="form-group">
               <label>Position</label>
               <select name="jobId" required>
                   ${jobs.map(j => `<option value="${j.id}">${escapeHtml(j.title)} — ${escapeHtml(j.company)}</option>`).join('')}
               </select>
           </div>`;

    const resumeNote = currentUser.resumeUrl
        ? `<p class="field-hint">Your profile CV will be attached: ${resumeLinkHtml(currentUser.resumeUrl)}</p>`
        : `<p class="field-hint">No CV on profile yet. <a href="#dashboard">Upload one in your dashboard</a> or add a link below.</p>`;

    return `
        <h2 id="modal-title">Apply for a role</h2>
        <p class="subtitle">Applying as <strong>${escapeHtml(currentUser.displayName)}</strong> (${escapeHtml(currentUser.email)})</p>
        <form id="apply-form">
            ${jobSelect}
            <div class="form-group">
                <label>Phone (optional)</label>
                <input type="tel" name="phone" value="${escapeHtml(currentUser.phone || '')}" placeholder="+21612345678" pattern="\\+216[0-9]{8}" maxlength="12" title="${PHONE_MESSAGE}">
                <small class="field-hint">Format: +216 then 8 digits</small>
            </div>
            ${resumeNote}
            <div class="form-group">
                <label>Resume link (optional, if no PDF)</label>
                <input type="text" name="resume" value="${escapeHtml(currentUser.resumeUrl?.startsWith('http') ? currentUser.resumeUrl : '')}" placeholder="https://linkedin.com/in/yourprofile">
            </div>
            <button type="submit" class="btn btn-primary">Submit application</button>
        </form>
    `;
}

function postJobFormHtml() {
    return `
        <h2 id="modal-title">Post a new job</h2>
        <p class="subtitle">Publishing as <strong>${escapeHtml(currentUser.companyName)}</strong></p>
        <form id="post-job-form">
            <div class="form-group">
                <label>Job title</label>
                <input type="text" name="title" required placeholder="Senior Java Software Engineer">
            </div>
            <div class="form-group">
                <label>Description</label>
                <textarea name="description" rows="4" required placeholder="Describe the role, responsibilities, and requirements."></textarea>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>Location</label>
                    <input type="text" name="location" required placeholder="Tunis, Tunisia or Remote">
                </div>
                <div class="form-group">
                    <label>Job type</label>
                    <select name="type" required>
                        <option value="full-time">Full-time</option>
                        <option value="part-time">Part-time</option>
                        <option value="contract">Contract</option>
                        <option value="internship">Internship</option>
                        <option value="remote">Remote</option>
                    </select>
                </div>
            </div>
            <div class="form-group">
                <label>Salary (optional)</label>
                <input type="text" name="salary" placeholder="2,500 - 3,500 TND / month">
            </div>
            <button type="submit" class="btn btn-primary">Publish job</button>
        </form>
    `;
}

function applicationsListHtml(applications) {
    if (applications.length === 0) {
        return '<p class="loading-state">No applications yet. Browse jobs and apply!</p>';
    }
    return `
        <ul class="application-list">
            ${applications.map(app => `
                <li class="application-item">
                    <h4>${escapeHtml(app.jobTitle)}</h4>
                    <p>${escapeHtml(app.company)} · Applied ${escapeHtml(app.appliedAt)}</p>
                    <span class="status-badge status-${escapeHtml(app.status)}">${escapeHtml(app.status)}</span>
                </li>
            `).join('')}
        </ul>
    `;
}

function manageApplicationsHtml(applications) {
    if (applications.length === 0) {
        return '<p class="loading-state">No applications yet. Post a job to start receiving candidates.</p>';
    }
    const statuses = ['pending', 'reviewed', 'interview', 'accepted', 'rejected'];
    return `
        <div class="manage-list">
            ${applications.map(app => `
                <article class="manage-item">
                    <div class="manage-item-header">
                        <div>
                            <h4>${escapeHtml(app.candidateName)}</h4>
                            <p class="meta">${escapeHtml(app.jobTitle)}</p>
                        </div>
                        <select class="status-select" data-status-id="${app.id}">
                            ${statuses.map(s => `<option value="${s}" ${app.status === s ? 'selected' : ''}>${escapeHtml(s)}</option>`).join('')}
                        </select>
                    </div>
                    <p class="meta">
                        <a href="mailto:${escapeHtml(app.candidateEmail)}">${escapeHtml(app.candidateEmail)}</a>
                        ${app.candidatePhone ? ` · ${escapeHtml(app.candidatePhone)}` : ''}
                        · Applied ${escapeHtml(app.appliedAt)}
                    </p>
                    ${app.resumeUrl ? `<p class="meta">${resumeLinkHtml(app.resumeUrl)}</p>` : ''}
                    <div class="manage-item-actions">
                        <button class="btn btn-ghost btn-sm" data-application-id="${app.id}">View messages</button>
                    </div>
                    <div class="contact-box">
                        <input type="text" placeholder="Write a message to this candidate..." data-contact-input="${app.id}">
                        <button class="btn btn-primary btn-sm" data-contact-send="${app.id}">Contact</button>
                    </div>
                </article>
            `).join('')}
        </div>
    `;
}

function jobDetailHtml(job) {
    return `
        <h2 id="modal-title">${escapeHtml(job.title)}</h2>
        <p class="subtitle">${escapeHtml(job.company)} · ${escapeHtml(job.industry || '')}</p>
        <div class="job-detail-meta">
            <span class="job-tag">${escapeHtml(job.location)}</span>
            <span class="job-tag type-${escapeHtml(job.type)}">${escapeHtml(formatType(job.type))}</span>
            <span class="job-tag">${escapeHtml(job.salary)}</span>
        </div>
        <p class="job-detail-description">${escapeHtml(job.description)}</p>
        <button class="btn btn-primary" data-apply="${job.id}">Apply for this role</button>
    `;
}

async function openManageModal() {
    if (!requireCompany()) return;
    openModal(`<h2 id="modal-title">Manage applicants</h2><p class="subtitle">Review, update status, and contact candidates.</p><div id="manage-results"><p class="loading-state">Loading...</p></div>`, true);
    try {
        const applications = await apiFetch('/applications/manage');
        document.getElementById('manage-results').innerHTML = manageApplicationsHtml(applications);
    } catch (err) {
        document.getElementById('manage-results').innerHTML = '';
        showToast(err.message, true);
    }
}

async function openMyApplications() {
    if (!requireCandidate()) return;
    openModal(`<h2 id="modal-title">My applications</h2><p class="subtitle">Track the status of your job applications.</p><div id="my-apps-results"><p class="loading-state">Loading...</p></div>`, true);
    try {
        const applications = await apiFetch('/applications/me');
        document.getElementById('my-apps-results').innerHTML = applicationsListHtml(applications);
    } catch (err) {
        showToast(err.message, true);
    }
}

async function handleModalOpen(type) {
    if (type === 'login') openModal(loginFormHtml());
    if (type === 'register') openModal(registerFormHtml());
    if (type === 'apply') {
        if (!requireCandidate()) return;
        openModal(applyFormHtml(null));
    }
    if (type === 'my-apps') await openMyApplications();
    if (type === 'post-job') {
        if (!requireCompany()) return;
        openModal(postJobFormHtml(), true);
    }
    if (type === 'manage') await openManageModal();
}

document.getElementById('type-filters').addEventListener('click', e => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    activeType = chip.dataset.type;
    loadJobs();
});

document.getElementById('hero-search').addEventListener('submit', e => {
    e.preventDefault();
    searchKeyword = document.getElementById('search-keyword').value.trim();
    searchLocation = document.getElementById('search-location').value.trim();
    document.getElementById('jobs').scrollIntoView({ behavior: 'smooth' });
    loadJobs();
});

jobsGrid.addEventListener('click', e => {
    const applyBtn = e.target.closest('[data-apply]');
    if (applyBtn) {
        if (!requireCandidate()) return;
        const job = jobs.find(j => j.id === Number(applyBtn.dataset.apply));
        if (job) openModal(applyFormHtml(job));
        return;
    }
    const detailsBtn = e.target.closest('[data-details]');
    if (detailsBtn) {
        const job = jobs.find(j => j.id === Number(detailsBtn.dataset.details));
        if (job) openModal(jobDetailHtml(job), true);
    }
});

document.querySelectorAll('[data-open-modal]').forEach(btn => {
    btn.addEventListener('click', () => handleModalOpen(btn.dataset.openModal));
});

modalOverlay.addEventListener('click', e => { if (e.target === modalOverlay) closeModal(); });
document.querySelector('.modal-close').addEventListener('click', closeModal);
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

modalBody.addEventListener('click', e => {
    if (e.target.matches('[data-switch-modal]')) {
        handleModalOpen(e.target.dataset.switchModal);
        return;
    }
    if (e.target.matches('[data-register-tab]')) {
        document.querySelectorAll('[data-register-tab]').forEach(t => t.classList.remove('active'));
        e.target.classList.add('active');
        document.getElementById('register-panel').innerHTML =
            e.target.dataset.registerTab === 'company' ? companyRegisterFormHtml() : candidateRegisterFormHtml();
        return;
    }
    const applyBtn = e.target.closest('[data-apply]');
    if (applyBtn) {
        if (!requireCandidate()) return;
        const job = jobs.find(j => j.id === Number(applyBtn.dataset.apply));
        if (job) openModal(applyFormHtml(job));
        return;
    }
    const contactBtn = e.target.closest('[data-contact-send]');
    if (contactBtn) {
        sendContact(Number(contactBtn.dataset.contactSend));
    }
    const threadBtn = e.target.closest('[data-view-thread]');
    if (threadBtn) {
        openMessageThread(Number(threadBtn.dataset.viewThread));
        return;
    }
    const appMsgBtn = e.target.closest('[data-application-id]');
    if (appMsgBtn) {
        openMessageThread(Number(appMsgBtn.dataset.applicationId));
    }
});

modalBody.addEventListener('change', async e => {
    const select = e.target.closest('[data-status-id]');
    if (!select) return;
    try {
        await apiFetch(`/applications/${select.dataset.statusId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status: select.value })
        });
        showToast('Status updated.');
        loadStats();
        if (isDashboardRoute()) loadDashboard();
    } catch (err) {
        showToast(err.message, true);
    }
});

async function sendContact(applicationId) {
    const input = document.querySelector(`[data-contact-input="${applicationId}"]`);
    const message = input?.value.trim();
    if (!message) {
        showToast('Please write a message first.', true);
        return;
    }
    try {
        await sendReply({ applicationId, conversationId: null }, message);
        input.value = '';
        showToast('Message sent!');
        loadNotifications();
        if (isMessagesRoute()) loadMessagesPage();
    } catch (err) {
        showToast(err.message, true);
    }
}

modalBody.addEventListener('input', e => {
    const input = e.target;
    if (input.name === 'phone' && input.type === 'tel') {
        const cursor = input.selectionStart;
        const before = input.value;
        input.value = normalizePhoneInput(before);
        if (before !== input.value && cursor != null) {
            input.setSelectionRange(input.value.length, input.value.length);
        }
    }
});

modalBody.addEventListener('submit', async e => {
    const form = e.target.closest('form');
    if (!form) return;

    if (form.id === 'login-form') {
        e.preventDefault();
        try {
            currentUser = await apiFetch('/auth/login', {
                method: 'POST',
                body: JSON.stringify({
                    email: form.email.value.trim(),
                    password: form.password.value
                })
            });
            closeModal();
            renderNav();
            loadNotifications();
            if (currentUser.role === 'COMPANY') location.hash = '#dashboard';
            showToast(`Welcome back, ${currentUser.displayName}!`);
        } catch (err) {
            showToast(err.message, true);
        }
    }

    if (form.id === 'register-candidate-form') {
        e.preventDefault();
        try {
            const phone = form.phone.value.trim() || null;
            if (phone && !isValidPhone(phone)) {
                throw new Error(PHONE_MESSAGE);
            }
            currentUser = await apiFetch('/auth/register/candidate', {
                method: 'POST',
                body: JSON.stringify({
                    firstName: form.firstName.value.trim(),
                    lastName: form.lastName.value.trim(),
                    email: form.email.value.trim(),
                    password: form.password.value,
                    phone,
                    resumeUrl: null
                })
            });
            closeModal();
            renderNav();
            loadNotifications();
            showToast('Candidate account created!');
        } catch (err) {
            showToast(err.message, true);
        }
    }

    if (form.id === 'register-company-form') {
        e.preventDefault();
        try {
            currentUser = await apiFetch('/auth/register/company', {
                method: 'POST',
                body: JSON.stringify({
                    companyName: form.companyName.value.trim(),
                    industry: form.industry.value.trim() || null,
                    email: form.email.value.trim(),
                    password: form.password.value
                })
            });
            closeModal();
            renderNav();
            loadNotifications();
            if (currentUser.role === 'COMPANY') location.hash = '#dashboard';
            showToast('Company account created!');
        } catch (err) {
            showToast(err.message, true);
        }
    }

    if (form.id === 'apply-form') {
        e.preventDefault();
        const btn = form.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.textContent = 'Submitting...';
        try {
            const jobId = Number(form.querySelector('[name="jobId"]').value);
            const phone = form.phone.value.trim() || null;
            if (phone && !isValidPhone(phone)) {
                throw new Error(PHONE_MESSAGE);
            }
            const resumeInput = form.resume.value.trim();
            const resumeUrl = resumeInput || (currentUser.resumeUrl || null);
            await apiFetch('/applications', {
                method: 'POST',
                body: JSON.stringify({
                    jobId,
                    phone,
                    resumeUrl
                })
            });
            const job = jobs.find(j => j.id === jobId);
            closeModal();
            showToast(`Application sent for ${job?.title || 'the role'}!`);
            loadStats();
        } catch (err) {
            showToast(err.message, true);
            btn.disabled = false;
            btn.textContent = 'Submit application';
        }
    }

    if (form.id === 'post-job-form') {
        e.preventDefault();
        const btn = form.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.textContent = 'Publishing...';
        try {
            await apiFetch('/jobs', {
                method: 'POST',
                body: JSON.stringify({
                    title: form.title.value.trim(),
                    description: form.description.value.trim(),
                    location: form.location.value.trim(),
                    type: form.type.value,
                    salary: form.salary.value.trim() || null
                })
            });
            closeModal();
            showToast('Job published!');
            await loadJobs();
            await loadCompanies();
        } catch (err) {
            showToast(err.message, true);
            btn.disabled = false;
            btn.textContent = 'Publish job';
        }
    }
});

document.querySelector('.nav-toggle').addEventListener('click', () => {
    const nav = document.querySelector('.nav');
    const toggle = document.querySelector('.nav-toggle');
    toggle.setAttribute('aria-expanded', nav.classList.toggle('open'));
});

notifBell?.addEventListener('click', e => {
    e.stopPropagation();
    notifDropdown?.classList.toggle('hidden');
    if (!notifDropdown?.classList.contains('hidden')) loadNotifications();
});

document.getElementById('notif-mark-all')?.addEventListener('click', e => {
    e.stopPropagation();
    markAllNotificationsRead();
});

notifList?.addEventListener('click', async e => {
    const item = e.target.closest('[data-notif-id]');
    if (!item) return;
    await markNotificationRead(Number(item.dataset.notifId));
    const relatedId = item.dataset.notifRelated;
    const notifType = item.dataset.notifType;
    if (relatedId) {
        if (notifType === 'OUTREACH_MESSAGE') {
            activeThread = { applicationId: null, conversationId: Number(relatedId) };
        } else {
            activeThread = { applicationId: Number(relatedId), conversationId: null };
        }
        location.hash = '#messages';
    }
    notifDropdown?.classList.add('hidden');
});

document.addEventListener('click', e => {
    if (!e.target.closest('#nav-notifications')) {
        notifDropdown?.classList.add('hidden');
    }
});

document.getElementById('dashboard-back')?.addEventListener('click', () => {
    location.hash = '#top';
});

document.getElementById('messages-back')?.addEventListener('click', () => {
    location.hash = '#top';
});

messagesInbox?.addEventListener('click', e => {
    const btn = e.target.closest('[data-application-id], [data-conversation-id]');
    if (!btn) return;
    const thread = parseThreadFromElement(btn);
    if (thread) loadThread(thread);
});

document.addEventListener('submit', async e => {
    if (e.target.id === 'outreach-form') {
        e.preventDefault();
        const form = e.target;
        const candidateId = Number(form.dataset.candidateId);
        const message = form.message.value.trim();
        if (!message) return;
        const btn = form.querySelector('button[type="submit"]');
        btn.disabled = true;
        try {
            const result = await apiFetch(`/messages/candidate/${candidateId}`, {
                method: 'POST',
                body: JSON.stringify({ message })
            });
            closeModal();
            showToast('Message sent to candidate!');
            loadNotifications();
            activeThread = { applicationId: null, conversationId: result.conversationId };
            location.hash = '#messages';
        } catch (err) {
            showToast(err.message, true);
        } finally {
            btn.disabled = false;
        }
        return;
    }

    if (e.target.id !== 'reply-form') return;
    e.preventDefault();
    const form = e.target;
    const thread = parseThreadFromElement(form);
    if (!thread) return;
    const message = form.message.value.trim();
    if (!message) return;
    const btn = form.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
        await sendReply(thread, message);
        form.message.value = '';
        showToast('Message sent!');
        loadNotifications();
        await loadThread(thread, { inModal: !isMessagesRoute() });
        if (isMessagesRoute()) {
            await loadInboxList();
            renderInboxList(messagesInbox, inboxItems, thread);
        }
    } catch (err) {
        showToast(err.message, true);
    } finally {
        btn.disabled = false;
    }
});

dashboardContent?.addEventListener('click', e => {
    const contactBtn = e.target.closest('[data-contact-send]');
    if (contactBtn) sendContact(Number(contactBtn.dataset.contactSend));
});

dashboardContent?.addEventListener('submit', async e => {
    if (e.target.id !== 'profile-form') return;
    e.preventDefault();
    const form = e.target;
    const phone = form.phone.value.trim() || null;
    if (phone && !isValidPhone(phone)) {
        showToast(PHONE_MESSAGE, true);
        return;
    }
    try {
        currentUser = await apiFetch('/candidates/profile', {
            method: 'PATCH',
            body: JSON.stringify({ phone })
        });
        showToast('Profile updated.');
        loadDashboard();
    } catch (err) {
        showToast(err.message, true);
    }
});

dashboardContent?.addEventListener('change', async e => {
    if (e.target.id === 'cv-upload') {
        const file = e.target.files?.[0];
        if (!file) return;
        try {
            await uploadResumeFile(file);
            showToast('CV uploaded successfully!');
            loadDashboard();
        } catch (err) {
            showToast(err.message, true);
        }
        e.target.value = '';
        return;
    }
    const select = e.target.closest('[data-status-id]');
    if (!select) return;
    try {
        await apiFetch(`/applications/${select.dataset.statusId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status: select.value })
        });
        showToast('Status updated.');
        loadStats();
        if (isDashboardRoute()) loadDashboard();
        loadNotifications();
    } catch (err) {
        showToast(err.message, true);
    }
});

window.addEventListener('hashchange', handleRoute);

loadUser().then(() => handleRoute());
loadStats();
loadJobs();
loadCompanies();
checkServer();
