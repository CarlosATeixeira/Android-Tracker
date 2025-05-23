# Android-Tracker

Android app for collecting and sending GPS location data in real time to a Node.js backend, with interactive map visualization via a desktop interface.

# 📍 Android Tracker – Fullstack + Desktop Project

This project consists of three main components that work together to track mobile devices:

-   📱 **Android App** – Sends the device's location data to the API.
-   🌐 **Node.js API** – Receives and stores the location data in a local **SQLite** database (no longer using JSON files for persistence), made accessible via a secure tunnel (Cloudflared).
-   🖥️ **Desktop App (Windows)** – Displays the locations on an interactive map through a graphical interface.

---

![Project Image](https://i.imgur.com/nQwZbGP.jpeg)

---

## 🧩 Project Structure

```
android-tracker/
├── android-app/        # Android mobile app
├── node-api/           # REST API built with Node.js
└── windows-app/        # Desktop app with GUI and map visualization
```

---

## 🔐 API Security

-   The `POST /api/enviarLocalizacao` route is **public** to maintain compatibility with existing Android devices.
-   Reading and deletion routes (`/listarLocalizacoes`, `/limparLocalizacoesLidas`, `/limparTudo`) are protected by **Bearer token authentication**.
-   The API is secured against unauthorized access.

---

## 🔧 Technologies Used

-   **Node.js + Express + SQLite (better-sqlite3)** (backend API with persistent storage)
-   **Android (Java/Kotlin)** (mobile app)
-   **Windows App + Leaflet.js** (map visualization)
-   **Cloudflared Tunnel** (secure API exposure)
-   **CefSharp (Chromium Embedded Framework)** for the desktop GUI

---

## 💾 Storage

-   **All location data is now stored in a local SQLite database (`localizacoes.db`) using the [`better-sqlite3`](https://github.com/WiseLibs/better-sqlite3) library.**
-   No JSON files are used for persistence anymore.

---

## ⚠️ Disclaimer

This public version **does not contain any real data**. All data has been removed or replaced with fictional examples.  
This project was developed for learning purposes and technical demonstration only.

---

## 📄 License

This project is free to use for educational and personal purposes. For other uses, please contact me.
