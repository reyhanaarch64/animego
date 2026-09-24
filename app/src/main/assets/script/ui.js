(function () {
  'use strict';
  var RH = window.RH = window.RH || {};
  var toast;
  var theme = 'dark';

  RH.ui = {
    screen: null,
    boot: function () {
      this.screen = document.getElementById('screen');
      toast = document.getElementById('toast');
      var saved = '';
      try { saved = localStorage.getItem('animego.theme') || ''; } catch (e) {}
      this.applyTheme(saved === 'light' ? 'light' : 'dark', false);
    },
    esc: function (v) {
      return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
        return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
      });
    },
    icon: function (name) {
      var map = {
        home: 'fa-solid fa-house', browse: 'fa-solid fa-compass', download: 'fa-solid fa-download',
        trash: 'fa-solid fa-trash-can', heart: 'fa-regular fa-heart', heartOn: 'fa-solid fa-heart',
        back: 'fa-solid fa-chevron-left', search: 'fa-solid fa-magnifying-glass', check: 'fa-solid fa-check',
        play: 'fa-solid fa-play', pause: 'fa-solid fa-pause', forward: 'fa-solid fa-forward-step',
        backward: 'fa-solid fa-backward-step', fullscreen: 'fa-solid fa-expand', minimize: 'fa-solid fa-compress',
        volume: 'fa-solid fa-volume-high', mute: 'fa-solid fa-volume-xmark', speed: 'fa-solid fa-gauge-high',
        calendar: 'fa-solid fa-calendar-days', genre: 'fa-solid fa-layer-group', filter: 'fa-solid fa-sliders', landscape: 'fa-solid fa-tablet-screen-button', sun: 'fa-solid fa-sun', moon: 'fa-solid fa-moon',
        clock: 'fa-solid fa-clock-rotate-left', movie: 'fa-solid fa-film', fire: 'fa-solid fa-fire',
        star: 'fa-solid fa-star', arrow: 'fa-solid fa-arrow-right', retry: 'fa-solid fa-rotate-right',
        folder: 'fa-solid fa-folder-open', info: 'fa-solid fa-circle-info', skull: 'fa-solid fa-skull', close: 'fa-solid fa-xmark',
        external: 'fa-solid fa-arrow-up-right-from-square', shield: 'fa-solid fa-shield-halved',
        chevron: 'fa-solid fa-chevron-right', tag: 'fa-solid fa-tag'
      };
      return '<i class="' + (map[name] || 'fa-solid fa-circle') + '" aria-hidden="true"></i>';
    },
    genreIcon: function (name) {
      var key = String(name || '').toLowerCase();
      if (key.indexOf('action') >= 0) return this.icon('fire');
      if (key.indexOf('comedy') >= 0 || key.indexOf('komedi') >= 0) return this.icon('star');
      if (key.indexOf('romance') >= 0) return this.icon('heart');
      if (key.indexOf('horror') >= 0 || key.indexOf('gore') >= 0) return this.icon('skull');
      if (key.indexOf('adventure') >= 0 || key.indexOf('petualangan') >= 0) return this.icon('arrow');
      if (key.indexOf('sci') >= 0) return this.icon('filter');
      if (key.indexOf('fantasy') >= 0) return this.icon('star');
      return this.icon('tag');
    },
    metaLine: function (a) {
      var p = [];
      if (a.episode) p.push(this.icon('movie') + ' ' + this.esc('Ep ' + a.episode));
      if (a.score) p.push(this.icon('star') + ' ' + this.esc(a.score));
      if (a.type) p.push(this.esc(a.type));
      if (a.status) p.push(this.esc(a.status));
      return p.join(' <span class="meta-sep">·</span> ');
    },
    card: function (a) {
      return '<article class="media-card" data-open="anime" data-slug="' + this.esc(a.slug) + '">' +
        '<div class="poster">' + (a.cover ? '<img src="' + this.esc(a.cover) + '" alt="" loading="lazy">' : '<div class="poster-ph">Tanpa sampul</div>') + '</div>' +
        '<div class="card-title">' + this.esc(a.title) + '</div><div class="card-meta">' + this.metaLine(a) + '</div></article>';
    },
    rail: function (title, items, route) {
      if (!items || !items.length) return '';
      var html = '<section class="section"><div class="section-head"><h2>' + this.esc(title) + '</h2>' + (route ? '<button class="text-button" data-nav="' + route + '">Lihat semua ' + this.icon('arrow') + '</button>' : '') + '</div><div class="rail">';
      for (var i = 0; i < items.length; i++) html += this.card(items[i]);
      return html + '</div></section>';
    },
    grid: function (items) {
      if (!items || !items.length) return '<div class="empty-state"><div class="state-icon">' + this.icon('search') + '</div><h2>Data tidak ditemukan</h2><p>Belum ada data yang sesuai dengan permintaan.</p></div>';
      var html = '<div class="grid">';
      for (var i = 0; i < items.length; i++) html += this.card(items[i]);
      return html + '</div>';
    },
    infoRow: function (label, value) {
      if (value == null || value === '') return '';
      return '<div class="info-row"><span>' + this.esc(label) + '</span><strong>' + this.esc(value) + '</strong></div>';
    },
    fmtClock: function (v) {
      var s = Math.max(0, Math.floor(Number(v) || 0));
      var h = Math.floor(s / 3600); s %= 3600;
      var m = Math.floor(s / 60); s %= 60;
      function pad(x) { return x < 10 ? '0' + x : String(x); }
      return h ? h + ':' + pad(m) + ':' + pad(s) : pad(m) + ':' + pad(s);
    },
    fmtBytes: function (value) {
      var n = Number(value) || 0;
      if (n < 1024) return n.toFixed(0) + ' B';
      var units = ['KB', 'MB', 'GB', 'TB'], i = -1;
      do { n /= 1024; i++; } while (n >= 1024 && i < units.length - 1);
      return n.toFixed(n >= 100 ? 0 : (n >= 10 ? 1 : 2)) + ' ' + units[i];
    },
    toast: function (message) {
      if (!toast) return;
      toast.textContent = message;
      toast.classList.add('show');
      clearTimeout(toast._timer);
      toast._timer = setTimeout(function () { toast.classList.remove('show'); }, 2200);
    },
    applyTheme: function (value, persist) {
      theme = value === 'light' ? 'light' : 'dark';
      document.body.classList.toggle('theme-dark', theme === 'dark');
      document.body.classList.toggle('theme-light', theme === 'light');
      var button = document.getElementById('themeBtn');
      if (button) {
        button.innerHTML = this.icon(theme === 'dark' ? 'sun' : 'moon');
        button.setAttribute('aria-label', theme === 'dark' ? 'Gunakan mode terang' : 'Gunakan mode gelap');
      }
      document.querySelector('meta[name="theme-color"]').setAttribute('content', theme === 'dark' ? '#0b1018' : '#f7f9fc');
      if (persist) {
        try { localStorage.setItem('animego.theme', theme); } catch (e) {}
      }
      if (RH.native) RH.native('theme', { dark: theme === 'dark' }).catch(function () {});
    },
    toggleTheme: function () {
      this.applyTheme(theme === 'dark' ? 'light' : 'dark', true);
    },
    setTop: function (title, back) {
      document.querySelector('.brand').textContent = title || 'AnimeGo';
      document.getElementById('backBtn').style.visibility = back ? 'visible' : 'hidden';
    },
    setActive: function (route) {
      var nodes = document.querySelectorAll('#bottomNav button');
      for (var i = 0; i < nodes.length; i++) nodes[i].classList.toggle('active', nodes[i].getAttribute('data-route') === route);
    },
    loading: function (title) {
      this.setTop(title || 'AnimeGo', true);
      this.screen.innerHTML = '<div class="loading"><div class="skeleton skeleton-hero"></div><div class="skeleton skeleton-title"></div><div class="skeleton skeleton-line"></div></div>';
    },
    error: function (message, retry) {
      var html = '<div class="state-card"><div class="state-icon">' + this.icon('info') + '</div><h2>Terjadi masalah</h2><p>' + this.esc(message || 'Data tidak dapat dimuat.') + '</p>' + (retry ? '<button class="primary-button" id="retryView">' + this.icon('retry') + ' Coba lagi</button>' : '') + '</div>';
      this.screen.innerHTML = html;
      if (retry) document.getElementById('retryView').onclick = retry;
    },
    modal: function (title, body, actions) {
      var el = document.getElementById('modal');
      var html = '<div class="sheet"><div class="sheet-grabber"></div><div class="sheet-head"><h2>' + this.esc(title) + '</h2><button class="sheet-close" id="closeModal" aria-label="Tutup">' + this.icon('close') + '</button></div><div class="sheet-body">' + body + '</div>';
      if (actions) html += '<div class="sheet-actions">' + actions + '</div>';
      html += '</div>';
      el.innerHTML = html;
      el.classList.remove('hidden');
      document.getElementById('closeModal').onclick = function () { RH.ui.closeModal(); };
      el.onclick = function (e) { if (e.target === el) RH.ui.closeModal(); };
    },
    closeModal: function () {
      var el = document.getElementById('modal');
      el.classList.add('hidden');
      el.innerHTML = '';
    },
    confirmModal: function (title, message, yesText, callback) {
      this.modal(title, '<p class="modal-copy">' + this.esc(message) + '</p>', '<button class="secondary-button" id="modalNo">' + this.icon('close') + ' Batal</button><button class="danger-button" id="modalYes">' + this.icon('check') + ' ' + this.esc(yesText || 'Lanjutkan') + '</button>');
      document.getElementById('modalNo').onclick = function () { RH.ui.closeModal(); };
      document.getElementById('modalYes').onclick = function () { RH.ui.closeModal(); callback(); };
    },
    pager: function (totalPages, page, onPage) {
      if (!totalPages || totalPages <= 1) return '';
      var html = '<div class="pager">';
      if (page > 1) html += '<button class="secondary-button" data-page="' + (page - 1) + '">' + this.icon('back') + ' Sebelumnya</button>';
      html += '<span class="page-counter">Halaman ' + page + ' / ' + totalPages + '</span>';
      if (page < totalPages) html += '<button class="secondary-button" data-page="' + (page + 1) + '">Berikutnya ' + this.icon('chevron') + '</button>';
      html += '</div>';
      this._pager = onPage;
      setTimeout(function () {
        var buttons = document.querySelectorAll('.pager [data-page]');
        for (var i = 0; i < buttons.length; i++) buttons[i].onclick = function () { RH.ui._pager(Number(this.getAttribute('data-page'))); };
      }, 0);
      return html;
    },
    bindCommon: function () {
      var nodes = document.querySelectorAll('[data-open="anime"]');
      for (var i = 0; i < nodes.length; i++) nodes[i].onclick = function () { RH.router.navigate('anime', { slug: this.getAttribute('data-slug') }); };
      nodes = document.querySelectorAll('[data-open="play"]');
      for (i = 0; i < nodes.length; i++) nodes[i].onclick = function () { RH.router.navigate('play', { slug: this.getAttribute('data-slug'), ep: this.getAttribute('data-ep'), t: 0 }); };
      nodes = document.querySelectorAll('[data-resume]');
      for (i = 0; i < nodes.length; i++) nodes[i].onclick = function () { RH.router.navigate('play', { slug: this.getAttribute('data-slug'), ep: this.getAttribute('data-ep'), t: this.getAttribute('data-t') }); };
      nodes = document.querySelectorAll('[data-nav]');
      for (i = 0; i < nodes.length; i++) nodes[i].onclick = function () { RH.router.navigate(this.getAttribute('data-nav')); };
    }
  };
})();
