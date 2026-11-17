/**
 * AS ANAMUL VOICE — injector config plugin.
 *
 * Wires the native mic-injector bridge into the generated Android
 * project, durably (survives `expo prebuild --clean`):
 *  1. AndroidManifest: Shizuku API_V23 permission, the stock
 *     ShizukuProvider (authorities ${applicationId}.shizuku) and the
 *     moe.shizuku.client.V3_SUPPORT meta-data.
 *  2. app/build.gradle: `implementation fileTree(dir: 'libs',
 *     include: ['*.jar'])` so the bundled Shizuku jars are on the
 *     compile classpath.
 *  3. Copies the Java sources (Injector, InjectorBridge,
 *     VoiceInjectorModule, VoiceInjectorPackage, rikka.sui.Sui)
 *     into android/app/src/main/java/ and the 4 Shizuku jars into
 *     android/app/libs/, and registers VoiceInjectorPackage in
 *     MainApplication.kt.
 *
 * Every edit is idempotent (existence checks) — running prebuild
 * twice applies nothing twice.
 */
const { withAndroidManifest, withAppBuildGradle, withDangerousMod, AndroidConfig } = require("@expo/config-plugins");
const fs = require("fs");
const path = require("path");

const PKG_SEGMENTS = ["space", "manus", "asanamulvoice", "t270965320760441"];
const SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23";
const SHIZUKU_PROVIDER = "rikka.shizuku.ShizukuProvider";

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, entry.name);
    const d = path.join(dest, entry.name);
    if (entry.isDirectory()) copyDir(s, d);
    else fs.copyFileSync(s, d);
  }
}

function withAsanamulInjector(config) {
  config = withAndroidManifest(config, (config) => {
    // modResults is the parsed document: { manifest: { $: {...},
    // "uses-permission": [...], application: [...] } }. Edits must
    // go INSIDE doc.manifest — a stray top-level key makes the XML
    // builder wrap the whole file in a <root> element.
    const doc = config.modResults;
    const manifest = doc.manifest;
    manifest["uses-permission"] = manifest["uses-permission"] || [];
    if (!manifest["uses-permission"].some((p) => p.$ && p.$["android:name"] === SHIZUKU_PERMISSION)) {
      manifest["uses-permission"].push({ $: { "android:name": SHIZUKU_PERMISSION } });
    }
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(doc);
    AndroidConfig.Manifest.addMetaDataItemToMainApplication(app, "moe.shizuku.client.V3_SUPPORT", "true");
    app.provider = app.provider || [];
    if (!app.provider.some((p) => p.$ && p.$["android:name"] === SHIZUKU_PROVIDER)) {
      app.provider.push({
        $: {
          "android:name": SHIZUKU_PROVIDER,
          "android:authorities": "${applicationId}.shizuku",
          "android:exported": "true",
          "android:multiprocess": "false",
        },
      });
    }
    return config;
  });

  config = withAppBuildGradle(config, (config) => {
    const line = "    implementation fileTree(dir: 'libs', include: ['*.jar'])";
    if (!config.modResults.contents.includes("fileTree(dir: 'libs'")) {
      config.modResults.contents = config.modResults.contents.replace(
        /dependencies\s*\{/,
        "dependencies {\n" + line
      );
    }
    return config;
  });

  config = withDangerousMod(config, [
    "android",
    async (config) => {
      const projectRoot = config.modRequest.projectRoot;
      const pluginDir = path.join(projectRoot, "plugins", "asanamul-injector");
      const androidRoot = path.join(projectRoot, "android");
      // 1. Java sources (Injector, InjectorBridge, VoiceInjectorModule,
      //    VoiceInjectorPackage, rikka.sui.Sui stub).
      const javaDest = path.join(androidRoot, "app", "src", "main", "java");
      copyDir(path.join(pluginDir, "java"), javaDest);
      // 2. Shizuku jars.
      const libsDest = path.join(androidRoot, "app", "libs");
      fs.mkdirSync(libsDest, { recursive: true });
      for (const f of fs.readdirSync(path.join(pluginDir, "libs"))) {
        if (f.endsWith(".jar")) {
          fs.copyFileSync(path.join(pluginDir, "libs", f), path.join(libsDest, f));
        }
      }
      // 3. Register VoiceInjectorPackage in MainApplication.kt.
      //    Same package as the module, so no import is needed.
      const mainApp = path.join(javaDest, ...PKG_SEGMENTS, "MainApplication.kt");
      if (fs.existsSync(mainApp)) {
        let src = fs.readFileSync(mainApp, "utf8");
        if (!src.includes("VoiceInjectorPackage()")) {
          const next = src.replace(
            /PackageList\(this\)\.packages\.apply\s*\{/,
            (m) => m + "\n          add(VoiceInjectorPackage())"
          );
          if (next !== src) fs.writeFileSync(mainApp, next);
        }
      }
      return config;
    },
  ]);

  return config;
}

module.exports = withAsanamulInjector;
