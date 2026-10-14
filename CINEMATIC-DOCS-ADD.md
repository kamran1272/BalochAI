# Cinematic UI / Animation Pass — Mobile Baloch

Kamran's order: *"interface high level, advanced pro, full mazzy ka, animated"* —
Iron Man's lab, not a settings page. Functional first: animations never block
input or delay actions; beauty serves speed.

## Animation inventory

| Component | File | What moves |
|---|---|---|
| `BalochOrb` | `ui/components/OrbView.kt` | Hero orb, 4 crossfading states: IDLE (4s breathing + drifting motes), LISTENING (2 staggered sonar rings + mic-modulated waveform ring), THINKING (12 particles on 2 tilted elliptical orbits), SPEAKING (core scale + glow follow TTS level). Halo, dashed counter-rotating ring, arc segments, bright core. |
| `ScanlineOverlay` | `ui/components/ScanlineOverlay.kt` | 40 drifting cyan micro-particles (twinkle), static scanlines every 7px, one ~7s vertical beam sweep, top vignette. |
| `SplashScreen` | `ui/SplashScreen.kt` | ~2.6s cold-start boot: wireframe rings assemble (arcs + comet heads + tick marks), "BALOCH" glitch-in, "Systems online, sir." typewriter. Tap anywhere to skip. |
| `ChatBubble` | `ui/components/ChatComponents.kt` | Slide-up 24dp + fade on a bouncy spring; Baloch bubbles get a one-time cyan edge-sweep (~600ms). |
| `TypingIndicator` | `ui/components/ChatComponents.kt` | 3 staggered bouncing cyan dots while Baloch composes. |
| `AgentChipRow` / `AgentChipState` | `ui/components/ChatComponents.kt` | Live agent chips: spring scale+fade in, tint crossfade (amber pulse = working, cyan ✓ = done, red ✗ = failed). `AgentChipState(name, icon, state: Int)` is a UI-local copy of `agents.AgentChip` — the parent maps `ChipState` WORKING/DONE/FAILED → 0/1/2. |
| `ModeToggle` | `ui/components/ChatComponents.kt` | MANUAL ⟷ JARVIS glowing switch: track morphs dim steel → cyan glow, thumb slides on a bouncy spring, labels crossfade, haptic tick on toggle. |
| `Modifier.glowPress()` | `ui/components/ChatComponents.kt` | Cyan radial glow-ripple press state for IconButtons. Non-consuming press detection, so the wrapped tap still fires. |

## Performance rules followed

- **No per-frame allocation inside draw scopes.** Particle/mote seeds are
  precomputed with `remember`; positions are pure functions of the animation
  clock (wrap-around modulo), never mutated per frame. `Brush`/`Path` objects
  are hoisted (`remember(viewSize)`) and never built inside `drawWithContent`
  / `Canvas` lambdas. `Color.copy()`, `Offset()`, `Size()` are inline value
  classes — no heap allocation.
- **One shared `rememberInfiniteTransition` per component**, not one per animated
  value; staggered dots use `initialStartOffset` instead of extra transitions.
- **Modest particle counts**: 40 background particles, 14 orb motes, 12 orbit
  dots, 56 waveform dots (simple circles, no shadows/blur in draw scopes).
- **State changes crossfade** via animated weight floats (`animateFloatAsState`
  0→1 per state) instead of popping layers on/off.
- **Mic/TTS levels are smoothed** (`tween(120)` followers) so the orb never snaps.
- **Hooks are never conditional.** Where a value is only needed in one state,
  the animation hook is still created unconditionally and only the *read* is
  gated — changing hook order across recompositions would crash.
- Animations are decorative only: `SplashScreen` is skippable by tap, chat input
  and buttons stay interactive while everything animates.

## Reduced-motion behaviour

Every component checks `isReducedMotion(context)` (`ui/components/OrbView.kt`):
`AccessibilityManager.isEnabled` **or** `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.

When reduced: the orb renders its current state statically (no breathing, spin,
sonar, orbits, or drift); the overlay keeps scanlines + vignette but drops the
beam sweep and freezes particles; typing dots and chip pulses render at rest;
the splash shows its finished frame and exits after ~700ms. One-shot entrance
animations (bubble slide, edge sweep) still play once — they carry information,
not loop.
