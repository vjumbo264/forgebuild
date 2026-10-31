# PROMPT HISTORY — otg-enabler

## 2026-10-31 — Operator instruction #1 (initial build contract)
ForgeBuild App Build Contract — NEW APP
Forked version of root USB OTG enabler app for modern devices (resolving "SDK version too low").
App description:
"I need a forked version of this root app that will work on my device.
Right now the SDK version of the app is too low for my device. So please make a forked version of this app so that it will enable USB OTG using root access. Also, do more research to find ways to increase its capability so that in case it will not work on my device to enable the OTG, it will still work when you've made more research. So make sure you increase its capability, making it work on all devices.

https://github.com/nikita-yfh/otg-enabler/releases/download/1.0/release.apk"


---

## 2026-10-31 — Operator instruction #2 (EXTEND / UPDATE contract for otg-enabler-v2)

# OTG Enabler — Fix Prompt (self-contained, for a stateless builder AI)

You have no memory of earlier sessions. Everything you need is in this file. You have **full autonomy**: decide the approach, write the code, build, and push. Do not ask for step-by-step instructions.

## ABSOLUTE RULE: NEVER ASK QUESTIONS

**Under no circumstances, for any reason, stop to ask the owner a clarifying question, ask for confirmation, offer options and wait, or ask "should I proceed?".** The owner is on a phone, often away, and will not answer. A question is a dead end that wastes the whole session.

- If something is ambiguous or missing, **make the most reasonable decision yourself**, write the decision into `BUILD_STATE.json` under `notes`, and keep going.
- If a file, path, slug, or setting cannot be found, search harder, infer it from the repo, or create what is needed. Do not ask where it is.
- If a step fails, diagnose and retry with a different approach. Do not stop to report the failure unless every reasonable approach has been exhausted, and even then finish everything else first.
- The only time you may stop and report back is the UI refinement pass described in section 11.
- Only report at the very end, in the short final report (section 12).

## 0. Credentials (placeholders — the owner fills these in)

```
GITHUB_PAT = <REDACTED>
CLOUDFLARE_API_TOKEN = <REDACTED>
CLOUDFLARE_ACCOUNT_ID = <REDACTED>    # only if the build pipeline needs it
FORGE_REPO=vjumbo264/forgebuild                  # the existing ForgeBuild repo (already exists, do not create anything new)
```

Never print these in logs or commit them.

## 1. Where the project lives

- **The repo is `vjumbo264/forgebuild` (ForgeBuild).** It already exists. **OTG Enabler is NOT its own repo.** It is one app inside ForgeBuild's single-repo architecture: `engine/` (shared Android engine), `apps/<slug>/` (one folder per app), `site/` (Dashboard on Cloudflare Pages), and one GitHub Release per app version tagged `<slug>-vN`.
- Find the app's folder first: look in `apps/` for a folder whose slug is `otg-enabler` or similar, or search for `OTG Enabler`, `usb_role`, `typec`. That folder is `apps/<slug>/`. Use the existing slug. Never invent a new one or create a second copy of the app.
- This is an **update to an existing app**, not a new app. Treat it like ForgeBuild's "extend/update existing app" flow: read the app's existing source and its `BUILD_STATE.json` / version info, then bump to the next version (`<slug>-v(N+1)`) and release it the way previous versions were released.
- Follow the repo's existing conventions: look at how the engine, other apps, and the GitHub Actions workflow are set up, and match them. Do **not** create a new repo, do not create per-app repos, and do not restructure the rest of ForgeBuild.
- Respect ForgeBuild's standards: lightweight (no unjustified dependencies), Material 3 + Material Symbols only (no emoji), keep the proper layered adaptive icon, use the shared engine where it already provides something.
- `BUILD_STATE.json`: if the app folder already has one, keep using it and extend it for this task. Otherwise create it in `apps/<slug>/` (see section 7).

## 2. Background

The owner (Victor, MOTIONSALT) works entirely from an Android phone (Termux). No desktop, no local CLI. Everything is built through GitHub Actions / browser workflows. Prefer free tiers.

He built **OTG Enabler**, a rooted Android app that forces USB OTG host mode, because his custom GSI does not have OTG built in yet.

- Device: MediaTek **mt6768**, running **AxionOS GSI**, **Android 16 (API 36)**.
- Root: KernelSU (`uid=0 ... context=u:r:ksu:s0`).
- SELinux was switched to permissive by the app during testing and it made no difference, so SELinux is probably not the blocker.
- The app has a "strategy" selector (e.g. "Universal Smart Auto-Detect", "Transsion / Tecno / Infinix", "Aggressive All-In-One Force"), a sysfs node scanner with per-node `echo` buttons, a Root Logs tab, and an Automation tab.

## 3. The problem

1. **The app finds OTG sysfs nodes but does not actually apply them.** It reports "USB OTG Enabled" and "Supplying 5V VBUS" even though nothing really changed. Plugging in a real OTG device does not work.
2. **It falsely shows "2 Attached" USB devices with nothing plugged in.**

## 4. Evidence (from the real device — trust this)

### 4.1 Log excerpt

```
Strategy: Universal Smart Auto-Detect
echo "host" > /sys/class/usb_role/mt_usb-role-switch/role        -> SUCCESS
echo "host" > /sys/class/typec/port0/data_role                   -> WARNING: Failed (empty error text)
echo "source" > /sys/class/typec/port0/power_role                -> WARNING: Failed (empty error text)
echo "dual" > /sys/class/typec/port0/port_type                   -> SUCCESS
setprop persist.sys.oem.otg_support true            (OnePlus/Oppo-specific, irrelevant here)
setprop persist.sys.oppo.otg_support true
setprop persist.vendor.otg.support 1
settings put global oneplus_otg_auto_close 0
settings put system otg_state 1
settings put global otg_enabled 1
=> "USB OTG Enabled! Applied to 3 driver nodes."

Strategy: Transsion / Tecno / Infinix
=> "USB OTG Enabled! Applied to 0 driver nodes."   (does nothing, yet reports success)

Scan => "Found 4 candidate sysfs OTG nodes." (never fed into the apply step)
Custom write: echo "host" > /sys/devices/platform/rt-pd-manager/typec/port0/data_role  -> "Custom write failed:" (empty error)

Disable path:
echo "USB-HOST=0" > /sys/class/extcon/extcon0/state
=> /system/bin/sh: can't create ...: Permission denied   (even as root)
```

### 4.2 Screenshot findings

- A node shown in the scan list: `/sys/devices/platform/dummy_udc.0/udc/dummy_udc.0/is_otg` with permissions `-r--r--r--` and value `1`. That is a **read-only node on the dummy UDC (a virtual placeholder gadget controller)**. Writing to it can never do anything. The scanner must not list it as controllable.
- "Connected USB Peripherals" shows two entries, both `VID 0x1D6B  PID 0x0002 (Peripheral (lsusb))`. **VID 0x1D6B is the Linux Foundation and PID 0x0002 is the USB 2.0 root hub.** These are the phone's built-in root hubs, always present. They are not accessories. Nothing was plugged in.
- Status card says "HOST MODE ACTIVE" and "Supplying 5V VBUS power to connected USB accessories" purely because writes returned without a shell error. Nothing verified it.

## 5. Root causes (diagnosed — confirm and fix)

1. **No verification.** Success is claimed when a write does not error. Values are never read back, so a write that is silently ignored counts as success.
2. **stderr/errno not captured.** Failed writes show an empty error, so the cause is invisible. Capture stderr (`2>&1`) and the exit code for every shell command.
3. **Scan results are not used.** The scanner finds 4 candidate nodes but the apply step uses a hardcoded list, and the Transsion strategy applies to 0 nodes.
4. **Wrong-OEM junk.** The OnePlus/Oppo props and settings do nothing on this device. Remove them from the default path (an "Extra legacy tweaks" toggle that is off by default is fine).
5. **MTK power path likely missing.** `power_role=source` fails, so VBUS probably never turns on. The port may be in host data mode with **no 5V power**, which looks like "applied but nothing works". MTK typically controls VBUS through the charger/PD stack (tcpc / rt-pd-manager / mtk charger / `usb_otg` regulator), not the typec node alone.
6. **Fake device detection.** The device list counts every `lsusb` line including root hubs.
7. **Disable path assumes a node that doesn't exist or is not writable** (`extcon0/state`). It should only touch nodes that were verified writable.

## 6. Required fixes

### 6.1 A single safe write helper
Implement one function used by everything that touches sysfs, props, or settings:
- Run as root via `su -c`, capturing stdout, stderr, and exit code.
- Before writing: record the current value (`cat`), whether the node exists, and its permissions (and whether it is writable as root).
- After writing: wait about 200 ms, read the value back, and compare.
- Return a structured result: `{path, requested, before, after, ok, errno/stderr}`.
- Show `ok` only when the readback matches the intent. Note that some nodes report a different string than what was written (e.g. a role file may show `[host] device`). Parse that format properly rather than doing a naive string compare.
- Log the full structured result to the Root Logs tab, with real error text, never blank.

### 6.2 Scanner
- Scan `/sys/class/usb_role`, `/sys/class/typec`, `/sys/class/power_supply`, `/sys/class/extcon`, `/sys/devices/platform` (matching names containing `usb`, `otg`, `typec`, `tcpc`, `pd`, `vbus`, `charger`, `mtu3`, `musb`), and `/sys/kernel/debug` where readable.
- Exclude `dummy_udc` and anything not writable as root. Mark read-only nodes as "info only" (display value, no echo buttons).
- Categorize nodes: `role switch`, `typec data role`, `typec power role`, `vbus / otg regulator`, `charger otg`, `extcon`, `info only`.
- Store the scan result and feed it into the apply step. Do not rely on hardcoded paths alone, and keep the known MTK paths as a fallback list.

### 6.3 Apply logic (replace the three hardcoded-strategy dispatchers)
Run the steps in order, verifying after each, and stop at the first full success:
1. Set the role switch to host (`usb_role/*/role`).
2. Set the typec port: `data_role=host`, `power_role=source` (try `port_type` first if needed). If these fail, try the `rt-pd-manager` / tcpc equivalents that the scanner found.
3. **Turn VBUS on.** Find and try, in verified order: charger-side OTG controls (e.g. nodes named like `otg_enable`, `vbus`, `otg_vbus`, `boost`), `/sys/class/power_supply/*/otg` or `typec` entries, regulator nodes for `usb_otg_vbus` / `otg-vbus`. Only report VBUS as on if a readback shows it, or if `dmesg` / power_supply data shows the port sourcing power.
4. Re-check kernel logs: capture `dmesg | grep -i -E "tcpc|typec|usb|otg|vbus|mtu3|musb|xhci"` before and after each attempt and show the diff in the Root Logs tab.
5. Wait a few seconds and check whether `/sys/bus/usb/devices` gained a **non-root-hub** device.

Report one of three honest states:
- **Verified**: role is host AND VBUS confirmed on (and ideally a real device enumerated).
- **Partial**: role is host but VBUS or enumeration is not confirmed. Say exactly which step is unconfirmed.
- **Failed**: say which step failed, with the real error text.

Never show "Supplying 5V VBUS" unless VBUS was actually verified. Otherwise say "Host role set, VBUS not confirmed".

Strategies: keep the strategy selector, but each strategy must be a real ordered list of verified steps built on the shared helper and scanner results. A strategy that ends up applying 0 nodes must say "0 nodes applied, nothing changed", never "enabled".

### 6.4 Disable / restore
- Restore only the nodes that were changed (use the recorded "before" values), and verify the restore with readback.
- Never write to nodes that were never found writable (e.g. `extcon0/state` here). Skip them silently.
- Do not toggle SELinux permissive unless the user turns on an explicit "Try with SELinux permissive" option. If used, restore the original mode on disable.

### 6.5 Device detection
- Read `/sys/bus/usb/devices/*` (idVendor, idProduct, product, manufacturer, bDeviceClass) instead of parsing raw `lsusb` lines.
- Ignore root hubs: `idVendor == 1d6b` or `bDeviceClass == 09` with `busnum` root entries like `usbN` names.
- The "USB Devices" counter and the "Connected USB Peripherals" list must only count real accessories. With nothing plugged in it must show 0 / "No devices attached".
- Auto-refresh on a short timer while OTG is enabled, plus on `UsbManager` attach/detach broadcasts.

### 6.6 Diagnostics export (important)
Add a "Copy diagnostics" button that produces one text blob the owner can paste to an AI for debugging:
- Device, Android version, kernel (`uname -a`), root provider.
- Full scan list with permissions and values.
- Every structured write result from the last attempt.
- `dmesg` filtered as above, before and after.
- `/sys/bus/usb/devices` listing.
- `getprop | grep -i -E "usb|otg|typec|mtk|charge"`.

### 6.7 Clean-up
- Remove the OnePlus/Oppo/other-OEM props and settings from default paths.
- Keep the UI visually as-is for now. Only change the UI where needed to show the new states (Verified / Partial / Failed), the "info only" nodes, and the Copy diagnostics button.

## 7–12. BUILD_STATE, Git discipline, Build & delivery, Testing, Out-of-scope UI polish, Final report
- Extend `apps/otg-enabler/BUILD_STATE.json` with step milestones, commit and push after every step, unit-test pure logic (readback parser, root-hub filter, state evaluator), build signed release APK via `release.yml` for `otg-enabler-v2`, verify live on GitHub Releases API, and report download link.
