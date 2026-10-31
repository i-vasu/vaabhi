import { createApiClient } from "@vaabhi/api-client";
import { getToken } from "./auth";

export const api = createApiClient({
  // `||` not `??`: an empty build-time value must fall back too.
  baseUrl: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  getToken,
});
