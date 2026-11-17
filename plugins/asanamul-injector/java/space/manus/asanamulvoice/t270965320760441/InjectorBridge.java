package space.manus.asanamulvoice.t270965320760441;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Own original Shizuku integration for AS ANAMUL VOICE (Expo build).
 *
 * Ported from the proven native app's ShizukuHelper (which reached
 * policy=true on the user's Android 11 phone): the mic injector
 * (Injector in this same package) runs as a shell-UID process via
 * Shizuku's app_process, so its audio is mixed into the microphone
 * stream. The app talks to the injector through FILES in the app's
 * EXTERNAL files dir (cmd + inject.log), not a socket: a shell-UID
 * process's abstract LocalServerSocket and an app-UID process
 * connecting to it can be blocked, and a socket gives the app no log
 * when it fails. The external dir is writable by the shell
 * (sdcard_rw); the private files dir (mode 700) is not.
 *
 * Critical rules (violating any of them breaks the launch — proven
 * across 13 versions of the native app):
 *  - Launch via the Shizuku SHELL with ONE command:
 *    CLASSPATH='<apk>' nohup app_process /system/bin <Injector> '<dir>'
 *    >/dev/null 2>&1 &  — env=NULL on every newProcess call; CLASSPATH
 *    and the runtime env live ONLY inline in the command string.
 *  - Working dir = getExternalFilesDir("inject") subdirectory, else
 *    new File(getFilesDir(), "inject"), then mkdirs().
 *  - Liveness = inject.log contains "Injector ready" (polled 400 ms,
 *    up to 10 s). A ready injector with policy=false is NOT a success.
 *  - The stock ShizukuProvider + the Sui STUB (rikka.sui.Sui in this
 *    project, isSui()->false): the real Sui NPE-crashes on Android 11.
 */
public class InjectorBridge {

    private static final String TAG = "ASAVShizuku";
    public static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    public static final String INJECTOR_CLASS =
            "space.manus.asanamulvoice.t270965320760441.Injector";
    private static final String SHELL_LOG_NAME = "injector_shell.log";
    private static final String DIRECT_LOG_NAME = "injector_last.txt";
    public static final String INJECT_LOG_NAME = "inject.log";
    // GUARANTEED log mirror: /data/local/tmp is world-writable (the
    // shell can ALWAYS write it) and world-readable (the app can
    // ALWAYS read it). The Injector mirrors every fileLog() line here
    // in ADDITION to the working dir's inject.log. The pre-clean
    // deletes it on every fresh attempt so a stale "Injector ready"
    // can never fake a start. (Distinct file name from the old native
    // app so the two apps never share one log.)
    public static final String TMP_LOG_PATH = "/data/local/tmp/asanamul_voice_inject.log";
    // FULL Android runtime environment for the injector launch.
    // app_process needs these to initialise ART; the Shizuku
    // newProcess env may NOT carry them — set them EXPLICITLY,
    // inline in every shell command. These are the standard values
    // adb shell uses.
    private static final String RUNTIME_ENV_INLINE =
            "ANDROID_DATA=/data ANDROID_ROOT=/system ANDROID_STORAGE=/storage ";
    // SHORT failure reason for the on-screen message. Set at every
    // failure point; the message shows this + the injector's own
    // last line ONLY — never a long diagnostic dump.
    private static volatile String lastFailShort = "";
    // Which path started the CURRENT injector ("shizuku" / "root"),
    // set on every successful start. The policy=false retry uses it
    // to know whether ROOT has already been tried.
    private static volatile String lastStartPath = "";
    public static final int REQ_SHIZUKU_PERMISSION = 1001;

    private static boolean micModeOn = false;
    private static boolean injectorConnected = false;

    /** Result of a synchronous injector start attempt. */
    public static class StartResult {
        public boolean ok = false;
        public boolean policy = false;
        public String detail = "";
    }

    /** The injector's working dir: a SUBDIRECTORY named "inject"
     * inside the app's EXTERNAL files dir, falling back to
     * new File(getFilesDir(), "inject") when external is null,
     * then mkdirs(). The injector runs as shell uid 2000
     * (sdcard_rw, CAN write the external dir) but CANNOT write
     * the private files dir (mode 700). The cmd file, inject.log
     * and the injector's working dir all live here. Never throws. */
    public static java.io.File injectorDir(Context ctx) {
        try {
            Context appCtx = ctx;
            try {
                Context ac = ctx.getApplicationContext();
                if (ac != null) appCtx = ac;
            } catch (Throwable ignored) {}
            java.io.File d = null;
            try {
                d = appCtx.getExternalFilesDir("inject");
            } catch (Throwable ignored) {}
            if (d == null) {
                try {
                    d = new java.io.File(appCtx.getFilesDir(), "inject");
                } catch (Throwable ignored) {}
            }
            if (d != null) {
                try { d.mkdirs(); } catch (Throwable ignored) {}
                return d;
            }
        } catch (Throwable ignored) {}
        try {
            return ctx.getFilesDir();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isMicActive() {
        try { return micModeOn && injectorConnected; } catch (Throwable t) { return false; }
    }

    public static boolean isMicModeOn() {
        try { return micModeOn; } catch (Throwable t) { return false; }
    }

    public static void setMicMode(boolean on) {
        micModeOn = on;
        if (!on) injectorConnected = false;
    }

    /** Shizuku API needs Android 6.0+ (API 23). */
    public static boolean apiLevelOk() {
        return Build.VERSION.SDK_INT >= 23;
    }

    public static boolean isShizukuInstalled(Context ctx) {
        try {
            if (ctx == null) return false;
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShizukuRunning() {
        if (!apiLevelOk()) return false;
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        if (!apiLevelOk()) return false;
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            Shizuku.requestPermission(REQ_SHIZUKU_PERMISSION);
        } catch (Throwable t) {
            Log.w(TAG, "requestPermission failed", t);
        }
    }

    /** Open the Shizuku app (or its Play Store page if it cannot be launched). */
    public static void openShizukuApp(Context ctx) {
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            }
        } catch (Exception ignored) {}
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + SHIZUKU_PKG));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://play.google.com/store/apps/details?id=" + SHIZUKU_PKG));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Start the Injector via Shizuku. SYNCHRONOUS — the caller MUST
     * run this on a background thread (it blocks up to ~10 s per
     * path, plus a root retry). Runs the checks in order and returns
     * a StartResult whose detail names the cause on failure. Never
     * throws.
     */
    public static StartResult startInjectorSync(final Context ctx) {
        StartResult res = new StartResult();
        if (!apiLevelOk()) {
            res.detail = "Mic Mode needs Android 6.0+";
            return res;
        }
        if (!isShizukuInstalled(ctx)) {
            res.detail = "Shizuku app is not installed. Install Shizuku from Play Store.";
            return res;
        }
        if (!isShizukuRunning()) {
            res.detail = "Shizuku is not connected — open Shizuku, tap Start, then try again.";
            return res;
        }
        if (!hasPermission()) {
            // Ask Shizuku for permission; the caller listens for the
            // result and calls startInjectorSync() again once granted.
            requestPermission();
            res.detail = "Shizuku permission requested — allow it, then start Inject again.";
            return res;
        }
        boolean ok = false;
        try {
            ok = startInjectorAll(ctx);
        } catch (Throwable ignored) {}
        if (ok) {
            // The injector may be "ready" while its AudioPolicy
            // NEVER registered ("policy=false") — then the AudioTrack
            // plays through the SPEAKER instead of the mic, so the
            // other player hears nothing. A ready injector with
            // policy=false is NOT a success: stop it and retry via
            // ROOT (uid 0), where registerAudioPolicy may be allowed.
            // At most ONE root retry per start; if root ALSO yields
            // policy=false, fail with the policy reason from the log.
            try {
                java.io.File injDir = injectorDir(ctx);
                java.io.File injLog = injDir == null ? null
                        : new java.io.File(injDir, INJECT_LOG_NAME);
                boolean policyFalse = injLog != null
                        && anyInjectorLogHasLine(injLog, "policy=false");
                if (policyFalse && !"root".equals(lastStartPath)) {
                    try { sendInjectorCommand(ctx, "quit"); } catch (Throwable ignored) {}
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    try {
                        newProcessReflective(
                                new String[]{"sh", "-c",
                                        "pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'"},
                                null, null);
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    boolean rootOk = false;
                    try { rootOk = startInjectorRoot(ctx); } catch (Throwable ignored) {}
                    if (rootOk) {
                        boolean stillFalse = injLog != null
                                && anyInjectorLogHasLine(injLog, "policy=false");
                        if (!stillFalse) {
                            ok = true;
                        } else {
                            ok = false;
                            setPolicyFailReason(ctx);
                        }
                    } else {
                        ok = false;
                        setPolicyFailReason(ctx);
                    }
                } else if (policyFalse) {
                    ok = false;
                    setPolicyFailReason(ctx);
                }
            } catch (Throwable ignored) {}
        }
        if (ok) {
            injectorConnected = true;
            micModeOn = true;
            res.ok = true;
            res.policy = injectorLogPolicyOk(ctx);
            res.detail = "Injector ON — sound now goes to the mic.";
            return res;
        }
        injectorConnected = false;
        // SHORT failure message: the reason (lastFailShort) + the
        // injector's own last line ONLY, under ~120 chars.
        String reason = lastFailShort;
        if (reason == null || reason.length() == 0) {
            reason = "Check whether Shizuku is running.";
        }
        String last = getInjectorMessageLine(ctx);
        String detail = reason;
        if (last.length() > 0) {
            if (last.length() > 60) last = last.substring(0, 60);
            detail = reason + " | " + last;
        }
        if (detail.length() > 120) detail = detail.substring(0, 120);
        res.ok = false;
        res.policy = false;
        res.detail = "Injector did not start — " + detail;
        return res;
    }

    /**
     * Shizuku API 13 made Shizuku.newProcess() private — invoke it via
     * reflection. Same arguments as the old public call (cmd, env, dir).
     * Throws on failure; the caller catches it and reports the failure.
     */
    private static ShizukuRemoteProcess newProcessReflective(
            String[] cmd, String[] env, String dir) throws Exception {
        java.lang.reflect.Method m = Shizuku.class.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        Object o = m.invoke(null, cmd, env, dir);
        return (ShizukuRemoteProcess) o;
    }

    /**
     * Run one SHORT shell command via Shizuku (`sh -c <cmd>`) and
     * capture its combined stdout+stderr. Returns the output
     * (possibly "") when the shell ran, or null when the shell itself
     * could not be started. The command must exit on its own within
     * timeoutMs. Runs on the caller's (background) thread; never
     * throws. env=NULL: the env assignments live ONLY inline in the
     * command string.
     */
    private static String runShellCapture(Context ctx, String shellCmd, int timeoutMs) {
        try {
            final ShizukuRemoteProcess p = newProcessReflective(
                    new String[]{"sh", "-c", shellCmd},
                    null, null);
            if (p == null) return null;
            final StringBuffer out = new StringBuffer();
            Thread tOut = new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.BufferedReader r = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            synchronized (out) { out.append(line).append("\n"); }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            Thread tErr = new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.BufferedReader r = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getErrorStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            synchronized (out) { out.append(line).append("\n"); }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            tOut.start();
            tErr.start();
            final boolean[] done = new boolean[1];
            Thread w = new Thread(new Runnable() {
                public void run() {
                    try { p.waitFor(); } catch (Throwable ignored) {}
                    done[0] = true;
                }
            });
            w.start();
            try { w.join(timeoutMs); } catch (InterruptedException ignored) {}
            if (!done[0]) {
                try { p.destroy(); } catch (Throwable ignored) {}
            }
            try { tOut.join(500); } catch (InterruptedException ignored) {}
            try { tErr.join(500); } catch (InterruptedException ignored) {}
            String s;
            synchronized (out) { s = out.toString(); }
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Launch the injector via `sh -c <shellCmd>` where the command
     * backgrounds itself with nohup + `&`. Returns true when the shell
     * was launched; false when newProcess threw or returned null.
     * Does NOT wait for the shell (it exits at once) and does NOT use
     * exitValue() — the inject.log "Injector ready" line is the
     * liveness signal. The shell's stdout+stderr are drained
     * (Java-side) into the shell log file. Never throws.
     */
    private static boolean launchShellInjector(Context ctx, String shellCmd,
            java.io.File shellLog) {
        try {
            // env=NULL: CLASSPATH + the runtime env are ONLY inline
            // in the command string, never in the env array.
            final ShizukuRemoteProcess p = newProcessReflective(
                    new String[]{"sh", "-c", shellCmd},
                    null, null);
            if (p == null) return false;
            drainStreamToFile(p.getInputStream(), shellLog);
            drainStreamToFile(p.getErrorStream(), shellLog);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "launchShellInjector failed", t);
            return false;
        }
    }

    /**
     * The injector start chain. PRIMARY: shell + nohup via Shizuku
     * with the exact command and env=NULL. FALLBACK: ROOT — `su -c`
     * with the SAME command string. Returns true as soon as one
     * path's inject.log shows "Injector ready". Never throws.
     */
    private static boolean startInjectorAll(Context ctx) {
        try {
            if (startInjectorProcess(ctx)) return true;
        } catch (Throwable ignored) {}
        try {
            if (startInjectorRoot(ctx)) return true;
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    /**
     * Start the Injector via the SHIZUKU SHELL with `nohup
     * app_process ...`, exactly the way the proven native app does
     * it. BEFORE starting, delete `<dir>/cmd` and `<dir>/inject.log`
     * and run `pkill -f <Injector class>` (kills any stale
     * injector); then ONE command string:
     * `CLASSPATH=<apk> nohup app_process /system/bin
     * <Injector> <dir> >/dev/null 2>&1 &`
     * Output goes to /dev/null — NOT to a file (a `tee`/redirect
     * to /sdcard kills the launch when the shell cannot write
     * there). The injector logs to its OWN file
     * (<filesDir>/inject.log), and the inject.log "Injector ready"
     * line is the ONLY liveness signal. The `sh` process exits
     * immediately (code 0) after launching nohup — that is
     * EXPECTED. Never throws.
     */
    private static boolean startInjectorProcess(Context ctx) {
        // PRE-CHECK: an injector from an earlier attempt may
        // already be listening. Reuse it instead of starting a
        // second one. The check is FILE-based: "ping" -> the
        // injector appends "pong" to inject.log (a nonce guards
        // against a stale pong from an earlier run).
        try {
            if (pingInjector(ctx)) {
                lastStartPath = "shizuku";
                return true;
            }
        } catch (Throwable ignored) {}
        // A stale reason must never be shown for a new attempt.
        lastFailShort = "";
        java.io.File shellLog = new java.io.File(ctx.getFilesDir(), SHELL_LOG_NAME);
        java.io.File logFile = new java.io.File(ctx.getFilesDir(), DIRECT_LOG_NAME);
        try {
            if (shellLog.exists()) shellLog.delete();
            if (logFile.exists()) logFile.delete();
        } catch (Throwable ignored) {}
        try {
            java.io.File injDir = injectorDir(ctx);
            String filesDir = injDir != null ? injDir.getAbsolutePath()
                    : ctx.getFilesDir().getAbsolutePath();
            // PRE-FLIGHT CHECK: do nohup / app_process even exist?
            // The output goes to the LOG FILE ONLY — never into the
            // failure message.
            String pre = runShellCapture(ctx,
                    "command -v nohup app_process; ls -l /system/bin/app_process", 5000);
            if (pre == null) {
                lastFailShort = "shizuku: shell start failed";
                appendInjectorLog(logFile,
                        "shizuku: shell start failed — trying root next");
                return false;
            }
            appendInjectorLog(shellLog, "shell: " + pre);
            boolean hasNohup = pre.contains("/nohup");
            boolean appProcessMissing = pre.contains("No such file");
            if (appProcessMissing) {
                lastFailShort = "shizuku: app_process not found";
                appendInjectorLog(shellLog, "shizuku: app_process not found");
                return false;
            }
            // PRE-CLEAN: kill any stale injector and delete the
            // stale cmd + inject.log files, so a dead injector can
            // never hold the cmd file and the log is fresh.
            try {
                runShellCapture(ctx,
                        "pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'; rm -f '"
                        + filesDir + "/cmd' '" + filesDir + "/inject.log' "
                        + TMP_LOG_PATH, 5000);
            } catch (Throwable ignored) {}
            // START VIA SHELL + nohup. When `nohup` is missing,
            // app_process runs without it (backgrounded with & so
            // the shell still exits).
            String apk = "";
            try { apk = ctx.getApplicationInfo().sourceDir; } catch (Throwable ignored) {}
            String cpPrefix = "CLASSPATH='" + apk + "' ";
            String shellCmd;
            if (hasNohup) {
                shellCmd = RUNTIME_ENV_INLINE + cpPrefix + "nohup app_process /system/bin "
                        + INJECTOR_CLASS + " '" + filesDir + "' >/dev/null 2>&1 &";
            } else {
                shellCmd = RUNTIME_ENV_INLINE + cpPrefix + "app_process /system/bin "
                        + INJECTOR_CLASS + " '" + filesDir + "' >/dev/null 2>&1 &";
            }
            boolean launched = launchShellInjector(ctx, shellCmd, shellLog);
            if (!launched) {
                lastFailShort = "shizuku: shell start failed";
                appendInjectorLog(logFile,
                        "shizuku: shell start failed — trying root next");
                return false;
            }
            // RETRY LOOP: poll the injector's OWN file log for
            // "Injector ready" every 400 ms for up to 10 s. Do NOT
            // use exitValue() — the `sh` process exits immediately
            // BY DESIGN. 10 s, not 60 s: the injector turns ON
            // within ~1–2 s when Shizuku is running + permitted.
            java.io.File injectLog = new java.io.File(filesDir, INJECT_LOG_NAME);
            for (int i = 0; i < 25; i++) {
                if (anyInjectorLogHasLine(injectLog, "Injector ready")) {
                    lastStartPath = "shizuku";
                    return true;
                }
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            }
            boolean noStartup = !logHasLine(shellLog, "Injector main started")
                    && !anyInjectorLogHasLine(injectLog, "Injector main started");
            String timeoutLine;
            if (noStartup) {
                timeoutLine = "shizuku: injector did not start after 10s — no startup output";
                lastFailShort = "shizuku: injector did not start after 10s — no startup output";
            } else {
                timeoutLine = "shizuku: injector did not become ready after 10s";
                lastFailShort = "shizuku: injector did not become ready after 10s";
            }
            appendInjectorLog(shellLog, timeoutLine);
            return false;
        } catch (Throwable t) {
            try {
                lastFailShort = "shizuku: start failed";
                String msg = t.getMessage();
                String line = "startInjectorProcess failed: " + t.getClass().getSimpleName()
                        + (msg != null && msg.length() > 0 ? ": " + msg : "");
                if (line.length() > 200) line = line.substring(0, 200);
                appendInjectorLog(logFile, line);
            } catch (Throwable ignored) {}
            Log.w(TAG, "startInjectorProcess failed", t);
            return false;
        }
    }

    /**
     * ROOT probe: is `su` available AND does it give uid 0? Runs
     * `su -c id` via a LOCAL ProcessBuilder (NOT Shizuku) with a
     * 5 s timeout and checks the output for "uid=0". Returns false
     * when su is missing, when it exits without uid 0, or when it
     * hangs. Uses the thread-join pattern (NOT
     * Process.waitFor(timeout, unit) — that is API 26+ and this
     * app is minSdk 24). Never throws.
     */
    public static boolean isRootAvailable() {
        try {
            final Process p = new ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true).start();
            if (p == null) return false;
            final StringBuffer out = new StringBuffer();
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.BufferedReader r = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            synchronized (out) { out.append(line).append("\n"); }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            t.start();
            final boolean[] done = new boolean[1];
            Thread w = new Thread(new Runnable() {
                public void run() {
                    try { p.waitFor(); } catch (Throwable ignored) {}
                    done[0] = true;
                }
            });
            w.start();
            try { w.join(5000); } catch (InterruptedException ignored) {}
            if (!done[0]) {
                try { p.destroy(); } catch (Throwable ignored) {}
                try { t.join(300); } catch (Throwable ignored) {}
                return false;
            }
            try { t.join(500); } catch (Throwable ignored) {}
            String s;
            synchronized (out) { s = out.toString(); }
            return s != null && s.contains("uid=0");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Run one SHORT command via `su -c <cmd>` (a LOCAL
     * ProcessBuilder, NOT Shizuku) and capture its combined
     * stdout+stderr. Returns the output (possibly "") when su ran,
     * or null when su could not be started. Never throws.
     */
    private static String runRootCapture(String rootCmd, int timeoutMs) {
        try {
            final Process p = new ProcessBuilder("su", "-c", rootCmd)
                    .redirectErrorStream(true).start();
            if (p == null) return null;
            final StringBuffer out = new StringBuffer();
            Thread tOut = new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.BufferedReader r = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            synchronized (out) { out.append(line).append("\n"); }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            tOut.start();
            final boolean[] done = new boolean[1];
            Thread w = new Thread(new Runnable() {
                public void run() {
                    try { p.waitFor(); } catch (Throwable ignored) {}
                    done[0] = true;
                }
            });
            w.start();
            try { w.join(timeoutMs); } catch (InterruptedException ignored) {}
            if (!done[0]) {
                try { p.destroy(); } catch (Throwable ignored) {}
            }
            try { tOut.join(500); } catch (Throwable ignored) {}
            String s;
            synchronized (out) { s = out.toString(); }
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * ROOT FALLBACK: `new ProcessBuilder("su", "-c", cmd)` with
     * redirectErrorStream(true), start(), and the input stream
     * read. The command string is the SAME as the Shizuku path
     * (CLASSPATH + full runtime env inline, nohup app_process,
     * output to /dev/null). Only tried AFTER the Shizuku path
     * failed. If the phone is not rooted (no `su`, or `su -c id`
     * is not uid=0), the root path is SKIPPED and the Shizuku
     * failure reason is KEPT. The inject.log "Injector ready"
     * line is the liveness signal (10 s). Never throws.
     */
    private static boolean startInjectorRoot(Context ctx) {
        try {
            if (!isRootAvailable()) {
                appendInjectorLog(new java.io.File(ctx.getFilesDir(), DIRECT_LOG_NAME),
                        "root: su not available — skipped root fallback");
                return false;
            }
            java.io.File shellLog = new java.io.File(ctx.getFilesDir(), SHELL_LOG_NAME);
            java.io.File injDir = injectorDir(ctx);
            String filesDir = injDir != null ? injDir.getAbsolutePath()
                    : ctx.getFilesDir().getAbsolutePath();
            try {
                runRootCapture("pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'; rm -f '"
                        + filesDir + "/cmd' '" + filesDir + "/inject.log' "
                        + TMP_LOG_PATH, 5000);
            } catch (Throwable ignored) {}
            String apk = "";
            try { apk = ctx.getApplicationInfo().sourceDir; } catch (Throwable ignored) {}
            String rootCmd = RUNTIME_ENV_INLINE + "CLASSPATH='" + apk + "' nohup app_process /system/bin "
                    + INJECTOR_CLASS + " '" + filesDir + "' >/dev/null 2>&1 &";
            final Process p;
            try {
                p = new ProcessBuilder("su", "-c", rootCmd).redirectErrorStream(true).start();
            } catch (Throwable t) {
                lastFailShort = "root: start failed";
                appendInjectorLog(shellLog,
                        "root: start failed: " + t.getClass().getSimpleName());
                return false;
            }
            if (p == null) {
                lastFailShort = "root: start failed";
                appendInjectorLog(shellLog, "root: start failed: null process");
                return false;
            }
            drainStreamToFile(p.getInputStream(), shellLog);
            java.io.File injectLog = new java.io.File(filesDir, INJECT_LOG_NAME);
            for (int i = 0; i < 25; i++) {
                if (anyInjectorLogHasLine(injectLog, "Injector ready")) {
                    lastStartPath = "root";
                    return true;
                }
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            }
            try { p.destroy(); } catch (Throwable ignored) {}
            boolean noStartup = !logHasLine(shellLog, "Injector main started")
                    && !anyInjectorLogHasLine(injectLog, "Injector main started");
            if (noStartup) {
                lastFailShort = "root: injector did not start after 10s — no startup output";
            } else {
                lastFailShort = "root: injector did not become ready after 10s";
            }
            appendInjectorLog(shellLog, lastFailShort);
            return false;
        } catch (Throwable t) {
            try {
                lastFailShort = "root: start failed";
                appendInjectorLog(new java.io.File(ctx.getFilesDir(), DIRECT_LOG_NAME),
                        "root: start failed: " + t.getClass().getSimpleName());
            } catch (Throwable ignored) {}
            return false;
        }
    }

    /** Drain a stream AND append every line to a log file (kept to
     * the last ~4 KB). Runs on its own thread, never blocks the
     * process start, and never throws. */
    private static void drainStreamToFile(final java.io.InputStream in,
            final java.io.File logFile) {
        if (in == null || logFile == null) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(in, "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        appendInjectorLog(logFile, line);
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    /** Append one line to the injector log, trimming it to the last
     * ~4 KB. Synchronized. Never throws. */
    private static synchronized void appendInjectorLog(java.io.File logFile, String line) {
        try {
            String old = "";
            try {
                java.io.FileInputStream fin = new java.io.FileInputStream(logFile);
                java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = fin.read(buf)) > 0) bout.write(buf, 0, n);
                fin.close();
                old = new String(bout.toByteArray(), "UTF-8");
            } catch (Exception ignored) {}
            String all = old + line + "\n";
            if (all.length() > 4096) all = all.substring(all.length() - 4096);
            java.io.FileOutputStream fout = new java.io.FileOutputStream(logFile, false);
            fout.write(all.getBytes("UTF-8"));
            fout.close();
        } catch (Throwable ignored) {}
    }

    /** Does EITHER injector log contain the given line? The TMP
     * mirror is checked FIRST — it is the guaranteed record; the
     * working dir's inject.log is the primary. Never throws. */
    private static boolean anyInjectorLogHasLine(java.io.File injectLog, String text) {
        try {
            if (logHasLine(new java.io.File(TMP_LOG_PATH), text)) return true;
        } catch (Throwable ignored) {}
        return logHasLine(injectLog, text);
    }

    /** Does the given log file contain a line with the given text?
     * Never throws. */
    private static boolean logHasLine(java.io.File logFile, String text) {
        try {
            if (logFile == null || !logFile.exists()) return false;
            java.io.FileInputStream in = new java.io.FileInputStream(logFile);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), "UTF-8").contains(text);
        } catch (Throwable t) {
            return false;
        }
    }

    /** ALL injector log sources concatenated with clear separators:
     * the TMP mirror (FIRST), the injector's OWN file log
     * (<dir>/inject.log), the Java-side drain
     * (injector_shell.log), and injector_last.txt. A missing/empty
     * source is noted, not silently skipped. Never throws. */
    public static String getInjectorLogCombined(Context ctx) {
        try {
            if (ctx == null) return "";
            java.io.File injDir = injectorDir(ctx);
            StringBuilder sb = new StringBuilder();
            appendLogSection(sb, "--- /data/local/tmp/asanamul_voice_inject.log (TMP mirror) ---",
                    readLogFile(new java.io.File(TMP_LOG_PATH)));
            appendLogSection(sb, "--- inject.log (injector's own log) ---",
                    readLogFile(injDir == null ? null : new java.io.File(injDir, INJECT_LOG_NAME)));
            appendLogSection(sb, "--- injector_shell.log (drain) ---",
                    readLogFile(new java.io.File(ctx.getFilesDir(), SHELL_LOG_NAME)));
            appendLogSection(sb, "--- injector_last.txt ---",
                    readLogFile(new java.io.File(ctx.getFilesDir(), DIRECT_LOG_NAME)));
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** One section of the combined log: title + content, or a note
     * when the source is missing/empty. Never throws. */
    private static void appendLogSection(StringBuilder sb, String title, String content) {
        try {
            sb.append(title).append("\n");
            if (content == null || content.length() == 0) {
                sb.append("(empty / not found)\n");
            } else {
                sb.append(content);
                if (!content.endsWith("\n")) sb.append("\n");
            }
        } catch (Throwable ignored) {}
    }

    /** The injector's saved log: the TMP mirror FIRST, then
     * <dir>/inject.log, then injector_shell.log, then
     * injector_last.txt. Returns "" when there is none. Never
     * throws. */
    public static String getInjectorLog(Context ctx) {
        try {
            if (ctx == null) return "";
            String log = readLogFile(new java.io.File(TMP_LOG_PATH));
            if (log == null || log.length() == 0) {
                java.io.File injDir = injectorDir(ctx);
                log = injDir == null ? "" : readLogFile(new java.io.File(injDir, INJECT_LOG_NAME));
            }
            if (log == null || log.length() == 0) {
                log = readLogFile(new java.io.File(ctx.getFilesDir(), SHELL_LOG_NAME));
            }
            if (log == null || log.length() == 0) {
                log = readLogFile(new java.io.File(ctx.getFilesDir(), DIRECT_LOG_NAME));
            }
            return log == null ? "" : log;
        } catch (Throwable t) {
            return "";
        }
    }

    /** The last line of the injector log that is the INJECTOR's own
     * output (not a shell diagnostic, not the timeout/exit reason
     * line). Used in the SHORT failure message only. Returns ""
     * when there is no such line. Never throws. */
    public static String getInjectorMessageLine(Context ctx) {
        try {
            String log = getInjectorLog(ctx);
            if (log == null || log.length() == 0) return "";
            String[] lines = log.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].trim();
                if (l.length() == 0) continue;
                if (isPreflightLine(l)) continue;
                return l;
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** The "setupAudioPolicy failed: ..." line from the injector
     * log (TMP mirror first), first 120 chars, or "". Used in the
     * failure message so the user sees WHY the mic policy did not
     * register instead of a generic "did not start". Never throws. */
    private static String getPolicyFailureLine(Context ctx) {
        try {
            String log = getInjectorLog(ctx);
            if (log == null || log.length() == 0) return "";
            String[] lines = log.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.contains("setupAudioPolicy failed")) {
                    if (l.length() > 120) l = l.substring(0, 120);
                    return l;
                }
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** Set lastFailShort to the policy failure reason from the
     * injector log when one exists. Leaves lastFailShort untouched
     * when the log has no policy line. Never throws. */
    private static void setPolicyFailReason(Context ctx) {
        try {
            String pl = getPolicyFailureLine(ctx);
            if (pl != null && pl.length() > 0) {
                lastFailShort = "mic policy failed: " + pl;
            }
        } catch (Throwable ignored) {}
    }

    /** Did the injector's log record a registered mic policy
     * ("policy=true")? Ground truth for the readiness check.
     * Never throws. */
    public static boolean injectorLogPolicyOk(Context ctx) {
        try {
            java.io.File injDir = injectorDir(ctx);
            java.io.File injLog = injDir == null ? null
                    : new java.io.File(injDir, INJECT_LOG_NAME);
            return injLog != null && anyInjectorLogHasLine(injLog, "policy=true");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Is this line pre-flight / diagnostic output rather than the
     * injector's own output? Skips: the "shell: " block, bare
     * paths, `ls: ` errors, "total " lines, "No such file",
     * timeout/exit/fallback reason lines, and `ls -l` OUTPUT
     * lines (a permission string in the first 10 chars). Never
     * throws. */
    private static boolean isPreflightLine(String l) {
        try {
            if (l.startsWith("shell: ")) return true;
            if (l.startsWith("shizuku: ")) return true;
            if (l.startsWith("root: ")) return true;
            if (l.contains("su not available")) return true;
            if (l.startsWith("/")) return true;
            if (l.startsWith("ls: ")) return true;
            if (l.startsWith("total ")) return true;
            if (l.contains("No such file")) return true;
            if (l.contains("did not become ready after")) return true;
            if (l.contains("did not start after")) return true;
            if (l.contains("injector process exited")) return true;
            if (l.contains("falling back to direct exec")) return true;
            if (l.contains("shell start failed")) return true;
            if (l.contains("app_process not found")) return true;
            if (l.length() >= 10) {
                char c0 = l.charAt(0);
                if ("ldcbps-".indexOf(c0) >= 0) {
                    boolean perm = true;
                    for (int i = 1; i < 10; i++) {
                        if ("rwxst-".indexOf(l.charAt(i)) < 0) { perm = false; break; }
                    }
                    if (perm) return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Read one whole log file as UTF-8, or "" when missing/unreadable. Never throws. */
    private static String readLogFile(java.io.File f) {
        try {
            if (f == null || !f.exists()) return "";
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    /** The last non-empty line of the injector log, or "". Never throws. */
    public static String getInjectorLogLastLine(Context ctx) {
        try {
            String log = getInjectorLog(ctx);
            if (log == null || log.length() == 0) return "";
            String[] lines = log.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].trim();
                if (l.length() > 0) return l;
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** The first non-empty line of the injector log, or "". Never throws. */
    public static String getInjectorLogFirstLine(Context ctx) {
        try {
            String log = getInjectorLog(ctx);
            if (log == null || log.length() == 0) return "";
            String[] lines = log.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.length() > 0) return l;
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** FILE-based command: write the command line to <dir>/cmd.tmp,
     * then ATOMICALLY rename it to <dir>/cmd. The injector's
     * cmdLoop polls cmd every 100 ms, reads the first line, DELETES
     * the file, and executes it. Returns true when the cmd file was
     * written. Never throws. */
    public static boolean sendInjectorCommand(Context ctx, String cmd) {
        try {
            if (ctx == null || cmd == null) return false;
            java.io.File dir = injectorDir(ctx);
            if (dir == null) return false;
            java.io.File tmp = new java.io.File(dir, "cmd.tmp");
            java.io.File target = new java.io.File(dir, "cmd");
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp, false);
            out.write((cmd + "\n").getBytes("UTF-8"));
            out.flush();
            out.close();
            if (!tmp.renameTo(target)) {
                try { target.delete(); } catch (Throwable ignored) {}
                if (!tmp.renameTo(target)) {
                    java.io.FileOutputStream o2 = new java.io.FileOutputStream(target, false);
                    o2.write((cmd + "\n").getBytes("UTF-8"));
                    o2.flush();
                    o2.close();
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** FILE-based "ping": write "ping:<nonce>" to <dir>/cmd and wait
     * up to 3 s for "pong:<nonce>" to appear in <dir>/inject.log.
     * The nonce guards against a stale pong from an earlier run.
     * Returns true when the injector answers. Never throws. */
    public static boolean pingInjector(Context ctx) {
        try {
            if (ctx == null) return false;
            java.io.File dir = injectorDir(ctx);
            if (dir == null) return false;
            String nonce = String.valueOf(System.currentTimeMillis());
            if (!sendInjectorCommand(ctx, "ping:" + nonce)) return false;
            String want = "pong:" + nonce;
            java.io.File injectLog = new java.io.File(dir, INJECT_LOG_NAME);
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end) {
                if (anyInjectorLogHasLine(injectLog, want)) return true;
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Stop the injector process: "quit" via the cmd file, then
     * pkill any detached injector by class name (the injector is
     * detached — nohup + & — so there is no process handle to
     * destroy). Never throws. */
    public static void stopInjector(Context ctx) {
        micModeOn = false;
        injectorConnected = false;
        try {
            if (ctx != null) sendInjectorCommand(ctx, "quit");
        } catch (Exception ignored) {}
        new Thread(new Runnable() {
            public void run() {
                try {
                    newProcessReflective(
                            new String[]{"sh", "-c",
                                    "pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'"},
                            null, null);
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    /** MANUAL injector test: runs the injector SYNCHRONOUSLY in the
     * foreground for up to 10 s per path (Shizuku shell, then ROOT)
     * and returns the captured output + whether it answered.
     * DIAGNOSTIC ONLY — it does not change the auto-launch flow.
     * Must be called on a BACKGROUND thread (it blocks up to ~20 s);
     * never throws. */
    public static String runInjectorTestSync(Context ctx) {
        try {
            if (!apiLevelOk()) return "Android 6.0+ needed";
            if (!isShizukuInstalled(ctx)) return "Shizuku is not installed";
            if (!isShizukuRunning()) return "Shizuku is not running — tap SHIZUKU SETUP";
            if (!hasPermission()) return "Shizuku permission is not granted";
            try {
                if (pingInjector(ctx)) {
                    return "Injector is already running — it answers. No test needed.";
                }
            } catch (Throwable ignored) {}
            String apk = "";
            String filesDir = "";
            try { apk = ctx.getApplicationInfo().sourceDir; } catch (Throwable ignored) {}
            try {
                java.io.File d = injectorDir(ctx);
                filesDir = d != null ? d.getAbsolutePath() : ctx.getFilesDir().getAbsolutePath();
            } catch (Throwable ignored) {}
            TestOutcome sh = testInjectorPath(ctx, apk, filesDir, false);
            if (sh.answered) {
                return "shizuku: answering\n" + sh.text;
            }
            TestOutcome rt = testInjectorPath(ctx, apk, filesDir, true);
            if (rt.answered) {
                return "root: answering\n" + rt.text;
            }
            return "shizuku: no answer\n" + sh.text
                    + "\n\nroot: no answer\n" + rt.text;
        } catch (Throwable t) {
            return "Test failed: " + t.getClass().getSimpleName();
        }
    }

    /** One injector-test path result: whether the injector
     * answered (file ping or "Injector ready" in a log) and the
     * captured output text. */
    private static class TestOutcome {
        boolean answered;
        String text;
    }

    /** Run the Injector Test through ONE path and report whether
     * it answered. useRoot=false: Shizuku shell (`sh -c`,
     * env=NULL). useRoot=true: ROOT (`su -c`) — skipped fast
     * when su is not available. The test runs the injector
     * SYNCHRONOUSLY in the foreground for up to 10 s (no nohup,
     * no `&`, so the test can kill it after the window). The
     * injector's own output is read back from its file logs (TMP
     * mirror FIRST, then <dir>/inject.log). Each path does its OWN
     * pre-clean, so a stale injector/log from the other path can
     * never fake the result. Never throws. */
    private static TestOutcome testInjectorPath(final Context ctx, final String apk,
            final String filesDir, final boolean useRoot) {
        TestOutcome o = new TestOutcome();
        o.answered = false;
        o.text = "";
        try {
            String preCmd = "pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'; rm -f '"
                    + filesDir + "/cmd' '" + filesDir + "/inject.log' " + TMP_LOG_PATH;
            try {
                new java.io.File(filesDir, INJECT_LOG_NAME).delete();
            } catch (Throwable ignored) {}
            try {
                new java.io.File(TMP_LOG_PATH).delete();
            } catch (Throwable ignored) {}
            try {
                if (useRoot) {
                    runRootCapture(preCmd, 5000);
                } else {
                    runShellCapture(ctx, preCmd, 5000);
                }
            } catch (Throwable ignored) {}
            String shellCmd = RUNTIME_ENV_INLINE + "CLASSPATH='" + apk + "' app_process /system/bin "
                    + INJECTOR_CLASS + " '" + filesDir + "' >/dev/null 2>&1";
            final java.lang.Process p;
            try {
                if (useRoot) {
                    p = new ProcessBuilder("su", "-c", shellCmd)
                            .redirectErrorStream(true).start();
                } else {
                    p = newProcessReflective(new String[]{"sh", "-c", shellCmd},
                            null, null);
                }
            } catch (Throwable t) {
                o.text = useRoot ? "su did not start: " + t.getClass().getSimpleName()
                        : "Test did not start: " + t.getClass().getSimpleName();
                return o;
            }
            if (p == null) {
                o.text = "Test did not start: null process";
                return o;
            }
            final StringBuffer out = new StringBuffer();
            Thread tOut = new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.BufferedReader r = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            synchronized (out) { out.append(line).append("\n"); }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            Thread tErr = null;
            if (!useRoot) {
                tErr = new Thread(new Runnable() {
                    public void run() {
                        try {
                            java.io.BufferedReader r = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(p.getErrorStream(), "UTF-8"));
                            String line;
                            while ((line = r.readLine()) != null) {
                                synchronized (out) { out.append(line).append("\n"); }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
            tOut.start();
            if (tErr != null) tErr.start();
            boolean pingOk = false;
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            try { pingOk = pingInjector(ctx); } catch (Throwable ignored) {}
            if (!pingOk) {
                try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                try { pingOk = pingInjector(ctx); } catch (Throwable ignored) {}
            }
            if (!pingOk) {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                try { pingOk = pingInjector(ctx); } catch (Throwable ignored) {}
            }
            boolean readyOk = anyInjectorLogHasLine(new java.io.File(filesDir, INJECT_LOG_NAME), "Injector ready");
            boolean policyOk = anyInjectorLogHasLine(new java.io.File(filesDir, INJECT_LOG_NAME), "policy=true");
            try { sendInjectorCommand(ctx, "quit"); } catch (Throwable ignored) {}
            try { p.destroy(); } catch (Throwable ignored) {}
            final boolean rootCleanup = useRoot;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        if (rootCleanup) {
                            runRootCapture(
                                    "pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'",
                                    5000);
                        } else {
                            newProcessReflective(
                                    new String[]{"sh", "-c",
                                            "pkill -f 'space.manus.asanamulvoice.t270965320760441.Injeco[r]'"},
                                    null, null);
                        }
                    } catch (Throwable ignored) {}
                }
            }).start();
            try { tOut.join(500); } catch (Throwable ignored) {}
            if (tErr != null) {
                try { tErr.join(500); } catch (Throwable ignored) {}
            }
            String pipeOut;
            synchronized (out) { pipeOut = out.toString(); }
            String fileLogOut = readLogFile(new java.io.File(TMP_LOG_PATH));
            String workLogOut = readLogFile(new java.io.File(filesDir, INJECT_LOG_NAME));
            if (workLogOut != null && workLogOut.trim().length() > 0) {
                if (fileLogOut != null && fileLogOut.trim().length() > 0) {
                    fileLogOut = fileLogOut + "\n" + workLogOut;
                } else {
                    fileLogOut = workLogOut;
                }
            }
            String shown = "";
            if (pipeOut != null && pipeOut.trim().length() > 0) shown = pipeOut.trim();
            if (fileLogOut != null && fileLogOut.trim().length() > 0) {
                if (shown.length() > 0) shown += "\n";
                shown += fileLogOut.trim();
            }
            StringBuilder sb = new StringBuilder();
            if (shown.length() == 0) {
                sb.append("No output came back (10s)");
            } else {
                sb.append(shown);
                if (sb.length() > 1500) sb.setLength(1500);
            }
            sb.append("\n\nping: ").append(pingOk ? "answered" : "no answer");
            sb.append("  |  ready: ").append(readyOk ? "yes" : "no");
            sb.append("  |  policy: ").append(policyOk ? "yes" : "no");
            o.answered = pingOk || readyOk;
            o.text = sb.toString();
            return o;
        } catch (Throwable t) {
            o.answered = false;
            o.text = "Test failed: " + t.getClass().getSimpleName();
            return o;
        }
    }
}
