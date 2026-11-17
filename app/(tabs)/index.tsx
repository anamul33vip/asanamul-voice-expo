import { useCallback, useMemo, useState } from "react";
import { Image, Linking, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect } from "expo-router";
import { ScreenContainer } from "@/components/screen-container";
import { NeonButton, NeonRail, Panel, SectionTitle, StatusPill, palette } from "@/components/neon-ui";
import { checkNativeCapability, getInitialCapability, openShizukuSetup, requestNativeShizukuPermission, startNativeInjector, stopNativeInjector, type NativeCapabilitySnapshot } from "@/lib/native-capability";
import { loadSettings, type LocalSettings } from "@/lib/settings";
import { trpc } from "@/lib/trpc";

export default function HomeScreen() {
  const [capability, setCapability] = useState<NativeCapabilitySnapshot>(getInitialCapability());
  const [message, setMessage] = useState("Run a readiness check before using Injector.");
  const [injectorOn, setInjectorOn] = useState(false);
  const [starting, setStarting] = useState(false);
  const [settings, setSettings] = useState<LocalSettings | null>(null);
  const catalog = trpc.catalog.useQuery();
  const serverSettings = catalog.data?.settings;

  const loadLocalSettings = useCallback(async () => {
    setSettings(await loadSettings());
  }, []);
  useFocusEffect(
    useCallback(() => {
      void loadLocalSettings();
    }, [loadLocalSettings])
  );

  const readinessLabel = useMemo(() => {
    if (capability.injector === "ready") return injectorOn ? "Injector ON" : "Ready to start";
    if (capability.injector === "unsupported") return "Android build required";
    return "Not ready";
  }, [capability.injector, injectorOn]);

  const refresh = async () => {
    setMessage("Checking the real device capability…");
    const next = await checkNativeCapability();
    setCapability(next);
    setMessage(next.detail);
    // Shizuku is running but the app has no permission yet — ask
    // for it now, then re-check. The user answers in the Shizuku
    // popup; a timeout just leaves the state as not-ready.
    if (next.shizuku === "not-ready" && next.shizukuRunning === true && next.hasPermission === false) {
      setMessage("Shizuku permission requested — allow it in the popup.");
      const granted = await requestNativeShizukuPermission();
      if (granted) {
        const after = await checkNativeCapability();
        setCapability(after);
        setMessage(after.detail);
      }
    }
  };

  const toggleInjector = async () => {
    if (starting) return;
    if (injectorOn) {
      setMessage("Stopping injector…");
      try {
        await stopNativeInjector();
      } catch {
        // Already stopped — the state below is what matters.
      }
      setInjectorOn(false);
      setMessage("Injector stopped.");
      return;
    }
    if (capability.injector !== "ready") {
      setMessage("Injector was not started: the native Shizuku/Root audio route is not ready.");
      return;
    }
    setStarting(true);
    setMessage("Starting injector…");
    try {
      const result = await startNativeInjector();
      if (result.ok) {
        setInjectorOn(true);
        setMessage(result.detail);
        const next = await checkNativeCapability();
        setCapability(next);
      } else {
        setMessage(result.detail);
      }
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Injector start failed.");
    } finally {
      setStarting(false);
    }
  };

  const openSocial = async (url: string) => {
    try {
      await Linking.openURL(url);
    } catch {
      setMessage("Could not open this link on the current device.");
    }
  };

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]}>
      <View style={styles.root}>
        <NeonRail />
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <View style={styles.header}>
            <Image source={require("../../assets/images/anamul-logo.png")} style={styles.logo} />
            <View style={styles.headerCopy}>
              <Text style={styles.kicker}>AS ANAMUL LAB / 01</Text>
              <Text style={styles.brand}>AS ANAMUL VOICE</Text>
              <Text style={styles.subtitle}>Voice control deck for {settings?.targetApp ?? serverSettings?.targetApp ?? "Free Fire"}</Text>
            </View>
            <StatusPill label={readinessLabel} state={capability.injector} />
          </View>

          <Panel style={styles.heroPanel}>
            <View style={styles.heroTopLine}>
              <Text style={styles.heroEyebrow}>INJECTOR CONTROL</Text>
              <Text style={styles.apiText}>API / {catalog.isLoading ? "SYNC" : catalog.isError || !catalog.data ? "OFFLINE" : "ONLINE"}</Text>
            </View>
            <Text style={styles.heroTitle}>{injectorOn ? "VOICE ROUTE ACTIVE" : "CHECK YOUR ROUTE"}</Text>
            <Text style={styles.heroDetail}>{message}</Text>
            <View style={styles.controlRow}>
              <Pressable onPress={toggleInjector} disabled={starting} style={[styles.powerButton, injectorOn && styles.powerButtonOn]}>
                <View style={[styles.powerCore, injectorOn && styles.powerCoreOn]} />
                <Text style={styles.powerLabel}>{injectorOn ? "STOP" : "INJECT"}</Text>
              </Pressable>
              <View style={styles.routeFacts}>
                <View><Text style={styles.factLabel}>TARGET</Text><Text style={styles.factValue}>{settings?.targetApp ?? serverSettings?.targetApp ?? "Free Fire"}</Text></View>
                <View><Text style={styles.factLabel}>ANDROID</Text><Text style={styles.factValue}>{capability.androidApiTarget}</Text></View>
                <View><Text style={styles.factLabel}>MODE</Text><Text style={styles.factValue}>{capability.platform}</Text></View>
              </View>
            </View>
            <View style={styles.actionRow}>
              <NeonButton label="REFRESH CHECK" onPress={refresh} tone="cyan" style={styles.actionButton} />
              <NeonButton label="SHIZUKU SETUP" onPress={openShizukuSetup} tone="violet" style={styles.actionButton} />
            </View>
          </Panel>

          <SectionTitle eyebrow="Diagnostics" title="Permission matrix" detail="A green state is shown only after a real native check reports readiness." />
          <View style={styles.matrix}>
            <Panel style={styles.matrixCard}><Text style={styles.matrixLabel}>SHIZUKU</Text><StatusPill label={capability.shizuku} state={capability.shizuku} /></Panel>
            <Panel style={styles.matrixCard}><Text style={styles.matrixLabel}>ROOT</Text><StatusPill label={capability.root} state={capability.root} /></Panel>
            <Panel style={styles.matrixCard}><Text style={styles.matrixLabel}>AUDIO ROUTE</Text><StatusPill label={capability.audioRoute} state={capability.audioRoute} /></Panel>
          </View>

          <Panel style={styles.noticePanel}>
            <View style={styles.noticeHeader}><Text style={styles.noticeTitle}>BUILD NOTES</Text><Text style={styles.noticeTag}>TRUTHFUL STATUS</Text></View>
            <Text style={styles.noticeBody}>{settings?.notice ? settings.notice : "Preview can show the control deck and API connection, but it cannot prove Shizuku, Root, AudioPolicy, or Free Fire microphone routing. Install the Android build and verify the route on the physical device."}</Text>
          </Panel>

          <View style={styles.linksRow}>
            <NeonButton label="TELEGRAM" tone="pink" onPress={() => openSocial(settings?.telegramUrl ?? serverSettings?.telegramUrl ?? "https://t.me/crackffpanel")} style={styles.linkButton} />
            <NeonButton label="YOUTUBE" tone="lime" onPress={() => openSocial(settings?.youtubeUrl ?? serverSettings?.youtubeUrl ?? "https://www.youtube.com/@Anamul_Islamic_Echo")} style={styles.linkButton} />
          </View>
        </ScrollView>
      </View>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: palette.bg },
  content: { padding: 22, paddingBottom: 42, gap: 22 },
  header: { flexDirection: "row", flexWrap: "wrap", justifyContent: "space-between", alignItems: "flex-start", gap: 12 },
  headerCopy: { flex: 1, minWidth: 220 },
  logo: { width: 58, height: 58, borderRadius: 29, borderWidth: 1, borderColor: palette.cyan },
  kicker: { color: palette.pink, fontSize: 10, fontWeight: "900", letterSpacing: 1.9, marginBottom: 8 },
  brand: { color: palette.text, fontSize: 26, fontWeight: "900", letterSpacing: -1.1 },
  subtitle: { color: palette.muted, marginTop: 5, fontSize: 13 },
  heroPanel: { borderColor: "#3A4566", shadowColor: palette.violet, shadowOpacity: 0.22, shadowRadius: 22, elevation: 8 },
  heroTopLine: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  heroEyebrow: { color: palette.cyan, fontSize: 11, fontWeight: "900", letterSpacing: 1.6 },
  apiText: { color: palette.lime, fontSize: 10, fontWeight: "800", letterSpacing: 1.2 },
  heroTitle: { color: palette.text, fontSize: 32, fontWeight: "900", letterSpacing: -1.2, marginTop: 22 },
  heroDetail: { color: palette.muted, fontSize: 13, lineHeight: 20, marginTop: 8, minHeight: 40 },
  controlRow: { flexDirection: "row", alignItems: "center", gap: 18, marginTop: 18 },
  powerButton: { width: 118, height: 118, borderRadius: 60, borderWidth: 2, borderColor: palette.pink, alignItems: "center", justifyContent: "center", backgroundColor: "#1A1023", shadowColor: palette.pink, shadowOpacity: 0.55, shadowRadius: 18, elevation: 10 },
  powerButtonOn: { borderColor: palette.lime, backgroundColor: "#122014", shadowColor: palette.lime },
  powerCore: { width: 42, height: 42, borderRadius: 22, backgroundColor: palette.pink, shadowColor: palette.pink, shadowOpacity: 0.85, shadowRadius: 16 },
  powerCoreOn: { backgroundColor: palette.lime, shadowColor: palette.lime },
  powerLabel: { color: palette.text, fontSize: 11, fontWeight: "900", letterSpacing: 1.1, marginTop: 10 },
  routeFacts: { gap: 11, flex: 1 },
  factLabel: { color: palette.muted, fontSize: 9, fontWeight: "800", letterSpacing: 1.2 },
  factValue: { color: palette.text, fontSize: 13, fontWeight: "800", marginTop: 2 },
  actionRow: { flexDirection: "row", gap: 10, marginTop: 22 },
  actionButton: { flex: 1 },
  matrix: { flexDirection: "row", gap: 10 },
  matrixCard: { flex: 1, padding: 12, minHeight: 96, justifyContent: "space-between" },
  matrixLabel: { color: palette.muted, fontSize: 10, fontWeight: "900", letterSpacing: 1.1, marginBottom: 10 },
  noticePanel: { borderColor: "#45335F" },
  noticeHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  noticeTitle: { color: palette.violet, fontSize: 11, fontWeight: "900", letterSpacing: 1.3 },
  noticeTag: { color: palette.amber, fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  noticeBody: { color: palette.muted, fontSize: 13, lineHeight: 20, marginTop: 12 },
  linksRow: { flexDirection: "row", gap: 10 },
  linkButton: { flex: 1 },
});
