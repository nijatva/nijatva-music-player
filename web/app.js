/* Nijatva Music — web player.
   Plays local audio files in the tab. No network, no accounts, no analytics. */
(() => {
  'use strict';

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  const audio = $('#audio');
  const state = {
    tracks: [],
    view: 'songs',
    drill: null,
    queue: [],
    index: -1,
    shuffle: false,
    repeat: 'off',
    query: '',
    favourites: new Set(),
    sleepMinutes: 0,
    sleepLeft: 0,
    sleepTimer: null,
  };

  const ART = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'];

  /* ---------------- helpers ---------------- */

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function fmt(ms) {
    if (!ms || !isFinite(ms) || ms <= 0) return '--:--';
    const total = Math.round(ms / 1000);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
  }

  function summary(count, ms) {
    const songs = count === 1 ? '1 song' : `${count} songs`;
    if (!ms) return songs;
    const mins = Math.round(ms / 60000);
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    const len = h && m ? `${h} hr ${m} min` : h ? `${h} hr` : `${m} min`;
    return `${songs} · ${len}`;
  }

  function toast(message) {
    const el = $('#toast');
    el.textContent = message;
    el.hidden = false;
    clearTimeout(toast.t);
    toast.t = setTimeout(() => { el.hidden = true; }, 2200);
  }

  /* ---------------- tag reading (ID3v2) ---------------- */

  function decodeText(bytes, encoding) {
    try {
      if (encoding === 1) {
        const hasBom = bytes[0] === 0xff && bytes[1] === 0xfe;
        const be = bytes[0] === 0xfe && bytes[1] === 0xff;
        const body = hasBom || be ? bytes.subarray(2) : bytes;
        return new TextDecoder(be ? 'utf-16be' : 'utf-16le').decode(body).replace(/\u0000.*$/, '').trim();
      }
      if (encoding === 2) return new TextDecoder('utf-16be').decode(bytes).replace(/\u0000.*$/, '').trim();
      if (encoding === 3) return new TextDecoder('utf-8').decode(bytes).replace(/\u0000.*$/, '').trim();
      return new TextDecoder('iso-8859-1').decode(bytes).replace(/\u0000.*$/, '').trim();
    } catch (err) {
      return '';
    }
  }

  function syncsafe(b, i) {
    return (b[i] & 0x7f) * 0x200000 + (b[i + 1] & 0x7f) * 0x4000 + (b[i + 2] & 0x7f) * 0x80 + (b[i + 3] & 0x7f);
  }

  async function readTags(file) {
    const tags = { title: '', artist: '', album: '', art: null };
    try {
      const head = new Uint8Array(await file.slice(0, 10).arrayBuffer());
      if (String.fromCharCode(head[0], head[1], head[2]) !== 'ID3') return tags;
      const version = head[3];
      const size = syncsafe(head, 6);
      const body = new Uint8Array(await file.slice(10, 10 + size).arrayBuffer());
      let i = 0;
      while (i + 10 <= body.length) {
        const id = String.fromCharCode(body[i], body[i + 1], body[i + 2], body[i + 3]);
        if (!/^[A-Z0-9]{4}$/.test(id)) break;
        const frameSize = version >= 4 ? syncsafe(body, i + 4) : (body[i + 4] << 24) | (body[i + 5] << 16) | (body[i + 6] << 8) | body[i + 7];
        if (frameSize <= 0 || i + 10 + frameSize > body.length) break;
        const data = body.subarray(i + 10, i + 10 + frameSize);
        if (id === 'TIT2') tags.title = decodeText(data.subarray(1), data[0]);
        else if (id === 'TPE1') tags.artist = decodeText(data.subarray(1), data[0]);
        else if (id === 'TALB') tags.album = decodeText(data.subarray(1), data[0]);
        else if (id === 'APIC' && !tags.art) {
          const enc = data[0];
          let p = 1;
          while (p < data.length && data[p] !== 0) p++; // mime
          const mime = new TextDecoder('iso-8859-1').decode(data.subarray(1, p)) || 'image/jpeg';
          p += 1; // null
          p += 1; // picture type
          if (enc === 1 || enc === 2) {
            while (p + 1 < data.length && !(data[p] === 0 && data[p + 1] === 0)) p += 2;
            p += 2;
          } else {
            while (p < data.length && data[p] !== 0) p++;
            p += 1;
          }
          if (p < data.length) tags.art = URL.createObjectURL(new Blob([data.subarray(p)], { type: mime }));
        }
        i += 10 + frameSize;
      }
    } catch (err) {
      /* a broken tag is not a broken song */
    }
    return tags;
  }

  function splitName(name) {
    const clean = name.replace(/\.[a-z0-9]+$/i, '').replace(/_/g, ' ').trim();
    const m = clean.match(/^(.{1,60}?)\s+[-–—]\s+(.+)$/);
    if (m) return { artist: m[1].trim(), title: m[2].trim() };
    return { artist: '', title: clean };
  }

  /* ---------------- library ---------------- */

  const AUDIO_EXT = /\.(mp3|m4a|m4b|aac|flac|ogg|oga|opus|wav|wma|aif|aiff|mp4)$/i;

  function isAudio(file) {
    return (file.type && file.type.startsWith('audio/')) || AUDIO_EXT.test(file.name);
  }

  async function addFiles(fileList) {
    const files = Array.from(fileList).filter(isAudio);
    if (!files.length) {
      toast('No audio files in that selection');
      return;
    }
    toast(`Reading ${files.length} file${files.length === 1 ? '' : 's'}…`);
    const added = [];
    for (const file of files) {
      const tags = await readTags(file);
      const guess = splitName(file.name);
      const rel = file.webkitRelativePath || '';
      const folder = rel ? rel.split('/').slice(0, -1).join('/') || 'Folder' : 'Files you picked';
      added.push({
        id: `${file.name}:${file.size}:${file.lastModified}:${Math.random().toString(36).slice(2, 7)}`,
        file,
        url: URL.createObjectURL(file),
        title: tags.title || guess.title || file.name,
        artist: tags.artist || guess.artist || '',
        album: tags.album || '',
        folder,
        art: tags.art,
        duration: 0,
      });
    }
    state.tracks = state.tracks.concat(added);
    state.tracks.sort((a, b) => a.title.localeCompare(b.title));
    render();
    probeDurations(added);
    toast(`${added.length} track${added.length === 1 ? '' : 's'} added`);
  }

  async function probeDurations(tracks) {
    let cursor = 0;
    const workers = Array.from({ length: 4 }, async () => {
      while (cursor < tracks.length) {
        const track = tracks[cursor++];
        track.duration = await probe(track.url);
        if (state.view === 'songs' && !state.drill) render();
        else scheduleLightUpdate();
      }
    });
    await Promise.all(workers);
    render();
  }

  let lightUpdate = 0;
  function scheduleLightUpdate() {
    if (lightUpdate) return;
    lightUpdate = requestAnimationFrame(() => {
      lightUpdate = 0;
      if (state.view === 'songs' && !state.drill) render();
    });
  }

  function probe(url) {
    return new Promise((resolve) => {
      const a = new Audio();
      let settled = false;
      const finish = (value) => {
        if (settled) return;
        settled = true;
        a.removeAttribute('src');
        a.load();
        resolve(value);
      };
      a.preload = 'metadata';
      a.onloadedmetadata = () => finish(isFinite(a.duration) ? Math.round(a.duration * 1000) : 0);
      a.onerror = () => finish(0);
      setTimeout(() => finish(0), 7000);
      a.src = url;
    });
  }

  /* ---------------- derived collections ---------------- */

  const byTitle = (a, b) => a.title.localeCompare(b.title);

  function visibleTracks() {
    const q = state.query.trim().toLowerCase();
    if (!q) return state.tracks;
    return state.tracks.filter((t) =>
      t.title.toLowerCase().includes(q) ||
      (t.artist || '').toLowerCase().includes(q) ||
      (t.album || '').toLowerCase().includes(q));
  }

  function albums() {
    const map = new Map();
    for (const t of visibleTracks()) {
      const key = (t.album || 'Unknown album').toLowerCase();
      if (!map.has(key)) map.set(key, { title: t.album || 'Unknown album', artist: t.artist || 'Unknown artist', tracks: [], art: t.art });
      const entry = map.get(key);
      entry.tracks.push(t);
      if (!entry.art && t.art) entry.art = t.art;
    }
    return Array.from(map.values()).sort((a, b) => a.title.localeCompare(b.title));
  }

  function artists() {
    const map = new Map();
    for (const t of visibleTracks()) {
      const key = t.artist || 'Unknown artist';
      if (!map.has(key)) map.set(key, { name: key, tracks: [] });
      map.get(key).tracks.push(t);
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }

  function folders() {
    const map = new Map();
    for (const t of visibleTracks()) {
      if (!map.has(t.folder)) map.set(t.folder, { path: t.folder, name: t.folder.split('/').filter(Boolean).pop() || t.folder, tracks: [] });
      map.get(t.folder).tracks.push(t);
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }

  /* ---------------- rendering ---------------- */

  function artMarkup(track, cls) {
    const style = track && track.art ? ` style="background-image:url('${track.art}')"` : '';
    const mark = track && track.art ? '' : esc((track && (track.title || '♪')).trim().charAt(0).toUpperCase() || '♪');
    return `<span class="art ${cls}"${style}>${mark}</span>`;
  }

  function trackRow(track, list) {
    const current = state.queue[state.index] && state.queue[state.index].id === track.id;
    return `<button class="row${current ? ' is-current' : ''}" data-track="${track.id}" data-list="${list}">
      ${artMarkup(track, 'art-sm')}
      <span class="row-text">
        <span class="row-title">${esc(track.title)}</span>
        <span class="row-sub">${esc(track.artist || 'Unknown artist')}</span>
      </span>
      <span class="row-time">${fmt(track.duration)}</span>
    </button>`;
  }

  function render() {
    const list = $('#list');
    const empty = $('#empty');
    const hasTracks = state.tracks.length > 0;

    empty.hidden = hasTracks;
    list.hidden = !hasTracks;
    if (!hasTracks) {
      list.innerHTML = '';
      return;
    }

    if (state.drill) {
      const d = state.drill;
      const total = d.tracks.reduce((sum, t) => sum + (t.duration || 0), 0);
      list.innerHTML = `
        <div class="drill-head">
          <button class="icon-btn" id="drillBack" aria-label="Back">
            <svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>
          </button>
          <div style="flex:1">
            <h2>${esc(d.title)}</h2>
            <p>${esc(summary(d.tracks.length, total))}</p>
          </div>
          <button class="btn" id="drillPlay">Play all</button>
        </div>
        ${d.tracks.map((t) => trackRow(t, 'drill')).join('')}`;
      $('#drillBack').onclick = () => { state.drill = null; render(); };
      $('#drillPlay').onclick = () => playList(d.tracks, 0);
      return;
    }

    if (state.view === 'songs') {
      const tracks = visibleTracks();
      list.innerHTML = tracks.length
        ? tracks.map((t) => trackRow(t, 'songs')).join('')
        : `<p class="hint" style="padding:24px 16px;color:var(--muted)">Nothing matches “${esc(state.query)}”.</p>`;
      return;
    }

    if (state.view === 'albums') {
      const items = albums();
      list.innerHTML = `<div class="grid">${items.map((al) => `
        <button class="card" data-album="${esc(al.title)}">
          ${artMarkup({ title: al.title, art: al.art }, '')}
          <span class="card-title">${esc(al.title)}</span>
          <span class="card-sub">${esc(al.artist)}</span>
        </button>`).join('')}</div>`;
      return;
    }

    if (state.view === 'artists') {
      list.innerHTML = artists().map((ar) => `
        <button class="row" data-artist="${esc(ar.name)}">
          ${artMarkup({ title: ar.name }, 'art-sm art-round')}
          <span class="row-text">
            <span class="row-title">${esc(ar.name)}</span>
            <span class="row-sub">${esc(summary(ar.tracks.length, ar.tracks.reduce((s, t) => s + (t.duration || 0), 0)))}</span>
          </span>
        </button>`).join('');
      return;
    }

    if (state.view === 'folders') {
      list.innerHTML = folders().map((f) => `
        <button class="row" data-folder="${esc(f.path)}">
          ${artMarkup({ title: f.name }, 'art-sm')}
          <span class="row-text">
            <span class="row-title">${esc(f.name)}</span>
            <span class="row-sub">${esc(summary(f.tracks.length, f.tracks.reduce((s, t) => s + (t.duration || 0), 0)))}</span>
          </span>
        </button>`).join('');
      return;
    }

    if (state.view === 'queue') {
      list.innerHTML = state.queue.length
        ? `<p class="section-title">Up next</p>` + state.queue.map((t) => trackRow(t, 'queue')).join('')
        : `<p class="hint" style="padding:24px 16px;color:var(--muted)">The queue is empty. Play something and it lands here.</p>`;
    }
  }

  /* ---------------- playback ---------------- */

  function playList(tracks, startIndex) {
    if (!tracks.length) return;
    state.queue = tracks.slice();
    state.index = Math.max(0, Math.min(startIndex, tracks.length - 1));
    loadCurrent(true);
  }

  function loadCurrent(autoplay) {
    const track = state.queue[state.index];
    if (!track) return;
    audio.src = track.url;
    if (autoplay) audio.play().catch(() => toast('Tap play to start — the browser blocked autoplay'));
    paintNowPlaying();
    updateMediaSession(track);
    if (state.view === 'queue' || state.view === 'songs') render();
  }

  function paintNowPlaying() {
    const track = state.queue[state.index];
    const title = track ? track.title : 'Nothing playing';
    const artist = track ? (track.artist || 'Unknown artist') : 'Pick a song to start';

    $('#miniTitle').textContent = title;
    $('#miniArtist').textContent = artist;
    $('#playerTitle').textContent = title;
    $('#playerArtist').textContent = track ? [track.artist, track.album].filter(Boolean).join(' · ') || 'Unknown artist' : '—';

    for (const el of ['#miniArt', '#playerArt']) {
      const node = $(el);
      node.textContent = track && !track.art ? (title.trim().charAt(0).toUpperCase() || '♪') : '';
      node.style.backgroundImage = track && track.art ? `url('${track.art}')` : '';
    }
    $('#mini').hidden = !track;
  }

  function toggle() {
    if (!state.queue.length) {
      if (state.tracks.length) playList(state.shuffle ? shuffled(state.tracks) : state.tracks, 0);
      return;
    }
    if (audio.paused) audio.play().catch(() => { });
    else audio.pause();
  }

  function shuffled(list) {
    const copy = list.slice();
    for (let i = copy.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [copy[i], copy[j]] = [copy[j], copy[i]];
    }
    return copy;
  }

  function next() {
    if (!state.queue.length) return;
    if (state.shuffle && state.queue.length > 1) {
      let candidate = state.index;
      while (candidate === state.index) candidate = Math.floor(Math.random() * state.queue.length);
      state.index = candidate;
      return loadCurrent(true);
    }
    if (state.index < state.queue.length - 1) {
      state.index += 1;
      return loadCurrent(true);
    }
    if (state.repeat === 'all') {
      state.index = 0;
      return loadCurrent(true);
    }
    audio.pause();
    audio.currentTime = 0;
  }

  function prev() {
    if (!state.queue.length) return;
    if (audio.currentTime > 3) {
      audio.currentTime = 0;
      return;
    }
    state.index = state.index > 0 ? state.index - 1 : (state.repeat === 'all' ? state.queue.length - 1 : 0);
    loadCurrent(true);
  }

  function setPlayIcon(playing) {
    const path = playing ? '<path d="M8 5h3v14H8zM13 5h3v14h-3z"/>' : '<path d="M8 5v14l11-7z"/>';
    $('#playIcon').innerHTML = path;
    $('#miniPlayIcon').innerHTML = path;
    $('#playBtn').setAttribute('aria-label', playing ? 'Pause' : 'Play');
    $('#miniPlay').setAttribute('aria-label', playing ? 'Pause' : 'Play');
  }

  /* ---------------- media session ---------------- */

  function updateMediaSession(track) {
    if (!('mediaSession' in navigator)) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.title,
        artist: track.artist || 'Unknown artist',
        album: track.album || '',
        artwork: track.art ? [{ src: track.art, sizes: '512x512' }] : [],
      });
    } catch (err) { /* older browsers */ }
  }

  if ('mediaSession' in navigator) {
    navigator.mediaSession.setActionHandler('play', () => audio.play());
    navigator.mediaSession.setActionHandler('pause', () => audio.pause());
    navigator.mediaSession.setActionHandler('nexttrack', next);
    navigator.mediaSession.setActionHandler('previoustrack', prev);
  }

  /* ---------------- equalizer ---------------- */

  const eq = { ctx: null, bands: [], gains: [0, 0, 0, 0, 0], preset: 'flat' };
  const EQ_FREQS = [60, 230, 910, 3600, 14000];
  const EQ_PRESETS = {
    flat: [0, 0, 0, 0, 0],
    bass: [9, 6, 1, 0, 0],
    vocal: [-2, 1, 5, 4, 1],
    treble: [0, 0, 2, 6, 9],
    warm: [5, 3, 0, -1, -2],
  };

  function ensureGraph() {
    if (eq.ctx) return eq.ctx;
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return null;
    eq.ctx = new Ctx();
    const source = eq.ctx.createMediaElementSource(audio);
    let node = source;
    eq.bands = EQ_FREQS.map((freq, i) => {
      const filter = eq.ctx.createBiquadFilter();
      filter.type = i === 0 ? 'lowshelf' : i === EQ_FREQS.length - 1 ? 'highshelf' : 'peaking';
      filter.frequency.value = freq;
      filter.Q.value = 1;
      filter.gain.value = eq.gains[i];
      node.connect(filter);
      node = filter;
      return filter;
    });
    node.connect(eq.ctx.destination);
    return eq.ctx;
  }

  function applyGains(gains) {
    eq.gains = gains.slice();
    ensureGraph();
    eq.bands.forEach((filter, i) => { filter.gain.value = eq.gains[i]; });
    $$('#bands input').forEach((input, i) => { input.value = eq.gains[i]; });
  }

  function buildBands() {
    const holder = $('#bands');
    holder.innerHTML = EQ_FREQS.map((freq, i) => `
      <label class="band">
        <input type="range" min="-12" max="12" step="1" value="${eq.gains[i]}" data-band="${i}">
        <span>${freq >= 1000 ? (freq / 1000) + 'k' : freq}</span>
      </label>`).join('');
    $$('#bands input').forEach((input) => {
      input.addEventListener('input', () => {
        eq.preset = 'custom';
        applyGains($$('#bands input').map((el) => Number(el.value)));
        paintPills('#eqPresets', 'eq', 'custom');
      });
    });
  }

  /* ---------------- appearance ---------------- */

  const PREF_KEY = 'nijatva.music.prefs';

  function loadPrefs() {
    try {
      const saved = JSON.parse(localStorage.getItem(PREF_KEY) || '{}');
      if (saved.theme) document.documentElement.dataset.theme = saved.theme;
      if (saved.accent) document.documentElement.dataset.accent = saved.accent;
      if (typeof saved.volume === 'number') { audio.volume = saved.volume; $('#volume').value = String(Math.round(saved.volume * 100)); }
      if (saved.eqPreset && EQ_PRESETS[saved.eqPreset]) { eq.preset = saved.eqPreset; eq.gains = EQ_PRESETS[saved.eqPreset].slice(); }
      paintPills('#themeRow', 'theme', document.documentElement.dataset.theme || 'black');
      paintPills('#accentRow', 'accent', document.documentElement.dataset.accent || 'saffron');
      paintPills('#eqPresets', 'eq', eq.preset);
    } catch (err) { /* first run */ }
  }

  function savePrefs() {
    try {
      localStorage.setItem(PREF_KEY, JSON.stringify({
        theme: document.documentElement.dataset.theme,
        accent: document.documentElement.dataset.accent,
        volume: audio.volume,
        eqPreset: eq.preset,
      }));
    } catch (err) { /* private mode */ }
  }

  function paintPills(container, key, value) {
    $$(`${container} .pill`).forEach((pill) => {
      pill.classList.toggle('is-active', pill.dataset[key] === value);
    });
  }

  /* ---------------- sheets ---------------- */

  function openSheet(id) {
    $(id).hidden = false;
  }

  function closeSheets() {
    $$('.sheet').forEach((sheet) => { sheet.hidden = true; });
    $('#menu').hidden = true;
  }

  /* ---------------- wiring ---------------- */

  function wire() {
    $('#pickFiles').onclick = () => $('#fileInput').click();
    $('#pickFolder').onclick = () => $('#folderInput').click();
    $('#fileInput').onchange = (e) => { addFiles(e.target.files); e.target.value = ''; };
    $('#folderInput').onchange = (e) => { addFiles(e.target.files); e.target.value = ''; };

    $('#searchBtn').onclick = () => {
      const bar = $('#searchbar');
      bar.hidden = !bar.hidden;
      if (!bar.hidden) $('#search').focus();
      else { state.query = ''; $('#search').value = ''; render(); }
    };
    $('#searchClose').onclick = () => { $('#searchbar').hidden = true; state.query = ''; $('#search').value = ''; render(); };
    $('#search').oninput = (e) => { state.query = e.target.value; render(); };

    $('#menuBtn').onclick = (e) => {
      e.stopPropagation();
      const menu = $('#menu');
      menu.hidden = !menu.hidden;
    };

    $('#menu').onclick = (e) => {
      const action = e.target.closest('button') && e.target.closest('button').dataset.action;
      if (!action) return;
      $('#menu').hidden = true;
      if (action === 'files') $('#fileInput').click();
      if (action === 'folder') $('#folderInput').click();
      if (action === 'stream') {
        const url = prompt('Paste a direct audio stream URL (http or https):');
        if (url) {
          const track = { id: url, url, title: 'Stream', artist: url.replace(/^https?:\/\//, '').split('/')[0], album: '', folder: 'Streams', art: null, duration: 0 };
          playList([track], 0);
        }
      }
      if (action === 'shuffle') {
        state.shuffle = true;
        $('#shuffleBtn').classList.add('is-on');
        playList(shuffled(state.tracks), 0);
      }
      if (action === 'sleep') openSheet('#sleepSheet');
      if (action === 'eq') { ensureGraph(); openSheet('#eqSheet'); }
      if (action === 'appearance') openSheet('#appearanceSheet');
      if (action === 'install') promptInstall();
    };

    $$('.tabs .tab').forEach((tab) => {
      tab.onclick = () => {
        state.view = tab.dataset.view;
        state.drill = null;
        $$('.tabs .tab').forEach((t) => t.classList.toggle('is-active', t === tab));
        render();
      };
    });

    $('#list').onclick = (e) => {
      const row = e.target.closest('[data-track]');
      if (row) {
        const id = row.dataset.track;
        const listName = row.dataset.list;
        let list = state.tracks;
        if (listName === 'queue') list = state.queue;
        if (listName === 'drill') list = state.drill.tracks;
        const index = list.findIndex((t) => t.id === id);
        if (index >= 0) {
          if (listName === 'queue') { state.index = index; loadCurrent(true); }
          else playList(list, index);
        }
        return;
      }
      const album = e.target.closest('[data-album]');
      if (album) {
        const found = albums().find((a) => a.title === album.dataset.album);
        if (found) { state.drill = found; render(); }
        return;
      }
      const artist = e.target.closest('[data-artist]');
      if (artist) {
        const found = artists().find((a) => a.name === artist.dataset.artist);
        if (found) { state.drill = { title: found.name, tracks: found.tracks }; render(); }
        return;
      }
      const folder = e.target.closest('[data-folder]');
      if (folder) {
        const found = folders().find((f) => f.path === folder.dataset.folder);
        if (found) { state.drill = { title: found.name, tracks: found.tracks }; render(); }
      }
    };

    $('#miniPlay').onclick = (e) => { e.stopPropagation(); toggle(); };
    $('#miniNext').onclick = (e) => { e.stopPropagation(); next(); };
    $('#miniOpen').onclick = () => { $('#player').hidden = false; };
    $('#playerClose').onclick = () => { $('#player').hidden = true; };

    $('#playBtn').onclick = toggle;
    $('#nextBtn').onclick = next;
    $('#prevBtn').onclick = prev;

    $('#shuffleBtn').onclick = () => {
      state.shuffle = !state.shuffle;
      $('#shuffleBtn').classList.toggle('is-on', state.shuffle);
    };

    $('#repeatBtn').onclick = () => {
      state.repeat = state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off';
      $('#repeatBtn').classList.toggle('is-on', state.repeat !== 'off');
      audio.loop = state.repeat === 'one';
      toast(state.repeat === 'off' ? 'Repeat off' : state.repeat === 'all' ? 'Repeat queue' : 'Repeat one');
    };

    $('#volume').oninput = (e) => { audio.volume = Number(e.target.value) / 100; savePrefs(); };

    $('#sleepBtn').onclick = () => openSheet('#sleepSheet');
    $('#eqBtn').onclick = () => { ensureGraph(); openSheet('#eqSheet'); };

    $('#sleepOptions').onclick = (e) => {
      const pill = e.target.closest('[data-min]');
      if (!pill) return;
      startSleep(Number(pill.dataset.min));
      closeSheets();
    };
    $('#sleepOff').onclick = () => { startSleep(0); closeSheets(); };

    $('#themeRow').onclick = (e) => {
      const pill = e.target.closest('[data-theme]');
      if (!pill) return;
      document.documentElement.dataset.theme = pill.dataset.theme;
      paintPills('#themeRow', 'theme', pill.dataset.theme);
      savePrefs();
    };

    $('#accentRow').onclick = (e) => {
      const pill = e.target.closest('[data-accent]');
      if (!pill) return;
      document.documentElement.dataset.accent = pill.dataset.accent;
      paintPills('#accentRow', 'accent', pill.dataset.accent);
      savePrefs();
    };

    $('#eqPresets').onclick = (e) => {
      const pill = e.target.closest('[data-eq]');
      if (!pill) return;
      eq.preset = pill.dataset.eq;
      applyGains(EQ_PRESETS[eq.preset]);
      paintPills('#eqPresets', 'eq', eq.preset);
      savePrefs();
    };

    $('#appearanceDone').onclick = closeSheets;
    $('#eqDone').onclick = closeSheets;
    $$('.sheet').forEach((sheet) => {
      sheet.addEventListener('click', (e) => { if (e.target === sheet) closeSheets(); });
    });

    document.addEventListener('click', (e) => {
      if (!e.target.closest('#menu') && !e.target.closest('#menuBtn')) $('#menu').hidden = true;
    });

    audio.addEventListener('play', () => { setPlayIcon(true); ensureGraph(); if (eq.ctx && eq.ctx.state === 'suspended') eq.ctx.resume(); });
    audio.addEventListener('pause', () => setPlayIcon(false));
    audio.addEventListener('ended', () => { if (state.repeat !== 'one') next(); });
    audio.addEventListener('timeupdate', () => {
      const dur = audio.duration && isFinite(audio.duration) ? audio.duration * 1000 : (state.queue[state.index] ? state.queue[state.index].duration : 0);
      const pos = audio.currentTime * 1000;
      $('#seek').value = dur ? String(Math.round((pos / dur) * 1000)) : '0';
      $('#timeNow').textContent = fmt(pos);
      $('#timeTotal').textContent = fmt(dur);
      $('#miniProgress').style.width = dur ? `${Math.min(100, (pos / dur) * 100)}%` : '0%';
    });
    audio.addEventListener('loadedmetadata', () => {
      const track = state.queue[state.index];
      if (track && isFinite(audio.duration)) track.duration = Math.round(audio.duration * 1000);
    });

    $('#seek').oninput = (e) => {
      const dur = audio.duration && isFinite(audio.duration) ? audio.duration : 0;
      if (dur) audio.currentTime = (Number(e.target.value) / 1000) * dur;
    };

    document.addEventListener('keydown', (e) => {
      if (e.target.matches('input, textarea')) return;
      if (e.key === ' ') { e.preventDefault(); toggle(); }
      if (e.key === 'ArrowRight') audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 5);
      if (e.key === 'ArrowLeft') audio.currentTime = Math.max(0, audio.currentTime - 5);
      if (e.key === 'n') next();
      if (e.key === 'p') prev();
      if (e.key === 's') $('#shuffleBtn').click();
      if (e.key === 'r') $('#repeatBtn').click();
      if (e.key === 'm') audio.muted = !audio.muted;
      if (e.key === '/') { e.preventDefault(); $('#searchbar').hidden = false; $('#search').focus(); }
      if (e.key === 'Escape') { closeSheets(); $('#player').hidden = true; }
    });

    document.addEventListener('dragover', (e) => { e.preventDefault(); });
    document.addEventListener('drop', async (e) => {
      e.preventDefault();
      const items = e.dataTransfer.items;
      if (items && items.length && items[0].webkitGetAsEntry) {
        const files = [];
        const readAll = (reader) => new Promise((resolve) => {
          const out = [];
          const step = () => reader.readEntries((entries) => {
            if (!entries.length) return resolve(out);
            out.push(...entries);
            step();
          }, () => resolve(out));
          step();
        });
        const walk = async (entry) => {
          if (entry.isFile) {
            await new Promise((resolve) => entry.file((f) => { files.push(f); resolve(); }, resolve));
          } else if (entry.isDirectory) {
            const children = await readAll(entry.createReader());
            for (const child of children) await walk(child);
          }
        };
        for (const item of Array.from(items)) {
          const entry = item.webkitGetAsEntry();
          if (entry) await walk(entry);
        }
        if (files.length) addFiles(files);
      } else if (e.dataTransfer.files && e.dataTransfer.files.length) {
        addFiles(e.dataTransfer.files);
      }
    });
  }

  function startSleep(minutes) {
    clearInterval(state.sleepTimer);
    state.sleepMinutes = minutes;
    state.sleepLeft = minutes * 60;
    const chip = $('#sleepBtn');
    if (!minutes) {
      chip.classList.remove('is-on');
      chip.textContent = 'Sleep';
      return;
    }
    chip.classList.add('is-on');
    const paint = () => {
      const m = Math.floor(state.sleepLeft / 60);
      const s = state.sleepLeft % 60;
      chip.textContent = `Sleep ${m}:${String(s).padStart(2, '0')}`;
    };
    paint();
    state.sleepTimer = setInterval(() => {
      state.sleepLeft -= 1;
      if (state.sleepLeft <= 0) {
        clearInterval(state.sleepTimer);
        audio.pause();
        chip.classList.remove('is-on');
        chip.textContent = 'Sleep';
        toast('Sleep timer finished — playback stopped');
        return;
      }
      paint();
    }, 1000);
  }

  /* ---------------- install as an app ---------------- */

  let installEvent = null;

  function promptInstall() {
    if (installEvent) {
      installEvent.prompt();
      installEvent.userChoice.then((choice) => {
        if (choice.outcome === 'accepted') {
          installEvent = null;
          $('#installBtn').hidden = true;
        }
      }).catch(() => {});
      return;
    }
    toast('In Chrome: menu ⋮ → “Add to Home screen”');
  }

  function wireInstall() {
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      installEvent = event;
      $('#installBtn').hidden = false;
    });
    window.addEventListener('appinstalled', () => {
      installEvent = null;
      $('#installBtn').hidden = true;
      toast('Installed — open Music from your home screen');
    });
    $('#installBtn').onclick = promptInstall;
    if ('serviceWorker' in navigator && location.protocol !== 'file:') {
      window.addEventListener('load', () => {
        navigator.serviceWorker.register('sw.js').catch(() => {});
      });
    }
  }

  /* ---------------- boot ---------------- */

  buildBands();
  loadPrefs();
  wire();
  wireInstall();
  paintNowPlaying();
  render();
})();
