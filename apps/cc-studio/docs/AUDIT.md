# MOTIONSALT CC Studio: Honest Code Audit (R1)
Date: 2026-11-05
Auditor: Build Agent
Target: apps/cc-studio (cc-studio-v1 released artifact vs Section 6 Binding Requirements)

## Executive Summary
Victor's report is 100% accurate: the app in cc-studio-v1 was a visual shell with static mock composables and empty lambda handlers. None of the interactive flows (frame picking, project deletion, view mode switching, effect adding, LUT fitting, or pack export) were actually connected to state or filesystem operations.

---

## Detailed Control Audit by Screen

### 1. Navigation & State
- **Bottom Navigation Bar (Projects, Packs, Library, Settings)**:
  - Status: PARTIAL.
  - File: `app/src/main/java/com/motionsalt/ccstudio/CcStudioApp.kt:33-54`
  - Switches `currentNav` state, but stage transitions are not bound to project status or prerequisites.
- **Stage Bar (Source | Fit | Tune | Export)**:
  - Status: PARTIAL / STUB.
  - File: `CcStudioApp.kt:70-89`
  - Stages are not locked based on prerequisites (Fit requires 1 complete pair; Tune requires finished fit; Export requires tune state). Stages can be tapped arbitrarily, but inner screens do not execute real work.

### 2. Projects List (Symptom 2: Cannot delete projects)
- **Projects List**:
  - Status: STUB.
  - File: `CcStudioApp.kt:92-120`
  - Cards are rendered from a static mock list. No overflow menu (`IconButton` with `DropdownMenu`) exists on cards.
  - No Rename, Duplicate, or Delete actions.
  - No Delete confirmation dialog.
  - No directory deletion for project folders (`context.filesDir/projects/<id>`).
  - No "Delete all projects" in Settings > Data.

### 3. Source Stage (Symptom 1: Cannot add frames to pairs)
- **Add Pair Button**:
  - Status: STUB.
  - File: `CcStudioApp.kt:132` (`onClick = {}`).
  - Does not open sheet or dialog.
- **Image Sources & Pickers**:
  - Status: MISSING / STUB.
  - Neither `rememberLauncherForActivityResult(PickVisualMedia())` nor `GetContent()` nor camera/video frame extractors are registered.
  - Base and Target thumbnails do not accept taps to pick/replace.
  - No file copy mechanism into the project directory.
  - Swap, crop/ignore-region editor, and remove actions are absent.
  - "Fit look" button is not gated on pair completion.

### 4. Fit Stage
- **Fit Solver Execution**:
  - Status: STUB.
  - File: `CcStudioApp.kt:195-225`
  - "Run Look Fitting Solver" has `Button(onClick = {})` at line 218.
  - No foreground service (`FitService`) implemented.
  - No notification, no progress steps (Prepare, Curves, Matrix, Residual, Validate, Build).
  - No before/target/recreated/heatmap tabs connected to real bitmap rendering.

### 5. Tune Stage (Symptoms 3 & 6: View modes don't work, cannot add effects)
- **View Mode Selector**:
  - Status: STUB.
  - File: `CcStudioApp.kt:274-280`
  - `ScrollableTabRow` with hardcoded tabs, all with `onClick = {}`.
  - Missing modes: Wipe with vertical/horizontal toggle, Side by side, Top/bottom, Vs Target, Difference + gain, Channels (R, G, B, Luma), Scopes (histogram + RGB parade).
- **Viewer**:
  - Status: STUB.
  - File: `CcStudioApp.kt:282-288`
  - Merely renders `Text("60 FPS OpenGL ES Preview Active")` inside a black Card. No GLSurfaceView / TextureView / Custom Painter. No pan/zoom gesture handlers.
- **Stack Panel**:
  - Status: STUB.
  - File: `CcStudioApp.kt:290-330`
  - Hardcoded list of 3 items (`ms_lut`, `ms_glow`, `ms_grain`).
  - No "Add Effect" button or picker sheet.
  - No reordering, enable switches, duplicate, or delete.
  - No parameter type controls (BOOL switch, CHOICE dropdown, COLOR hex picker).
  - No undo/redo stack.
  - Sliders do not alter any rendering engine.

### 6. Export Stage (Symptom 4: Cannot export)
- **Export Controls**:
  - Status: STUB.
  - File: `CcStudioApp.kt:335-350`
  - `Button(onClick = {})` at line 345.
  - Does not assemble `.kfpack` or write to MediaStore/SAF `Downloads/MOTIONSALT/CC Studio/`.
  - Pack modes (Add to existing, Create new, Merge) are not implemented.
  - Collision dialog is missing.
  - Success sheet (Share, Open folder, Open in Keyflaw) is absent.

### 7. Permissions (Symptom 5: App never asks for permissions)
- **Runtime Permissions**:
  - Status: MISSING.
  - While permissions are declared in `AndroidManifest.xml`, no runtime permission requests (`RequestPermission()` or `RequestMultiplePermissions()`) exist in the UI code.
  - No just-in-time explanation dialogs.
  - No fallback handling.
  - No Settings > Permissions management screen.

---

## Conclusion & Action Plan
All six symptoms are confirmed and documented with line numbers. The repair queue R1 through R9 will systematically replace the shell with production-grade, fully exercised implementations.
