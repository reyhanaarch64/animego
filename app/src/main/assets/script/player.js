(function () {
  'use strict';
  var RH = window.RH = window.RH || {};

  RH.player = {
    timer: null,
    hideTimer: null,
    video: null,
    wrap: null,
    save: null,
    state: null,
    fullscreenMode: null,
    landscapeLaunching: false,

    findIndex: function (eps, ep) {
      for (var i = 0; i < eps.length; i++) if (String(eps[i].name) === String(ep)) return i;
      return -1;
    },

    episodeRows: function (anime, eps, current) {
      var html = '';
      for (var i = 0; i < eps.length; i++) {
        var e = eps[i];
        html += '<div class="episode-row ' + (i === current ? 'current' : '') + '"><button class="episode-main" data-open="play" data-slug="' + RH.ui.esc(anime.slug) + '" data-ep="' + RH.ui.esc(e.name) + '"><span>' + RH.ui.icon('play') + ' Episode ' + RH.ui.esc(e.name) + '</span>' + (i === current ? '<small>' + RH.ui.icon('check') + ' Sedang diputar</small>' : '') + '</button><button class="episode-download" data-download-index="' + i + '" aria-label="Unduh episode">' + RH.ui.icon('download') + '</button></div>';
      }
      return html || '<div class="empty-state">Episode tidak tersedia.</div>';
    },

    sameMedia: function (a, current, localPath, offline) {
      if (!this.state) return false;
      return this.state.offline === offline && this.state.localPath === (localPath || '') && this.state.slug === String(a.slug || '') && this.state.ep === String(current.name || '');
    },

    open: function (a, eps, index, time, localPath, offline) {
      var current = eps[index];
      var host = document.getElementById('playerHost');
      if (!host || !current) return;

      if (this.wrap && this.sameMedia(a, current, localPath, offline)) {
        host.appendChild(this.wrap);
        this.wrap.classList.remove('mini-mode');
        this.wrap.style.display = '';
        this.hideDock();
        this.exitFullscreen(false);
        this.showControls();
        return;
      }

      this.destroy();
      this.state = {
        slug: String(a.slug || ''),
        animeId: Number(a.id || 0),
        title: String(a.title || 'Anime'),
        ep: String(current.name || 'Episode'),
        cover: String(a.cover || ''),
        src: String(current.url || ''),
        localPath: localPath || '',
        offline: !!offline,
        eps: eps,
        index: index,
        position: Number(time) || 0,
        duration: 0,
        wasPlayingBeforeLandscape: false
      };
      this.renderInto(host, a, eps, index, time || 0, localPath, offline);
    },

    renderInto: function (host, a, eps, index, time, localPath, offline) {
      var current = eps[index];
      var src = offline ? encodeURI('file://' + localPath) : current.url;
      var wrap = document.createElement('div');
      wrap.className = 'player-wrap';
      wrap.id = 'videoWrap';
      wrap.innerHTML = '<div class="video-box" id="videoBox"><video id="video" playsinline webkit-playsinline preload="metadata" src="' + RH.ui.esc(src) + '"></video><button class="player-surface" id="playerSurface" aria-label="Tampilkan atau sembunyikan kontrol"></button><button class="player-center" id="playerCenter" aria-label="Putar">' + RH.ui.icon('play') + '</button><div class="player-top-controls"><button class="player-icon" id="portraitFullBtn" aria-label="Layar penuh vertikal">' + RH.ui.icon('fullscreen') + '</button><button class="player-icon" id="landscapeFullBtn" aria-label="Layar penuh horizontal">' + RH.ui.icon('landscape') + '</button><button class="player-icon exit-full-btn" id="exitFullBtn" aria-label="Keluar layar penuh">' + RH.ui.icon('minimize') + '</button></div><div class="player-controls"><input class="player-seek" id="playerSeek" type="range" min="0" max="1000" value="0" step="1" aria-label="Posisi video"><div class="player-bottom"><button class="player-icon player-mini" id="rewindBtn" aria-label="Mundur 10 detik">' + RH.ui.icon('backward') + '</button><button class="player-icon player-mini" id="playerPlay" aria-label="Putar atau jeda">' + RH.ui.icon('play') + '</button><span class="player-time"><span id="currentTime">00:00</span> / <span id="totalTime">00:00</span></span><button class="player-icon player-mini" id="muteBtn" aria-label="Senyapkan">' + RH.ui.icon('volume') + '</button><button class="player-icon player-mini" id="forwardBtn" aria-label="Maju 10 detik">' + RH.ui.icon('forward') + '</button></div></div><div id="videoError" class="overlay-error"></div><div class="mini-overlay"><button class="mini-surface" id="miniSurface" aria-label="Putar atau jeda video mini"></button><div class="mini-top"><div class="mini-info"><strong id="miniTitle">' + RH.ui.esc(a.title) + '</strong><small id="miniEpisode">Episode ' + RH.ui.esc(current.name) + '</small></div><div class="mini-actions"><button class="mini-icon" id="miniOpen" aria-label="Buka halaman tontonan">' + RH.ui.icon('expand') + '</button><button class="mini-icon" id="miniClose" aria-label="Tutup pemutar mini">' + RH.ui.icon('close') + '</button></div></div><button class="mini-play" id="miniPlay" aria-label="Putar atau jeda">' + RH.ui.icon('play') + '</button></div></div>';
      host.appendChild(wrap);
      this.wrap = wrap;
      this.video = wrap.querySelector('#video');
      this.bind(a, eps, index, time, localPath, offline);
    },

    bind: function (anime, eps, index, time, localPath, offline) {
      var self = this;
      var video = this.video;
      var wrap = this.wrap;
      var seek = wrap.querySelector('#playerSeek');
      var currentTime = wrap.querySelector('#currentTime');
      var totalTime = wrap.querySelector('#totalTime');
      var playBtn = wrap.querySelector('#playerPlay');
      var centerBtn = wrap.querySelector('#playerCenter');
      var muteBtn = wrap.querySelector('#muteBtn');
      var portraitBtn = wrap.querySelector('#portraitFullBtn');
      var landscapeBtn = wrap.querySelector('#landscapeFullBtn');
      var exitFullBtn = wrap.querySelector('#exitFullBtn');
      var surface = wrap.querySelector('#playerSurface');
      var miniPlay = wrap.querySelector('#miniPlay');
      var miniOpen = wrap.querySelector('#miniOpen');
      var miniClose = wrap.querySelector('#miniClose');
      var miniSurface = wrap.querySelector('#miniSurface');
      clearInterval(this.timer);
      clearTimeout(this.hideTimer);
      if (!video || !wrap) return;

      video.controls = false;
      video.playsInline = true;
      video.preload = 'metadata';
      video.volume = 1;

      function updateTime() {
        var duration = Number(video.duration) || 0;
        var pos = Number(video.currentTime) || 0;
        if (currentTime) currentTime.textContent = RH.ui.fmtClock(pos);
        if (totalTime) totalTime.textContent = RH.ui.fmtClock(duration);
        if (seek) seek.value = duration > 0 ? String(Math.round(pos / duration * 1000)) : '0';
        if (self.state) {
          self.state.position = pos;
          self.state.duration = duration;
        }
      }

      function setPlayingIcon() {
        var icon = video.paused ? RH.ui.icon('play') : RH.ui.icon('pause');
        if (playBtn) playBtn.innerHTML = icon;
        if (miniPlay) miniPlay.innerHTML = icon;
        if (centerBtn) {
          centerBtn.innerHTML = icon;
          centerBtn.style.display = video.paused ? 'grid' : 'none';
        }
      }

      function hideControls() {
        if (video.paused || wrap.classList.contains('mini-mode')) return;
        wrap.classList.add('controls-hidden');
      }

      function showControls() {
        wrap.classList.remove('controls-hidden');
        clearTimeout(self.hideTimer);
        if (!video.paused && !wrap.classList.contains('mini-mode')) {
          self.hideTimer = setTimeout(hideControls, 2800);
        }
      }

      function toggleControls() {
        if (wrap.classList.contains('mini-mode')) return;
        if (wrap.classList.contains('controls-hidden')) showControls();
        else if (!video.paused) hideControls();
        else showControls();
      }

      function togglePlay() {
        if (video.paused) {
          video.play().catch(function () { RH.ui.toast('Pemutaran memerlukan interaksi pengguna.'); });
        } else {
          video.pause();
        }
        setPlayingIcon();
        if (!wrap.classList.contains('mini-mode')) showControls();
      }

      function skip(seconds) {
        try { video.currentTime = Math.max(0, Math.min((video.duration || Infinity), video.currentTime + seconds)); } catch (e) {}
        showControls();
      }

      function updateMute() {
        if (muteBtn) muteBtn.innerHTML = video.muted ? RH.ui.icon('mute') : RH.ui.icon('volume');
      }

      if (time > 0) video.addEventListener('loadedmetadata', function () {
        try { video.currentTime = Math.min(Number(time), Math.max(0, video.duration - 1)); } catch (e) {}
        updateTime();
      });

      self.save = function () {
        updateTime();
        if (localPath) return;
        RH.native('history_progress', {
          slug: anime.slug, title: anime.title, ep: String(eps[index].name), cover: anime.cover,
          src: eps[index].url, position: video.currentTime || 0, duration: video.duration || 0
        }).catch(function () {});
      };

      if (!localPath) {
        RH.native('history_save', {
          slug: anime.slug, title: anime.title, ep: String(eps[index].name), cover: anime.cover,
          src: eps[index].url, position: time, duration: 0
        }).catch(function () {});
        this.timer = setInterval(this.save, 5000);
        video.addEventListener('pause', this.save);
      }

      video.addEventListener('loadedmetadata', updateTime);
      video.addEventListener('timeupdate', updateTime);
      video.addEventListener('progress', updateTime);
      video.addEventListener('play', function () { setPlayingIcon(); showControls(); });
      video.addEventListener('pause', function () { setPlayingIcon(); showControls(); });
      video.addEventListener('volumechange', updateMute);
      video.addEventListener('error', function () {
        var box = wrap.querySelector('#videoError');
        if (box) { box.textContent = 'Video tidak dapat diputar. Coba lagi atau buka sumber streaming eksternal.'; box.style.display = 'grid'; }
        setPlayingIcon();
      });
      video.addEventListener('ended', function () {
        if (self.save) self.save();
        setPlayingIcon();
        if (localPath) return;
        if (index + 1 < eps.length) {
          RH.ui.toast('Episode berikutnya akan diputar.');
          setTimeout(function () { RH.router.navigate('play', { slug: anime.slug, ep: eps[index + 1].name, t: 0 }); }, 450);
        }
      });

      if (surface) surface.onclick = function (e) { e.stopPropagation(); toggleControls(); };
      if (playBtn) playBtn.onclick = function (e) { e.stopPropagation(); togglePlay(); };
      if (centerBtn) centerBtn.onclick = function (e) { e.stopPropagation(); togglePlay(); };
      var rewind = wrap.querySelector('#rewindBtn');
      var forward = wrap.querySelector('#forwardBtn');
      if (rewind) rewind.onclick = function (e) { e.stopPropagation(); skip(-10); };
      if (forward) forward.onclick = function (e) { e.stopPropagation(); skip(10); };
      if (muteBtn) muteBtn.onclick = function (e) { e.stopPropagation(); video.muted = !video.muted; updateMute(); showControls(); };
      if (seek) seek.oninput = function (e) {
        e.stopPropagation();
        if (video.duration > 0) video.currentTime = Number(this.value) / 1000 * video.duration;
        showControls();
      };
      if (portraitBtn) portraitBtn.onclick = function (e) { e.stopPropagation(); self.enterFullscreen('portrait'); };
      if (landscapeBtn) landscapeBtn.onclick = function (e) { e.stopPropagation(); self.openLandscape(); };
      if (exitFullBtn) exitFullBtn.onclick = function (e) { e.stopPropagation(); self.exitFullscreen(); self.showControls(); };

      if (miniSurface) miniSurface.onclick = function (e) { e.stopPropagation(); togglePlay(); };
      if (miniPlay) miniPlay.onclick = function (e) { e.stopPropagation(); togglePlay(); };
      if (miniOpen) miniOpen.onclick = function (e) { e.stopPropagation(); self.restoreToPlayer(); };
      if (miniClose) miniClose.onclick = function (e) { e.stopPropagation(); self.destroy(); };

      var prev = document.getElementById('prevEp');
      var next = document.getElementById('nextEp');
      if (prev) prev.onclick = function () {
        if (index > 0) RH.router.navigate('play', { slug: anime.slug, ep: eps[index - 1].name, t: 0 });
        else RH.ui.toast('Ini episode pertama.');
      };
      if (next) next.onclick = function () {
        if (index + 1 < eps.length) RH.router.navigate('play', { slug: anime.slug, ep: eps[index + 1].name, t: 0 });
        else RH.ui.toast('Ini episode terakhir.');
      };
      var detailBtn = document.getElementById('detailFromPlayer');
      if (detailBtn) detailBtn.onclick = function () { RH.router.navigate('anime', { slug: anime.slug }); };
      var openStream = document.getElementById('openStream');
      if (openStream) openStream.onclick = function () { RH.openUrl(eps[index].url); };

      this.showControls();
      setPlayingIcon();
      updateMute();
      updateTime();
      video.play().catch(function () {});
    },

    toMini: function () {
      if (!this.wrap) return;
      if (document.body.classList.contains('player-fullscreen')) this.exitFullscreen(false);
      var dock = document.getElementById('playerDock');
      if (!dock) return;
      dock.appendChild(this.wrap);
      this.wrap.classList.add('mini-mode');
      this.wrap.style.display = '';
      dock.classList.remove('hidden');
      clearTimeout(this.hideTimer);
    },

    restoreToPlayer: function () {
      if (!this.wrap || !this.state) return;
      if (RH.router.current.name !== 'play') {
        RH.router.navigate('play', {
          slug: this.state.slug,
          ep: this.state.ep,
          animeId: this.state.animeId,
          title: this.state.title,
          cover: this.state.cover,
          t: this.state.position || 0,
          localPath: this.state.localPath || ''
        });
      } else {
        var host = document.getElementById('playerHost');
        if (host) {
          host.appendChild(this.wrap);
          this.wrap.classList.remove('mini-mode');
          this.wrap.style.display = '';
          this.hideDock();
          this.showControls();
        }
      }
    },

    hideDock: function () {
      var dock = document.getElementById('playerDock');
      if (dock) dock.classList.add('hidden');
    },

    enterFullscreen: function (mode) {
      if (!this.wrap) return;
      if (mode !== 'portrait') return;
      this.fullscreenMode = 'portrait';
      document.body.classList.add('player-fullscreen', 'player-fullscreen-portrait');
      document.body.classList.remove('player-fullscreen-landscape');
      this.hideDock();
      this.wrap.classList.remove('controls-hidden');
      this.showControls();
    },

    exitFullscreen: function () {
      this.fullscreenMode = null;
      document.body.classList.remove('player-fullscreen', 'player-fullscreen-portrait', 'player-fullscreen-landscape');
    },

    openLandscape: function () {
      if (!this.video || !this.state || this.state.offline || this.landscapeLaunching) return;
      this.landscapeLaunching = true;
      this.state.wasPlayingBeforeLandscape = !this.video.paused;
      this.state.position = Number(this.video.currentTime) || 0;
      this.state.duration = Number(this.video.duration) || 0;
      var payload = {
        url: this.state.src,
        slug: this.state.slug,
        animeId: this.state.animeId,
        title: this.state.title,
        ep: this.state.ep,
        cover: this.state.cover,
        position: this.state.position,
        duration: this.state.duration,
        rate: Number(this.video.playbackRate) || 1,
        muted: !!this.video.muted,
        autoplay: !!this.state.wasPlayingBeforeLandscape
      };
      try { this.video.pause(); } catch (e) {}
      if (this.save) this.save();
      RH.native('landscape_player', payload).then(function () {
        self.landscapeLaunching = false;
      }).catch(function (e) {
        self.landscapeLaunching = false;
        if (self.state && self.state.wasPlayingBeforeLandscape) self.video.play().catch(function () {});
        RH.ui.toast(e.message || 'Pemutar lanskap tidak dapat dibuka.');
      });
      var self = this;
    },

    onLandscapeCancel: function () {
      this.landscapeLaunching = false;
      if (!this.video || !this.state) return;
      if (this.state.wasPlayingBeforeLandscape) this.video.play().catch(function () {});
      this.showControls();
    },

    onLandscapeResult: function (position, duration, rate, playing, muted) {
      this.landscapeLaunching = false;
      if (!this.video || !this.state) return;
      try {
        this.video.currentTime = Math.max(0, Number(position) || 0);
        this.video.playbackRate = Number(rate) || 1;
        this.video.muted = !!muted;
      } catch (e) {}
      this.state.position = Number(position) || 0;
      this.state.duration = Number(duration) || 0;
      if (playing) this.video.play().catch(function () {});
      this.showControls();
      this.save && this.save();
    },

    showControls: function () {
      if (!this.wrap) return;
      this.wrap.classList.remove('controls-hidden');
      clearTimeout(this.hideTimer);
      if (this.video && !this.video.paused && !this.wrap.classList.contains('mini-mode')) {
        var self = this;
        this.hideTimer = setTimeout(function () {
          if (self.wrap && self.video && !self.video.paused && !self.wrap.classList.contains('mini-mode')) self.wrap.classList.add('controls-hidden');
        }, 2800);
      }
    },

    destroy: function () {
      clearInterval(this.timer);
      clearTimeout(this.hideTimer);
      this.timer = null;
      this.hideTimer = null;
      if (this.video && this.save) this.save();
      if (this.video) {
        try { this.video.pause(); } catch (e) {}
      }
      if (this.wrap && this.wrap.parentNode) this.wrap.parentNode.removeChild(this.wrap);
      this.video = null;
      this.wrap = null;
      this.save = null;
      this.state = null;
      this.fullscreenMode = null;
      this.landscapeLaunching = false;
      this.hideDock();
      this.exitFullscreen();
    }
  };
})();
