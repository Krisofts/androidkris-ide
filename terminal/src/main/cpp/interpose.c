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
