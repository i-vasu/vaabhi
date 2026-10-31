import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

export const API = "http://localhost:8080";

export const server = setupServer(
  http.get(`${API}/actuator/health`, () => HttpResponse.json({ status: "UP" })),
  http.get(`${API}/api/v1/store/categories`, () =>
    HttpResponse.json([
      { id: 1, slug: "dresses", name: "Dresses" },
      { id: 2, slug: "tops", name: "Tops" },
      { id: 3, slug: "ethnic-wear", name: "Ethnic Wear" },
    ]),
  ),
);
