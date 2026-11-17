package space.manus.asanamulvoice.t270965320760441;

import android.content.pm.PackageManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import rikka.shizuku.Shizuku;

/**
 * React Native bridge for the AS ANAMUL VOICE mic injector.
 *
 * Legacy ReactContextBaseJavaModule (works under New Architecture
 * interop). Every blocking call runs on a background thread — none
 * of these methods ever blocks the JS thread. Every method reports
 * the REAL device state; nothing is faked. A missing Shizuku, a
 * missing permission, or a policy=false injector is reported as
 * such — never as ready.
 */
public class VoiceInjectorModule extends ReactContextBaseJavaModule {

    private static final java.util.concurrent.ExecutorService BG =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    public VoiceInjectorModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "VoiceInjector";
    }

    /** Real readiness: Shizuku install/run/permission, root probe,
     * injector ping, and the mic policy from the injector log. */
    @ReactMethod
    public void checkReadiness(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    ReactApplicationContext ctx = getReactApplicationContext();
                    WritableMap m = Arguments.createMap();
                    boolean installed = InjectorBridge.isShizukuInstalled(ctx);
                    boolean running = installed && InjectorBridge.isShizukuRunning();
                    boolean perm = running && InjectorBridge.hasPermission();
                    String shizuku;
                    String detail;
                    if (!installed) {
                        shizuku = "unsupported";
                        detail = "Shizuku app is not installed.";
                    } else if (!running) {
                        shizuku = "not-ready";
                        detail = "Shizuku is installed but not running — open Shizuku and tap Start.";
                    } else if (!perm) {
                        shizuku = "not-ready";
                        detail = "Shizuku is running but this app has no permission — grant it in Shizuku.";
                    } else {
                        shizuku = "ready";
                        detail = "";
                    }
                    boolean rootReady = InjectorBridge.isRootAvailable();
                    String root = rootReady ? "ready" : "not-ready";
                    boolean injectorRunning = false;
                    boolean policy = false;
                    String injector;
                    String audioRoute;
                    if ("ready".equals(shizuku)) {
                        injectorRunning = InjectorBridge.pingInjector(ctx);
                        policy = injectorRunning && InjectorBridge.injectorLogPolicyOk(ctx);
                        if (injectorRunning && policy) {
                            injector = "ready";
                            audioRoute = "ready";
                            detail = "Injector ready — sound goes to the mic.";
                        } else if (injectorRunning) {
                            injector = "failed";
                            audioRoute = "failed";
                            String pl = InjectorBridge.getInjectorMessageLine(ctx);
                            detail = pl.length() > 0 ? pl
                                    : "Injector is running but the mic policy did not register.";
                        } else {
                            injector = "not-ready";
                            audioRoute = "not-ready";
                            detail = "Shizuku is connected. Ready to start the injector.";
                        }
                    } else {
                        injector = "not-ready";
                        audioRoute = "not-ready";
                    }
                    m.putString("shizuku", shizuku);
                    m.putString("root", root);
                    m.putString("audioRoute", audioRoute);
                    m.putString("injector", injector);
                    m.putString("detail", detail);
                    m.putBoolean("hasPermission", perm);
                    m.putBoolean("providerPresent", true);
                    m.putBoolean("shizukuInstalled", installed);
                    m.putBoolean("shizukuRunning", running);
                    m.putBoolean("policyOk", policy);
                    m.putBoolean("injectorRunning", injectorRunning);
                    promise.resolve(m);
                } catch (Throwable t) {
                    promise.reject("READINESS_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Ask Shizuku for permission. Resolves when the Shizuku
     * permission result arrives (or after a 30 s timeout with
     * granted=false, timeout=true). */
    @ReactMethod
    public void requestPermission(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    if (InjectorBridge.hasPermission()) {
                        WritableMap m = Arguments.createMap();
                        m.putBoolean("granted", true);
                        promise.resolve(m);
                        return;
                    }
                    final boolean[] done = new boolean[1];
                    final Shizuku.OnRequestPermissionResultListener listener =
                            new Shizuku.OnRequestPermissionResultListener() {
                        @Override
                        public void onRequestPermissionResult(int requestCode, int grantResult) {
                            synchronized (done) {
                                if (done[0]) return;
                                done[0] = true;
                            }
                            try { Shizuku.removeRequestPermissionResultListener(this); } catch (Throwable ignored) {}
                            WritableMap m = Arguments.createMap();
                            m.putBoolean("granted", grantResult == PackageManager.PERMISSION_GRANTED);
                            promise.resolve(m);
                        }
                    };
                    Shizuku.addRequestPermissionResultListener(listener);
                    InjectorBridge.requestPermission();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        public void run() {
                            synchronized (done) {
                                if (done[0]) return;
                                done[0] = true;
                            }
                            try { Shizuku.removeRequestPermissionResultListener(listener); } catch (Throwable ignored) {}
                            WritableMap m = Arguments.createMap();
                            m.putBoolean("granted", false);
                            m.putBoolean("timeout", true);
                            promise.resolve(m);
                        }
                    }, 30000);
                } catch (Throwable t) {
                    promise.reject("PERMISSION_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Start the injector (Shizuku shell, root fallback). Resolves
     * with {ok, policy, detail} — ok is true ONLY when the injector
     * is running AND its mic policy registered. */
    @ReactMethod
    public void startInjector(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    InjectorBridge.StartResult r =
                            InjectorBridge.startInjectorSync(getReactApplicationContext());
                    WritableMap m = Arguments.createMap();
                    m.putBoolean("ok", r.ok);
                    m.putBoolean("policy", r.policy);
                    m.putString("detail", r.detail);
                    promise.resolve(m);
                } catch (Throwable t) {
                    promise.reject("START_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Stop the injector (quit command + pkill). Always resolves. */
    @ReactMethod
    public void stopInjector(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    InjectorBridge.stopInjector(getReactApplicationContext());
                    WritableMap m = Arguments.createMap();
                    m.putBoolean("ok", true);
                    promise.resolve(m);
                } catch (Throwable t) {
                    promise.reject("STOP_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Play a file through the injector (into the mic). Sends
     * "play:<path>" and waits up to 30 s for the injector's
     * "play: wrote <N> bytes" line. Resolves with {bytes,
     * playState}; rejects when the file is missing/unreadable, the
     * injector is not running, or the play times out. */
    @ReactMethod
    public void playFile(final String path, final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    ReactApplicationContext ctx = getReactApplicationContext();
                    if (!InjectorBridge.pingInjector(ctx)) {
                        promise.reject("INJECTOR_NOT_RUNNING",
                                "Injector is not running — start it first.");
                        return;
                    }
                    String before = InjectorBridge.getInjectorLog(ctx);
                    int wroteBaseline = countOccurrences(before, "play: wrote");
                    int missingBaseline = countOccurrences(before, "play: file missing");
                    if (!InjectorBridge.sendInjectorCommand(ctx, "play:" + path)) {
                        promise.reject("CMD_FAILED", "Could not send the play command.");
                        return;
                    }
                    long end = System.currentTimeMillis() + 30000;
                    while (System.currentTimeMillis() < end) {
                        String log = InjectorBridge.getInjectorLog(ctx);
                        if (countOccurrences(log, "play: wrote") > wroteBaseline) {
                            String line = lastLineContaining(log, "play: wrote");
                            WritableMap m = Arguments.createMap();
                            m.putInt("bytes", parseBytes(line));
                            m.putInt("playState", parsePlayState(line));
                            promise.resolve(m);
                            return;
                        }
                        if (countOccurrences(log, "play: file missing") > missingBaseline) {
                            promise.reject("FILE_MISSING",
                                    "Play failed: file missing or unreadable: " + path);
                            return;
                        }
                        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    }
                    promise.reject("PLAY_TIMEOUT",
                            "Play did not finish within 30s — see the injector log.");
                } catch (Throwable t) {
                    promise.reject("PLAY_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Quick status: is the injector answering, did its mic policy
     * register, and what is the log's last line. */
    @ReactMethod
    public void getStatus(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    ReactApplicationContext ctx = getReactApplicationContext();
                    boolean running = InjectorBridge.pingInjector(ctx);
                    boolean policy = running && InjectorBridge.injectorLogPolicyOk(ctx);
                    WritableMap m = Arguments.createMap();
                    m.putBoolean("running", running);
                    m.putBoolean("policy", policy);
                    m.putString("lastLine", InjectorBridge.getInjectorLogLastLine(ctx));
                    m.putString("detail", running
                            ? (policy ? "Injector ready, mic policy registered."
                                    : "Injector is running but the mic policy did not register.")
                            : "Injector is not running.");
                    promise.resolve(m);
                } catch (Throwable t) {
                    promise.reject("STATUS_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Last 4000 chars of the combined injector log. */
    @ReactMethod
    public void getInjectorLog(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    String log = InjectorBridge.getInjectorLogCombined(getReactApplicationContext());
                    if (log.length() > 4000) log = log.substring(log.length() - 4000);
                    WritableMap m = Arguments.createMap();
                    m.putString("log", log);
                    promise.resolve(m);
                } catch (Throwable t) {
                    promise.reject("LOG_ERROR", t.getMessage());
                }
            }
        });
    }

    /** Manual injector diagnostic (Shizuku path, then root path).
     * Resolves with {result} — the captured output text. */
    @ReactMethod
    public void runInjectorTest(final Promise promise) {
        BG.execute(new Runnable() {
            public void run() {
                try {
                    String result = InjectorBridge.runInjectorTestSync(getReactApplicationContext());
                    WritableMap m = Arguments.createMap();
                    m.putString("result", result);
                    promise.resolve(m);
                } catch (Throwable t) {
                    promise.reject("TEST_ERROR", t.getMessage());
                }
            }
        });
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || needle == null) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String lastLineContaining(String log, String needle) {
        if (log == null) return "";
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].contains(needle)) return lines[i];
        }
        return "";
    }

    /** "play: wrote 123456 bytes to track, playState=3" -> 123456. */
    private static int parseBytes(String line) {
        try {
            int i = line.indexOf("wrote ");
            if (i < 0) return 0;
            int j = line.indexOf(" bytes", i + 6);
            if (j < 0) return 0;
            return Integer.parseInt(line.substring(i + 6, j).trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** "play: wrote 123456 bytes to track, playState=3" -> 3. */
    private static int parsePlayState(String line) {
        try {
            int i = line.indexOf("playState=");
            if (i < 0) return -1;
            return Integer.parseInt(line.substring(i + 10).trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
