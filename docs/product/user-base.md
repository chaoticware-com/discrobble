# User Base

## Primary Persona

### The Meticulous Collector

- Owns a sizable vinyl collection and already maintains it in Discogs.
- Uses Last.fm regularly and notices bad metadata or missing scrobbles immediately.
- Accepts a little session setup if it results in cleaner history.
- Understands album sides, discs, reissues, and pressing differences.
- Is comfortable granting microphone and camera permissions if the value is obvious.

## Secondary Persona

### The Guided Listener

- Has a smaller or less perfectly maintained Discogs library.
- Wants a faster route than typing tracks manually, but may not know exact pressings.
- Will use barcode scan or search first, and only occasionally correct side or track matches.
- Prefers clear prompts and recoverable errors over advanced configuration.

## Shared Motivations

- Preserve a complete listening history on Last.fm, including vinyl sessions.
- Avoid manual track entry and timestamp bookkeeping.
- Use a physical collection as the source of truth instead of guessing from generic album data.
- Keep personal listening data out of a custom cloud backend.

## Shared Pain Points

- Turntables do not expose digital playback events.
- Vinyl releases often differ by pressing, side layout, track order, and edition metadata.
- Barcode search is helpful but incomplete.
- Recognition alone can be wrong when different releases share tracks or when songs are hard to identify.
- Existing scrobble tools usually assume streaming or local digital files.

## Expected Behaviors

- Users will start with records they already have in Discogs.
- Many sessions will begin from the collection picker, not from pure audio recognition.
- Users will tolerate manual confirmation for uncertain matches if strong matches proceed automatically.
- Users will accept a foreground-first session as long as retries and corrections are reliable.

## Adoption Assumptions

- First adopters are already motivated enough to connect both integrations during onboarding.
- The MVP does not need to persuade users to start using Discogs or Last.fm for the first time.
- A lightweight library browser is sufficient initially because power users usually know what they are about to play.
- Reliability and metadata cleanliness matter more than aggressive automation for this audience.

## Product Implications

- Onboarding can be opinionated: Last.fm and Discogs are required, not optional.
- The UI should optimize for quick release selection, track confirmation, and retry visibility rather than generic music discovery.
- Manual correction is part of the intended product experience, not just an edge-case escape hatch.
- Privacy messaging should be explicit and credible because avoiding a personal backend is part of the product value.

