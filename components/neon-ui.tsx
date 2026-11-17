import { ReactNode } from "react";
import { Pressable, StyleSheet, Text, View, type PressableProps } from "react-native";

export const palette = {
  bg: "#070A12",
  panel: "#101522",
  panelRaised: "#151C2E",
  text: "#F3F7FF",
  muted: "#8E9AB5",
  cyan: "#28E7FF",
  violet: "#9B6CFF",
  pink: "#FF4FD8",
  lime: "#A7FF4F",
  amber: "#FFC857",
  red: "#FF5C7A",
  border: "#26304A",
};

export function NeonRail() {
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <View style={[styles.rail, { backgroundColor: palette.cyan, top: 18, left: 20 }]} />
      <View style={[styles.rail, { backgroundColor: palette.pink, top: 18, right: 20 }]} />
      <View style={[styles.rail, { backgroundColor: palette.violet, bottom: 18, left: 20 }]} />
      <View style={[styles.rail, { backgroundColor: palette.lime, bottom: 18, right: 20 }]} />
    </View>
  );
}

export function Panel({ children, style }: { children: ReactNode; style?: object }) {
  return <View style={[styles.panel, style]}>{children}</View>;
}

export function SectionTitle({ eyebrow, title, detail }: { eyebrow: string; title: string; detail?: string }) {
  return (
    <View style={styles.sectionTitle}>
      <Text style={styles.eyebrow}>{eyebrow.toUpperCase()}</Text>
      <Text style={styles.title}>{title}</Text>
      {detail ? <Text style={styles.detail}>{detail}</Text> : null}
    </View>
  );
}

export function NeonButton({ label, tone = "cyan", style, ...props }: Omit<PressableProps, "style"> & { label: string; tone?: "cyan" | "pink" | "violet" | "lime"; style?: object }) {
  const color = tone === "pink" ? palette.pink : tone === "violet" ? palette.violet : tone === "lime" ? palette.lime : palette.cyan;
  return (
    <Pressable {...props} style={({ pressed }) => [styles.button, { borderColor: color, opacity: pressed ? 0.72 : props.disabled ? 0.42 : 1 }, style]}>
      <Text style={[styles.buttonText, { color }]}>{label}</Text>
    </Pressable>
  );
}

export function StatusPill({ label, state }: { label: string; state: string }) {
  const color = state === "ready" ? palette.lime : state === "unsupported" ? palette.muted : state === "not-ready" ? palette.amber : palette.cyan;
  return (
    <View style={[styles.pill, { borderColor: `${color}88`, backgroundColor: `${color}12` }]}>
      <View style={[styles.dot, { backgroundColor: color }]} />
      <Text style={[styles.pillText, { color }]}>{label}</Text>
    </View>
  );
}

export function stylesFor(theme = palette) {
  return theme;
}

const styles = StyleSheet.create({
  rail: { position: "absolute", width: 42, height: 2, opacity: 0.85, shadowColor: "#FFFFFF", shadowOpacity: 0.55, shadowRadius: 8 },
  panel: { backgroundColor: palette.panel, borderWidth: 1, borderColor: palette.border, borderRadius: 22, padding: 18 },
  sectionTitle: { gap: 4, marginBottom: 12 },
  eyebrow: { color: palette.cyan, fontSize: 11, fontWeight: "800", letterSpacing: 1.7 },
  title: { color: palette.text, fontSize: 24, fontWeight: "900", letterSpacing: -0.5 },
  detail: { color: palette.muted, fontSize: 13, lineHeight: 19 },
  button: { minHeight: 46, borderWidth: 1, borderRadius: 14, paddingHorizontal: 16, alignItems: "center", justifyContent: "center", backgroundColor: "#11192A" },
  buttonText: { fontWeight: "900", fontSize: 13, letterSpacing: 0.7 },
  pill: { flexDirection: "row", alignItems: "center", gap: 7, borderWidth: 1, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 6, alignSelf: "flex-start" },
  dot: { width: 7, height: 7, borderRadius: 99 },
  pillText: { fontSize: 11, fontWeight: "800", letterSpacing: 0.3 },
});
