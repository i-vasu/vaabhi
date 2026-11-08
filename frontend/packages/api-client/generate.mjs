// Fetches the running backend's OpenAPI spec into openapi.json, then generates src/schema.d.ts from it.
// Plain Node so it works the same in cmd, PowerShell and bash. OPENAPI_URL overrides the local backend.
import { execSync } from "node:child_process";
import { writeFileSync } from "node:fs";

const url = process.env.OPENAPI_URL || "http://localhost:8080/v3/api-docs";

let response;
try {
  response = await fetch(url);
} catch (error) {
  console.error(`Could not reach ${url}. Is the backend running?\n${error.cause?.message ?? error.message}`);
  process.exit(1);
}
if (!response.ok) {
  console.error(`${url} answered ${response.status} ${response.statusText}`);
  process.exit(1);
}
// Pretty-printed like the committed file; numbers keep their source text so "2.0" stays "2.0" (not "2").
const spec = JSON.parse(await response.text(), (_key, value, context) =>
  typeof value === "number" ? JSON.rawJSON(context.source) : value,
);
writeFileSync("openapi.json", JSON.stringify(spec, null, 4) + "\n");
console.log(`Saved openapi.json from ${url}`);

execSync("pnpm exec openapi-typescript openapi.json -o src/schema.d.ts", { stdio: "inherit" });
