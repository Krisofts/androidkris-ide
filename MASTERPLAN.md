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
- [ ] **Belum diverifikasi di device** — build pertama yang benar-benar pakai native code (NDK).
      Risiko lebih tinggi dari fix-fix sebelumnya: kalau shim ini crash/salah, bisa bikin **semua**
      proses (termasuk `bash` sendiri) gagal start via `LD_PRELOAD`, bukan cuma dpkg — balik ke
      gejala "terminal blank" tapi kali ini penyebabnya beda. Kalau itu terjadi, laporkan segera;
      fallback tercepat: hapus baris `LD_PRELOAD`/`ANDROIDKRIS_REAL_PREFIX` dari `shellEnv()`.
      Coba lagi: `apt update && apt install -y openjdk-17` sampai tuntas (unpack + configure).
- [ ] Setelah `java -version` jalan: pasang **Gradle** (paket Termux `gradle` 9.6.1, butuh JDK 21
      sebagai dependency menurut build script-nya — cek ulang versi JDK yang dibutuhkan) →
      prasyarat Fase 3 (Gradle sync). Android SDK/build-tools aarch64 menyusul terpisah (Termux
      tidak menyediakan ini, masih perlu cari sumber lain karena AndroidIDE sudah mati).
- **Sumber:** `github.com/termux/termux-packages` (aktif, rilis mingguan).

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
