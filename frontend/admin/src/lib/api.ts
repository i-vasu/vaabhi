import { createApiClient } from "@vaabhi/api-client";
import { getToken } from "./auth";

export const api = createApiClient({
  baseUrl: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",
  getToken,
});
