# AS ANAMUL VOICE — Build ও Setup Guide

এই প্রথম checkpoint-এ AS ANAMUL VOICE-এর RGB control deck, voice catalog screen, authenticated admin foundation, managed MySQL schema, public catalog API এবং document picker যুক্ত আছে। Project checkpoint: `8dca998`।

## ১. Source প্রস্তুত করুন

ZIP archive extract করে project folder-এ terminal খুলুন। Node.js 20+ এবং pnpm ইনস্টল থাকা দরকার। তারপর চালান:

```bash
pnpm install
pnpm check
pnpm lint
pnpm test
```

## ২. Local preview চালান

```bash
pnpm dev
```

Metro/Web preview port `8081` এবং API port `3000` ব্যবহার করবে। Browser-এ Expo Web preview খুললে Home, Voices এবং Admin tab দেখা যাবে। Public catalog খালি থাকলে “No voices published” দেখানো স্বাভাবিক; কোনো fake voice seed করা নেই।

## ৩. Android APK তৈরি করুন

Expo/EAS account ব্যবহার করে cloud build চালানোর একটি সাধারণ পদ্ধতি:

```bash
npm install --global eas-cli
eas login
eas build:configure
eas build --platform android --profile preview
```

`preview` profile-এ APK তৈরি হবে। Build শেষ হলে EAS যে APK link দেবে, সেটি ফোনে ডাউনলোড করে install করুন। Release Play Store package-এর জন্য:

```bash
eas build --platform android --profile production
```

Signing keystore নিরাপদে সংরক্ষণ করুন। Keystore হারালে future update করা কঠিন হবে।

## ৪. ফোনে প্রথম যাচাই

OnePlus Nord 2T / OxygenOS 14-এ APK install করে Microphone ও overlay permission কেবল Android-এর system prompt এলে দিন। App-এর Home tab-এ আগে `REFRESH CHECK` চাপুন। Shizuku setup খুলে Shizuku app-এ আপনার পছন্দের official start method চালু করুন। এরপর app-এর diagnostics দেখুন।

Green `ready` না দেখালে Injector চালু করার চেষ্টা করবেন না। বর্তমান build ইচ্ছাকৃতভাবে false success দেখায় না। Preview বা managed Expo build native Shizuku/audio route-এর প্রমাণ নয়।

## ৫. Admin access

Admin tab-এ `SIGN IN` চাপুন এবং configured owner/admin Manus OAuth account দিয়ে sign in করুন। Owner identity project environment-এ configured হলে starter user role admin হিসেবে সংরক্ষণ করে। Admin role না এলে server-side owner configuration ঠিক করতে হবে; app-এর ভিতর থেকে role bypass করা যায় না।

Admin panel-এ voice metadata publish করতে প্রথমে managed storage-এ file upload workflow সম্পূর্ণ করতে হবে, তারপর stable storage key, voice name, category এবং format দিন। Server credentials বা API key client app-এ রাখবেন না।

## ৬. Database ও API

Managed project-এ schema migration চালানো হয়েছে। নতুন environment-এ চালান:

```bash
pnpm db:push
```

Public catalog procedure voice, notices এবং settings ফেরত দেয়। Admin procedures authenticated admin account ছাড়া কাজ করবে না। Social links-এর বর্তমান defaults:

- Telegram: `https://t.me/crackffpanel`
- YouTube: `https://www.youtube.com/@AnamulModzOfficial`

## ৭. গুরুত্বপূর্ণ capability সীমা

Android 12–15+ এবং সব manufacturer-এ AudioPolicy, remote-submix, Shizuku এবং microphone routing একরকম নয়। এই checkpoint-এর native capability helper অনুপস্থিত native bridge-এ `unsupported` বা `not-ready` দেখায়; এটি demo injector নয়। Android build-এ বাস্তব native bridge, Root/Shizuku status এবং device-specific audio route আলাদা করে implement ও physical device-এ যাচাই করতে হবে।

Free Fire বা অন্য app-এর anti-cheat, microphone permission বা security restriction bypass করার code এই project-এ রাখা হয়নি। কেবল অনুমোদিত Android audio capability-এর মধ্যে কাজ করুন।

## ৮. Backup

প্রতিটি গুরুত্বপূর্ণ change-এর আগে source ZIP, managed database export, storage asset list এবং Android signing keystore-এর encrypted backup রাখুন। API secrets, OAuth credentials, keystore password, OTP বা admin password chat/Telegram-এ পাঠাবেন না।
