import { Tabs } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Platform } from "react-native";
import { HapticTab } from "@/components/haptic-tab";
import { IconSymbol } from "@/components/ui/icon-symbol";
import { palette } from "@/components/neon-ui";

export default function TabLayout() {
  const insets = useSafeAreaInsets();
  const bottomPadding = Platform.OS === "web" ? 12 : Math.max(insets.bottom, 8);
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: palette.cyan,
        tabBarInactiveTintColor: palette.muted,
        tabBarButton: HapticTab,
        tabBarStyle: { paddingTop: 8, paddingBottom: bottomPadding, height: 56 + bottomPadding, backgroundColor: palette.bg, borderTopColor: palette.border, borderTopWidth: 1 },
      }}
    >
      <Tabs.Screen name="index" options={{ title: "Control", tabBarIcon: ({ color }) => <IconSymbol size={25} name="house.fill" color={color} /> }} />
      <Tabs.Screen name="voices" options={{ title: "Voices", tabBarIcon: ({ color }) => <IconSymbol size={25} name="paperplane.fill" color={color} /> }} />
      <Tabs.Screen name="admin" options={{ title: "Admin", tabBarIcon: ({ color }) => <IconSymbol size={25} name="chevron.right" color={color} /> }} />
    </Tabs>
  );
}
