(function () {
  'use strict';
  var RH = window.RH = window.RH || {};
  RH.views = {};
  var homeLoaded = false;
  var homeData = null;
  var detailCache = {};
  var suggestTimer = null;

  function historySection(items) {
    if (!items || !items.length) return '';
    var html = '<section class="section"><div class="section-head"><h2>Lanjutkan menonton</h2><button class="text-button" data-nav="history">Riwayat</button></div><div class="continue-rail">';
    for (var i = 0; i < items.length && i < 10; i++) {
      var x = items[i], pct = x.duration > 0 ? Math.min(100, Math.max(0, x.position / x.duration * 100)) : 0;
      html += '<article class="continue-card" data-resume="1" data-slug="' + RH.ui.esc(x.slug) + '" data-ep="' + RH.ui.esc(x.ep) + '" data-t="' + RH.ui.esc(x.position) + '"><div class="continue-cover">' +
        (x.cover ? '<img src="' + RH.ui.esc(x.cover) + '" alt="" loading="lazy">' : '') + '<div class="progress"><i style="width:' + pct + '%"></i></div></div><div class="continue-info"><strong>' + RH.ui.esc(x.title) + '</strong><small>Ep ' + RH.ui.esc(x.ep) + ' · ' + RH.ui.fmtClock(x.position) + '</small></div></article>';
    }
    return html + '</div></section>';
  }

  function renderListScreen(title, kind, page) {
    RH.ui.loading(title);
    RH.native('list', { kind: kind, page: page || 1, limit: 12 }).then(function (res) {
      var p = page || 1;
      RH.ui.setTop(title, true);
      RH.ui.screen.innerHTML = '<div class="screen-head"><div><h1 class="page-title">' + RH.ui.esc(title) + '</h1><p class="subtext">Menampilkan katalog AnimeGo.</p></div></div>' + RH.ui.grid(res.items || []) + RH.ui.pager(res.totalPages || 1, p, function (next) { renderListScreen(title, kind, next); });
      RH.ui.bindCommon();
    }).catch(function (e) { RH.ui.error(e.message, function () { renderListScreen(title, kind, page); }); });
  }

  RH.views.home = function () {
    RH.player.toMini();
    RH.ui.setActive('home');
    if (!homeLoaded) {
      RH.ui.loading('AnimeGo');
      RH.native('history').then(function (hist) {
        return RH.native('home').then(function (data) { return { history: hist.items || [], data: data }; });
      }).then(function (all) {
        homeData = all.data; homeLoaded = true; renderHome(all.history);
      }).catch(function (e) { RH.ui.error(e.message, function () { homeLoaded = false; RH.views.home(); }); });
    } else {
      RH.native('history').then(function (hist) { renderHome(hist.items || []); }).catch(function () { renderHome([]); });
    }
  };

  function renderHome(history) {
    RH.ui.setTop('', false);
    var h = homeData || {};
    var ongoing = h.ongoing && h.ongoing.items || [];
    var top = h.top && h.top.items || [];
    var movies = h.movies && h.movies.items || [];
    var completed = h.completed && h.completed.items || [];
    var featured = top.length ? top[0] : (ongoing[0] || null);
    var html = '<div class="welcome"><span class="eyebrow">ANIMEGO</span><h1>Temukan tontonan berikutnya.</h1><p>Streaming, riwayat, favorit, dan unduhan dalam satu tempat.</p></div>';
    if (featured) html += '<section class="hero-card"><div class="hero-bg">' + (featured.cover ? '<img src="' + RH.ui.esc(featured.cover) + '" alt="">' : '') + '</div><div class="hero-shade"></div><div class="hero-content"><span class="eyebrow">Pilihan Anime</span><h2>' + RH.ui.esc(featured.title) + '</h2><p>' + RH.ui.metaLine(featured) + '</p><button class="primary-button" data-open="anime" data-slug="' + RH.ui.esc(featured.slug) + '">' + RH.ui.icon('info') + ' Lihat detail</button></div></section>';
    html += historySection(history);
    html += RH.ui.rail('Sedang tayang', ongoing, 'browse');
    html += RH.ui.rail('Populer', top, 'browse');
    html += RH.ui.rail('Film', movies, 'browse');
    html += RH.ui.rail('Selesai', completed, 'browse');
    RH.ui.screen.innerHTML = html;
    RH.ui.bindCommon();
  }

  RH.views.browse = function (params) {
    RH.player.toMini();
    var kind = params.kind || 'latest', page = params.page || 1;
    RH.ui.setTop('Jelajah', true);
    RH.ui.setActive('browse');
    RH.ui.screen.innerHTML = '<h1 class="page-title">Jelajah</h1><div class="chip-scroll" id="browseFilters">' +
      filterButton('latest','Terbaru',kind,'clock') + filterButton('ongoing','Sedang tayang',kind,'fire') + filterButton('completed','Selesai',kind,'check') + filterButton('movies','Film',kind,'movie') + filterButton('top','Populer',kind,'star') + filterButton('oldest','Terlama',kind,'history') +
      '</div><div class="action-row"><button class="large-secondary" id="openGenres">' + RH.ui.icon('genre') + ' Genre</button><button class="large-secondary" id="openSchedule">' + RH.ui.icon('calendar') + ' Jadwal</button></div><div id="browseBody"></div>';
    var filters = document.querySelectorAll('#browseFilters [data-kind]');
    for (var i = 0; i < filters.length; i++) filters[i].onclick = function () { RH.router.navigate('browse', { kind: this.getAttribute('data-kind'), page: 1 }); };
    document.getElementById('openGenres').onclick = function () { RH.router.navigate('genres'); };
    document.getElementById('openSchedule').onclick = function () { RH.router.navigate('schedule', { day: 'senin', page: 1 }); };
    document.getElementById('browseBody').innerHTML = '<div class="loading-inline">Memuat katalog...</div>';
    RH.native('list', { kind: kind, page: page, limit: 12 }).then(function (res) {
      document.getElementById('browseBody').innerHTML = RH.ui.grid(res.items || []) + RH.ui.pager(res.totalPages || 1, page, function (p) { RH.router.navigate('browse', { kind: kind, page: p }, false); });
      RH.ui.bindCommon();
    }).catch(function (e) { RH.ui.error(e.message, function () { RH.views.browse(params); }); });
  };

  function filterButton(k, label, active, iconName) {
    return '<button class="chip-button ' + (k === active ? 'active' : '') + '" data-kind="' + k + '">' + RH.ui.icon(iconName || 'filter') + ' ' + label + '</button>';
  }

  RH.views.search = function (params) {
    RH.player.toMini();
    var q = params.q || '', page = params.page || 1;
    RH.ui.setTop('Pencarian', true);
    RH.ui.screen.innerHTML = '<h1 class="page-title">Cari anime</h1><div class="search-box"><input id="searchInput" autocomplete="off" value="' + RH.ui.esc(q) + '" placeholder="Judul anime"><button class="primary-button" id="searchSubmit">' + RH.ui.icon('search') + ' Cari</button></div><div id="suggestBox"></div><div id="searchResults"></div>';
    var input = document.getElementById('searchInput');
    var suggestBox = document.getElementById('suggestBox');
    function loadSuggest() {
      var value = input.value.trim();
      clearTimeout(suggestTimer);
      if (!value) { suggestBox.innerHTML = ''; return; }
      suggestTimer = setTimeout(function () {
        RH.native('suggest', { q: value, limit: 6 }).then(function (res) {
          var items = res.items || [], html = '<div class="suggest-panel">';
          for (var i = 0; i < items.length; i++) html += '<button class="suggest-item" data-suggest="' + RH.ui.esc(items[i].slug) + '"><div class="suggest-thumb">' + (items[i].cover ? '<img src="' + RH.ui.esc(items[i].cover) + '" alt="">' : '') + '</div><div><strong>' + RH.ui.esc(items[i].title) + '</strong><small>' + RH.ui.metaLine(items[i]) + '</small></div></button>';
          suggestBox.innerHTML = items.length ? html + '</div>' : '';
          var buttons = suggestBox.querySelectorAll('[data-suggest]');
          for (var j = 0; j < buttons.length; j++) buttons[j].onclick = function () { suggestBox.innerHTML = ''; RH.router.navigate('anime', { slug: this.getAttribute('data-suggest') }); };
        }).catch(function () { suggestBox.innerHTML = ''; });
      }, 280);
    }
    function submit() { var value = input.value.trim(); if (!value) return; RH.router.navigate('search', { q: value, page: 1 }); }
    input.oninput = loadSuggest;
    input.onkeydown = function (e) { if (e.keyCode === 13) submit(); };
    document.getElementById('searchSubmit').onclick = submit;
    if (q) {
      document.getElementById('searchResults').innerHTML = '<div class="loading-inline">Mencari...</div>';
      RH.native('search', { q: q, page: page, limit: 12 }).then(function (res) {
        document.getElementById('searchResults').innerHTML = RH.ui.grid(res.items || []) + RH.ui.pager(res.totalPages || 1, page, function (p) { RH.router.navigate('search', { q: q, page: p }, false); });
        RH.ui.bindCommon();
      }).catch(function (e) { RH.ui.error(e.message); });
    }
    setTimeout(function () { input.focus(); }, 100);
  };

  RH.views.anime = function (params) {
    RH.player.toMini();
    RH.ui.loading('Detail anime');
    var source = detailCache[params.slug] ? Promise.resolve(detailCache[params.slug]) : RH.native('detail', { key: params.slug });
    source.then(function (a) {
      if (!a) throw new Error('Anime tidak ditemukan.');
      detailCache[params.slug] = a;
      return RH.native('favorite_state', { slug: a.slug }).then(function (fav) { return { anime: a, favorite: !!fav.favorite }; });
    }).then(renderDetail).catch(function (e) { RH.ui.error(e.message, function () { RH.views.anime(params); }); });
  };

  function renderDetail(state) {
    var a = state.anime, favorite = state.favorite, eps = a.episodes || [];
    RH.ui.setTop(a.title, true);
    var genres = (a.genreText || '').split(',').filter(function (x) { return x.trim(); }), html = '<article class="detail-card"><div class="detail-layout"><div class="detail-cover">' + (a.cover ? '<img src="' + RH.ui.esc(a.cover) + '" alt="">' : '') + '</div><div><span class="eyebrow">' + RH.ui.esc(a.type || 'Anime') + '</span><h1>' + RH.ui.esc(a.title) + '</h1><p class="muted">' + RH.ui.metaLine(a) + '</p><div class="button-grid"><button class="primary-button" id="playFirst">' + RH.ui.icon('play') + ' Putar</button><button class="large-secondary" id="favBtn">' + (favorite ? RH.ui.icon('heartOn') + ' Favorit' : RH.ui.icon('heart') + ' Tambah favorit') + '</button></div></div></div>' +
      '<div class="chip-scroll detail-chips">' + genres.map(function (g) { var name = g.trim(); return '<button class="tag-button genre-chip" data-genre-name="' + RH.ui.esc(name) + '">' + RH.ui.genreIcon(name) + ' ' + RH.ui.esc(name) + '</button>'; }).join('') + '</div>' +
      '<div class="detail-block"><h2>' + RH.ui.icon('info') + ' Sinopsis</h2><p class="detail-copy">' + RH.ui.esc(a.synopsis || 'Sinopsis tidak tersedia.') + '</p></div>' +
      '<div class="detail-block"><h2>' + RH.ui.icon('filter') + ' Informasi</h2><div class="info-list">' + RH.ui.infoRow('Status', a.status) + RH.ui.infoRow('Tipe', a.type) + RH.ui.infoRow('Tahun', a.year) + RH.ui.infoRow('Durasi', a.duration) + RH.ui.infoRow('Judul Jepang', a.japanese) + RH.ui.infoRow('Episode tersedia', eps.length) + '</div></div>' +
      '<div class="detail-block"><div class="section-head"><h2>' + RH.ui.icon('movie') + ' Episode</h2><span class="counter-label">' + eps.length + ' episode</span></div><div class="episode-tools"><input id="epFilter" placeholder="Filter episode"></div><div id="episodeList" class="episode-list">' + RH.player.episodeRows(a, eps, -1) + '</div></div></article>';
    RH.ui.screen.innerHTML = html;
    document.getElementById('playFirst').onclick = function () { if (eps.length) RH.router.navigate('play', { slug: a.slug, ep: eps[0].name, t: 0 }); };
    document.getElementById('favBtn').onclick = function () {
      var action = favorite ? 'favorite_remove' : 'favorite_add';
      RH.native(action, { slug: a.slug, title: a.title, cover: a.cover }).then(function () { RH.ui.toast(favorite ? 'Dihapus dari favorit.' : 'Ditambahkan ke favorit.'); renderDetail({ anime: a, favorite: !favorite }); });
    };
    document.getElementById('epFilter').oninput = function () {
      var q = this.value.toLowerCase().trim(), filtered = [];
      for (var i = 0; i < eps.length; i++) if (!q || String(eps[i].name).toLowerCase().indexOf(q) >= 0) filtered.push(eps[i]);
      document.getElementById('episodeList').innerHTML = RH.player.episodeRows(a, filtered, -1);
      bindEpisodeDownloads(a, filtered);
      RH.ui.bindCommon();
    };
    bindEpisodeDownloads(a, eps);
    var genresButtons = document.querySelectorAll('[data-genre-name]');
    for (var j = 0; j < genresButtons.length; j++) genresButtons[j].onclick = function () { RH.router.navigate('genre', { slug: this.getAttribute('data-genre-name'), page: 1 }); };
    RH.ui.bindCommon();
  }

  function bindEpisodeDownloads(anime, eps) {
    var buttons = document.querySelectorAll('[data-download-index]');
    for (var i = 0; i < buttons.length; i++) buttons[i].onclick = function (e) {
      e.stopPropagation();
      var ep = eps[Number(this.getAttribute('data-download-index'))];
      if (!ep) return;
      RH.downloads.queue(anime, ep).then(function () { RH.ui.toast('Unduhan ditambahkan. Cek tab Offline.'); }).catch(function (err) { RH.ui.toast(err.message); });
    };
  }

  RH.views.play = function (params) {
    var localPath = params.localPath || '';
    RH.ui.loading(localPath ? 'Offline' : 'Memuat player');
    var source = localPath ? Promise.resolve(null) : (detailCache[params.slug] ? Promise.resolve(detailCache[params.slug]) : RH.native('detail', { key: params.slug }));
    source.then(function (a) {
      if (localPath) {
        var fake = { slug: params.slug || 'offline', id: Number(params.animeId || 0), title: params.title || 'Video offline', cover: params.cover || '', episodes: [{ name: params.ep || 'Video', url: '' }] };
        renderPlayer(fake, fake.episodes, 0, 0, localPath, true);
        return;
      }
      if (!a) throw new Error('Anime tidak ditemukan.');
      detailCache[params.slug] = a;
      var eps = a.episodes || [], index = RH.player.findIndex(eps, params.ep);
      if (index < 0) index = 0;
      if (!eps.length) throw new Error('Episode tidak tersedia.');
      renderPlayer(a, eps, index, parseFloat(params.t || 0) || 0, '', false);
    }).catch(function (e) { RH.ui.error(e.message); });
  };

  function renderPlayer(a, eps, index, time, localPath, offline) {
    var current = eps[index];
    RH.ui.setTop(offline ? 'Offline' : a.title, true);
    RH.ui.screen.innerHTML = '<div class="player-head"><span class="eyebrow">' + (offline ? 'Video tersimpan' : 'Sedang diputar') + '</span><h1>' + RH.ui.esc(a.title) + '</h1><p class="muted">' + RH.ui.icon('movie') + ' Episode ' + RH.ui.esc(current.name) + '</p></div>' +
      '<div id="playerHost" class="player-host"></div>' +
      '<div class="player-speed-outside" id="speedBar"><span>' + RH.ui.icon('speed') + ' Kecepatan</span><button class="speed-button" data-speed="0.75">0,75×</button><button class="speed-button active" data-speed="1">1×</button><button class="speed-button" data-speed="1.25">1,25×</button><button class="speed-button" data-speed="1.5">1,5×</button><button class="speed-button" data-speed="2">2×</button></div>' +
      '<div class="player-actions"><button class="large-secondary" id="prevEp">' + RH.ui.icon('back') + ' Sebelumnya</button><button class="primary-button" id="detailFromPlayer">' + RH.ui.icon('info') + ' Detail</button><button class="large-secondary" id="nextEp">Berikutnya ' + RH.ui.icon('chevron') + '</button>' + (offline ? '' : '<button class="large-secondary" id="openStream">' + RH.ui.icon('external') + ' Buka stream</button>') + '</div>' +
      (offline ? '' : '<section class="detail-block"><div class="section-head"><h2>' + RH.ui.icon('movie') + ' Episode</h2></div><div class="episode-list">' + RH.player.episodeRows(a, eps, index) + '</div></section>');
    RH.player.open(a, eps, index, time, localPath, offline);
    var speedButtons = document.querySelectorAll('#speedBar .speed-button');
    var activeRate = RH.player.video ? Number(RH.player.video.playbackRate) || 1 : 1;
    for (var sr = 0; sr < speedButtons.length; sr++) speedButtons[sr].classList.toggle('active', Number(speedButtons[sr].getAttribute('data-speed')) === activeRate);
    for (var i = 0; i < speedButtons.length; i++) speedButtons[i].onclick = function (e) {
      e.stopPropagation();
      if (!RH.player.video) return;
      RH.player.video.playbackRate = Number(this.getAttribute('data-speed')) || 1;
      for (var j = 0; j < speedButtons.length; j++) speedButtons[j].classList.remove('active');
      this.classList.add('active');
    };
    RH.ui.bindCommon();
    if (!offline) bindEpisodeDownloads(a, eps);
  }

  RH.views.history = function () {
    RH.player.toMini();
    RH.ui.setActive('history');
    RH.ui.loading('Riwayat');
    RH.native('history').then(function (res) {
      var items = res.items || [], html = '<h1 class="page-title">Riwayat</h1>';
      if (items.length) {
        html += '<div class="toolbar-row"><span class="subtext">' + items.length + ' tontonan tersimpan</span><button class="large-secondary" id="clearHistory">' + RH.ui.icon('trash') + ' Hapus semua</button></div><div class="history-list">';
        for (var i = 0; i < items.length; i++) {
          var x = items[i], pct = x.duration > 0 ? Math.min(100, x.position / x.duration * 100) : 0;
          html += '<article class="history-card" data-resume="1" data-slug="' + RH.ui.esc(x.slug) + '" data-ep="' + RH.ui.esc(x.ep) + '" data-t="' + RH.ui.esc(x.position) + '"><div class="history-cover">' + (x.cover ? '<img src="' + RH.ui.esc(x.cover) + '" alt="">' : '') + '<div class="progress"><i style="width:' + pct + '%"></i></div></div><div class="history-info"><strong>' + RH.ui.esc(x.title) + '</strong><small>Episode ' + RH.ui.esc(x.ep) + ' · ' + RH.ui.fmtClock(x.position) + '</small><button class="danger-text" data-remove-history="' + RH.ui.esc(x.slug) + '">' + RH.ui.icon('trash') + ' Hapus</button></div></article>';
        }
        html += '</div>';
      } else html += '<div class="empty-state">Belum ada riwayat tontonan.</div>';
      RH.ui.screen.innerHTML = html;
      if (items.length) document.getElementById('clearHistory').onclick = function () { RH.ui.confirmModal('Hapus riwayat', 'Semua riwayat tontonan akan dihapus.', 'Hapus', function () { RH.native('history_clear').then(function () { RH.views.history({}); }); }); };
      RH.ui.bindCommon();
      var removes = document.querySelectorAll('[data-remove-history]');
      for (var j = 0; j < removes.length; j++) removes[j].onclick = function (e) { e.stopPropagation(); RH.native('history_remove', { slug: this.getAttribute('data-remove-history') }).then(function () { RH.views.history({}); }); };
    }).catch(function (e) { RH.ui.error(e.message); });
  };

  RH.views.offline = function () {
    RH.player.toMini();
    RH.ui.setActive('offline');
    RH.ui.loading('Offline');
    loadOffline();
  };

  function loadOffline() {
    RH.native('downloads').then(function (res) {
      var items = res.items || [], html = '<div class="screen-head"><div><h1 class="page-title">Offline</h1><p class="subtext">Video tersimpan dikelompokkan berdasarkan anime.</p></div><button class="large-secondary" id="backgroundSettings">' + RH.ui.icon('shield') + ' Latar belakang</button></div>';
      if (!items.length) html += '<div class="empty-state"><div class="state-icon">' + RH.ui.icon('download') + '</div><h2>Belum ada video offline</h2><p>Unduh episode dari halaman detail anime untuk menontonnya tanpa koneksi.</p></div>';
      else {
        var groups = {};
        var order = [];
        for (var i = 0; i < items.length; i++) {
          var x = items[i];
          var groupKey = String(x.slug || x.animeId || x.title || ('anime-' + i));
          if (!groups[groupKey]) { groups[groupKey] = { title: x.title || 'Anime', animeId: x.animeId || 0, slug: x.slug || '', items: [] }; order.push(groupKey); }
          groups[groupKey].items.push(x);
        }
        html += '<div class="offline-groups">';
        for (var g = 0; g < order.length; g++) {
          var group = groups[order[g]];
          html += '<section class="offline-group"><div class="offline-group-head"><div class="offline-group-icon">' + RH.ui.icon('movie') + '</div><div><h2>' + RH.ui.esc(group.title) + '</h2><small>' + group.items.length + ' episode tersimpan</small></div></div><div class="offline-list">';
          for (var j = 0; j < group.items.length; j++) {
            var item = group.items[j], pct = item.total > 0 ? Math.min(100, Math.floor(item.progress * 100 / item.total)) : 0;
            html += '<article class="offline-card"><div class="offline-main"><div class="offline-icon">' + (item.status === 'completed' ? RH.ui.icon('check') : RH.ui.icon('download')) + '</div><div><strong>Episode ' + RH.ui.esc(item.ep) + '</strong><small>' + RH.ui.esc(RH.downloads.statusLabel(item.status, pct)) + '</small><div class="download-status">' + (item.status === 'completed' ? RH.ui.icon('check') + ' Tersimpan' : RH.ui.esc(RH.downloads.statusLabel(item.status, pct))) + '</div>' + (item.status === 'downloading' || item.status === 'queued' ? '<div class="download-bar"><i style="width:' + pct + '%"></i></div>' : '') + (item.status === 'failed' && item.error ? '<div class="download-error">' + RH.ui.esc(item.error) + '</div>' : '') + '</div></div><div class="offline-actions">' + (item.status === 'completed' ? '<button class="primary-button" data-offline-play="' + RH.ui.esc(item.id) + '">' + RH.ui.icon('play') + ' Putar</button>' : '') + (item.status === 'failed' || item.status === 'missing' ? '<button class="large-secondary" data-download-retry="' + RH.ui.esc(item.id) + '">' + RH.ui.icon('retry') + ' Ulangi</button>' : '') + '<button class="large-secondary" data-download-delete="' + RH.ui.esc(item.id) + '" aria-label="Hapus unduhan">' + RH.ui.icon('trash') + '</button></div></article>';
          }
          html += '</div></section>';
        }
        html += '</div>';
      }
      RH.ui.screen.innerHTML = html;
      var bg = document.getElementById('backgroundSettings');
      if (bg) bg.onclick = function () {
        RH.ui.modal('Unduhan latar belakang', '<p class="modal-copy">Agar unduhan tetap berjalan setelah aplikasi ditutup, izinkan pengelolaan latar belakang untuk AnimeGo. Pada Vivo, aktifkan izin autostart. Selanjutnya, izinkan AnimeGo mengabaikan optimasi baterai Android.</p>', '<button class="secondary-button" id="bgVivo">' + RH.ui.icon('shield') + ' Autostart Vivo</button><button class="primary-button" id="bgBattery">' + RH.ui.icon('shield') + ' Optimasi baterai</button>');
        document.getElementById('bgVivo').onclick = function () { RH.ui.closeModal(); RH.native('background_vivo').catch(function (e) { RH.ui.toast(e.message); }); };
        document.getElementById('bgBattery').onclick = function () { RH.ui.closeModal(); RH.native('background_battery').catch(function (e) { RH.ui.toast(e.message); }); };
      };
      bindOffline(items);
    }).catch(function (e) { RH.ui.error(e.message, loadOffline); });
  }

  function bindOffline(items) {
    var play = document.querySelectorAll('[data-offline-play]');
    for (var i = 0; i < play.length; i++) play[i].onclick = function () {
      var id = Number(this.getAttribute('data-offline-play')), row = null;
      for (var j = 0; j < items.length; j++) if (Number(items[j].id) === id) row = items[j];
      if (row) RH.router.navigate('play', { localPath: row.path, title: row.title, ep: row.ep, slug: row.slug, animeId: row.animeId, cover: '', t: 0 });
    };
    var retry = document.querySelectorAll('[data-download-retry]');
    for (i = 0; i < retry.length; i++) retry[i].onclick = function () { var id = Number(this.getAttribute('data-download-retry')); RH.downloads.retry(id).then(function () { RH.ui.toast('Unduhan dimulai ulang.'); loadOffline(); }).catch(function (e) { RH.ui.toast(e.message); }); };
    var del = document.querySelectorAll('[data-download-delete]');
    for (i = 0; i < del.length; i++) del[i].onclick = function () { RH.downloads.remove(Number(this.getAttribute('data-download-delete')), loadOffline); };
    setTimeout(function () {
      var still = document.querySelector('[data-download-retry], .download-bar');
      if (still && RH.router.current.name === 'offline') loadOffline();
    }, 1800);
  }

  RH.views.favorites = function () {
    RH.player.toMini();
    RH.ui.setActive('favorites');
    RH.ui.loading('Favorit');
    RH.native('favorites').then(function (res) {
      var items = res.items || [];
      RH.ui.setTop('Favorit', true);
      RH.ui.screen.innerHTML = '<h1 class="page-title">Favorit</h1>' + (items.length ? RH.ui.grid(items) : '<div class="empty-state">Belum ada anime favorit.</div>');
      RH.ui.bindCommon();
    }).catch(function (e) { RH.ui.error(e.message); });
  };

  RH.views.genres = function () {
    RH.player.toMini();
    RH.ui.setTop('Genre', true);
    RH.ui.loading('Genre');
    RH.native('genres').then(function (res) {
      var items = res.items || [], html = '<h1 class="page-title">Genre</h1><div class="genre-list">';
      for (var i = 0; i < items.length; i++) html += '<button class="genre-tile" data-genre="' + RH.ui.esc(items[i].slug) + '"><span class="genre-icon">' + RH.ui.genreIcon(items[i].name) + '</span><div><strong>' + RH.ui.esc(items[i].name) + '</strong><small>' + RH.ui.esc(items[i].count || 0) + ' anime</small></div></button>';
      html += '</div>'; RH.ui.screen.innerHTML = html;
      var buttons = document.querySelectorAll('[data-genre]'); for (i = 0; i < buttons.length; i++) buttons[i].onclick = function () { RH.router.navigate('genre', { slug: this.getAttribute('data-genre'), page: 1 }); };
    }).catch(function (e) { RH.ui.error(e.message); });
  };

  RH.views.genre = function (params) {
    RH.player.toMini();
    RH.ui.loading('Genre');
    RH.native('genre', { key: params.slug, page: params.page || 1, limit: 12 }).then(function (res) {
      if (!res.term) throw new Error('Genre tidak ditemukan.');
      var page = params.page || 1;
      RH.ui.setTop(res.term.name, true);
      RH.ui.screen.innerHTML = '<h1 class="page-title">' + RH.ui.esc(res.term.name) + '</h1>' + RH.ui.grid(res.list.items || []) + RH.ui.pager(res.list.totalPages || 1, page, function (p) { RH.router.navigate('genre', { slug: params.slug, page: p }, false); });
      RH.ui.bindCommon();
    }).catch(function (e) { RH.ui.error(e.message); });
  };

  RH.views.schedule = function (params) {
    RH.player.toMini();
    var days = ['senin','selasa','rabu','kamis','jumat','sabtu','minggu'], day = params.day || 'senin', page = params.page || 1;
    RH.ui.setTop('Jadwal', true);
    RH.ui.screen.innerHTML = '<h1 class="page-title">Jadwal rilis</h1><div class="chip-scroll">' + days.map(function (d) { return '<button class="chip-button ' + (d === day ? 'active' : '') + '" data-day="' + d + '">' + RH.ui.icon('calendar') + ' ' + d.charAt(0).toUpperCase() + d.substring(1) + '</button>'; }).join('') + '</div><div id="scheduleBody" class="loading-inline">Memuat jadwal...</div>';
    var buttons = document.querySelectorAll('[data-day]'); for (var i = 0; i < buttons.length; i++) buttons[i].onclick = function () { RH.router.navigate('schedule', { day: this.getAttribute('data-day'), page: 1 }); };
    RH.native('schedule', { day: day, page: page, limit: 12 }).then(function (res) { document.getElementById('scheduleBody').innerHTML = RH.ui.grid(res.items || []) + RH.ui.pager(res.totalPages || 1, page, function (p) { RH.router.navigate('schedule', { day: day, page: p }, false); }); RH.ui.bindCommon(); }).catch(function (e) { RH.ui.error(e.message); });
  };
})();
