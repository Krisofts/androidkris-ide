// LD_PRELOAD shim that redirects path-taking libc calls away from Termux's own
// absolute prefix (baked into several bootstrap binaries at compile time — see
// Environment.TERMUX_HARDCODED_PREFIX) towards ours instead. Android forbids
// traversing another app's data directory outright (EACCES on the whole
// subtree, not just "missing file"), so there is no config file or environment
// variable that can fix this for every binary — interposing the syscalls
// themselves is the only general fix.
//
// Target prefix comes from the ANDROIDKRIS_REAL_PREFIX env var (set in
// Environment.shellEnv()), not a compiled-in constant, so this stays in sync
// with Environment.prefix without a rebuild.

#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

static const char *const OLD_PREFIX = "/data/data/com.termux/files/usr";
static size_t old_prefix_len;
static char *new_prefix;

// Termux's packages ship data.tar entries as paths relative to "/" (their dpkg root really is
// "/") — e.g. "./data/data/com.termux/files/usr/lib/foo" — and dpkg walks the chain one
// component at a time (chdir + relative mkdir/stat for "data", then "data/data", then
// "data/data/com.termux", ...) rather than always passing one fully-qualified absolute path.
// So this shim needs to track cwd itself to resolve relative paths, refreshed after every
// successful chdir().
static char cwd_buf[PATH_MAX];

static void refresh_cwd(void) {
    if (getcwd(cwd_buf, sizeof(cwd_buf)) == NULL) {
        cwd_buf[0] = '/';
        cwd_buf[1] = '\0';
    }
}

__attribute__((constructor)) static void interpose_init(void) {
    old_prefix_len = strlen(OLD_PREFIX);
    const char *env = getenv("ANDROIDKRIS_REAL_PREFIX");
    if (env != NULL) new_prefix = strdup(env);
    refresh_cwd();
}

// Resolves `path` to an absolute path (using our tracked cwd if it's relative) and returns a
// malloc'd redirected path if that absolute form falls at-or-under OLD_PREFIX (normal case:
// append the OLD_PREFIX-relative suffix onto new_prefix) or is a proper *ancestor* of
// OLD_PREFIX (dpkg checking/creating "/data", then "/data/data", etc. on the way to the real
// target — collapse all of those to new_prefix itself, since new_prefix already exists and is
// ours to write to; dpkg only cares that the call succeeds so it can proceed one level
// deeper). Returns NULL (meaning: use the original path unchanged) if neither applies.
// Caller must free() a non-NULL result.
static char *rewrite_path(const char *path) {
    if (path == NULL || new_prefix == NULL) return NULL;

    char resolved[PATH_MAX];
    if (path[0] == '/') {
        snprintf(resolved, sizeof(resolved), "%s", path);
    } else {
        const char *rel = path;
        if (rel[0] == '.' && rel[1] == '/') rel += 2;
        if (cwd_buf[0] == '/' && cwd_buf[1] == '\0') {
            snprintf(resolved, sizeof(resolved), "/%s", rel);
        } else {
            snprintf(resolved, sizeof(resolved), "%s/%s", cwd_buf, rel);
        }
    }
    size_t resolved_len = strlen(resolved);

    if (resolved_len >= old_prefix_len && strncmp(resolved, OLD_PREFIX, old_prefix_len) == 0 &&
        (resolved[old_prefix_len] == '/' || resolved[old_prefix_len] == '\0')) {
        const char *suffix = resolved + old_prefix_len;
        size_t len = strlen(new_prefix) + strlen(suffix) + 1;
        char *out = malloc(len);
        if (out == NULL) return NULL;
        snprintf(out, len, "%s%s", new_prefix, suffix);
        return out;
    }

    if (resolved_len < old_prefix_len && strncmp(OLD_PREFIX, resolved, resolved_len) == 0 &&
        OLD_PREFIX[resolved_len] == '/') {
        return strdup(new_prefix);
    }

    return NULL;
}

typedef int (*open_fn)(const char *, int, ...);
int open(const char *pathname, int flags, ...) {
    static open_fn real = NULL;
    if (real == NULL) real = (open_fn)dlsym(RTLD_NEXT, "open");
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = (mode_t)va_arg(ap, int);
        va_end(ap);
    }
    char *rewritten = rewrite_path(pathname);
    int fd = real(rewritten != NULL ? rewritten : pathname, flags, mode);
    free(rewritten);
    return fd;
}

typedef int (*openat_fn)(int, const char *, int, ...);
int openat(int dirfd, const char *pathname, int flags, ...) {
    static openat_fn real = NULL;
    if (real == NULL) real = (openat_fn)dlsym(RTLD_NEXT, "openat");
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = (mode_t)va_arg(ap, int);
        va_end(ap);
    }
    // A relative pathname is resolved against our tracked cwd, which is only correct when
    // dirfd is AT_FDCWD; an absolute pathname ignores dirfd entirely (same as the real
    // openat()), so it's always safe to rewrite regardless of dirfd.
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int fd = real(dirfd, rewritten != NULL ? rewritten : pathname, flags, mode);
    free(rewritten);
    return fd;
}

typedef FILE *(*fopen_fn)(const char *, const char *);
FILE *fopen(const char *path, const char *mode) {
    static fopen_fn real = NULL;
    if (real == NULL) real = (fopen_fn)dlsym(RTLD_NEXT, "fopen");
    char *rewritten = rewrite_path(path);
    FILE *f = real(rewritten != NULL ? rewritten : path, mode);
    free(rewritten);
    return f;
}

typedef DIR *(*opendir_fn)(const char *);
DIR *opendir(const char *name) {
    static opendir_fn real = NULL;
    if (real == NULL) real = (opendir_fn)dlsym(RTLD_NEXT, "opendir");
    char *rewritten = rewrite_path(name);
    DIR *d = real(rewritten != NULL ? rewritten : name);
    free(rewritten);
    return d;
}

// dpkg's own config-directory scan calls scandir() directly (confirmed from its source),
// not opendir() — and bionic's scandir() doesn't necessarily resolve its internal directory
// access through the *public* opendir symbol our interposition above relies on, so it needs
// its own explicit override.
typedef int (*scandir_fn)(const char *, struct dirent ***,
                           int (*)(const struct dirent *),
                           int (*)(const struct dirent **, const struct dirent **));
int scandir(const char *dirp, struct dirent ***namelist,
            int (*filter)(const struct dirent *),
            int (*compar)(const struct dirent **, const struct dirent **)) {
    static scandir_fn real = NULL;
    if (real == NULL) real = (scandir_fn)dlsym(RTLD_NEXT, "scandir");
    char *rewritten = rewrite_path(dirp);
    int r = real(rewritten != NULL ? rewritten : dirp, namelist, filter, compar);
    free(rewritten);
    return r;
}

typedef int (*stat_fn)(const char *, struct stat *);
int stat(const char *path, struct stat *buf) {
    static stat_fn real = NULL;
    if (real == NULL) real = (stat_fn)dlsym(RTLD_NEXT, "stat");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, buf);
    free(rewritten);
    return r;
}

int lstat(const char *path, struct stat *buf) {
    static stat_fn real = NULL;
    if (real == NULL) real = (stat_fn)dlsym(RTLD_NEXT, "lstat");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, buf);
    free(rewritten);
    return r;
}

typedef int (*access_fn)(const char *, int);
int access(const char *path, int mode) {
    static access_fn real = NULL;
    if (real == NULL) real = (access_fn)dlsym(RTLD_NEXT, "access");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, mode);
    free(rewritten);
    return r;
}

typedef int (*execve_fn)(const char *, char *const[], char *const[]);
int execve(const char *path, char *const argv[], char *const envp[]) {
    static execve_fn real = NULL;
    if (real == NULL) real = (execve_fn)dlsym(RTLD_NEXT, "execve");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, argv, envp);
    free(rewritten);
    return r;
}

// dpkg mutates its admin/state files (status/status-old/status-new rotation) and unpacks
// package contents (creating directories, files, symlinks, hardlinks, permissions) — all of
// which touch paths under the prefix it thinks it's rooted at. Every mutating path-taking
// call it might reasonably make needs the same redirect as the read-only ones above.

typedef int (*unlink_fn)(const char *);
int unlink(const char *path) {
    static unlink_fn real = NULL;
    if (real == NULL) real = (unlink_fn)dlsym(RTLD_NEXT, "unlink");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path);
    free(rewritten);
    return r;
}

typedef int (*rmdir_fn)(const char *);
int rmdir(const char *path) {
    static rmdir_fn real = NULL;
    if (real == NULL) real = (rmdir_fn)dlsym(RTLD_NEXT, "rmdir");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path);
    free(rewritten);
    return r;
}

typedef int (*mkdir_fn)(const char *, mode_t);
int mkdir(const char *path, mode_t mode) {
    static mkdir_fn real = NULL;
    if (real == NULL) real = (mkdir_fn)dlsym(RTLD_NEXT, "mkdir");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, mode);
    free(rewritten);
    return r;
}

typedef int (*chmod_fn)(const char *, mode_t);
int chmod(const char *path, mode_t mode) {
    static chmod_fn real = NULL;
    if (real == NULL) real = (chmod_fn)dlsym(RTLD_NEXT, "chmod");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, mode);
    free(rewritten);
    return r;
}

typedef int (*rename_fn)(const char *, const char *);
int rename(const char *oldpath, const char *newpath) {
    static rename_fn real = NULL;
    if (real == NULL) real = (rename_fn)dlsym(RTLD_NEXT, "rename");
    char *old_r = rewrite_path(oldpath);
    char *new_r = rewrite_path(newpath);
    int r = real(old_r != NULL ? old_r : oldpath, new_r != NULL ? new_r : newpath);
    free(old_r);
    free(new_r);
    return r;
}

typedef int (*link_fn)(const char *, const char *);
int link(const char *oldpath, const char *newpath) {
    static link_fn real = NULL;
    if (real == NULL) real = (link_fn)dlsym(RTLD_NEXT, "link");
    char *old_r = rewrite_path(oldpath);
    char *new_r = rewrite_path(newpath);
    int r = real(old_r != NULL ? old_r : oldpath, new_r != NULL ? new_r : newpath);
    free(old_r);
    free(new_r);
    return r;
}

typedef int (*symlink_fn)(const char *, const char *);
int symlink(const char *target, const char *linkpath) {
    static symlink_fn real = NULL;
    if (real == NULL) real = (symlink_fn)dlsym(RTLD_NEXT, "symlink");
    // Only linkpath (where the link gets created) needs redirecting into our sandbox; target
    // is the link's stored text and should stay whatever the caller intended.
    char *rewritten = rewrite_path(linkpath);
    int r = real(target, rewritten != NULL ? rewritten : linkpath);
    free(rewritten);
    return r;
}

typedef ssize_t (*readlink_fn)(const char *, char *, size_t);
ssize_t readlink(const char *path, char *buf, size_t bufsiz) {
    static readlink_fn real = NULL;
    if (real == NULL) real = (readlink_fn)dlsym(RTLD_NEXT, "readlink");
    char *rewritten = rewrite_path(path);
    ssize_t r = real(rewritten != NULL ? rewritten : path, buf, bufsiz);
    free(rewritten);
    return r;
}

// dpkg-deb hardcodes a temp-directory path under Termux's prefix independently of $TMPDIR
// for at least one internal case (control-member extraction) — chdir() there is what actually
// surfaced it ("dpkg-deb (subprocess): failed to chdir to directory: Permission denied").
typedef int (*chdir_fn)(const char *);
int chdir(const char *path) {
    static chdir_fn real = NULL;
    if (real == NULL) real = (chdir_fn)dlsym(RTLD_NEXT, "chdir");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path);
    if (r == 0) refresh_cwd();
    free(rewritten);
    return r;
}

typedef int (*chown_fn)(const char *, uid_t, gid_t);
int chown(const char *path, uid_t owner, gid_t group) {
    static chown_fn real = NULL;
    if (real == NULL) real = (chown_fn)dlsym(RTLD_NEXT, "chown");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, owner, group);
    free(rewritten);
    return r;
}

int lchown(const char *path, uid_t owner, gid_t group) {
    static chown_fn real = NULL;
    if (real == NULL) real = (chown_fn)dlsym(RTLD_NEXT, "lchown");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, owner, group);
    free(rewritten);
    return r;
}

typedef int (*truncate_fn)(const char *, off_t);
int truncate(const char *path, off_t length) {
    static truncate_fn real = NULL;
    if (real == NULL) real = (truncate_fn)dlsym(RTLD_NEXT, "truncate");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, length);
    free(rewritten);
    return r;
}

typedef int (*utimes_fn)(const char *, const struct timeval[2]);
int utimes(const char *path, const struct timeval times[2]) {
    static utimes_fn real = NULL;
    if (real == NULL) real = (utimes_fn)dlsym(RTLD_NEXT, "utimes");
    char *rewritten = rewrite_path(path);
    int r = real(rewritten != NULL ? rewritten : path, times);
    free(rewritten);
    return r;
}

// dpkg spawns external helper commands (`rm` for cleanup, possibly `tar`/`find` elsewhere) as
// separate processes rather than calling libc directly — they inherit LD_PRELOAD same as any
// child process, but modern coreutils implements most of these via the *at() syscall family
// (unlinkat, fstatat, ...) instead of the older non-at() ones, which need their own
// interposition since dirfd-relative resolution is a different code path entirely. As with
// openat(), only rewrite when dirfd is AT_FDCWD or the path is already absolute — otherwise
// our tracked cwd isn't what the path is actually relative to.

typedef int (*unlinkat_fn)(int, const char *, int);
int unlinkat(int dirfd, const char *pathname, int flags) {
    static unlinkat_fn real = NULL;
    if (real == NULL) real = (unlinkat_fn)dlsym(RTLD_NEXT, "unlinkat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int r = real(dirfd, rewritten != NULL ? rewritten : pathname, flags);
    free(rewritten);
    return r;
}

typedef int (*renameat_fn)(int, const char *, int, const char *);
int renameat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath) {
    static renameat_fn real = NULL;
    if (real == NULL) real = (renameat_fn)dlsym(RTLD_NEXT, "renameat");
    char *old_r = (olddirfd == AT_FDCWD || oldpath[0] == '/') ? rewrite_path(oldpath) : NULL;
    char *new_r = (newdirfd == AT_FDCWD || newpath[0] == '/') ? rewrite_path(newpath) : NULL;
    int r = real(olddirfd, old_r != NULL ? old_r : oldpath, newdirfd, new_r != NULL ? new_r : newpath);
    free(old_r);
    free(new_r);
    return r;
}

typedef int (*mkdirat_fn)(int, const char *, mode_t);
int mkdirat(int dirfd, const char *pathname, mode_t mode) {
    static mkdirat_fn real = NULL;
    if (real == NULL) real = (mkdirat_fn)dlsym(RTLD_NEXT, "mkdirat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int r = real(dirfd, rewritten != NULL ? rewritten : pathname, mode);
    free(rewritten);
    return r;
}

typedef int (*fchmodat_fn)(int, const char *, mode_t, int);
int fchmodat(int dirfd, const char *pathname, mode_t mode, int flags) {
    static fchmodat_fn real = NULL;
    if (real == NULL) real = (fchmodat_fn)dlsym(RTLD_NEXT, "fchmodat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int r = real(dirfd, rewritten != NULL ? rewritten : pathname, mode, flags);
    free(rewritten);
    return r;
}

typedef int (*symlinkat_fn)(const char *, int, const char *);
int symlinkat(const char *target, int newdirfd, const char *linkpath) {
    static symlinkat_fn real = NULL;
    if (real == NULL) real = (symlinkat_fn)dlsym(RTLD_NEXT, "symlinkat");
    char *rewritten = (newdirfd == AT_FDCWD || linkpath[0] == '/') ? rewrite_path(linkpath) : NULL;
    int r = real(target, newdirfd, rewritten != NULL ? rewritten : linkpath);
    free(rewritten);
    return r;
}

typedef ssize_t (*readlinkat_fn)(int, const char *, char *, size_t);
ssize_t readlinkat(int dirfd, const char *pathname, char *buf, size_t bufsiz) {
    static readlinkat_fn real = NULL;
    if (real == NULL) real = (readlinkat_fn)dlsym(RTLD_NEXT, "readlinkat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    ssize_t r = real(dirfd, rewritten != NULL ? rewritten : pathname, buf, bufsiz);
    free(rewritten);
    return r;
}

typedef int (*fchownat_fn)(int, const char *, uid_t, gid_t, int);
int fchownat(int dirfd, const char *pathname, uid_t owner, gid_t group, int flags) {
    static fchownat_fn real = NULL;
    if (real == NULL) real = (fchownat_fn)dlsym(RTLD_NEXT, "fchownat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int r = real(dirfd, rewritten != NULL ? rewritten : pathname, owner, group, flags);
    free(rewritten);
    return r;
}

typedef int (*faccessat_fn)(int, const char *, int, int);
int faccessat(int dirfd, const char *pathname, int mode, int flags) {
    static faccessat_fn real = NULL;
    if (real == NULL) real = (faccessat_fn)dlsym(RTLD_NEXT, "faccessat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int r = real(dirfd, rewritten != NULL ? rewritten : pathname, mode, flags);
    free(rewritten);
    return r;
}

typedef int (*fstatat_fn)(int, const char *, struct stat *, int);
int fstatat(int dirfd, const char *pathname, struct stat *buf, int flags) {
    static fstatat_fn real = NULL;
    if (real == NULL) real = (fstatat_fn)dlsym(RTLD_NEXT, "fstatat");
    char *rewritten = (dirfd == AT_FDCWD || pathname[0] == '/') ? rewrite_path(pathname) : NULL;
    int r = real(dirfd, rewritten != NULL ? rewritten : pathname, buf, flags);
    free(rewritten);
    return r;
}
