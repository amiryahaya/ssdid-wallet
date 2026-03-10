# SSDID Landing Page — Design Spec

## Overview

Single-page landing site for SSDID (Self-Sovereign Distributed Identity) wallet. Drives app downloads while communicating the product's value to both end users and developers/enterprises.

## Tech Stack

- **Single `index.html`** — inline CSS, minimal vanilla JS
- No build step, no dependencies, no framework
- Deploy target: GitHub Pages, Netlify, or any static host

## Visual Design

- **Direction:** Dark & premium with subtle glow (Direction A)
- **Background:** `#0A0C10` base with soft radial `rgba(74,158,255,0.12)` glows
- **Accent:** `#4A9EFF` (blue) — buttons, highlights, section labels
- **Typography:** DM Sans (Google Fonts) — weights 400, 500, 600, 700
- **Cards:** Glass-morphism style — `rgba(255,255,255,0.03)` bg, `rgba(255,255,255,0.06)` border, `border-radius: 12px`
- **Logo:** Existing `ic_launcher_playstore_512.png` hexagonal badge, resized for web (favicon 32px, nav 36px, hero 80px)

## Page Sections

### 1. Navigation Bar
- Fixed/sticky top
- Left: Logo (36px) + "SSDID" wordmark
- Right: "Features" | "How It Works" | Download button (blue)
- Transparent over hero, transitions to solid `#0A0C10` bg on scroll
- Mobile: hamburger menu

### 2. Hero Section
- Full viewport height (`100vh`)
- Centered layout: logo → headline → subtitle → CTA buttons
- Headline: "Your Identity. Your Control."
- Subtitle: "A self-sovereign identity wallet with post-quantum cryptography. Own your digital identity — no middlemen, no compromises."
- CTAs: "Google Play" (primary blue) + "Learn More" (outline, scrolls to features)
- Background: subtle radial glow effects (top-right, bottom-left)

### 3. Features Grid
- Section label: "FEATURES" (small, blue, uppercase, tracked)
- Heading: "Why SSDID?"
- 6 cards in 3x2 grid (2-col tablet, 1-col mobile):

| Card | Icon | Title | Description |
|------|------|-------|-------------|
| 1 | Shield/lock | Post-Quantum Security | KAZ-Sign algorithms protect against future quantum threats |
| 2 | Person | Self-Sovereign | You own your keys and data. No third party controls your identity |
| 3 | Key | Hardware-Backed | Keys stored in TEE/StrongBox. Biometric unlock for every action |
| 4 | Document | Verifiable Credentials | W3C standard credentials you can present anywhere, instantly |
| 5 | Rotate | Recovery Options | Three-tier recovery: recovery keys, Shamir's Secret Sharing, institutional backup |
| 6 | Globe | W3C DID Compliant | Built on open standards. Interoperable with the decentralized identity ecosystem |

- Cards animate in via intersection observer (fade-up on scroll)

### 4. How It Works
- Section label: "HOW IT WORKS"
- Heading: "Get Started in Minutes"
- 3 numbered steps, horizontal layout (vertical on mobile):
  1. **Download** — Get SSDID Wallet from Google Play
  2. **Create Identity** — Generate your DID with biometric protection
  3. **Use Everywhere** — Authenticate, sign, and present credentials
- Numbered circles with blue accent border
- Subtle background gradient shift to differentiate from features

### 5. Download CTA
- Heading: "Ready to Own Your Identity?"
- Subtitle: "Download SSDID Wallet and take control of your digital identity today."
- Single large CTA: "Download on Google Play"
- Platform note: "Available on Android · iOS & HarmonyOS coming soon"
- Centered radial glow behind

### 6. Footer
- Darker shade: `#060810`
- Top border: `rgba(255,255,255,0.06)`
- Left: "© 2026 SSDID. All rights reserved."
- Right: Privacy | Terms | GitHub links

## Responsive Breakpoints

- **Desktop:** ≥1024px — full 3-column grid, horizontal steps
- **Tablet:** 768–1023px — 2-column grid, horizontal steps
- **Mobile:** <768px — 1-column grid, vertical steps, hamburger nav

## Interactions

- Smooth scroll on nav link clicks
- Intersection observer: cards fade-up on scroll into view
- Nav background transition: transparent → solid on scroll past hero
- Mobile hamburger toggle (pure CSS or minimal JS)

## Assets Required

- `ic_launcher_playstore_512.png` → resize to favicon.ico (32px), logo-nav (36px), logo-hero (80px)
- Google Play badge SVG (standard asset)
- DM Sans font (Google Fonts CDN link)

## File Structure

```
ssdid-landing-page/
├── index.html          # Single-page landing (all CSS/JS inline)
├── assets/
│   ├── logo-512.png    # Full-size logo
│   ├── logo-80.png     # Hero logo
│   ├── logo-36.png     # Nav logo
│   └── favicon.png     # 32px favicon
└── docs/               # Design docs
```

## Out of Scope

- Multi-page site
- CMS or blog
- Developer documentation portal
- Analytics (can be added later as a one-liner)
- Contact form
