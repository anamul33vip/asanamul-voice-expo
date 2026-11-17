import AsyncStorage from "@react-native-async-storage/async-storage";
import { Directory, File, Paths } from "expo-file-system";

/**
 * Local voice library — stored on the device only, no server.
 * Imported files are copied into the app's document directory
 * (voices/) and their metadata is kept in AsyncStorage. The native
 * injector can play any of these files (it decodes audio from
 * audio AND video files); speaker playback uses expo-audio.
 */
export type LocalVoice = {
  id: string;
  name: string;
  uri: string;
  category: string;
  format: string;
  addedAt: number;
  hidden: boolean;
};

const STORE_KEY = "asanamul.localVoices.v1";

function voicesDir(): Directory {
  return new Directory(Paths.document, "voices");
}

function ensureVoicesDir(): void {
  const dir = voicesDir();
  if (!dir.exists) {
    dir.create({ idempotent: true });
  }
}

export async function loadLocalVoices(): Promise<LocalVoice[]> {
  try {
    const raw = await AsyncStorage.getItem(STORE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as LocalVoice[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

async function saveLocalVoices(voices: LocalVoice[]): Promise<void> {
  await AsyncStorage.setItem(STORE_KEY, JSON.stringify(voices));
}

function safeFileName(name: string): string {
  const base = name.replace(/[^\w.()\- ]+/g, "_").trim();
  return base.length > 0 ? base : "voice";
}

function formatFromName(name: string): string {
  const dot = name.lastIndexOf(".");
  if (dot < 0) return "audio";
  return name.slice(dot + 1).toLowerCase() || "audio";
}

/** Copy a picked file into the app's voices/ dir and register it. */
export async function addLocalVoice(pickedUri: string, pickedName: string): Promise<LocalVoice> {
  ensureVoicesDir();
  const dir = voicesDir();
  const existing = await loadLocalVoices();
  let fileName = safeFileName(pickedName);
  let dest = new File(dir, fileName);
  let n = 1;
  while (existing.some((v) => v.uri === dest.uri)) {
    const dot = fileName.lastIndexOf(".");
    const stem = dot > 0 ? fileName.slice(0, dot) : fileName;
    const ext = dot > 0 ? fileName.slice(dot) : "";
    fileName = `${stem} (${n})${ext}`;
    dest = new File(dir, fileName);
    n += 1;
  }
  await new File(pickedUri).copy(dest);
  const voice: LocalVoice = {
    id: `${Date.now()}-${Math.floor(Math.random() * 100000)}`,
    name: pickedName,
    uri: dest.uri,
    category: "Local",
    format: formatFromName(pickedName),
    addedAt: Date.now(),
    hidden: false,
  };
  await saveLocalVoices([voice, ...existing]);
  return voice;
}

/** Remove a voice from the library and delete its file. */
export async function removeLocalVoice(id: string): Promise<LocalVoice[]> {
  const existing = await loadLocalVoices();
  const voice = existing.find((v) => v.id === id);
  const next = existing.filter((v) => v.id !== id);
  await saveLocalVoices(next);
  if (voice) {
    try {
      new File(voice.uri).delete();
    } catch {
      // File already gone — metadata removal is what matters.
    }
  }
  return next;
}

/** Hide a voice from the library (Admin only; reversible). */
export async function setVoiceHidden(id: string, hidden: boolean): Promise<LocalVoice[]> {
  const existing = await loadLocalVoices();
  const next = existing.map((v) => (v.id === id ? { ...v, hidden } : v));
  await saveLocalVoices(next);
  return next;
}
