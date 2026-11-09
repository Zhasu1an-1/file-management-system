'use strict';

const $ = (id) => document.getElementById(id);
const account = $('actor');
const createDialog = $('create-dialog');
const detailDialog = $('detail-dialog');
const confirmDialog = $('confirm-dialog');
const storageLabels = { LOCAL: 'Local', DATABASE: 'Database' };
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
let users = [];
let folders = [];
let folderId = '';
let selectedFile;
let busy = false;

function icon(name) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    const use = document.createElementNS(svg.namespaceURI, 'use');
    svg.setAttribute('class', 'icon');
    svg.setAttribute('aria-hidden', 'true');
    use.setAttribute('href', '#icon-' + name);
    svg.append(use);
    return svg;
}

function textSpan(text, className) {
    const span = document.createElement('span');
    span.className = className;
    span.textContent = text;
    return span;
}

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
    notice.querySelector('p').textContent = text;
    notice.querySelector('use').setAttribute('href', isError ? '#icon-alert' : '#icon-check');
    notice.hidden = !text;
    notice.classList.toggle('error', isError);
    if (isError) notice.scrollIntoView({ block: 'nearest' });
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
        if ($('file-count').hidden) $('empty-title').textContent = 'Files are unavailable';
    } finally {
        busy = false;
        account.disabled = !users.length;
    }
}

function isOwner() {
    return users.some(user => user.username === account.value && user.role === 'OWNER');
}

function renderAccount() {
    const actor = users.find(user => user.username === account.value);
    $('avatar').textContent = actor.displayName.charAt(0);
    const role = $('account-role');
    role.textContent = isOwner() ? 'Owner' : 'Viewer';
    role.classList.toggle('viewer', !isOwner());
    role.hidden = false;
}

function renderFolders() {
    const options = [{ id: '', name: 'All files' }, ...folders];
    $('folders').replaceChildren(...options.map(folder => {
        const button = document.createElement('button');
        button.type = 'button';
        button.append(icon(folder.id ? 'folder' : 'files'), textSpan(folder.name, 'folder-name'));
        button.setAttribute('aria-current', String(folderId === folder.id));
        button.onclick = () => run(async () => {
            folderId = folder.id;
            renderFolders();
            $('folders').querySelector('[aria-current="true"]').focus();
            await loadFiles();
        });
        return button;
    }));
    $('files-heading').textContent = options.find(folder => folder.id === folderId).name;
    $('view-description').textContent = isOwner()
        ? (folderId ? 'Browse and manage files in this folder.' : 'Browse, store, and share your files.')
        : 'Only files shared with you are shown.';
    $('folder-actions').hidden = !isOwner();
    $('delete-folder').hidden = !folderId;
    $('new-file').disabled = !isOwner();
}

function renderFileRow(file) {
    const row = document.createElement('tr');
    row.setAttribute('role', 'row');
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'file-link';
    const tile = textSpan('', 'file-icon');
    tile.append(icon('file'));
    button.append(tile, textSpan(file.name, 'file-name'));
    button.onclick = () => run(() => openFile(file.id));
    row.insertCell().append(button);

    const storage = textSpan('', 'storage-label');
    storage.append(icon(file.storageType === 'LOCAL' ? 'disk' : 'database'), storageLabels[file.storageType]);
    const fields = [
        ['Type', textSpan(file.fileType, 'type-badge')],
        ['Size', `${file.sizeBytes} B`],
        ['Storage', storage],
        ['Created', dateFormat.format(new Date(file.createdAt))]
    ];
    for (const [label, value] of fields) {
        const mobileLabel = textSpan(label, 'mobile-label');
        mobileLabel.setAttribute('aria-hidden', 'true');
        row.insertCell().append(mobileLabel, value);
    }
    for (const cell of row.cells) cell.setAttribute('role', 'cell');
    return row;
}

function renderEmptyState(count, searching) {
    $('empty').hidden = count > 0;
    $('empty-create').hidden = !isOwner() || searching || Boolean(folderId);
    if (count > 0) return;

    const title = $('empty-title');
    const description = $('empty-description');
    if (searching) {
        title.textContent = 'No matching files';
        description.textContent = 'Try a different search or clear the search field.';
    } else if (!isOwner()) {
        title.textContent = 'No shared files yet';
        description.textContent = 'Files will appear here when an owner shares them with you.';
    } else if (folderId) {
        title.textContent = 'This folder is empty';
        description.textContent = 'Open a file and use Move to to add it to this folder.';
    } else {
        title.textContent = 'No files yet';
        description.textContent = 'Create a text file and choose where to store it.';
    }
}

async function loadFiles() {
    const search = $('search').value.trim();
    const params = new URLSearchParams({ search });
    if (folderId) params.set('folderId', folderId);
    const files = await api('/files?' + params);
    $('files').replaceChildren(...files.map(renderFileRow));
    $('file-count').textContent = `${files.length} ${files.length === 1 ? 'file' : 'files'}`;
    $('file-count').hidden = false;
    renderEmptyState(files.length, search.length > 0);
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
    const canEdit = isOwner() && selectedFile.file.owner === account.value;
    $('owner-tools').open = canEdit;
    $('viewer-note').hidden = canEdit;
    $('restricted').hidden = canEdit;
    message('detail-notice', '');
    detailDialog.showModal();
}

function confirmDeletion(title, text) {
    $('confirm-heading').textContent = title;
    $('confirm-text').textContent = text;
    confirmDialog.returnValue = '';
    confirmDialog.showModal();
    return new Promise(resolve => {
        confirmDialog.addEventListener('close', () => resolve(confirmDialog.returnValue === 'delete'), { once: true });
    });
}

function submit(id, action, notice) {
    $(id).addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        run(() => action(form), notice);
    });
}

account.addEventListener('change', () => run(async () => {
    renderAccount();
    folderId = '';
    selectedFile = undefined;
    $('content').textContent = '';
    $('metadata').replaceChildren();
    $('folders').replaceChildren();
    $('files').replaceChildren();
    $('file-count').hidden = true;
    $('empty').hidden = false;
    $('empty-title').textContent = 'Loading files…';
    $('empty-description').textContent = '';
    $('empty-create').hidden = true;
    $('lookup').open = false;
    $('folder-create-panel').open = false;
    $('folder-actions').hidden = !isOwner();
    $('new-file').disabled = !isOwner();
    await refresh();
}));

function showCreateDialog() {
    if (busy || !isOwner()) return;
    message('create-notice', '');
    createDialog.showModal();
}

$('new-file').onclick = showCreateDialog;
$('empty-create').onclick = showCreateDialog;

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
    $('folder-create-panel').open = false;
    $('folder-create-panel').querySelector('summary').focus();
    await refresh();
    message('notice', 'Folder created.');
});

$('delete-folder').onclick = () => run(async () => {
    const folder = folders.find(folder => folder.id === folderId);
    if (!folder || !await confirmDeletion('Delete folder?', `“${folder.name}” can be deleted once it is empty.`)) return;
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
    if (!await confirmDeletion('Delete file?', `“${selectedFile.file.name}” will be permanently deleted.`)) return;
    await api('/files/' + selectedFile.file.id, 'DELETE');
    detailDialog.close();
    $('open-id').value = '';
    await refresh();
    message('notice', 'File deleted.');
}, 'detail-notice');

run(async () => {
    users = await api('/users');
    account.replaceChildren(...users.map(user =>
        new Option(user.displayName, user.username)));
    renderAccount();
    await refresh();
});
