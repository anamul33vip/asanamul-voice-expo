import { useCallback, useEffect, useState } from "react";
import * as SecureStore from "expo-secure-store";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { ScreenContainer } from "@/components/screen-container";
import { NeonButton, NeonRail, Panel, SectionTitle, palette } from "@/components/neon-ui";
import { DEFAULT_TARGET_APP, DEFAULT_TELEGRAM_URL, DEFAULT_YOUTUBE_URL, loadSettings, saveSettings } from "@/lib/settings";
import { loadLocalVoices, removeLocalVoice, setVoiceHidden, type LocalVoice } from "@/lib/voices";

/**
 * Local PIN admin — no server, no OAuth, no secrets in the app.
 * The owner sets a PIN on first launch (stored in the device's
 * encrypted SecureStore); later launches require it, and changing
 * it needs the current PIN. Admin manages local voices (hide /
 * delete), the social links, the target app and the Home notice.
 */
const PIN_KEY = "asanamul.adminPin.v1";

export default function AdminScreen() {
  const [checking, setChecking] = useState(true);
  const [unlocked, setUnlocked] = useState(false);
  const [mode, setMode] = useState<"unlock" | "setup" | "change">("unlock");
  const [pinInput, setPinInput] = useState("");
  const [pinConfirm, setPinConfirm] = useState("");
  const [currentPin, setCurrentPin] = useState("");
  const [message, setMessage] = useState("Admin actions are protected by a PIN set on this device.");
  const [voices, setVoices] = useState<LocalVoice[]>([]);
  const [telegramUrl, setTelegramUrl] = useState(DEFAULT_TELEGRAM_URL);
  const [youtubeUrl, setYoutubeUrl] = useState(DEFAULT_YOUTUBE_URL);
  const [targetApp, setTargetApp] = useState(DEFAULT_TARGET_APP);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const stored = await SecureStore.getItemAsync(PIN_KEY);
      setMode(stored != null ? "unlock" : "setup");
      setChecking(false);
    })();
  }, []);

  const loadAdminData = useCallback(async () => {
    setVoices(await loadLocalVoices());
    const s = await loadSettings();
    setTelegramUrl(s.telegramUrl);
    setYoutubeUrl(s.youtubeUrl);
    setTargetApp(s.targetApp);
    setNotice(s.notice);
  }, []);

  const unlock = async () => {
    const stored = await SecureStore.getItemAsync(PIN_KEY);
    if (stored != null && pinInput === stored) {
      setUnlocked(true);
      setPinInput("");
      setMessage("Admin unlocked.");
      await loadAdminData();
    } else {
      setMessage("Wrong PIN — try again.");
    }
  };

  const setupPin = async () => {
    if (!/^\d{4,8}$/.test(pinInput)) {
      setMessage("PIN must be 4–8 digits.");
      return;
    }
    if (pinInput !== pinConfirm) {
      setMessage("The two PINs do not match.");
      return;
    }
    await SecureStore.setItemAsync(PIN_KEY, pinInput);
    setUnlocked(true);
    setPinInput("");
    setPinConfirm("");
    setMessage("Admin PIN set. You are unlocked.");
    await loadAdminData();
  };

  const changePin = async () => {
    const stored = await SecureStore.getItemAsync(PIN_KEY);
    if (stored == null || currentPin !== stored) {
      setMessage("Current PIN is wrong.");
      return;
    }
    if (!/^\d{4,8}$/.test(pinInput)) {
      setMessage("New PIN must be 4–8 digits.");
      return;
    }
    if (pinInput !== pinConfirm) {
      setMessage("The two new PINs do not match.");
      return;
    }
    await SecureStore.setItemAsync(PIN_KEY, pinInput);
    setCurrentPin("");
    setPinInput("");
    setPinConfirm("");
    setMode("unlock");
    setMessage("PIN changed.");
  };

  const saveLinks = async () => {
    setBusy(true);
    try {
      await saveSettings({
        telegramUrl: telegramUrl.trim() || DEFAULT_TELEGRAM_URL,
        youtubeUrl: youtubeUrl.trim() || DEFAULT_YOUTUBE_URL,
        targetApp: targetApp.trim() || DEFAULT_TARGET_APP,
        notice: notice.trim(),
      });
      setMessage("Settings saved — Home uses them now.");
    } catch {
      setMessage("Could not save settings.");
    } finally {
      setBusy(false);
    }
  };

  const toggleHide = async (voice: LocalVoice) => {
    setVoices(await setVoiceHidden(voice.id, !voice.hidden));
  };

  const deleteVoice = async (voice: LocalVoice) => {
    setVoices(await removeLocalVoice(voice.id));
    setMessage(`Deleted “${voice.name}”.`);
  };

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]}>
      <View style={styles.root}>
        <NeonRail />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <SectionTitle eyebrow="Operator console" title="Admin panel" detail="Manage local voices and settings without placing server secrets in the app." />
          {checking ? <Panel><Text style={styles.muted}>Checking admin PIN…</Text></Panel> : null}
          {!checking && !unlocked && mode === "setup" ? (
            <Panel style={styles.locked}>
              <Text style={styles.lockTitle}>Set admin PIN</Text>
              <Text style={styles.muted}>First launch: choose a 4–8 digit PIN. It is stored only on this device, and it is required every time this screen opens.</Text>
              <TextInput value={pinInput} onChangeText={setPinInput} placeholder="New PIN (4–8 digits)" placeholderTextColor={palette.muted} style={styles.input} keyboardType="numeric" secureTextEntry maxLength={8} />
              <TextInput value={pinConfirm} onChangeText={setPinConfirm} placeholder="Repeat PIN" placeholderTextColor={palette.muted} style={styles.input} keyboardType="numeric" secureTextEntry maxLength={8} />
              <NeonButton label="SET PIN" onPress={() => void setupPin()} tone="pink" />
              <Text style={styles.formHint}>{message}</Text>
            </Panel>
          ) : null}
          {!checking && !unlocked && mode === "unlock" ? (
            <Panel style={styles.locked}>
              <Text style={styles.lockTitle}>Admin access locked</Text>
              <Text style={styles.muted}>Enter the admin PIN set on this device. This screen never bypasses authentication.</Text>
              <TextInput value={pinInput} onChangeText={setPinInput} placeholder="Admin PIN" placeholderTextColor={palette.muted} style={styles.input} keyboardType="numeric" secureTextEntry maxLength={8} />
              <NeonButton label="UNLOCK" onPress={() => void unlock()} tone="pink" />
              <Text style={styles.formHint}>{message}</Text>
            </Panel>
          ) : null}
          {unlocked ? (
            <>
              <Panel style={styles.formPanel}>
                <Text style={styles.formTitle}>LOCAL VOICES</Text>
                {voices.length === 0 ? <Text style={styles.muted}>No local voices yet. Import one from the Voices tab.</Text> : null}
                {voices.map((voice) => (
                  <View key={voice.id} style={styles.entry}>
                    <View style={styles.entryMeta}>
                      <Text style={styles.entryName}>{voice.name}</Text>
                      <Text style={styles.muted}>{voice.category} · {voice.format.toUpperCase()} · {voice.hidden ? "hidden" : "visible"}</Text>
                    </View>
                    <View style={styles.entryActions}>
                      <Pressable onPress={() => void toggleHide(voice)}>
                        <Text style={styles.actionText}>{voice.hidden ? "SHOW" : "HIDE"}</Text>
                      </Pressable>
                      <Pressable onPress={() => void deleteVoice(voice)}>
                        <Text style={styles.deleteText}>DEL</Text>
                      </Pressable>
                    </View>
                  </View>
                ))}
              </Panel>
              <Panel style={styles.formPanel}>
                <Text style={styles.formTitle}>SOCIAL LINKS &amp; NOTICE</Text>
                <TextInput value={telegramUrl} onChangeText={setTelegramUrl} placeholder="Telegram URL" placeholderTextColor={palette.muted} style={styles.input} autoCapitalize="none" />
                <TextInput value={youtubeUrl} onChangeText={setYoutubeUrl} placeholder="YouTube URL" placeholderTextColor={palette.muted} style={styles.input} autoCapitalize="none" />
                <TextInput value={targetApp} onChangeText={setTargetApp} placeholder="Target app" placeholderTextColor={palette.muted} style={styles.input} />
                <TextInput value={notice} onChangeText={setNotice} placeholder="Notice shown on Home (empty = default text)" placeholderTextColor={palette.muted} style={styles.input} multiline />
                <NeonButton label={busy ? "SAVING…" : "SAVE SETTINGS"} onPress={() => void saveLinks()} disabled={busy} tone="lime" />
                <Text style={styles.formHint}>{message}</Text>
              </Panel>
              {mode === "change" ? (
                <Panel style={styles.formPanel}>
                  <Text style={styles.formTitle}>CHANGE PIN</Text>
                  <TextInput value={currentPin} onChangeText={setCurrentPin} placeholder="Current PIN" placeholderTextColor={palette.muted} style={styles.input} keyboardType="numeric" secureTextEntry maxLength={8} />
                  <TextInput value={pinInput} onChangeText={setPinInput} placeholder="New PIN (4–8 digits)" placeholderTextColor={palette.muted} style={styles.input} keyboardType="numeric" secureTextEntry maxLength={8} />
                  <TextInput value={pinConfirm} onChangeText={setPinConfirm} placeholder="Repeat new PIN" placeholderTextColor={palette.muted} style={styles.input} keyboardType="numeric" secureTextEntry maxLength={8} />
                  <NeonButton label="CHANGE PIN" onPress={() => void changePin()} tone="pink" />
                </Panel>
              ) : (
                <NeonButton label="CHANGE PIN" onPress={() => setMode("change")} tone="violet" />
              )}
            </>
          ) : null}
        </ScrollView>
      </View>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: palette.bg },
  content: { padding: 22, paddingBottom: 42, gap: 16 },
  locked: { borderColor: "#5B314A", gap: 12 },
  lockTitle: { color: palette.pink, fontWeight: "900", fontSize: 18, marginBottom: 8 },
  muted: { color: palette.muted, fontSize: 13, lineHeight: 20 },
  formPanel: { borderColor: "#385B3C", gap: 12 },
  formTitle: { color: palette.lime, fontWeight: "900", fontSize: 11, letterSpacing: 1.3, marginBottom: 2 },
  input: { color: palette.text, backgroundColor: "#0A0F1C", borderColor: palette.border, borderWidth: 1, borderRadius: 12, paddingHorizontal: 13, paddingVertical: 12, fontSize: 14 },
  formHint: { color: palette.muted, fontSize: 12, lineHeight: 18 },
  listTitle: { color: palette.violet, fontSize: 11, fontWeight: "900", letterSpacing: 1.4 },
  entry: { padding: 14, flexDirection: "row", alignItems: "center", gap: 10 },
  entryMeta: { flex: 1 },
  entryName: { color: palette.text, fontSize: 15, fontWeight: "900", marginBottom: 4 },
  entryActions: { flexDirection: "row", gap: 14, alignItems: "center" },
  actionText: { color: palette.cyan, fontSize: 11, fontWeight: "900", letterSpacing: 1 },
  deleteText: { color: palette.pink, fontSize: 11, fontWeight: "900", letterSpacing: 1 },
});
