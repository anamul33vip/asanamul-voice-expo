import AsyncStorage from "@react-native-async-storage/async-storage";

/**
 * Local settings — stored on the device only, no server.
 * The Home screen reads these for its social links, target app and
 * notice; the Admin screen edits them. Defaults match the owner's
 * real channels.
 */
export type LocalSettings = {
  telegramUrl: string;
  youtubeUrl: string;
  targetApp: string;
  notice: string;
};

export const DEFAULT_TELEGRAM_URL = "https://t.me/crackffpanel";
export const DEFAULT_YOUTUBE_URL = "https://www.youtube.com/@Anamul_Islamic_Echo";
export const DEFAULT_TARGET_APP = "Free Fire";

const KEYS = {
  telegramUrl: "asanamul.settings.telegramUrl",
  youtubeUrl: "asanamul.settings.youtubeUrl",
  targetApp: "asanamul.settings.targetApp",
  notice: "asanamul.settings.notice",
} as const;

const DEFAULTS: LocalSettings = {
  telegramUrl: DEFAULT_TELEGRAM_URL,
  youtubeUrl: DEFAULT_YOUTUBE_URL,
  targetApp: DEFAULT_TARGET_APP,
  notice: "",
};

function orDefault(value: string | null, fallback: string): string {
  if (value == null || value.trim().length === 0) return fallback;
  return value;
}

export async function loadSettings(): Promise<LocalSettings> {
  try {
    const pairs = await AsyncStorage.multiGet([
      KEYS.telegramUrl,
      KEYS.youtubeUrl,
      KEYS.targetApp,
      KEYS.notice,
    ]);
    const byKey = new Map(pairs.map(([k, v]) => [k, v] as [string, string | null]));
    return {
      telegramUrl: orDefault(byKey.get(KEYS.telegramUrl) ?? null, DEFAULTS.telegramUrl),
      youtubeUrl: orDefault(byKey.get(KEYS.youtubeUrl) ?? null, DEFAULTS.youtubeUrl),
      targetApp: orDefault(byKey.get(KEYS.targetApp) ?? null, DEFAULTS.targetApp),
      notice: byKey.get(KEYS.notice) ?? "",
    };
  } catch {
    return { ...DEFAULTS };
  }
}

export async function saveSettings(patch: Partial<LocalSettings>): Promise<LocalSettings> {
  const current = await loadSettings();
  const next = { ...current, ...patch };
  const pairs: [string, string][] = [];
  if (patch.telegramUrl !== undefined) pairs.push([KEYS.telegramUrl, patch.telegramUrl]);
  if (patch.youtubeUrl !== undefined) pairs.push([KEYS.youtubeUrl, patch.youtubeUrl]);
  if (patch.targetApp !== undefined) pairs.push([KEYS.targetApp, patch.targetApp]);
  if (patch.notice !== undefined) pairs.push([KEYS.notice, patch.notice]);
  if (pairs.length > 0) await AsyncStorage.multiSet(pairs);
  return next;
}
