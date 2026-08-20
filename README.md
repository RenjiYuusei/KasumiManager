<h1 align="center">Kasumi Manager</h1>

<p align="center">
  <a href="https://github.com/RenjiYuusei/KasumiManager/releases"><img alt="Releases" src="https://img.shields.io/github/v/release/RenjiYuusei/KasumiManager?style=flat-square"></a>
  <a href="https://github.com/RenjiYuusei/KasumiManager/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/badge/license-MIT-blue?style=flat-square"></a>
  <a href="https://github.com/RenjiYuusei/KasumiManager/issues"><img alt="Issues" src="https://img.shields.io/github/issues/RenjiYuusei/KasumiManager?style=flat-square"></a>
</p>

<p align="center">
  A fast, friendly, and focused Android mod manager for Discord — rebranded for the KasumiCord community.
</p>

---

## ✨ Why Kasumi Manager?

Kasumi Manager makes installing and managing KasumiCord simple:
- Clean, KasumiCord-branded UI and icons
- Safe vector handling to avoid runtime inflation issues
- Fast install/update workflows

---

## 🚀 Quick Start

<p><strong>Download & run</strong> — get a released APK from Releases and install with:</p>

Get newest apk from [release list](https://github.com/RenjiYuusei/KasumiManager/releases/) and install it.

<p><strong>Build from source</strong> — clone and assemble:</p>

```bash
git clone https://github.com/RenjiYuusei/KasumiManager.git
cd KasumiManager
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

---

## ⚙️ Features

- One-tap install, update, or remove of supported mods
- About screen with contributors and "fun facts"
- Safe vector drawable usage (vector groups + scale/translate)
- Customizable branding via resources (colors, icons, strings)

---

## 🎨 Customization & Theming

You can fully tailor the appearance:

- App name: edit `app/src/main/res/values/strings.xml` — ensure the UI uses `@string/app_name`.
- Icons: swap `app/src/main/res/drawable/ic_rounded_shiggy.xml` and `app/src/main/res/drawable/ic_discord_aliucord.xml` with your vector or PNG.
- Colors: use `colors.xml` to apply a new palette.
- Glyph sizing: vector drawables support `<group android:scaleX="" android:scaleY="" android:translateX="" android:translateY="">`. To center an element after scaling, compute translation as:

```bash
translate = (viewportSize - (viewportSize * scale)) / 2
# Example for viewport 256 and scale 0.7 -> translate = 38.4
```

---

## 🤝 Contributing

We love contributions! Here's how to help:

1. Fork the repository.
2. Create a branch:
```bash
git checkout -b feat/my-cool-feature
```
3. Make your changes, run tests and build:
```bash
./gradlew :app:assembleDebug
```
4. Open a PR describing:
   - What you changed
   - Why it helps
   - Screenshots if UI changes

---

## 🧾 License

Kasumi Manager is open source under the OSL License. See `LICENSE` for details.

---

<p align="center">
  <strong>Made with ❤️ for the KasumiCord community.</strong>
</p>
