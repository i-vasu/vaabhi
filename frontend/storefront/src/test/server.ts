import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

export const API = "http://localhost:8080";

export const categories = [
  { id: 1, slug: "sarees", name: "Sarees" },
  { id: 2, slug: "lehengas", name: "Lehengas" },
  { id: 3, slug: "unknown-new-category", name: "Dupattas" },
];

export const server = setupServer(
  http.get(`${API}/api/v1/store/categories`, () => HttpResponse.json(categories)),
  // The header checks the session on every page; tests are signed out unless they say otherwise.
  http.get("*/api/auth/session", () => HttpResponse.json({ customer: null })),
);
