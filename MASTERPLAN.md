# AndroidKris IDE — Master Plan

On-device Android IDE (build APK langsung di HP), gaya AndroidIDE. Kotlin + Jetpack Compose /
sebagian View klasik. Tujuan: user bisa buka project Android, edit kode dengan autocomplete,
lalu **compile jadi APK di HP tanpa PC**.

> Baca file ini dulu di setiap sesi baru. Proyek ini besar dan bertingkat — jangan lompat ke
> "build APK" sebelum fondasi editor + terminal + environment jalan.

---

## 0. Realita & batasan keras (baca sebelum menulis kode)

- **Hanya ARM64 (aarch64).** Semua build-tool (Gradle, AAPT2, d8/r8) yang dipakai adalah versi
  yang dikompilasi ulang untuk Linux-on-Android aarch64. HP x86/emulator x86 **tidak** bisa build.
  Target device nyata ARM64, Android 8+ (API 26+).
- **Ini bukan proyek 1 file.** AndroidIDE = beberapa tahun kerja tim. Rencana ini realistis kalau
  dikerjakan bertahap dengan milestone yang bisa dipakai di tiap tahap, bukan "big bang".
- **Lisensi = GPLv3 (WAJIB DIPAHAMI).** AndroidIDE, Termux, dan sebagian besar komponen yang kita
  reuse berlisensi **GPLv3 / LGPL**. Kalau kita menyalin atau menautkan kode mereka, aplikasi kita
  **wajib open-source GPLv3 juga**. Tidak bisa jadi produk closed-source berbayar tanpa mengganti
  komponen inti. Keputusan bisnis ini harus jelas sejak awal. (sora-editor = LGPL-2.1, Termux =
  GPLv3, AndroidIDE = GPLv3.)
- **Storage & memori besar.** Toolchain + SDK + Gradle cache bisa 1.5–3 GB. Build butuh RAM;
  device 4GB RAM ke bawah akan berat.

---

## 1. Anatomi AndroidIDE — apa saja bagiannya

Supaya klon terarah, ini komponen yang membuat AndroidIDE bisa build APK di HP:

| Lapisan | Fungsi | Sumber reuse |
|---|---|---|
| **Code editor** | Editor teks cepat, syntax highlight (TextMate/tree-sitter), code folding | `sora-editor` (Rosemoe) |
| **Terminal + Linux env** | Shell, paket Linux, tempat toolchain hidup | Komponen **Termux** (terminal-emulator, terminal-view) + bootstrap rootfs |
| **Build tools ARM64** | Gradle, AAPT2, d8, apksigner, dll. dikompilasi untuk aarch64 | Repo **`androidide-build-tools`** (prebuilt) |
| **Android SDK (subset)** | platform jar, build-tools, platform-tools untuk aarch64 | Distribusi SDK AndroidIDE |
| **JDK** | Java 17 untuk aarch64 (menjalankan Gradle & compiler) | OpenJDK build untuk Android (AndroidIDE sediakan) |
| **Gradle Tooling** | Jembatan app ↔ Gradle daemon (model project, task, sync) | Gradle Tooling API + **plugin Gradle custom** yang di-inject ke project user |
| **LSP Java** | Autocomplete, error, go-to-def untuk Java | Fork **Eclipse JDT** (AndroidIDE `java-language-server`) |
| **LSP XML / Kotlin** | Bantuan XML layout & Kotlin | LemMinX (XML) + Kotlin LSP |
| **UI Designer (opsional)** | Drag-drop layout XML | Modul terpisah, fase akhir |

**Insight kunci:** yang membuat build "jalan di HP" bukan kode Android app-nya, tapi **toolchain
native yang sudah dikompilasi untuk ARM64** + **Gradle daemon** yang dijalankan lewat environment
mirip-Termux. App kita sebagian besar adalah **orkestrator + editor + UI di atas toolchain itu.**

---

## 2. Strategi reuse (karena kita pilih "maksimalkan reuse")

Ada dua jalur besar:

**Jalur A — Fork langsung AndroidIDE (tercepat sampai bisa build APK).**
- Clone `github.com/AndroidIDEOfficial/AndroidIDE`, rebrand jadi "AndroidKris", modifikasi UI &
  fitur di atasnya.
- Konsekuensi: **wajib tetap GPLv3 & open-source.** Kita "berdiri di atas" arsitektur matang,
  fokus energi ke fitur/UI baru, bukan bikin ulang toolchain.
- Risiko: basis kode besar & rumit; belajar internal-nya butuh waktu; upgrade dari upstream repot.

**Jalur B — Bangun app baru, reuse komponen satu per satu.**
- App Compose baru; tarik `sora-editor` (editor), komponen Termux (terminal), dan **prebuilt
  build-tools AndroidIDE** (Gradle/AAPT2/JDK aarch64) sebagai aset yang di-bootstrap.
- Lebih banyak kontrol & arsitektur bersih, tapi kita harus menyambung sendiri Gradle Tooling +
  LSP + bootstrap environment — bagian tersulit dari AndroidIDE justru di sini.

**Rekomendasi:** mulai **Jalur B** untuk fase 1–3 (editor, file, terminal, environment) supaya
paham fondasinya, lalu untuk fase build APK (fase 4+) **pinjam mekanisme AndroidIDE** (prebuilt
tools + pola Gradle plugin injection). Kalau ternyata menyambung Gradle Tooling terlalu berat,
pivot ke Jalur A (fork) tidak masalah — fase 1–3 tetap ilmu berharga. Keputusan A-vs-B untuk fase
build diambil di akhir Fase 3, bukan sekarang.

---

## 3. Roadmap bertingkat (tiap fase = app yang bisa dipakai)

### Fase 0 — Fondasi (1–2 minggu)
- [ ] Setup project di Android Studio (Kotlin, minSdk 26, `arm64-v8a` only untuk rilis toolchain).
- [ ] Struktur modul: `:app`, `:editor`, `:terminal`, `:build-engine`, `:lsp`.
- [ ] Cek device target: harus ARM64. Tambah guard runtime yang menolak build di non-arm64.
- [ ] Keputusan lisensi ditulis di README (GPLv3 karena reuse GPL/LGPL).

### Fase 1 — Editor kode yang layak ✅ (sebagian; sisanya → 1.1)
- [x] Integrasi **`sora-editor`** 0.24.4 (`:editor`): `CodeEditorView` (AndroidView) + `EditorController`,
      line number, undo/redo, **cari/ganti** (`EditorSearcher`), **symbol input row**, scheme
      Darcula/GitHub, buffer hand-off per tab (race-safe via `controller.attached`).
- [x] Syntax highlight **Java** built-in (`language-java`, tanpa aset). Kotlin/XML → Fase 1.1.
- [x] File manager (`:app` `workspace/`): pohon project (`FileTreePanel`, expand/collapse),
      tab multi-file (dirty dot), buka/simpan, **buat file/folder, rename, hapus** (long-press menu),
      sample project di-seed otomatis. Real `java.io.File` di `getExternalFilesDir/projects`.
- **Milestone tercapai: editor kode fungsional yang berguna walau belum bisa build.**

**Fase 1.1 ✅ (lanjutan editor):**
- [x] Highlight **Java/Kotlin/XML** via TextMate (`language-textmate` + core library desugaring +
      grammar & theme JSON di `editor/src/main/assets/textmate/`, diambil dari demo sora).
      `TextMateSetup` (idempoten) daftarkan `FileProviderRegistry`/`GrammarRegistry`/`ThemeRegistry`;
      `languages.json` ramping (java/kotlin/xml). `.gradle.kts` → source.kotlin. `language-java`
      built-in **diganti** TextMate agar warna konsisten (`TextMateColorScheme`).
- [x] Buka folder proyek mana pun via `MANAGE_EXTERNAL_STORAGE` (`StoragePermission` + intent
      All-Files-Access, fallback legacy < API 30) + `FolderPickerDialog` (browser File) →
      `WorkspaceViewModel.setProjectRoot`. Tombol folder di header file-tree.
- [x] Scheme editor **reaktif tema**: `CodeEditorView` `update` lambda re-apply `TextMateSetup`
      theme (darcula/quietlight) + `colorScheme` saat `darkTheme` berubah.

**Catatan grammar:** hanya java/kotlin/xml yang di-bundel. `.gradle` (Groovy), JSON, Markdown, dll
masih plain — tambah grammar-nya ke `assets/textmate/` + `languages.json` bila perlu.

### Fase 2 — Terminal + environment Linux (3–6 minggu)

**Fase 2.0 ✅ (terminal + shell sistem):**
- [x] Integrasi komponen **Termux** via JitPack (`com.termux.termux-app:terminal-view:0.118.0`,
      tarik `terminal-emulator` transitif; JNI arm64). Modul `:terminal`:
      `TerminalScreen` (AndroidView host `TerminalView`) + `TerminalSessionClientImpl` +
      `TerminalViewClientImpl` + baris **extra-keys** (ESC/TAB/^C/^D/panah, dibangun dari
      `Char(code)` — tanpa control char mentah di source).
- [x] Menjalankan **shell sistem Android** (`/system/bin/sh`) — tanpa bootstrap, jalan di device
      arm64 apa pun. Dibuka via ikon Terminal di top bar → overlay layar penuh (`TerminalOverlay`).
- **Milestone 2.0 tercapai: terminal fungsional, bisa `ls`/`cd`/`cat`/`sh` di HP.**

**Fase 2.1a ✅ (bootstrap Linux — terverifikasi di device):** dipilih **Opsi C** (reuse toolchain AndroidIDE).
- [x] **`targetSdk` diturunkan ke 28** — WAJIB: Android 29+ melarang exec binari dari data app
      (SELinux W^X); AndroidIDE (targetSdk 28) & Termux pun begitu. Efek: model storage legacy
      (folder-picker eksternal Fase 1.1 perlu penyesuaian ulang — **known gap** sementara).
- [x] `Environment` (path `$PREFIX/$HOME/bin/lib/tmp`, builder env) + `BootstrapInstaller`
      (unduh bootstrap AndroidIDE `terminal-packages` bootstrap-16.12.2023 aarch64 26MB, verifikasi
      SHA-256, ekstrak + `Os.chmod 0700` + proses `SYMLINKS.txt` via `Os.symlink`, pindah ke prefix).
- [x] `TerminalHost`: layar setup (progres unduh/ekstrak + **error di layar** + retry) → jalankan
      **login bash** (`$PREFIX/bin/bash -bash`) dengan env; fallback shell sistem.
- [x] **Bug ditemukan & di-fix di device:** `TerminalView` dibuat tanpa background eksplisit →
      cell default (hitam) tembus ke `Surface` putih Compose di baliknya → teks putih default jadi
      tak terbaca ("blank" palsu). Fix: `setBackgroundColor(Color.BLACK)` di `TerminalScreen.kt`.
- **Milestone 2.1a tercapai: `bash-5.2$` jalan nyata di device, prompt terbaca.**
- **Sumber:** `github.com/AndroidIDEOfficial/terminal-packages` (bootstrap lama, tetap dipakai
      untuk histori — **AndroidIDE resmi discontinued** Des 2024, org di-archive, tidak ada
      update lagi).

**Fase 2.1b 🔨 (apt/dpkg + JDK/Gradle — sedang diuji):** pivot sumber bootstrap karena AndroidIDE mati.
- [x] **Ganti sumber bootstrap ke Termux resmi** (`termux/termux-packages`, rilis mingguan aktif),
      superset dari yang lama: bash + coreutils **+ apt/dpkg/curl/nano/gpgv**. `BOOTSTRAP_URL`/
      `BOOTSTRAP_SHA256` di `Environment.kt` diupdate ke `bootstrap-2026.07.05-r1+apt.android-7`
      (diverifikasi SHA-256 manual, ukuran ~30 MB).
- [x] **Ditemukan & di-fix sebelum ke device:** bootstrap Termux resmi dibuild untuk prefix
      tetap `/data/data/com.termux/files/usr` (app Termux sendiri), bukan prefix relokatable
      seperti build AndroidIDE. Dua dampak, dua fix di `BootstrapInstaller.kt`:
      1. 20 entri di `SYMLINKS.txt` (keyring GPG apt, alias busybox-style) pakai absolute path
         ke prefix Termux → di-retarget ke prefix kita saat parsing (`extract()`).
      2. `apt`/`dpkg` (beda dari `bash`) tidak fallback ke env var `$PREFIX` untuk `Dir::Bin::Methods`
         dkk — di-generate `etc/apt/apt.conf.d/00androidkris-prefix.conf` yang override semua
         `Dir::*` ke prefix kita (`writeAptPrefixOverride()`), plus siapkan folder
         `var/lib/apt/lists/partial`, `var/cache/apt/archives/partial`, dll yang tidak ada di zip.
- [x] **Diuji di device (putaran 1):** `apt update` gagal — `apt` sama sekali tidak baca
      `etc/apt/apt.conf.d/00androidkris-prefix.conf` di prefix kita, karena `Dir::Etc` default
      apt sendiri (hardcoded) masih nunjuk ke prefix Termux; override kita nggak pernah
      "ditemukan" (chicken-and-egg). Juga kelihatan warning `-bash: .../etc/profile: Permission
      denied` (bash login-shell coba baca profile Termux, `EACCES` karena app lain nggak
      bisa ditelusuri di Android).
- [x] **Fix (putaran 1):** set env var **`APT_CONFIG`** (dibaca apt sebelum default manapun,
      resmi didukung apt) langsung ke path `apt.conf` kita di `Environment.shellEnv()` — bypass
      masalah chicken-and-egg tanpa perlu bootstrap di-download ulang. Ganti `-bash` (login shell)
      → `bash` biasa di `TerminalHost.kt` supaya nggak coba baca `/etc/profile` Termux sama sekali.
- [x] **Diuji di device (putaran 2):** `APT_CONFIG` fix jalan — apt beneran fetch `InRelease`
      (14.0 kB) dari `packages-cf.termux.dev`. Gagal berikutnya: `Couldn't execute
      /data/data/com.termux/files/usr/bin/apt-key` → repo dianggap "not signed", di-disable.
- [x] **Ditemukan:** bukan cuma `Dir::*` config — **~110 file di bootstrap adalah shell script**
      (`apt-key`, `termux-*`, `dpkg-buildapi`, dll) dengan **shebang** `#!/data/data/com.termux/
      files/usr/bin/sh` hardcoded (bukan cuma di baris shebang, tapi juga isinya — mis. `apt-key`
      nyimpen path `trusted.gpg.d` hardcoded juga). Interpreter-nya nggak ada di sandbox kita
      (`EACCES`, punya app lain), jadi script-nya nggak bisa dieksekusi sama sekali.
- [x] **Fix (putaran 2):** `Environment.repairPrefix()` — sekarang jalan tiap terminal dibuka
      (bukan cuma sekali pas install): (1) tulis ulang `apt.conf` (+ `Dir::Bin::apt-key` yang
      kelewat sebelumnya), (2) `fixHardcodedScriptShebangs()` — scan seluruh prefix, untuk file
      yang diawali `#!` ganti semua kemunculan path Termux ke prefix kita (bukan cuma baris
      shebang). Refactor ini juga bikin iterasi berikutnya nggak butuh reinstall bootstrap sama
      sekali — cukup update APK.
- [x] **Diuji di device (putaran 3):** jauh lebih jauh — `apt update` sukses penuh (GPG/keyring
      verifikasi lolos!), `apt install -y openjdk-17` **berhasil fetch 118 MB** paket (openjdk-17
      95.6 MB + puluhan dependency X11/font/audio). Gagal di tahap akhir (unpack): `dpkg: error:
      error opening configuration directory '/data/data/com.termux/files/usr/etc/dpkg/dpkg.cfg.d':
      Permission denied`.
- [x] **Ditemukan (beda kategori dari 4 fix sebelumnya):** dicek langsung ke source resmi dpkg
      (`dpkg_options_load_dir()`) — path config dpkg (`CONFIGDIR`) itu **konstanta compile-time
      yang genuinely terpisah** dari `--root`/`--instdir`/`--admindir`/`DPKG_ROOT`, **tidak ada**
      env var atau flag CLI buat override. Dan karena target-nya path app Termux, Android **OS-level**
      menolak traversal ke situ (`EACCES` di seluruh subtree, bukan sekadar "belum ada") — nggak
      ada trik filesystem userspace yang bisa nembus ini.
- [x] **Fix (putaran 3) — lompatan scope, disetujui user:** native **LD_PRELOAD shim**
      (`terminal/src/main/cpp/interpose.c`, dibangun via CMake/NDK, `ndkVersion = 26.1.10909125`).
      Intercept `open`/`openat`/`fopen`/`opendir`/`stat`/`lstat`/`access`/`execve` di level libc;
      kalau path diawali prefix Termux, alihkan ke prefix kita (dibaca dari env var
      `ANDROIDKRIS_REAL_PREFIX`, bukan hardcoded, biar tetap sinkron sama `Environment.prefix`
      tanpa rebuild native). Diaktifkan via `LD_PRELOAD` di `shellEnv()` — **hanya kalau file
      `.so`-nya benar-benar ada** (`interposeLib.exists()`), supaya kalau native build gagal,
      efeknya "sebagian belum jalan", bukan "semua proses gagal start". Teknik sama yang dipakai
      Termux sendiri (`termux-exec`, ikut ke-bundle di bootstrap kita) — terbukti jalan di
      Android/bionic dengan `targetSdk=28` kita.
- [x] **Diuji di device (putaran 4):** shim jalan (bash normal, error "opening configuration
      directory" hilang total) → `apt update` sukses penuh, GPG lolos. Gagal berikutnya:
      `dpkg-deb (subprocess): failed to chdir to directory: Permission denied` — `chdir()`
      belum ada di daftar intercept. Fix: tambah `chdir`/`chown`/`lchown`/`truncate`/`utimes`.
- [x] **Diuji di device (putaran 5):** lolos chdir, sampai proses **unpack semua 39 paket**
      (termasuk openjdk-17!). Gagal di error baru: `unable to stat './data/data/com.termux'
      (which was about to be installed): Permission denied`.
- [x] **Ditemukan (root cause berbeda lagi):** paket resmi Termux membungkus isi `data.tar`
      dengan path **relatif ke `/`** (mis. `./data/data/com.termux/files/usr/lib/foo`) — karena
      root dpkg Termux sendiri memang `/`. `dpkg` menelusuri direktori itu **selangkah demi
      selangkah** (`chdir` + `mkdir`/`stat` relatif untuk "data", lalu "data/data", lalu
      "data/data/com.termux", dst) — bukan selalu satu path absolut utuh. Shim lama cuma cocokkan
      path **absolut yang PERSIS diawali** prefix Termux; path relatif dan path **leluhur**
      (ancestor, lebih pendek dari prefix Termux) lolos tanpa dialihkan → nyentuh filesystem asli
      → `EACCES`. Sempat salah duga & coba hapus `Dir` dari `apt.conf` (supaya dpkg root balik ke
      `/`) — ternyata itu memang **prasyarat** yang benar (dpkg WAJIB berpikir root-nya `/`,
      persis kayak Termux, biar shim di level syscall yang nangani semuanya) — tapi butuh shim-nya
      diperkuat dulu supaya benar-benar menangani kasus ini.
- [x] **Fix (putaran 5):** `interpose.c` sekarang **lacak `cwd` sendiri** (refresh tiap `chdir()`
      sukses) buat resolve path relatif, dan `rewrite_path()` jadi dua arah: (1) path yang match
      persis/lebih dalam dari prefix Termux → tetap alihkan seperti biasa; (2) path yang jadi
      **leluhur** dari prefix Termux (mis. `/data`, `/data/data`, dst — dpkg lagi mastiin
      direktori perantara ada sebelum masuk lebih dalam) → dikumpulkan jadi satu, dialihkan ke
      **root prefix kita sendiri** (yang sudah pasti ada & bisa ditulis), supaya panggilannya
      sukses dan dpkg lanjut ke level berikutnya. `apt.conf`: baris `Dir` (root) dihapus permanen
      (root dpkg dibiarkan default `/`, sesuai desain Termux, ditangani penuh oleh shim).
- [x] **Diuji di device (putaran 6):** lolos jauh lebih jauh, sampai `unpack` 39 paket. Gagal baru:
      `unable to securely remove '.../var/lib/dpkg/files.dpkg-tmp'` — ini pembersihan defensif dpkg
      sendiri sebelum nulis (`path_remove_tree` di `archives.c`), berdiri sendiri, **bukan** bagian
      dari `rename()` di run yang sama. Aman dialihkan **hanya untuk pemanggil read-only/remove**
      (`stat`/`lstat`/`access`/`unlink`/`rmdir`/`opendir`/`scandir`/`*at()` versinya), **tidak
      pernah** untuk `rename`/`link`/`symlink`/`open` (kalau leluhur-plus-suffix dialihkan di situ,
      `rename()` bisa memindah **lalu hapus rekursif seluruh prefix** — sempat kepikiran fix umum
      begini, dilacak ke source dpkg dulu sebelum commit, dibatalkan sebelum sempat di-build/push).
      Fix: `rewrite_path()` (ketat, dipakai buat *builder* dst.) vs `rewrite_path_loose()`
      (dipakai cuma buat 10 syscall read-only/remove di atas).
- [x] **BUG SERIUS — seluruh `$PREFIX` hilang, 2x kejadian:** setelah beberapa putaran
      `dpkg --purge --force-all --force-remove-reinstreq libunbound` (buat keluar dari status
      `reinstreq`/"very bad inconsistent state"), `$PREFIX/bin` lalu **seluruh `$PREFIX`** hilang
      total (`ls -la $PREFIX` → "No such file or directory"). User clear-data + install ulang dari
      nol (30 MB fresh) — **hilang lagi**, kali ini **tanpa** menjalankan perintah destruktif apa
      pun, cuma `pkg update`/`pkg upgrade` yang gagal "not installed".
- [x] **Root cause (kejadian ke-2, ditemukan lewat baca ulang `BootstrapInstaller.extract()`):**
      race condition, bukan corruption dpkg. `extract()` selalu `if (prefix.exists())
      prefix.deleteRecursively()` **sebelum** `staging.renameTo(prefix)` — kalau `install()`
      terpanggil **dua kali bersamaan** (double-tap tombol "Unduh & pasang" sebelum Compose sempat
      menyembunyikan tombolnya di frame berikutnya, ATAU install pertama masih jalan di background
      IO thread walau composable-nya sudah dibuang — `withContext(Dispatchers.IO)` **tidak**
      cooperative-cancel operasi file blocking), run kedua yang selesai belakangan akan
      men-delete-recursively prefix yang **sudah jadi & sedang dipakai** run pertama (bash sudah
      jalan dari situ!). Proses bash yang sudah ke-fork tetap hidup (binary-nya sudah di-map ke
      memori), tapi setiap lookup path baru (`$PREFIX`, `$PREFIX/bin`, cari `pkg`/`apt` di `PATH`)
      langsung ENOENT — persis gejala "abis install fresh kok prefix-nya kosong total".
- [x] **Fix:** `BootstrapInstaller.install()` sekarang dijaga `Mutex.tryLock()` — run kedua yang
      tumpang tindih langsung ditolak (`BootstrapState.Failed("Instalasi lain sedang berjalan...")`)
      alih-alih ikut race ke `extract()`. Tambahan jaga-jaga di UI (`TerminalHost.kt`): `onClick`
      tombol install sekarang cek ulang `state is Idle/Failed` sebelum `scope.launch`, biar
      double-tap dalam satu frame yang sama tidak dua-duanya lolos ke `install()`.
- [x] **Race condition BUKAN penyebabnya di kejadian ke-3:** user konfirmasi install bersih (app
      di-uninstall dulu, sekali tap, tunggu "Selesai"), lalu **masuk terminal, jalanin cuma
      command baca-baca doang** (`cat`, `ls`, `apt update`) — `install.log` nunjukin prefix
      **sehat penuh saat launch** (`binCount=394`), tapi **hilang total dalam sesi yang sama**,
      app **tetap di foreground terus** (tidak pindah app). Device: **Redmi Note 10S (MIUI)** dan
      **Vivo (FuntouchOS/OriginOS)** — dua-duanya kejadian yang sama.
- [x] **Root cause paling mungkin (di luar kendali kode kita):** MIUI & FuntouchOS/OriginOS
      punya heuristik keamanan yang **secara spesifik mendeteksi pola tulis-file-lalu-chmod-
      executable berulang ratusan kali** (persis yang `extract()` lakukan: ~700 file, tulis lalu
      `chmod 0700` satu-satu) sebagai signature "malware dropper", dan diam-diam menghapus/
      mencabut file hasilnya. Ini masalah kompatibilitas yang **sudah didokumentasikan buat
      Termux sendiri** di ROM-ROM ini — bukan hal yang bisa di-fix tuntas dari kode app (tidak
      ada API publik buat opt-out dari heuristik keamanan OEM tertentu).
- [x] **Mitigasi yang bisa dilakukan dari kode (dua arah, keduanya di-ship):**
      1. **Auto pulih cepat**: `Environment.cachedZip` (sibling `ide/`, bukan di `context.cacheDir`
         lagi) sekarang **disimpan permanen** setelah extract sukses (dulu langsung dihapus).
         `BootstrapInstaller.hasValidCache()` cek hash-nya masih cocok; kalau prefix ternyata
         hilang/rusak, `TerminalHost` otomatis re-extract dari zip yang sudah ke-cache ini
         (`BootstrapSetup(autoStart = true)`, lewat `install()` yang sama — deteksi cache
         otomatis, skip fase download) — pemulihan dari total wipe jadi **hitungan detik**,
         bukan unduh ulang 30 MB + tap manual lagi.
      2. **Kurangi signature yang dicurigai**: `extract()` sekarang menulis **semua** file dulu,
         baru **satu batch `chmod`** terpisah di akhir (bukan interleaved tulis-lalu-chmod per
         file). Tidak menjamin lolos dari heuristik OEM (tidak ada cara memastikan tanpa akses ke
         source heuristik-nya), tapi mengubah pola syscall-nya — satu-satunya tuas yang tersedia
         dari sisi app.
      3. **Diagnostic log** (`Environment.logDiag()`, ditambahkan sebelumnya) dipertahankan buat
         menangkap kejadian berikutnya kalau masih terjadi.
- [ ] **Rekomendasi ke user (di luar kode, harus dilakukan manual di device):** MIUI — matikan
      **"MIUI Optimization"** di Developer Options (bukan cuma battery saver/autostart biasa).
      Vivo — cari pengaturan keamanan/"behavior interception" di iManager buat whitelist app ini.
      Kalau ROM tetap agresif menghapus walau sudah dimatikan, **auto-repair di atas** jadi jaring
      pengaman utama: user cukup keluar-masuk lagi ke terminal, tanpa unduh ulang.
- [ ] **Belum diverifikasi di device** (fix di atas — batch chmod + auto-repair cache). User perlu
      test lagi: kalau prefix masih kena hapus ROM, cek apakah re-entry ke terminal langsung
      "Memperbaiki environment (dari cache)…" dalam hitungan detik alih-alih kembali ke layar
      unduh 30 MB.
- [x] **Riset: "tiru AndroidIDE" buat JDK/Gradle** — user minta refactor niru cara AndroidIDE
      pasang JDK/Gradle setelah capek berkali-kali gagal. Dicek langsung ke source resminya
      (`androidide-tools` — repo arsip, tapi releases/manifest.json masih bisa diakses):
      **JDK-nya AndroidIDE sendiri pasang lewat `pkg install openjdk-$version`** — mekanisme
      **persis sama** dengan yang sudah kita pakai (apt/dpkg Termux), cuma jalan di dalam bootstrap
      yang mereka build ulang khusus buat path app mereka sendiri (`com.itsaky.androidide`) saat
      compile time — sesuatu yang nggak bisa kita tiru tanpa build farm cross-compile sendiri buat
      ratusan paket Termux. Jadi "niru AndroidIDE" untuk JDK **tidak** memberi jalan pintas.
      **Tapi** SDK Android-nya AndroidIDE **beneran langsung tarball download+extract**, sama
      sekali skip package manager — pola ini bisa & layak ditiru buat **Gradle**.
- [x] **Gradle: dipasang langsung dari upstream resmi, skip Termux/apt sama sekali**
      (`GradleInstaller.kt`, baru). Gradle itu murni JVM bytecode + launcher shell script — **tidak
      ada build khusus aarch64**, jadi zip resminya (`services.gradle.org/distributions/
      gradle-8.10.2-bin.zip`, SHA-256 dicocokkan manual ke `gradle.org/release-checksums/`) bisa
      langsung dipakai, persis seperti Gradle Wrapper di desktop manapun. Ini sekaligus lepas dari
      concern lama "paket `gradle` Termux butuh JDK 21 sebagai dependency" — nggak lagi relevan.
      Muncul sebagai bar kecil non-modal di atas terminal begitu bash jalan (`GradleBar` di
      `TerminalHost.kt`), bukan full-screen gate seperti bootstrap — install Gradle nggak nge-block
      pemakaian shell buat hal lain. Pola cache+auto-repair yang sama kayak bootstrap (`hasValidCache`,
      zip disimpan permanen) ikut diterapkan, karena extract-nya juga nulis ~2000 file + chmod satu
      launcher script → risiko OEM-cleanup yang sama seperti bootstrap.
- [x] **Refactor kecil:** logic download+verifySha256 yang tadinya duplikat di `BootstrapInstaller`
      diekstrak ke `Downloader.kt` (dipakai bareng oleh `BootstrapInstaller` & `GradleInstaller`).
- [x] **`JAVA_HOME` sekarang diset eksplisit.** User share `idesetup.sh` AndroidIDE langsung —
      konfirmasi paket `openjdk-*` Termux (yang sama persis kita pakai lewat `apt install`) selalu
      terpasang di `$PREFIX/opt/openjdk`, bukan path per-versi. Sebelumnya sengaja tidak di-set
      (nggak yakin path-nya, andalkan Gradle nyari `java` sendiri lewat `PATH`) — sekarang
      `Environment.javaHome` + `shellEnv()` set `JAVA_HOME` kalau direktorinya sudah ada.
- [ ] **JDK:** tetap lewat `apt install -y openjdk-17` (jalur yang sudah jauh lebih matang — semua
      bug dpkg/apt yang pernah ditemukan sudah di-fix termasuk `.dpkg-tmp`). Blocker yang tersisa
      murni OEM-level (MIUI/Vivo), bukan apt/dpkg lagi — lihat mitigasi cache+auto-repair +
      rekomendasi setting OS di atas. Kalau OEM-wipe masih terjadi bahkan setelah setting device
      diperbaiki, opsi berikutnya (belum dikerjakan, butuh keputusan): extract manual `.deb`
      openjdk-17 (skip dpkg's dependency graph 39 paket → cuma ambil JDK-nya sendiri), tapi ini
      butuh parser `ar`+`tar`+`xz` baru (dependency `commons-compress`+`org.tukaani:xz`) yang belum
      diverifikasi terhadap `.deb` Termux asli — risiko tinggi buat di-ship tanpa testing langsung.
- [ ] **Belum diverifikasi di device** (Gradle installer baru). Android SDK/build-tools aarch64
      masih menyusul terpisah (Termux tidak menyediakan ini, AndroidIDE juga sudah mati jadi
      binernya nggak bisa dipakai ulang — perlu sumber lain).
- **Sumber:** `github.com/termux/termux-packages` (aktif, rilis mingguan) untuk bash/coreutils/apt;
  `services.gradle.org` (resmi Gradle) untuk Gradle.

### Fase 3 — Gradle sync (project model) (4–8 minggu) ⚠️ tersulit
- [ ] Pasang **Gradle aarch64 + Android build-tools + platform jar** dari distribusi AndroidIDE.
- [ ] Sambung **Gradle Tooling API**: jalankan Gradle daemon, ambil model project (module, sumber,
      dependency).
- [ ] Pola **plugin Gradle injection** (tiru AndroidIDE): plugin kecil ditempel ke build user
      untuk melaporkan info tooling balik ke app.
- [ ] Tampilkan hasil sync: daftar module, task, error sync di UI.
- **Milestone: buka project Android nyata → app mengerti strukturnya (belum compile).**

### Fase 4 — Build & install APK (3–6 minggu)
- [ ] Jalankan task `assembleDebug` lewat Gradle daemon; streaming log build ke panel output.
- [ ] Tangani AAPT2 + d8 + apksigner (semua versi aarch64) sampai keluar `.apk`.
- [ ] `Install APK` via `Intent` / session installer; jalankan app hasil build.
- **Milestone: BUILD APK DI HP BERHASIL. Ini inti proyek.** 🎯

### Fase 5 — Kecerdasan editor / LSP (4–8 minggu)
- [ ] Integrasi **Java LSP (fork JDT)** AndroidIDE: autocomplete, error inline, go-to-def, hover.
- [ ] XML LSP (LemMinX) untuk layout & manifest; Kotlin LSP kalau sanggup.
- **Milestone: editor pintar — autocomplete & error real-time seperti IDE beneran.**

### Fase 6 — Poles & fitur khas (berkelanjutan)
- [ ] Logcat viewer, template project baru (Empty Activity dsb.), UI designer drag-drop,
      git client, tema, pengaturan, manajemen SDK.

**Total realistis untuk versi bisa-build-APK: ~6–12 bulan kerja fokus** (lebih cepat kalau ambil
Jalur A fork sejak awal, dengan konsekuensi lisensi & pemahaman kode).

---

## 4. Masalah sulit yang HARUS diantisipasi (di sinilah proyek serupa mati)

1. **Kompilasi toolchain ke ARM64.** Jangan kompilasi Gradle/AAPT2/JDK sendiri — **pakai prebuilt
   AndroidIDE.** Bikin sendiri = neraka cross-compile berbulan-bulan.
2. **Menjalankan proses native di Android modern.** Sejak Android 10+ ada `W^X` / batasan exec dari
   writable storage. Termux menyiasati dengan menaruh binari di `nativeLibraryDir`
   (`lib/arm64-v8a/*.so`) atau app data dengan trik exec. Pelajari cara Termux, jangan lawan OS.
3. **Gradle daemon di memori terbatas.** Perlu tuning `-Xmx`, matikan fitur berat, kelola daemon
   supaya tidak OOM-kill oleh Android.
4. **Jembatan Gradle Tooling ↔ app.** Bagian paling ruwet AndroidIDE. Kalau macet di sini,
   pertimbangkan Jalur A (fork) untuk komponen ini.
5. **Ukuran unduhan/bootstrap.** Toolchain 1.5–3 GB. Butuh mekanisme unduh bertahap + verifikasi.

---

## 5. Tech stack yang direncanakan

- Kotlin, sebagian Jetpack Compose (UI baru) + View klasik (editor/terminal pakai View — sora &
  Termux berbasis View).
- Hilt DI, Coroutines, DataStore/Room untuk preferensi & metadata project.
- `sora-editor` (LGPL), komponen Termux (GPLv3), prebuilt tools AndroidIDE (GPLv3).
- minSdk 26, `abiFilters "arm64-v8a"`, targetSdk terbaru.

---

## 6. Langkah paling awal (sesi berikutnya)

1. **Riset & baca sumber** (paling penting sebelum ngoding):
   - `github.com/AndroidIDEOfficial/AndroidIDE` — arsitektur & modul.
   - `github.com/AndroidIDEOfficial/androidide-build-tools` — cara distribusi toolchain aarch64.
   - `github.com/Rosemoe/sora-editor` — dokumentasi integrasi editor.
   - `github.com/termux/termux-app` — `terminal-emulator` & `terminal-view`.
2. **Putuskan lisensi & model rilis** (open-source GPLv3 — konsekuensi dari reuse).
3. **Fase 0 + Fase 1**: bikin project, integrasi sora-editor → editor kode dulu. Bagian ini paling
   cepat memberi hasil nyata dan tidak butuh ARM64.
4. Baru masuk terminal & environment (Fase 2).

---

## 7. Jalan pintas jujur

Kalau tujuan utamamu adalah **punya IDE yang bisa build APK secepatnya** (bukan belajar bikin
toolchain dari nol): **fork AndroidIDE (Jalur A), rebrand, lalu modifikasi UI/fitur.** Itu jujur
adalah cara tercepat dan realistis untuk "aplikasi seperti AndroidIDE" — dengan syarat menerima
GPLv3. Bikin klon toolchain dari nol sendirian, walau mungkin secara teknis, secara praktis jarang
selesai. Fase 1–3 di rencana ini tetap berharga sebagai pembelajaran fondasi apa pun jalur akhirmu.
