(function () {
  'use strict';
  var RH = window.RH = window.RH || {};
  document.addEventListener('DOMContentLoaded', function () {
    RH.ui.boot();
    document.getElementById('backBtn').onclick = function () { RH.router.back(); };
    document.getElementById('searchBtn').onclick = function () { RH.router.navigate('search', { q: '', page: 1 }); };
    document.getElementById('themeBtn').onclick = function () { RH.ui.toggleTheme(); };
    var nav = document.querySelectorAll('#bottomNav button');
    for (var i = 0; i < nav.length; i++) nav[i].onclick = function () { RH.router.navigate(this.getAttribute('data-route')); };
    RH.router.navigate('home', {}, false);
  });
})();
