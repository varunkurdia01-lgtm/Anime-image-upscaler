---
name: Tactile Ceramic Studio
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#3f4850'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#707881'
  outline-variant: '#bfc7d2'
  surface-tint: '#006398'
  primary: '#006194'
  on-primary: '#ffffff'
  primary-container: '#007bb9'
  on-primary-container: '#fdfcff'
  inverse-primary: '#93ccff'
  secondary: '#00668a'
  on-secondary: '#ffffff'
  secondary-container: '#40c2fd'
  on-secondary-container: '#004d6a'
  tertiary: '#894d00'
  on-tertiary: '#ffffff'
  tertiary-container: '#ac6200'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#cce5ff'
  primary-fixed-dim: '#93ccff'
  on-primary-fixed: '#001d31'
  on-primary-fixed-variant: '#004b73'
  secondary-fixed: '#c4e7ff'
  secondary-fixed-dim: '#7bd0ff'
  on-secondary-fixed: '#001e2c'
  on-secondary-fixed-variant: '#004c69'
  tertiary-fixed: '#ffdcc0'
  tertiary-fixed-dim: '#ffb875'
  on-tertiary-fixed: '#2d1600'
  on-tertiary-fixed-variant: '#6b3b00'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display:
    fontFamily: Inter
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 22px
    letterSpacing: -0.005em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.25rem
  margin-mobile: 1rem
  space-2xs: 0.25rem
  space-xs: 0.5rem
  space-sm: 0.75rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 2.5rem
---

## Brand & Style

The visual identity embodies tactile precision, effortless technological power, and studio-grade craftsmanship tailored for high-performance AI mobile imaging. Designed to bridge hyper-modern computational photography with physical reassurance, the interface uses a restrained, high-fidelity soft ceramic aesthetic. It moves away from harsh skeuomorphism or flat sterility toward sculptural depth: molded panels, pill-shaped control chambers, diffused back-glows, and liquid-smooth touch feedback.

Targeted at creators, photographers, power users, and everyday mobile archivists, the emotional experience prioritizes tactile responsiveness, calm certainty, and visual focus. Interfaces recede seamlessly behind high-resolution imagery, framing visual assets within recessed trays and subtly extruded cards.

Design Movement: **Soft Neumorphism & Ceramic Tactility**. Every interactive surface feels sculpted out of solid, matte mineral material with dual-directional ambient light casting diffuse shadows and subtle specular rim highlights. Electric sky-blue accents deliver crisp visual hierarchy, signaling computational energy, progress, and active operational states.

## Colors

The palette establishes a soft ceramic atmosphere balanced by pure sky-blue energetic tokens. The canvas is founded on `#EBF0F5`, an engineered slate-tinted white that prevents visual glare while offering luminance for dual-source highlight and shadow modeling.

- **Primary (`#0284C7`) & Secondary Cyan (`#38BDF8`)**: Applied to high-intent actions, circular progress indicators, active segmented control capsules, and primary button gradients. They radiate a soft, controlled glow rather than a harsh flare.
- **Surface Foundations (`#F0F4F8` / `#EBF0F5`)**: Ceramic-like base tones that behave as physical extrusions and recessed control beds.
- **Recessed / Inset Wells (`#E2E8F0`)**: Used for inactive slider tracks, toggle depressions, and segmented control troughs.
- **Typography & Neutral Hierarchy**: Text defaults to deep mineral tones (`#0F172A` primary, `#475569` secondary, `#64748B` tertiary).
- **Dark Mode Correspondence**: In dark mode, the canvas pivots to a dense, deep charcoal slate (`#141C24` base, `#18222C` surfaces), while cyan highlights retain vibrant luminescence against dark recessed wells (`#0F151B`).

## Typography

Typography uses the neutral, systematic clarity of **Inter** across all display, body, and label roles. Its geometric proportions and structural legibility ground the soft-extrusion interface, preserving precision and preventing the visual soft-focus of neumorphism from becoming ambiguous.

- **Display & Headlines**: Tightly tracked, confident, and semi-bold/bold. Headings describe process state, scale factors, and primary actions clearly.
- **Labels & Numbers**: Used across processing percentages, image resolution specs (e.g., `1280 × 960 → 5120 × 3840`), and segment selectors. They use medium and semi-bold weights with subtle positive tracking for immediate legibility at a glance.
- **Body Copy**: Set with open line spacing to maximize readability against ceramic surfaces without requiring high contrast borders.

## Layout & Spacing

The layout model is tailored for ergonomic, single-thumb mobile interaction within an intuitive structural grid:

- **Grid Architecture**: Mobile viewports utilize a 4-column fluid layout with an inner margin of `1.25rem` (`20px`) and `0.75rem` (`12px`) gutters. For photo picker screens, a compact 3-column square grid is deployed with micro-gutters (`0.5rem`).
- **Vertical Rhythm**: Layouts follow an 8-point base module. Interactive wells, preview cards, and segmented tracks utilize `space-lg` (`24px`) bottom separation to maintain clear tactile distinction.
- **Padded Boundaries**: Card containers and control beds have an internal padding of `space-md` (`16px`) to `space-lg` (`24px`), ensuring tactile shadows have adequate room to fall without clipping neighboring UI components.

## Elevation & Depth

Visual hierarchy is communicated through directional ceramic modeling: elements are either **extruded upward** toward the user or **debossed/carved into** the base plane.

### Elevation Levels:
1. **Recessed Wells / Inset Plates (Level -1)**:
   - *Application*: Segmented control tracks, switch base slots, photo unselected borders, text input fields.
   - *Shadow Mechanics*: `box-shadow: inset 3px 3px 6px rgba(166, 180, 200, 0.35), inset -3px -3px 6px rgba(255, 255, 255, 0.9)`.
2. **Base Canvas (Level 0)**:
   - *Application*: The uninterrupted, non-interactive substrate background (`#EBF0F5`).
3. **Soft Extrusion Cards (Level 1)**:
   - *Application*: Setting tiles, photo thumbnails, media cards, bottom navigation dock.
   - *Shadow Mechanics*: Dual-source ambient dispersion.
     - Dark drop shadow: `6px 6px 16px rgba(166, 180, 200, 0.45)`.
     - Light specular highlight: `-6px -6px 14px rgba(255, 255, 255, 0.95)`.
4. **Active Tactile Elements (Level 2)**:
   - *Application*: Primary CTAs (`Enhance & Upscale`), floating action buttons, active segmented control capsules.
   - *Shadow Mechanics*: Soft ambient drop shadow paired with a localized cyan light emission (`0 6px 20px rgba(56, 189, 248, 0.35)`).

## Shapes

The design system employs a refined radius scale categorized under roundedness level **2** (`0.5rem` base, scaling to `1rem` for medium components and `1.5rem` for primary containers).

- **Standard Cards & Containers (`rounded-xl` / `1.5rem` / `24px`)**: Structural panels, image comparison viewports, modal upload drawers, and processing containers.
- **Pills & Selectors (`rounded-full` / `9999px`)**: Primary action buttons, segmented control buttons, category filter chips, comparison slider center handles, and input wells.
- **Grid Tiles (`rounded-lg` / `1rem` / `16px`)**: Media picker thumbnails, setting list rows, and sub-card previews.
- **Micro Radii (`rounded-sm` / `0.375rem` / `6px`)**: Tags, resolution badges, and status indicator dots.

## Components

### Buttons
- **Primary Button**: Pill-shaped with a linear gradient spanning `#38BDF8` (top) to `#0284C7` (bottom). Elevated with an outer cyan glow (`0 8px 24px rgba(2, 132, 199, 0.35)`) and a subtle inner specular top highlight (`inset 0 1px 1px rgba(255, 255, 255, 0.6)`). Label in white `label-lg` semi-bold.
- **Secondary / Ceramic Button**: Pill-shaped in `#F0F4F8` with standard Level 1 dual shadows. Pressed state triggers an inset shadow transition simulating physical depth depression.

### Segmented Controls
- **Track**: Pill-shaped recessed channel with an inset dual shadow.
- **Active Thumb**: Raised white/ceramic pill (or sky-blue fill) that slides above the recessed channel, cast with an elevated diffuse drop shadow (`0 2px 8px rgba(0, 0, 0, 0.08)`).
- **Segment Labels**: Medium `12px` font. Transitions from `#64748B` to `#0F172A` (or white when on blue) upon activation.

### Processing Rings & Progress Indicators
- Circular SVG progress meters set against an inset circular track.
- Active path highlighted with a gradient stroke (`#38BDF8` to `#22D3EE`), tipped with a soft, circular glow bead. Centered typographic readouts show scale milestones and percentage values.

### Before / After Image Comparison Slider
- Full-bleed media container with a vertical dividing hairline (`rgba(255, 255, 255, 0.8)`).
- Center handle features an extruded tactile circular knob (`36px` diameter) displaying dual horizontal arrows (`◀ ▶`), casting a directional shadow over both preview images. Pill chips with semi-transparent frosted backing demarcate "Before" and "After" states.

### Floating Bottom Navigation Bar
- Continuous sculpted bar floating `16px` above the bottom viewport edge.
- Features Level 1 dual-source neumorphic elevation. Active icons are indicated by primary cyan coloring and a faint ambient light bloom directly beneath the icon.

### Form Inputs & Toggles
- **Text Inputs**: Inset well styling with placeholder copy in `#94A3B8` and a right-aligned ceramic trailing icon.
- **Switches**: Recessed oval tracks with a ceramic thumb that moves horizontally, transitioning the track from neutral recessed slate to vibrant cyan upon activation.