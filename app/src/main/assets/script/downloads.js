(function () {
  'use strict';
  var RH = window.RH = window.RH || {};

  RH.downloads = {
    queue: function (anime, episode) {
      return RH.native('download', {
        slug: anime.slug,
        animeId: anime.id || 0,
        title: anime.title,
        ep: episode.name,
        url: episode.url
      });
    },
    retry: function (id) {
      return RH.native('download_retry', { id: id });
    },
    remove: function (id, refresh) {
      RH.ui.confirmModal('Hapus unduhan', 'File video akan dihapus dari penyimpanan perangkat.', 'Hapus', function () {
        RH.native('download_delete', { id: id }).then(function () {
          RH.ui.toast('Unduhan dihapus.');
          if (refresh) refresh();
        }).catch(function (e) { RH.ui.toast(e.message); });
      });
    },
    statusLabel: function (status, progress) {
      if (status === 'completed') return 'Tersimpan offline';
      if (status === 'downloading') return progress + '% sedang diunduh';
      if (status === 'queued') return 'Menunggu unduhan';
      if (status === 'failed') return 'Unduhan gagal';
      if (status === 'missing') return 'File tidak ditemukan';
      return status || 'Status tidak diketahui';
    }
  };
})();
