import { Linking, NativeModules, Platform } from "react-native";

export type CapabilityState = "checking" | "ready" | "not-ready" | "unsupported" | "failed";

export type NativeCapabilitySnapshot = {
  platform: string;
  androidApiTarget: string;
  shizuku: CapabilityState;
  root: CapabilityState;
  audioRoute: CapabilityState;
  injector: CapabilityState;
  detail: string;
  /** Real device facts from the native module (Android only). */
  hasPermission?: boolean;
  shizukuRunning?: boolean;
  shizukuInstalled?: boolean;
  policyOk?: boolean;
  injectorRunning?: boolean;
};

export type ReadinessResult = {
  shizuku: CapabilityState;
  root: CapabilityState;
  audioRoute: CapabilityState;
  injector: CapabilityState;
  detail: string;
  hasPermission: boolean;
  providerPresent: boolean;
  shizukuInstalled: boolean;
  shizukuRunning: boolean;
  policyOk: boolean;
  injectorRunning: boolean;
};

export type InjectorStartResult = { ok: boolean; policy: boolean; detail: string };
export type InjectorStatus = { running: boolean; policy: boolean; lastLine: string; detail: string };
export type PlayResult = { bytes: number; playState: number };

type VoiceInjectorNative = {
  checkReadiness(): Promise<ReadinessResult>;
  requestPermission(): Promise<{ granted: boolean; timeout?: boolean }>;
  startInjector(): Promise<InjectorStartResult>;
  stopInjector(): Promise<{ ok: boolean }>;
  playFile(path: string): Promise<PlayResult>;
  getStatus(): Promise<InjectorStatus>;
  getInjectorLog(): Promise<{ log: string }>;
  runInjectorTest(): Promise<{ result: string }>;
};

const VoiceInjector = NativeModules.VoiceInjector as VoiceInjectorNative | undefined;

/** True only in an Android build that includes the native module. */
export function isNativeInjectorAvailable(): boolean {
  return Platform.OS === "android" && VoiceInjector != null;
}

export function getInitialCapability(): NativeCapabilitySnapshot {
  if (Platform.OS === "web") {
    return {
      platform: "Web Preview",
      androidApiTarget: "Android 12–15+",
      shizuku: "unsupported",
      root: "unsupported",
      audioRoute: "unsupported",
      injector: "unsupported",
      detail: "Native Shizuku and audio routing are available only in an Android build.",
    };
  }
  return {
    platform: "Android device",
    androidApiTarget: "Android 12–15+",
    shizuku: "not-ready",
    root: "not-ready",
    audioRoute: "not-ready",
    injector: "not-ready",
    detail: "The native bridge must verify Shizuku/Root and AudioPolicy on this device before injection can start.",
  };
}

/** Real readiness from the native module — never a guessed state. */
export async function checkNativeCapability(): Promise<NativeCapabilitySnapshot> {
  if (Platform.OS === "web") {
    return getInitialCapability();
  }
  if (!VoiceInjector) {
    return {
      ...getInitialCapability(),
      detail: "Native injector module is not in this build — rebuild the Android app.",
    };
  }
  try {
    const r = await VoiceInjector.checkReadiness();
    return {
      platform: "Android device",
      androidApiTarget: "Android 12–15+",
      shizuku: r.shizuku,
      root: r.root,
      audioRoute: r.audioRoute,
      injector: r.injector,
      detail: r.detail,
      hasPermission: r.hasPermission,
      shizukuRunning: r.shizukuRunning,
      shizukuInstalled: r.shizukuInstalled,
      policyOk: r.policyOk,
      injectorRunning: r.injectorRunning,
    };
  } catch {
    return {
      ...getInitialCapability(),
      shizuku: "failed",
      injector: "failed",
      audioRoute: "failed",
      detail: "Readiness check failed — see the injector log.",
    };
  }
}

export async function openShizukuSetup() {
  if (Platform.OS === "web") {
    await Linking.openURL("https://shizuku.rikka.app/");
    return;
  }
  try {
    await Linking.openURL("shizuku://");
  } catch {
    await Linking.openURL("https://shizuku.rikka.app/");
  }
}

/** Ask Shizuku for permission. True only when it is granted. */
export async function requestNativeShizukuPermission(): Promise<boolean> {
  if (!VoiceInjector) return false;
  const r = await VoiceInjector.requestPermission();
  return r.granted;
}

/** Start the injector. Resolves with {ok, policy, detail} — ok is
 * true ONLY when the injector is running AND its mic policy
 * registered. Throws when the native module is missing. */
export async function startNativeInjector(): Promise<InjectorStartResult> {
  if (!VoiceInjector) throw new Error("Native injector module is not in this build.");
  return VoiceInjector.startInjector();
}

/** Stop the injector. No-op when the native module is missing. */
export async function stopNativeInjector(): Promise<void> {
  if (!VoiceInjector) return;
  await VoiceInjector.stopInjector();
}

/** Play a file through the injector (into the mic). Resolves with
 * {bytes, playState}; rejects with the log's failure reason. */
export async function playVoiceFile(path: string): Promise<PlayResult> {
  if (!VoiceInjector) throw new Error("Native injector module is not in this build.");
  return VoiceInjector.playFile(path);
}

/** Quick injector status (running / mic policy / last log line). */
export async function getNativeInjectorStatus(): Promise<InjectorStatus> {
  if (!VoiceInjector) throw new Error("Native injector module is not in this build.");
  return VoiceInjector.getStatus();
}

/** Last lines of the injector log (for diagnostics). */
export async function getNativeInjectorLog(): Promise<string> {
  if (!VoiceInjector) throw new Error("Native injector module is not in this build.");
  const r = await VoiceInjector.getInjectorLog();
  return r.log;
}

/** Manual injector diagnostic (Shizuku path, then root path). */
export async function runNativeInjectorTest(): Promise<string> {
  if (!VoiceInjector) throw new Error("Native injector module is not in this build.");
  const r = await VoiceInjector.runInjectorTest();
  return r.result;
}
