import { useCallback, useEffect, useRef, useState } from "react";
import * as DocumentPicker from "expo-document-picker";
import { createAudioPlayer, type AudioPlayer } from "expo-audio";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect } from "expo-router";
import { ScreenContainer } from "@/components/screen-container";
import { NeonButton, NeonRail, Panel, SectionTitle, palette } from "@/components/neon-ui";
import { getNativeInjectorStatus, playVoiceFile } from "@/lib/native-capability";
import { addLocalVoice, loadLocalVoices, removeLocalVoice, type LocalVoice } from "@/lib/voices";
import { trpc } from "@/lib/trpc";

const VIDEO_FORMATS = new Set(["mp4", "mov", "webm", "mkv", "avi", "3gp"]);

export default function VoicesScreen() {
  const [message, setMessage] = useState("Your local voice files stay on this device until you choose to share or upload them.");
  const [localVoices, setLocalVoices] = useState<LocalVoice[]>([]);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const speakerRef = useRef<AudioPlayer | null>(null);
  const catalog = trpc.catalog.useQuery();
  const voices = catalog.data?.voices ?? [];

  const reloadLocal = useCallback(async () => {
    setLocalVoices(await loadLocalVoices());
  }, []);
  useFocusEffect(
    useCallback(() => {
      void reloadLocal();
    }, [reloadLocal])
  );
  useEffect(() => {
    return () => {
      try {
        speakerRef.current?.remove();
      } catch {
        // Player already released.
      }
      speakerRef.current = null;
    };
  }, []);

  const stopSpeaker = () => {
    try {
      speakerRef.current?.pause();
      speakerRef.current?.remove();
    } catch {
      // Player already released.
    }
    speakerRef.current = null;
  };

  const importVoice = async () => {
    const result = await DocumentPicker.getDocumentAsync({
      type: ["audio/*", "video/*"],
      copyToCacheDirectory: true,
      multiple: false,
    });
    if (result.canceled) {
      setMessage("Import cancelled.");
      return;
    }
    const asset = result.assets[0];
    try {
      const voice = await addLocalVoice(asset.uri, asset.name);
      await reloadLocal();
      setMessage(`Imported “${voice.name}” — it is stored on this device and ready to play.`);
    } catch {
      setMessage("Import failed — the file could not be copied to the app.");
    }
  };

  const playVoice = async (voice: LocalVoice) => {
    // Tapping the playing voice again stops speaker playback.
    if (playingId === voice.id) {
      stopSpeaker();
      setPlayingId(null);
      setMessage("Stopped.");
      return;
    }
    stopSpeaker();
    // Mic path first: when the injector is running with a registered
    // mic policy, the sound goes to the other player.
    try {
      const status = await getNativeInjectorStatus();
      if (status.running && status.policy) {
        setPlayingId(voice.id);
        setMessage(`Playing “${voice.name}” to the mic…`);
        try {
          const r = await playVoiceFile(voice.uri);
          setMessage(`Played “${voice.name}” to the mic (${r.bytes} bytes).`);
        } catch (e) {
          setMessage(e instanceof Error ? e.message : "Mic play failed.");
        } finally {
          setPlayingId(null);
        }
        return;
      }
    } catch {
      // Native module unavailable — fall through to speaker.
    }
    // Speaker path (injector off). Video files carry their audio to
    // the mic only — the speaker preview cannot play them here.
    if (VIDEO_FORMATS.has(voice.format)) {
      setMessage("This is a video file — turn INJECT on to send its sound to the mic.");
      return;
    }
    try {
      const player = createAudioPlayer(voice.uri);
      speakerRef.current = player;
      setPlayingId(voice.id);
      player.play();
      setMessage(`Playing “${voice.name}” on the speaker. Tap again to stop.`);
    } catch {
      setPlayingId(null);
      setMessage("Could not play this file on the speaker.");
    }
  };

  const deleteVoice = async (voice: LocalVoice) => {
    if (playingId === voice.id) {
      stopSpeaker();
      setPlayingId(null);
    }
    await removeLocalVoice(voice.id);
    await reloadLocal();
    setMessage(`Deleted “${voice.name}”.`);
  };

  const visibleLocal = localVoices.filter((v) => !v.hidden);

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]}>
      <View style={styles.root}>
        <NeonRail />
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <SectionTitle eyebrow="Media bay" title="Voice library" detail="Import MP3, WAV, M4A or OGG audio, or choose a video for native audio extraction." />
          <Panel style={styles.importPanel}>
            <View style={styles.importHeader}><Text style={styles.importTitle}>LOCAL VOICE INTAKE</Text><Text style={styles.formatText}>MP3 · WAV · M4A · OGG</Text></View>
            <Text style={styles.body}>{message}</Text>
            <View style={styles.buttonRow}>
              <NeonButton label="IMPORT AUDIO / VIDEO" onPress={importVoice} tone="cyan" style={styles.button} />
              <NeonButton label=".ISFILE GUIDE" onPress={() => setMessage("Backup/share packages are kept in the AS ANAMUL VOICE workflow; export behavior will be completed in the native Android build.")} tone="violet" style={styles.button} />
            </View>
          </Panel>

          <View style={styles.sectionRow}><Text style={styles.sectionLabel}>LOCAL VOICES</Text><Text style={styles.count}>{visibleLocal.length} VOICES</Text></View>
          {visibleLocal.length === 0 ? (
            <Panel style={styles.emptyPanel}><Text style={styles.emptyTitle}>No local voices yet</Text><Text style={styles.empty}>Import an audio or video file above. Local voices play to the mic when INJECT is on, or on the speaker when it is off.</Text></Panel>
          ) : null}
          {visibleLocal.map((voice) => (
            <Panel key={voice.id} style={styles.voiceCard}>
              <View style={styles.voiceIcon}><Text style={styles.voiceIconText}>♪</Text></View>
              <View style={styles.voiceMeta}><Text style={styles.voiceName}>{voice.name}</Text><Text style={styles.voiceDetail}>{voice.category} · {voice.format.toUpperCase()}</Text></View>
              <View style={styles.voiceActions}>
                <Pressable onPress={() => void playVoice(voice)}>
                  <Text style={styles.playText}>{playingId === voice.id ? "STOP" : "PLAY"}</Text>
                </Pressable>
                <Pressable onPress={() => void deleteVoice(voice)}>
                  <Text style={styles.deleteText}>DEL</Text>
                </Pressable>
              </View>
            </Panel>
          ))}

          <View style={styles.sectionRow}><Text style={styles.sectionLabel}>SERVER CATALOG</Text><Text style={styles.count}>{voices.length} VOICES</Text></View>
          {catalog.isLoading ? <Panel><Text style={styles.empty}>Syncing catalog…</Text></Panel> : null}
          {!catalog.isLoading && voices.length === 0 ? (
            <Panel style={styles.emptyPanel}><Text style={styles.emptyTitle}>No voices published</Text><Text style={styles.empty}>Admin catalog entries will appear here after an authenticated admin publishes them. Nothing is faked or prefilled.</Text></Panel>
          ) : null}
          {voices.map((voice) => (
            <Panel key={voice.id} style={styles.voiceCard}>
              <View style={styles.voiceIcon}><Text style={styles.voiceIconText}>♪</Text></View>
              <View style={styles.voiceMeta}><Text style={styles.voiceName}>{voice.name}</Text><Text style={styles.voiceDetail}>{voice.category} · {voice.format.toUpperCase()}</Text></View>
              <Text style={styles.voiceState}>SYNCED</Text>
            </Panel>
          ))}
        </ScrollView>
      </View>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: palette.bg },
  content: { padding: 22, paddingBottom: 42, gap: 16 },
  importPanel: { borderColor: "#234E63" },
  importHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 10 },
  importTitle: { color: palette.cyan, fontWeight: "900", fontSize: 11, letterSpacing: 1.2 },
  formatText: { color: palette.muted, fontSize: 10, fontWeight: "800" },
  body: { color: palette.muted, fontSize: 13, lineHeight: 20, marginTop: 14 },
  buttonRow: { flexDirection: "row", gap: 10, marginTop: 18 },
  button: { flex: 1 },
  sectionRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 6 },
  sectionLabel: { color: palette.violet, fontSize: 11, fontWeight: "900", letterSpacing: 1.5 },
  count: { color: palette.muted, fontSize: 10, fontWeight: "800" },
  emptyPanel: { alignItems: "center", paddingVertical: 28 },
  emptyTitle: { color: palette.text, fontWeight: "900", fontSize: 18 },
  empty: { color: palette.muted, fontSize: 13, lineHeight: 20, textAlign: "center" },
  voiceCard: { flexDirection: "row", alignItems: "center", gap: 12, padding: 14 },
  voiceIcon: { width: 46, height: 46, borderRadius: 16, backgroundColor: "#241B3A", borderColor: palette.violet, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  voiceIconText: { color: palette.pink, fontSize: 22 },
  voiceMeta: { flex: 1 },
  voiceName: { color: palette.text, fontSize: 15, fontWeight: "900" },
  voiceDetail: { color: palette.muted, fontSize: 12, marginTop: 4 },
  voiceState: { color: palette.lime, fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  voiceActions: { flexDirection: "row", gap: 14, alignItems: "center" },
  playText: { color: palette.cyan, fontSize: 11, fontWeight: "900", letterSpacing: 1 },
  deleteText: { color: palette.pink, fontSize: 11, fontWeight: "900", letterSpacing: 1 },
});
