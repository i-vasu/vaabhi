import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

export const API = "http://localhost:8080";

export const server = setupServer(
  http.get(`${API}/actuator/health`, () => HttpResponse.json({ status: "UP" })),
  http.get(`${API}/api/v1/store/categories`, () =>
    HttpResponse.json([
      { id: 1, slug: "sarees", name: "Sarees", description: null },
      { id: 2, slug: "lehengas", name: "Lehengas", description: null },
      { id: 3, slug: "kurta-sets", name: "Kurta sets", description: null },
    ]),
  ),
);
