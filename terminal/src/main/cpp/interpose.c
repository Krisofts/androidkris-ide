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

__attribute__((constructor)) static void interpose_init(void) {
    old_prefix_len = strlen(OLD_PREFIX);
    const char *env = getenv("ANDROIDKRIS_REAL_PREFIX");
    if (env != NULL) new_prefix = strdup(env);
}

// Returns a malloc'd rewritten path if `path` falls under OLD_PREFIX, else NULL
// (meaning: use the original path unchanged). Caller must free() a non-NULL result.
static char *rewrite_path(const char *path) {
    if (path == NULL || new_prefix == NULL) return NULL;
    if (strncmp(path, OLD_PREFIX, old_prefix_len) != 0) return NULL;
    const char *suffix = path + old_prefix_len;
    size_t len = strlen(new_prefix) + strlen(suffix) + 1;
    char *out = malloc(len);
    if (out == NULL) return NULL;
    snprintf(out, len, "%s%s", new_prefix, suffix);
    return out;
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
    char *rewritten = rewrite_path(pathname);
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
