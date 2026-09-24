(function () {
  'use strict';
  var RH = window.RH = window.RH || {};
  RH.router = {
    current: { name: 'home', params: {} },
    stack: [],
    navigate: function (name, params, push) {
      if (this.current.name === 'play' && name !== 'play' && RH.player && RH.player.wrap) RH.player.toMini();
      if (push !== false && this.current.name !== name) this.stack.push(this.current);
      this.current = { name: name, params: params || {} };
      this.render();
    },
    back: function () {
      var modal = document.getElementById('modal');
      if (modal && !modal.classList.contains('hidden')) { RH.ui.closeModal(); return; }
      if (document.body.classList.contains('player-fullscreen')) {
        if (RH.player) RH.player.exitFullscreen();
        return;
      }
      if (this.current.name === 'home' || !this.stack.length) {
        RH.closeApp();
        return;
      }
      this.current = this.stack.pop();
      this.render();
    },
    render: function () {
      var fn = (RH.views || {})[this.current.name];
      if (typeof fn === 'function') fn(this.current.params || {});
      else this.navigate('home', {}, false);
    }
  };
  window.__handleAndroidBack = function () { RH.router.back(); };
})();

window.__openRoute = function (name) {
  if (window.RH && RH.router) RH.router.navigate(name, {}, false);
};
