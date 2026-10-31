import { createApiClient } from "@vaabhi/api-client";

/** Server-side API client. API_URL is the internal address (http://backend:8080 in Docker). */
export const api = createApiClient({
  // `||` not `??`: an empty build-time value must fall back too.
  baseUrl: process.env.API_URL || process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
});
