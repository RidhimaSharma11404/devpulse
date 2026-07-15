# ⚡ DevPulse: Developer Telemetry & Productivity Platform

DevPulse is a high-performance Java Full-Stack developer telemetry dashboard and execution sandbox. It monitors typing metrics, WPM, and compilation error diagnostics in real-time, using AI to generate Daily Standup reports and static refactoring insights.

This project is built using **Spring Boot**, **WebSockets**, **SQLite**, and **Monaco Editor**.

---

## 🚀 One-Click Cloud Deployment

You can deploy DevPulse to the cloud for free with a single click. Click the button below, log in with your GitHub account, and Render will automatically host the application live!

[![Deploy to Render](https://render.com/images/deploy-to-render.button.svg)](https://render.com/deploy?repo=https://github.com/RidhimaSharma11404/devpulse)

---

## 🛠️ Key Features

* **Monaco Editor Sandbox**: In-browser code editing (Java/JavaScript) with isolated sub-process execution, safety timeouts, and stdout/stderr capture.
* **Real-time Telemetry Stream**: Native WebSocket connection streaming character counts, typing velocity (WPM), and focus states.
* **Live Analytics Chart**: Interactive time-series charts mapping productivity metrics using Chart.js.
* **Hybrid AI Co-Pilot**: Automated Daily Standup generators and static code review heuristics, featuring optional OpenAI GPT-4 integration.
* **Embedded Relational Storage**: Zero-configuration persistence using SQLite and JPA/Hibernate.
* **Dockerized Scaffolding**: Packaged container configuration for instant multi-cloud deployment.

---

## 💻 Running Locally

1. Ensure you have **Java 21** installed.
2. Double-click or run the launcher script in your terminal:
   ```bash
   .\run.cmd
   ```
   *The launcher automatically downloads Maven locally, packages the jar, and boots the server.*
3. Open your browser to **http://localhost:8080**.
