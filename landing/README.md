# Carda landing page

Informational Next.js static site for the Carda research prototype. Run `npm ci`, `npm run typecheck`, and `npm run build` in this directory. The static output is `out/`.

This is **not a published site or final privacy policy**. Before publication, the team must approve a real contact channel, legal controller/retention details, privacy wording, validated compatibility list, official download destination, and final Figma visual pass. Product claims must be checked against `docs/engineering/requirements-matrix.md` and evidence for the candidate build. No public APK link is invented.

## Local browser smoke

`tests/browser_smoke.py` checks home/privacy navigation, viewport overflow, visible keyboard focus and page errors at desktop/mobile widths using Python Playwright. Run it against the static export with the `webapp-testing` skill server helper or your local HTTP server. Pass `--output /private/tmp/carda-browser-report`; optional `--browser-executable` selects an installed local Chromium. Screenshots and reports stay outside Git. This does not establish full screen-reader accessibility or participant usability.
