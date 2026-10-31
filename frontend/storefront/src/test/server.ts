import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

export const API = "http://localhost:8080";

export const categories = [
  { id: 1, slug: "dresses", name: "Dresses" },
  { id: 2, slug: "tops", name: "Tops" },
];

export const server = setupServer(http.get(`${API}/api/v1/store/categories`, () => HttpResponse.json(categories)));
