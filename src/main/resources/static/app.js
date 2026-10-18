'use strict';

const byId = (id) => document.getElementById(id);
const state = { users: [], query: '', mode: 'name', page: 0, size: 10, hasNext: false, loading: false, pendingDelete: null, deleting: false };
let listController;
let toastTimer;
let emptyAction = 'create';

class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

function connection(online) {
  byId('connection').className = `connection ${online ? 'online' : 'offline'}`;
  byId('connection-text').textContent = online ? 'Сервер на связи' : 'Нет соединения';
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const abort = () => controller.abort();
  const externalSignal = options.signal;
  if (externalSignal?.aborted) controller.abort();
  externalSignal?.addEventListener('abort', abort, { once: true });
  const timeout = setTimeout(abort, 12000);
  try {
    const response = await fetch(url, { ...options, headers: { Accept: 'application/json', ...options.headers }, signal: controller.signal });
    connection(true);
    const data = response.status === 204 ? null : await response.json();
    if (!response.ok) throw new ApiError(data.error || 'Не удалось выполнить запрос.', response.status);
    return data;
  } catch (error) {
    if (!(error instanceof ApiError) && !externalSignal?.aborted) {
      connection(false);
      throw new Error('Не удалось связаться с сервером. Проверьте, что он запущен, и попробуйте снова.');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
    externalSignal?.removeEventListener('abort', abort);
  }
}

function notify(message) {
  clearTimeout(toastTimer);
  byId('toast-text').textContent = message;
  byId('toast').hidden = false;
  toastTimer = setTimeout(() => { byId('toast').hidden = true; }, 5000);
}

function focusCreate() {
  byId('create-title').scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'center' });
  byId('name').focus({ preventScroll: true });
}

function icon(name) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.classList.add('icon');
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `#i-${name}`);
  svg.append(use);
  return svg;
}

function renderRows() {
  const body = byId('users-body');
  body.replaceChildren();
  for (const user of state.users) {
    const row = document.createElement('tr');
    const nameCell = document.createElement('td');
    const person = document.createElement('div');
    person.className = 'user-cell';
    const avatar = document.createElement('span');
    avatar.className = `avatar tone-${user.id % 5}`;
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = user.name.split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0] || '').join('').toLocaleUpperCase('ru');
    const name = document.createElement('span');
    name.className = 'user-name';
    name.textContent = user.name;
    person.append(avatar, name);
    nameCell.append(person);
    const emailCell = document.createElement('td');
    emailCell.className = 'email-cell';
    emailCell.textContent = user.email;
    const idCell = document.createElement('td');
    const id = document.createElement('span');
    id.className = 'id-label';
    id.textContent = `#${user.id}`;
    idCell.append(id);
    const actions = document.createElement('td');
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'icon-button delete-button';
    remove.setAttribute('aria-label', `Удалить пользователя ${user.name}`);
    remove.title = `Удалить ${user.name}`;
    remove.append(icon('trash'));
    remove.addEventListener('click', () => openDelete(user));
    actions.append(remove);
    row.append(nameCell, emailCell, idCell, actions);
    body.append(row);
  }
}

function updatePagination() {
  byId('previous-page').disabled = state.loading || state.page === 0 || state.mode === 'id' && Boolean(state.query);
  byId('next-page').disabled = state.loading || !state.hasNext;
  byId('page-size').disabled = state.loading || state.mode === 'id' && Boolean(state.query);
}

function showEmpty(title, description, action) {
  byId('table-wrap').hidden = true;
  byId('empty-state').hidden = false;
  byId('empty-title').textContent = title;
  byId('empty-description').textContent = description;
  byId('empty-action').hidden = !action;
  emptyAction = action;
  if (action) {
    byId('empty-action').replaceChildren(icon(action === 'create' ? 'plus' : action === 'retry' ? 'refresh' : 'close'), document.createTextNode(action === 'create' ? 'Добавить первого' : action === 'retry' ? 'Попробовать снова' : 'Сбросить поиск'));
  }
}

async function loadUsers() {
  listController?.abort();
  const controller = new AbortController();
  listController = controller;
  state.loading = true;
  byId('list-region').setAttribute('aria-busy', 'true');
  byId('refresh').disabled = true;
  byId('user-count').textContent = '…';
  byId('page-description').textContent = 'Загрузка…';
  updatePagination();
  showEmpty('Загружаем пользователей', 'Список появится через мгновение.');
  try {
    let users;
    const lookup = state.mode === 'id' && Boolean(state.query);
    if (lookup) {
      try { users = [await request(`/users/${encodeURIComponent(state.query)}`, { signal: controller.signal })]; }
      catch (error) { if (error.status === 404) users = []; else throw error; }
      state.hasNext = false;
    } else {
      // Запрашиваем ещё одну запись, чтобы точно знать, есть ли следующая страница.
      const params = new URLSearchParams({ limit: state.size + 1, offset: state.page * state.size });
      if (state.query) params.set('name', state.query);
      users = await request(`/users?${params}`, { signal: controller.signal });
      if (controller.signal.aborted) return;
      state.hasNext = users.length > state.size;
      users = users.slice(0, state.size);
      if (!users.length && state.page > 0) { state.page--; return await loadUsers(); }
    }
    if (controller.signal.aborted) return;
    state.users = users;
    byId('user-count').textContent = users.length;
    byId('filter-description').hidden = !state.query;
    byId('filter-description').textContent = state.query ? `Поиск ${lookup ? 'по ID' : 'по имени'}: ${state.query}` : '';
    if (users.length) {
      renderRows();
      byId('table-wrap').hidden = false;
      byId('empty-state').hidden = true;
    } else {
      showEmpty(state.query ? 'Никого не нашли' : 'Пока никого нет', state.query ? 'Попробуйте другой запрос или вернитесь ко всем пользователям.' : 'Добавьте первого пользователя — и ваш круг начнёт расти.', state.query ? 'reset' : 'create');
    }
    byId('page-description').textContent = users.length ? lookup ? 'Найден 1 пользователь' : `Показаны ${state.page * state.size + 1}–${state.page * state.size + users.length} · Страница ${state.page + 1}` : 'Нет пользователей';
    byId('last-updated').textContent = `Обновлено в ${new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`;
    return true;
  } catch (error) {
    if (controller.signal.aborted) return;
    state.users = [];
    state.hasNext = false;
    byId('user-count').textContent = '—';
    byId('page-description').textContent = 'Не удалось обновить список';
    showEmpty('Список пока недоступен', error.message, 'retry');
    return false;
  } finally {
    if (listController === controller) {
      state.loading = false;
      byId('list-region').setAttribute('aria-busy', 'false');
      byId('refresh').disabled = false;
      updatePagination();
    }
  }
}

function resetSearch() {
  byId('search').value = '';
  byId('search').setCustomValidity('');
  byId('clear-search').hidden = true;
  state.query = '';
  state.page = 0;
  loadUsers();
}

byId('search-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const query = byId('search').value.trim();
  const mode = byId('search-mode').value;
  if (mode === 'id' && query && (!/^\d+$/.test(query) || Number(query) < 1 || Number(query) > 2147483647)) {
    byId('search').setCustomValidity('ID должен быть целым числом от 1 до 2147483647.');
    byId('search').reportValidity();
    return;
  }
  state.query = query;
  state.mode = mode;
  state.page = 0;
  loadUsers();
});
byId('search').addEventListener('input', () => {
  byId('search').setCustomValidity('');
  byId('clear-search').hidden = !byId('search').value;
});
byId('search-mode').addEventListener('change', () => {
  byId('search').setCustomValidity('');
  byId('search').placeholder = byId('search-mode').value === 'id' ? 'Введите ID пользователя…' : 'Найти пользователя…';
  byId('search').inputMode = byId('search-mode').value === 'id' ? 'numeric' : 'search';
});
byId('clear-search').addEventListener('click', resetSearch);
byId('refresh').addEventListener('click', loadUsers);
byId('previous-page').addEventListener('click', () => { if (state.page > 0) { state.page--; loadUsers(); } });
byId('next-page').addEventListener('click', () => { if (state.hasNext) { state.page++; loadUsers(); } });
byId('page-size').addEventListener('change', () => { state.size = Number(byId('page-size').value); state.page = 0; loadUsers(); });
byId('focus-create').addEventListener('click', focusCreate);
byId('empty-action').addEventListener('click', () => { if (emptyAction === 'retry') loadUsers(); else if (emptyAction === 'reset') resetSearch(); else focusCreate(); });
byId('close-toast').addEventListener('click', () => { byId('toast').hidden = true; });
for (const field of ['name', 'email']) {
  byId(field).addEventListener('input', () => { byId('create-error').hidden = true; });
}

byId('create-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const name = byId('name').value.trim();
  const email = byId('email').value.trim();
  const error = byId('create-error');
  byId('toast').hidden = true;
  error.hidden = true;
  if (!name) { error.textContent = 'Введите имя пользователя.'; error.hidden = false; byId('name').focus(); return; }
  byId('create-submit').disabled = true;
  byId('create-submit').lastElementChild.textContent = 'Добавляем…';
  byId('name').disabled = byId('email').disabled = true;
  try {
    const user = await request('/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, email }) });
    byId('create-form').reset();
    byId('search').value = '';
    byId('search').setCustomValidity('');
    byId('clear-search').hidden = true;
    state.mode = 'name';
    byId('search-mode').value = 'name';
    byId('search').placeholder = 'Найти пользователя…';
    byId('search').inputMode = 'search';
    state.query = '';
    state.page = 0;
    const refreshed = await loadUsers();
    notify(refreshed ? `${user.name} — теперь в вашем круге` : 'Пользователь добавлен. Обновите список, когда соединение восстановится.');
  } catch (cause) {
    error.textContent = cause.message;
    error.hidden = false;
  } finally {
    byId('create-submit').disabled = false;
    byId('create-submit').lastElementChild.textContent = 'Добавить пользователя';
    byId('name').disabled = byId('email').disabled = false;
  }
});

function openDelete(user) {
  state.pendingDelete = user;
  byId('delete-description').textContent = `${user.name} (${user.email}) будет удалён из вашего круга. Это действие нельзя отменить.`;
  byId('delete-error').hidden = true;
  byId('delete-dialog').showModal();
}
byId('cancel-delete').addEventListener('click', () => byId('delete-dialog').close());
byId('delete-dialog').addEventListener('cancel', (event) => { if (state.deleting) event.preventDefault(); });
byId('delete-dialog').addEventListener('close', () => { state.pendingDelete = null; byId('refresh').focus(); });
byId('confirm-delete').addEventListener('click', async () => {
  if (!state.pendingDelete || state.deleting) return;
  state.deleting = true;
  const user = state.pendingDelete;
  byId('confirm-delete').disabled = byId('cancel-delete').disabled = true;
  byId('confirm-delete').textContent = 'Удаляем…';
  byId('delete-error').hidden = true;
  try {
    try { await request(`/users/${user.id}`, { method: 'DELETE' }); }
    catch (error) { if (error.status !== 404) throw error; }
    byId('delete-dialog').close();
    await loadUsers();
    notify(`${user.name} удалён из списка`);
  } catch (error) {
    byId('delete-error').textContent = error.message;
    byId('delete-error').hidden = false;
  } finally {
    state.deleting = false;
    byId('confirm-delete').disabled = byId('cancel-delete').disabled = false;
    byId('confirm-delete').textContent = 'Удалить';
  }
});

loadUsers();
