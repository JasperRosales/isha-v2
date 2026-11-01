# isha v2

A private, native Android personal assistant. isha v2 is a standalone Kotlin/Jetpack Compose app: it talks to Gemini directly for reasoning, to Composio for your apps (Gmail, Calendar, Classroom, Drive, Notion…), and can fall back to an on-device model when there is no signal. No PC, Node server, or companion daemon involved.

The app is intentionally small in surface area and large in capability: a chat drawer for conversations, streaming answers, tool calls with an approval card for consequential actions, a local inbox, and an app catalog for connecting Gmail, Calendar, Drive, Notion, Slack, GitHub, Discord, and the rest of Composio's toolkits.

```
┌──────────────────────────── Galaxy / Pixel ────────────────────────────┐
│  Compose UI  ──  ChatViewModel  ──  AgentRunner                        │
│                                      │                                 │
│                    ┌─────────────────┼──────────────────┐              │
│                    ▼                 ▼                  ▼              │
│             Gemini REST API    Native tools       Composio             │
│             (streaming +       (web, notify,     (Connect MCP or        │
│              function calls)    open, alarm)      REST v3.1 sessions)   │
└─────────────────────────────────────────────────────────────────────────┘
```

## What it does

- **Personal-assistant brain.** The system prompt is tuned for running your day (inbox, calendar, tasks, school/work, reminders), not for coding, and tells the model how to drive Composio's search → schema → multi-execute flow.
- **Offline mode.** Download a small on-device model (Qwen 2.5 1.5B/0.5B, Gemma 3 1B, Phi-4 mini) or import any MediaPipe `.task` file. **Automatic** mode uses Gemini online and the local model offline, and falls back on-device if Gemini becomes unreachable mid-chat.
- **Claude as a backup brain.** Gemini always answers first. Add an optional Claude API key in Settings and, when Gemini's free quota runs out (or Gemini errors), the same reply continues on Claude with full tool access. isha then skips Gemini until its quota resets (midnight Pacific for the daily limit). Claude usage is billed to your Anthropic API credits.
- **Push notifications.** Replies that finish while you are away arrive as chat-style notifications with an inline **Reply** box; reminders fire at the exact time (and survive reboots); an optional **daily briefing** summarises calendar, email and tasks each morning.
- **Budget tracking (works offline).** Log spending and income by chat ("spent 150 on lunch", "got 3k allowance", "set food budget to 3000", "how much is left?") or from the **Budget** screen. Monthly and per-category limits, 80%/over-budget push alerts, month history, and an all-time balance (all income minus all spending). Budget commands work with no network and even with no model downloaded.
- **Memory.** isha saves lasting facts you tell it (`remember_fact` / `forget_fact`); you can review and delete them in Settings.
- **Fluid chat.** The list follows the streaming reply only while you are at the bottom, pauses when you scroll up, and offers a "Latest" button to jump back.
- **Streaming chat with Gemini** using `streamGenerateContent?alt=sse`, function calling, and tool-result continuation.
- **Composio integrations** through either Composio Connect MCP (consumer key) or REST v3.1 tool-router sessions (project key).
- **Approval before consequential actions.** Multi-tool execution, remote bash, and the remote workbench pause on an in-app approval card. Safe helpers (search, schemas, connection links, read-only native tools) do not.
- **Native phone tools**: web search, read a URL, current time, reminders (set/list/cancel), memory, local notification + inbox entry, open a link, share text, and pre-fill an alarm.
- **Read-only auto-approval.** A Composio batch whose tools only read (`GMAIL_FETCH_EMAILS`, `GOOGLECALENDAR_LIST_EVENTS`, …) runs without an approval card; anything that sends, creates or deletes still asks. Toggle in Settings → Approvals.
- **Local-first history**: conversations, attachments, and the inbox are files on the device. There is no isha server and no account system.
- **Neumorphic cream/espresso interface** with a light/dark switch, a drawer of conversations, an apps screen, an inbox, an offline-models screen, and first-run setup.
- **Mascot icon and splash.** A chibi girl with space buns (`art/isha.svg`) is the adaptive/themed launcher icon, the in-app avatar and the notification avatar; a short animated greeting follows the system splash on cold start.
- **Encrypted keys.** The Gemini, Claude and Composio keys are encrypted with AES/GCM using a key held by the Android Keystore. Keys are entered in the app, not baked into the APK or the repository.

## Requirements

- Android 8.0 (API 26) or newer.
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey). The default model is `gemini-3.5-flash-lite`; you can change it in Settings. Optional if you only use offline mode.
- Optional: a Claude API key from the [Anthropic Console](https://console.anthropic.com/settings/keys) as a paid backup when Gemini's free limit is used up.
- A Composio key from [dashboard.composio.dev](https://dashboard.composio.dev):
  - `ck_…` — **Connect MCP** (recommended; verified with the current consumer key). Uses `https://connect.composio.dev/mcp`.
  - `ak_…` — **REST project key** (v3.1 tool-router sessions). Uses `https://backend.composio.dev/api/v3.1`.
- A machine with JDK 17 and Android SDK platform 35 to build.

## Build and install

The repository does not include `local.properties` or any signing material.

```bash
git clone git@github.com:JasperRosales/isha-v2.git
cd isha-v2
printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties
JAVA_HOME="$HOME/.local/opt/jdk-17" ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If your SDK or JDK live elsewhere, point `sdk.dir` and `JAVA_HOME` at them. The debug APK is self-signed and suitable for sideloading; it is not a Play Store build.

Run the unit tests (schema normalization) with:

```bash
JAVA_HOME="$HOME/.local/opt/jdk-17" ./gradlew testDebugUnitTest
```

## First run

1. Install and open the app.
2. Paste your **Gemini API key**, or tap **Use isha offline** to skip it and download an on-device model instead.
3. Optionally paste a **Composio key**. A `ck_` key uses the already-connected Composio Connect account; an `ak_` key creates a private tool-router session for the stable user id generated on this device.
4. Start chatting.

Keys can be changed later in **Settings**. **Apps** shows the Composio connection state, connected accounts, and a searchable catalog with Connect links. **Clear everything** wipes local chats, inbox items, and stored keys.

## Composio modes

| Key prefix | Mode | Transport | Notes |
| --- | --- | --- | --- |
| `ck_` | Connect MCP | Streamable HTTP MCP at `connect.composio.dev/mcp` | Simplest. The Composio dashboard already owns the app connections. The app discovers the 11 meta-tools (search, schemas, multi-execute, connections, remote bash, workbench, etc.). |
| `ak_` | REST project | REST v3.1 tool-router sessions | Creates a session with `manage_connections` and the workbench disabled, then loads tool schemas from `/tool_router/session/{id}/tools`. Connected accounts and link sessions are handled with the same key. |

Both modes expose the same approval policy. In REST mode the workbench is disabled at session creation, so remote bash/workbench tools are not offered by the session even though the in-app policy still recognizes them.

### Tool schemas

Composio returns full JSON Schema. Gemini's `parameters` field accepts only a subset, so `SchemaSanitizer` resolves local `$ref`s, folds `allOf`, turns `oneOf`/`const` into `anyOf`/`enum`, normalizes nullable type arrays, and strips unsupported keys (including `additionalProperties`, `$defs`, `$id`, `$schema`). This is covered by `SchemaSanitizerTest`.

## Offline models

**Offline brain** (drawer or Settings) lists models from Hugging Face's `litert-community`, chosen for everyday assistant work:

| Model | Download | Notes |
| --- | --- | --- |
| Qwen 2.5 · 1.5B (recommended) | 1.6 GB | Best all-rounder; 4096-token memory; 6 GB+ RAM. |
| Gemma 3 · 1B | 555 MB | Needs a free Hugging Face token from an account that accepted the Gemma license. |
| Qwen 2.5 · 0.5B (lite) | 547 MB | Fast; fine on older phones. |
| Phi-4 mini · 3.8B | 3.9 GB | Strongest reasoning, slow; 8 GB+ RAM. |

Downloads run as a foreground WorkManager job with a progress notification and resume from a `.part` file. You can also import any MediaPipe LLM `.task` bundle. Inference uses MediaPipe LLM Inference (`tasks-genai`) on the CPU. Offline replies have no tools (no apps, web or reminders) and are marked "answered on-device".

## Budget

Everything is stored in `budget.json` in app-private storage; nothing is sent anywhere unless you ask Gemini about it.

| Where | How |
| --- | --- |
| Online (Gemini) | Tools `add_transaction`, `budget_summary`, `list_transactions`, `set_budget`, `delete_transaction`. isha logs money you mention and answers "can I afford…" from your real numbers. |
| Offline / no model | `BudgetParser` understands plain commands: *spent / paid / bought N on X*, *got N allowance*, *received N salary*, *set food budget to N*, *budget N for transport*, *how much did I spend today / this week*, *how much is left*, *undo last*. Anything else goes to the on-device model, which also receives a short snapshot of this month's budget. |
| Budget screen | Drawer → Budget. Monthly spend, budget left, income/net tiles, category limits, transactions by day (long-press to delete), currency symbol (default ₱). |

Expense categories: Food, Transport, Bills, Shopping, School, Health, Entertainment, Personal, Other. Income: Allowance, Salary, Freelance, Gift, Other income. A category is guessed from keywords (jeep/grab → Transport, photocopy → School, …).

## Notifications

| Channel | When |
| --- | --- |
| Replies | A reply finished while the app was in the background. Has an inline Reply action. |
| Reminders | A reminder from `set_reminder` fires (exact alarm; re-armed after reboot). |
| Daily briefing | The morning summary, if enabled in Settings → Personal assistant. |
| Assistant work / Model downloads | Silent ongoing notifications while a reply generates or a model downloads. |
| Budget alerts | A category or the monthly budget reaches 80% or goes over. |
| isha notifications | Inbox items from `notify_user` and the mail/Classroom pollers. |

These are local notifications scheduled on the phone. There is no push server, so nothing arrives while the phone is off.

## Approvals

With **Manual** approval (the default), calls whose names match `COMPOSIO_MULTI_EXECUTE_TOOL`, `COMPOSIO_REMOTE_BASH_TOOL`, or `COMPOSIO_REMOTE_WORKBENCH` pause before running. The chat shows an approval card with the arguments and a 10-minute timeout. Denying the call returns an error to the model instead of executing it. **Auto** approval skips the card; use it only if you trust the conversation.

Native tools can open the browser, the share sheet, and the clock app, but they always require a visible user action in the destination app before anything changes. `notify_user` writes a local notification and an inbox item.

## Architecture

| Path | Role |
| --- | --- |
| `agent/AgentRunner.kt` | Streams model turns, accumulates text/thought/tool-call parts, executes calls, persists history, repeats until an answer or the step limit. |
| `agent/ApprovalBroker.kt` | Bridges a waiting tool call and the Compose approval card. |
| `agent/NativeTools.kt` | On-device tools and their JSON schemas. |
| `agent/SystemPrompt.kt` | Persona, memory, current time/timezone, Composio status; also the trimmed prompt for offline models. |
| `agent/ChatService.kt` | Foreground service that keeps a reply alive in the background and posts the reply notification. |
| `local/` | Model catalog, download worker, and the MediaPipe engine (`LocalLlm`). |
| `data/BudgetStore.kt` / `agent/BudgetAssistant.kt` | Budget data, monthly summaries, threshold alerts, and the offline command parser. |
| `notify/` | Inline-reply receiver and reminder alarms. |
| `worker/BriefingWorker.kt` | Daily briefing run in a background conversation. |
| `net/GeminiClient.kt` | Direct Gemini SSE client with cancellation and friendly API errors. |
| `net/McpClient.kt` / `net/ComposioMcp.kt` | Minimal MCP JSON-RPC client and the Connect transport. |
| `net/ComposioRest.kt` | REST v3.1 sessions, tools, execute, accounts, links, catalog. |
| `net/ComposioManager.kt` | Mode selection, connect/retry, declarations, execution, approval gating. |
| `net/SchemaSanitizer.kt` | JSON Schema → Gemini `parameters`. |
| `data/` | Settings, encrypted prefs, JSON conversation store, inbox store, models. |
| `ui/` | Compose screens, drawer, markdown renderer, message bubbles, approval card, composer. |

State is persisted as JSON under the app's private files directory. There is no Room/KSP dependency and no background service.

## Security and privacy

- Requests go directly from the phone to `generativelanguage.googleapis.com`, `connect.composio.dev`, or `backend.composio.dev`; there is no intermediary server.
- API keys are stored as AES/GCM ciphertext in private SharedPreferences; the wrapping key never leaves the Android Keystore. Android backup is disabled.
- Conversations and attachments live only in app-private storage. Clearing app data or using **Clear everything** removes them.
- Tool results are clipped before being sent back to the model to bound request size.
- The app does not request contacts, location, SMS, or accessibility permissions. It requests `INTERNET`, `ACCESS_NETWORK_STATE` (online/offline switching), `POST_NOTIFICATIONS`, `USE_EXACT_ALARM` (reminders), boot-completed (re-arm reminders), and foreground-service permissions for replies and model downloads.
- Offline replies never leave the phone. Model files are stored in app-private storage.

## Limitations

- The debug APK is signed with a local debug key. Rebuilding on another machine creates a different signature; uninstall before reinstalling across machines.
- REST project mode is implemented against the published v3.1 API but has not been exercised end-to-end here because only a `ck_` key was available. Connect MCP is verified.
- Background work is limited to finishing an in-flight reply, reminders, model downloads and the daily briefing. isha cannot control a PC shell or files.
- The daily briefing runs unattended, so actions that need approval are skipped (read-only lookups still run when "skip approval for read-only lookups" is on).
- On-device models are much weaker than Gemini and have no tools. The APK ships arm64-v8a and x86_64 native libraries only.
- Free-tier Gemini quotas apply. A 429 is surfaced in the chat; wait for the quota window to reset.
- Model names and tool availability change. Update the model in Settings if your key does not have access to the default.
- This is a personal project, not a hardened multi-user product. Keep the repository private and do not commit keys.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Add a Gemini API key in Settings.` | Finish onboarding or enter the key in Settings. |
| Gemini `400 INVALID_ARGUMENT` | Check the model name in Settings. If you added tools, run the unit tests; the sanitizer must not emit keys Gemini rejects. |
| Composio `401`/`403` | The key is wrong or is a project key used with the wrong mode. `ck_` → Connect MCP; `ak_` → REST. Regenerate it in the Composio dashboard if needed. |
| Composio connects but no apps work | Open **Apps**, connect the toolkit, then retry. Connect mode uses the account already linked in Composio. |
| `429` from either service | Rate limit or quota. Wait and retry. |
| No notifications | Grant the notification permission on Android 13+, and check that the inbox item was created. |
| Model download fails with 401/403 | The model is gated (Gemma). Accept its license on Hugging Face and save a read token in Offline brain. |
| Offline model is slow or crashes | Pick a smaller model; the 1.5B model wants 6 GB+ RAM and Phi-4 mini 8 GB+. |

## Repository

Private: `github.com/JasperRosales/isha-v2`. No secrets, `local.properties`, keystores, or build outputs are tracked.
