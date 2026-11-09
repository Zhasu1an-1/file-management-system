'use strict';

const $ = (id) => document.getElementById(id);
const account = $('actor');
const createDialog = $('create-dialog');
const detailDialog = $('detail-dialog');
const storageLabels = { LOCAL: 'Local', DATABASE: 'Database' };
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
let users = [];
let folders = [];
let folderId = '';
let selectedFile;
let busy = false;

async function api(path, method = 'GET', data) {
    const headers = { 'X-User': account.value };
    if (data !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch('/api' + path, { method, headers, body: JSON.stringify(data) });
    if (response.status === 204) return;
    const result = await response.json();
    if (!response.ok) throw new Error(`${response.status} · ${result.message || response.statusText}`);
    return result;
}

function message(target, text, isError = false) {
    const notice = $(target);
    notice.textContent = text;
    notice.hidden = !text;
    notice.classList.toggle('error', isError);
}

async function run(action, notice = 'notice') {
    if (busy) return;
    busy = true;
    account.disabled = true;
    message(notice, '');
    try {
        await action();
    } catch (error) {
        const dialog = $(notice).closest('dialog');
        message(dialog && !dialog.open ? 'notice' : notice, error.message, true);
    } finally {
        busy = false;
        account.disabled = !users.length;
    }
}

function isOwner() {
    return users.some(user => user.username === account.value && user.role === 'OWNER');
}

function renderFolders() {
    const options = [{ id: '', name: 'All files' }, ...folders];
    $('folders').replaceChildren(...options.map(folder => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = folder.name;
        button.setAttribute('aria-current', String(folderId === folder.id));
        button.onclick = () => run(async () => {
            folderId = folder.id;
            renderFolders();
            await loadFiles();
        });
        return button;
    }));
    $('files-heading').textContent = options.find(folder => folder.id === folderId).name;
    $('folder-actions').hidden = !isOwner();
    $('delete-folder').hidden = !folderId;
    $('new-file').disabled = !isOwner();
}

async function loadFiles() {
    const params = new URLSearchParams({ search: $('search').value });
    if (folderId) params.set('folderId', folderId);
    const files = await api('/files?' + params);
    $('empty').textContent = 'No files match this view.';
    $('empty').hidden = files.length > 0;
    $('files').replaceChildren(...files.map(file => {
        const row = document.createElement('tr');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'file-link';
        button.textContent = file.name;
        button.onclick = () => run(() => openFile(file.id));
        row.insertCell().append(button);
        for (const value of [file.fileType, `${file.sizeBytes} B`, storageLabels[file.storageType],
            dateFormat.format(new Date(file.createdAt))]) {
            row.insertCell().textContent = value;
        }
        return row;
    }));
}

async function refresh() {
    folders = await api('/folders');
    if (!folders.some(folder => folder.id === folderId)) folderId = '';
    renderFolders();
    await loadFiles();
}

function renderDetails() {
    const { file, content } = selectedFile;
    $('detail-heading').textContent = file.name;
    $('detail-id').value = file.id;
    $('content').textContent = content;
    $('rename-name').value = file.name;
    const owner = users.find(user => user.username === file.owner)?.displayName || file.owner;
    const folderName = folders.find(folder => folder.id === file.folderId)?.name || 'Unfiled';
    const fields = [
        ['Type', file.fileType], ['Size', `${file.sizeBytes} bytes`],
        ['Created', dateFormat.format(new Date(file.createdAt))],
        ['Owner', owner], ['Folder', folderName], ['Storage', storageLabels[file.storageType]]
    ];
    $('metadata').replaceChildren(...fields.map(([label, value]) => {
        const group = document.createElement('div');
        const term = document.createElement('dt');
        const description = document.createElement('dd');
        term.textContent = label;
        description.textContent = value;
        group.append(term, description);
        return group;
    }));
    $('move-folder').replaceChildren(new Option('Unfiled', ''),
        ...folders.map(folder => new Option(folder.name, folder.id)));
    $('move-folder').value = file.folderId || '';
    $('share-viewer').replaceChildren(...users
        .filter(user => user.username !== account.value && (!isOwner() || user.role === 'VIEWER'))
        .map(user => new Option(user.displayName, user.username)));
}

async function openFile(id) {
    $('open-id').value = id;
    selectedFile = await api('/files/' + encodeURIComponent(id));
    renderDetails();
    message('detail-notice', '');
    detailDialog.showModal();
}

function submit(id, action, notice) {
    $(id).addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        run(() => action(form), notice);
    });
}

account.addEventListener('change', () => run(async () => {
    folderId = '';
    selectedFile = undefined;
    $('content').textContent = '';
    $('metadata').replaceChildren();
    $('folders').replaceChildren();
    $('files').replaceChildren();
    await refresh();
}));

$('new-file').onclick = () => {
    message('create-notice', '');
    createDialog.showModal();
};

submit('search-form', loadFiles);
submit('open-form', () => openFile($('open-id').value.trim()));
submit('create-file', async (form) => {
    const file = await api('/files', 'POST', Object.fromEntries(new FormData(form)));
    $('open-id').value = file.id;
    form.reset();
    createDialog.close();
    folderId = '';
    $('search').value = '';
    await refresh();
    message('notice', `Created ${file.name} in ${storageLabels[file.storageType].toLowerCase()} storage.`);
}, 'create-notice');

submit('create-folder', async (form) => {
    await api('/folders', 'POST', { name: $('folder-name').value });
    form.reset();
    await refresh();
    message('notice', 'Folder created.');
});

$('delete-folder').onclick = () => run(async () => {
    if (!folderId || !window.confirm('Delete the selected empty folder?')) return;
    await api('/folders/' + folderId, 'DELETE');
    folderId = '';
    await refresh();
    message('notice', 'Folder deleted.');
});

submit('rename-form', async () => {
    const id = selectedFile.file.id;
    selectedFile.file = await api(`/files/${id}/name`, 'PATCH', { name: $('rename-name').value });
    renderDetails();
    await loadFiles();
    message('detail-notice', 'File renamed.');
}, 'detail-notice');

submit('share-form', async () => {
    const id = selectedFile.file.id;
    const viewer = $('share-viewer');
    await api(`/files/${id}/shares`, 'POST', { viewer: viewer.value });
    message('detail-notice', `Shared with ${viewer.selectedOptions[0].text}.`);
}, 'detail-notice');

submit('move-form', async () => {
    const id = selectedFile.file.id;
    selectedFile.file = await api(`/files/${id}/folder`, 'PUT', { folderId: $('move-folder').value || null });
    renderDetails();
    await loadFiles();
    message('detail-notice', 'File moved.');
}, 'detail-notice');

$('delete-file').onclick = () => run(async () => {
    if (!window.confirm(`Delete ${selectedFile.file.name}?`)) return;
    await api('/files/' + selectedFile.file.id, 'DELETE');
    detailDialog.close();
    $('open-id').value = '';
    await refresh();
    message('notice', 'File deleted.');
}, 'detail-notice');

run(async () => {
    users = await api('/users');
    account.replaceChildren(...users.map(user =>
        new Option(`${user.displayName} (${user.role === 'OWNER' ? 'Owner' : 'Viewer'})`, user.username)));
    await refresh();
});
