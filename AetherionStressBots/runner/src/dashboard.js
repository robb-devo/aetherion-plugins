/**
 * Localhost dashboard shell. Live numbers come from GET /status.
 * No external assets — the control server is often reached over an SSH tunnel.
 */
export function dashboardUrl(base, token) {
  const root = String(base || '').trim().replace(/\/+$/, '')
  if (!root.startsWith('http://') && !root.startsWith('https://')) return ''
  if (!token) return `${root}/`
  return `${root}/?token=${encodeURIComponent(token)}`
}

export function dashboardHtml() {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Aetherion Stress Bots</title>
<style>
  :root {
    color-scheme: dark;
    --bg: #10140f;
    --panel: #1a2118;
    --line: #2c3828;
    --text: #e7efe4;
    --muted: #9aab96;
    --accent: #c6a15a;
    --ok: #8fbf7a;
    --warn: #d2a15a;
    --bad: #d4726a;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    font: 14px/1.45 "Segoe UI", system-ui, sans-serif;
    background: var(--bg);
    color: var(--text);
  }
  header {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    align-items: flex-end;
    padding: 20px 24px 12px;
    border-bottom: 1px solid var(--line);
  }
  h1 { font-size: 20px; font-weight: 600; margin: 0; }
  h1 span { color: var(--accent); font-weight: 500; }
  .sub { color: var(--muted); margin-top: 4px; }
  main { padding: 16px 24px 32px; }
  .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; margin-bottom: 16px; }
  .card { background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; }
  .card b { display: block; font-size: 18px; font-weight: 600; }
  .card span { color: var(--muted); font-size: 12px; }
  table { width: 100%; border-collapse: collapse; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); vertical-align: top; }
  th { color: var(--muted); font-size: 12px; font-weight: 600; }
  tr:last-child td { border-bottom: 0; }
  .tag { display: inline-block; padding: 1px 6px; border-radius: 999px; background: #243024; color: var(--ok); font-size: 12px; }
  .tag.warn { color: var(--warn); background: #2d2618; }
  .tag.bad { color: var(--bad); background: #2d1c1a; }
  .muted { color: var(--muted); }
  .err { color: var(--bad); }
  button, select, input { background: #243024; color: var(--text); border: 1px solid var(--line); border-radius: 6px; padding: 6px 10px; }
  button { cursor: pointer; }
  .controls { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 14px; }
  .controls label { color: var(--muted); font-size: 12px; }
  .empty { padding: 28px; color: var(--muted); }
</style>
</head>
<body>
<header>
  <div>
    <h1>Aetherion <span>Stress Bots</span></h1>
    <div class="sub" id="meta">connecting…</div>
  </div>
  <button id="refresh" type="button">Refresh</button>
</header>
<main>
  <form class="controls" id="controls">
    <label>Role <select id="role" name="role">
      <option>mine</option><option>forage</option><option>catch</option><option>roam</option>
      <option>combat</option><option>fish</option><option>trade</option><option>quest</option>
      <option>pad</option><option>mining</option>
    </select></label>
    <label>Count <input id="count" name="count" type="number" min="0" max="40" value="2" /></label>
    <button type="submit" id="start" data-action="start">Start</button>
    <button type="button" id="stop" data-action="stop">Stop</button>
    <button type="button" id="stopall" data-action="stop-all">Stop all</button>
    <span class="muted" id="controlMsg"></span>
  </form>
  <section class="cards" id="cards"></section>
  <div id="table"></div>
</main>
<script>
const token = new URLSearchParams(location.search).get('token');
const headers = token ? { 'X-Testbots-Token': token } : {};
function esc(v) {
  return String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;' }[c]));
}
function fmtTime(ms) {
  const s = Math.max(0, Math.floor((ms || 0) / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  if (h) return h + 'h ' + m + 'm';
  if (m) return m + 'm ' + r + 's';
  return r + 's';
}
function pos(p) {
  if (!p) return '—';
  return p.x.toFixed(0) + ' ' + p.y.toFixed(0) + ' ' + p.z.toFixed(0);
}
function tag(activity) {
  const bad = activity === 'error' || activity === 'void' || activity === 'stuck';
  const warn = activity === 'recovering' || activity === 'retreating' || activity === 'eating';
  const cls = bad ? 'tag bad' : warn ? 'tag warn' : 'tag';
  return '<span class="' + cls + '">' + esc(activity || 'idle') + '</span>';
}
function purse(e) {
  if (e.purse == null) return '—';
  return (e.purseKnown ? '' : '~') + e.purse;
}
function render(data) {
  const bots = data.bots || [];
  const econ = data.economy || {};
  document.getElementById('meta').textContent = bots.length + ' online · updated ' + new Date().toLocaleTimeString();
  const cards = [
    ['Online', bots.length],
    ['Listed', econ.listed || 0],
    ['Bought', econ.bought || 0],
    ['Sales', econ.sales || 0],
    ['Earned', econ.earned || 0],
    ['Spent', econ.spent || 0],
    ['Failed', econ.failed || 0],
    ['Chats', data.chats || 0]
  ];
  document.getElementById('cards').innerHTML = cards.map(([k,v]) => '<div class="card"><b>' + esc(v) + '</b><span>' + esc(k) + '</span></div>').join('');
  if (!bots.length) {
    document.getElementById('table').innerHTML = '<div class="empty">No bots online. Start a role from the dev menu or the runner CLI. This page polls /status every 2s.</div>';
    return;
  }
  const rows = bots.map((b) => {
    const p = b.persona || {};
    const inv = (b.inventory || []).map((i) => i.name + '×' + i.count).join(', ') || '—';
    const err = b.lastError ? '<div class="err">' + esc(b.lastError) + '</div>' : '';
    return '<tr>'
      + '<td><b>' + esc(b.name) + '</b><div class="muted">' + esc(b.role) + ' · ' + esc(p.combatStyle || '') + ' / ' + esc(p.economyStyle || '') + '</div></td>'
      + '<td>' + tag(b.activity) + '<div class="muted">' + esc(b.goal || '') + '</div></td>'
      + '<td>' + esc(pos(b.position)) + '<div class="muted">' + esc(b.world || '') + '</div></td>'
      + '<td>' + esc(b.health == null ? '—' : Math.round(b.health) + ' hp') + '<div class="muted">' + esc(b.food == null ? '' : b.food + ' food') + '</div></td>'
      + '<td>' + esc(purse(b.economy || {})) + '<div class="muted">L' + (b.economy?.listed||0) + ' B' + (b.economy?.bought||0) + ' +' + (b.economy?.earned||0) + '</div></td>'
      + '<td>' + esc(b.heldItem || '-') + '<div class="muted">' + esc(inv) + '</div></td>'
      + '<td>' + esc(fmtTime(b.uptimeMs)) + '<div class="muted">deaths ' + esc(b.deaths || 0) + ' · chats ' + esc(b.chats || 0) + '</div>' + err + '</td>'
      + '</tr>';
  }).join('');
  document.getElementById('table').innerHTML = '<table><thead><tr><th>Bot</th><th>Activity</th><th>Location</th><th>State</th><th>Coins</th><th>Inventory</th><th>Uptime</th></tr></thead><tbody>' + rows + '</tbody></table>';
}
async function load() {
  try {
    const res = await fetch('status', { headers, cache: 'no-store' });
    if (!res.ok) {
      document.getElementById('meta').textContent = 'status ' + res.status + (res.status === 401 ? ' — open with ?token=' : '');
      return;
    }
    render(await res.json());
  } catch (err) {
    document.getElementById('meta').textContent = 'offline (' + err.message + ')';
  }
}
async function post(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify(body || {})
  });
  const text = await res.text();
  let payload = {};
  try { payload = JSON.parse(text); } catch { payload = { message: text }; }
  document.getElementById('controlMsg').textContent = payload.message || (res.ok ? 'ok' : ('http ' + res.status));
  if (res.ok) load();
}
document.getElementById('controls').addEventListener('submit', (event) => {
  event.preventDefault();
  const role = document.getElementById('role').value;
  const count = Number(document.getElementById('count').value);
  post('desired', { role, count }).catch((err) => {
    document.getElementById('controlMsg').textContent = err.message;
  });
});
document.getElementById('stop').onclick = () => {
  post('stop', { role: document.getElementById('role').value }).catch((err) => {
    document.getElementById('controlMsg').textContent = err.message;
  });
};
document.getElementById('stopall').onclick = () => {
  post('stop-all', {}).catch((err) => {
    document.getElementById('controlMsg').textContent = err.message;
  });
};
document.getElementById('refresh').onclick = load;
load();
setInterval(load, 2000);
</script>
</body>
</html>`
}
