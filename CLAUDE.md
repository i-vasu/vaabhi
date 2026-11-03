# Working on Vaabhi

These rules apply to every agent and subagent working in this repository.

## Build the happy path first

- Focus on making the real end-to-end flow work: admin action → API → database/storage → storefront.
- Fix whatever blocks that flow (bugs, stale config, missing wiring) as part of the task. Fix only what is
  needed for the flow to work.
- Leave edge cases, polish and refactors unless they block the happy path. Mention them at the end instead.

## No hardcoded demo data

- Never hardcode products, prices, banners or other content in the frontend. Load it from the API.
- If the API has no endpoint for something a page needs, add the endpoint.
- If there is no data yet, show an honest empty state. Do not show placeholder items.

## Do not run tests

- Do not run test suites, integration tests, end-to-end tests, coverage or `verify` builds. The owner tests
  the end-to-end flow themselves.
- Typecheck and lint are allowed, because they are quick checks that the code compiles.
- You may add or update tests alongside a change when it is cheap, but do not run them.
- At the end, list what the owner should check by hand to confirm the flow works.
