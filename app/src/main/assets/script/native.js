(function () {
  'use strict';
  var RH = window.RH = window.RH || {};
  RH.pending = {};
  RH.sequence = 0;

  RH.native = function (action, data) {
    return new Promise(function (resolve, reject) {
      var id = 'cb' + (++RH.sequence);
      RH.pending[id] = { resolve: resolve, reject: reject };
      try {
        window.AndroidBridge.call(action, JSON.stringify(data || {}), id);
      } catch (e) {
        delete RH.pending[id];
        reject(e);
      }
    });
  };

  RH.openUrl = function (url) {
    try { window.AndroidBridge.openUrl(url); } catch (e) { RH.ui.toast('Tidak dapat membuka tautan.'); }
  };

  RH.closeApp = function () {
    try { window.AndroidBridge.closeApp(); } catch (e) {}
  };

  window.__nativeDone = function (id, ok, payload) {
    var item = RH.pending[id];
    if (!item) return;
    delete RH.pending[id];
    if (ok) {
      try { item.resolve(JSON.parse(payload)); }
      catch (e) { item.reject(e); }
    } else {
      item.reject(new Error(String(payload || 'Terjadi kesalahan.')));
    }
  };
})();
