AnimeGo

AnimeGo adalah aplikasi streaming dan download anime berbasis Android + WebView Hybrid. Seluruh antarmuka dibuat menggunakan HTML, CSS, dan JavaScript, sementara fitur Android seperti database, download background, notifikasi, dan bridge ke sistem ditulis dengan Java 7 agar tetap kompatibel dengan AIDE tanpa AndroidX.

Desain aplikasi menggunakan gaya iOS yang ringan dengan aksen biru langit, tombol rounded, dark mode, dan player video custom.

---

Fitur

- Streaming anime dari API Karanime.
- Pencarian anime dan episode.
- Halaman Home, Jelajah, Genre, Jadwal, Favorit, Riwayat, dan Offline.
- Custom video player.
  - Play / Pause.
  - Seekbar.
  - Skip episode.
  - Fullscreen portrait.
  - Fullscreen landscape seperti aplikasi streaming.
  - Auto hide controller.
- Download episode ke penyimpanan perangkat.
- Foreground Service untuk download background.
- Notifikasi progres download (judul, episode, persentase, ukuran file).
- Riwayat tontonan dengan posisi terakhir.
- Favorit.
- Dark Mode & Light Mode.
- Mini player saat berpindah tab.
- Offline library yang dikelompokkan berdasarkan judul anime.

---

Tampilan

«Tambahkan screenshot aplikasi di bagian ini.»

Home| Player| Offline
Screenshot| Screenshot| Screenshot

---

Teknologi

<table><table-section header><table-row header><table-cell header>Bagian</table-cell><table-cell header>Teknologi</table-cell></table-row></table-section><table-row><table-cell>Bahasa Android</table-cell><table-cell>Java 7</table-cell></table-row><table-row><table-cell>UI</table-cell><table-cell>HTML, CSS, JavaScript</table-cell></table-row><table-row><table-cell>Ikon</table-cell><table-cell>Font Awesome 6</table-cell></table-row><table-row><table-cell>Database</table-cell><table-cell>SQLite</table-cell></table-row><table-row><table-cell>Video</table-cell><table-cell>HTML5 Custom Player</table-cell></table-row><table-row><table-cell>Target Android</table-cell><table-cell>Android 6.0+ (API 23)</table-cell></table-row><table-row><table-cell>IDE</table-cell><table-cell>AIDE</table-cell></table-row></table>---

Struktur Proyek

animeGo/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── css/
│   │   │   ├── script/
│   │   │   ├── img/
│   │   │   ├── webfonts/
│   │   │   ├── index.html
│   │   │   └── landscape_player.html
│   │   ├── java/app/animego/inc/
│   │   │   ├── api/
│   │   │   ├── bridge/
│   │   │   ├── db/
│   │   │   ├── download/
│   │   │   ├── MainActivity.java
│   │   │   ├── LandscapePlayerActivity.java
│   │   │   └── BootReceiver.java
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
└── settings.gradle

---

Cara Build (AIDE)

Persyaratan

- Android Studio tidak diperlukan.
- AIDE.
- Android SDK bawaan AIDE.

Langkah

1. Clone repository.
2. Buka folder proyek di AIDE.
3. Build APK.
4. Install APK di perangkat Android.

Tidak memerlukan AndroidX atau library tambahan di luar proyek.

---

Penyimpanan Data

AnimeGo menggunakan SQLite untuk menyimpan:

- Favorit.
- Riwayat tontonan.
- Daftar download.
- Status progres download.

File video hasil download disimpan di penyimpanan eksternal aplikasi agar tetap bisa diputar secara offline.

---

Arsitektur

WebView UI
      │
      ▼
JavaScript (Router / Player / UI)
      │
AndroidBridge
      │
JsBridge.java
 ├── ApiService
 ├── DatabaseHelper
 └── DownloadService

Semua komunikasi JavaScript ke Android menggunakan bridge berbasis JSON.

---

Download Background

Download berjalan menggunakan Foreground Service, sehingga proses tetap berjalan ketika aplikasi berada di background.

Fitur yang didukung:

- Queue download.
- Resume download.
- Progress notification.
- Boot receiver untuk melanjutkan download yang tertunda.

---

Roadmap

- [ ] Multiple server stream.
- [ ] Chromecast / Cast.
- [ ] Picture in Picture (PiP).
- [ ] Sinkronisasi akun.
- [ ] Auto update data anime.
- [ ] Download banyak episode sekaligus.

---

Lisensi

Proyek ini dibuat untuk tujuan pembelajaran dan pengembangan aplikasi Android berbasis WebView Hybrid.

Data anime berasal dari layanan API pihak ketiga dan tetap mengikuti kebijakan penyedia data.
