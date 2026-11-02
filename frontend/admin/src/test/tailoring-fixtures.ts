import type {
  Alteration,
  AlterationPage,
  Client,
  ClientPage,
  ClientSummary,
  Order,
  OrderPage,
  ServiceProduct,
} from "@vaabhi/api-client";
import { http, HttpResponse } from "msw";
import { API, server } from "./server";
import { serveSettings, stages } from "./settings-fixtures";

export const client: Client = {
  id: 7,
  phone: "+919876500001",
  firstName: "Meera",
  lastName: "Iyer",
  gender: "FEMALE",
  photoKey: null,
  email: null,
  altPhone: null,
  whatsapp: "+919876500002",
  address: "12 MG Road",
  city: "Vijayawada",
  state: "Andhra Pradesh",
  pinCode: "520001",
  clientType: "VIP",
  gstin: null,
  preferredStyle: "Boat necks",
  birthDate: null,
  occasionName: "Wedding",
  occasionDate: "2027-01-20",
  createdAt: "2026-10-01T10:00:00+05:30",
  orderCount: 1,
  spentPaise: 99000,
  outstandingPaise: 69000,
  measurements: [
    {
      id: 3,
      templateId: 1,
      garment: "Blouse",
      values: { bust: "34", waist: "28" },
      notes: "Loose fit",
      takenBy: "Asha Rao",
      takenAt: "2026-10-01T10:05:00+05:30",
    },
  ],
};

export const clientSummary: ClientSummary = {
  id: 7,
  name: "Meera Iyer",
  phone: "+919876500001",
  photoKey: null,
  clientType: "VIP",
  orderCount: 1,
  spentPaise: 99000,
  outstandingPaise: 69000,
  lastOrderDate: "2026-11-01",
};

export const order: Order = {
  id: 42,
  number: "ORD-2042",
  client: clientSummary,
  status: "BOOKED",
  orderDate: "2026-11-01",
  deliveryDate: "2026-11-08",
  dueTime: null,
  trialDate: null,
  deliveryPackageId: null,
  deliveryChargePaise: 0,
  itemsPaise: 110000,
  discountKind: "PERCENT",
  discountValue: 1000,
  discountPaise: 11000,
  couponCode: null,
  totalPaise: 99000,
  paidPaise: 30000,
  balancePaise: 69000,
  notes: "Wedding on the 20th",
  createdBy: "Asha Rao",
  createdAt: "2026-11-01T11:00:00+05:30",
  deliveredAt: null,
  items: [
    {
      id: 101,
      templateId: 1,
      serviceProductId: 11,
      description: "3 dart blouse",
      quantity: 2,
      pricePaise: 50000,
      colour: "Maroon",
      note: null,
      measurements: { bust: "34" },
      clothPhotoKey: null,
      stage: stages[0]!,
      stageChangedAt: "2026-11-01T11:00:00+05:30",
    },
  ],
  payments: [
    {
      id: 1,
      amountPaise: 30000,
      mode: "UPI",
      kind: "ADVANCE",
      note: null,
      receivedBy: "Asha Rao",
      receivedAt: "2026-11-01T11:00:00+05:30",
    },
  ],
};

export const orderPage: OrderPage = {
  items: [
    {
      id: 42,
      number: "ORD-2042",
      clientId: 7,
      clientName: "Meera Iyer",
      clientPhone: "+919876500001",
      status: "BOOKED",
      orderDate: "2026-11-01",
      deliveryDate: "2026-11-08",
      trialDate: null,
      itemCount: 2,
      itemsLabel: "3 dart blouse",
      totalPaise: 99000,
      balancePaise: 69000,
      overdue: false,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

export const clientPage: ClientPage = { items: [clientSummary], total: 1, page: 1, pageSize: 20 };

export const alteration: Alteration = {
  id: 9,
  number: "ALT-109",
  client: clientSummary,
  orderId: null,
  orderNumber: null,
  status: "READY",
  readyBy: "2026-11-04",
  paid: true,
  totalPaise: 40000,
  collectedPaise: 0,
  balancePaise: 40000,
  notes: null,
  undertaking: "I understand that alterations are best-effort and the final fit is not guaranteed.",
  createdBy: "Asha Rao",
  createdAt: "2026-11-02T10:00:00+05:30",
  deliveredAt: null,
  items: [
    {
      id: 1,
      kind: "LENGTH",
      garment: "Silk saree blouse",
      instructions: "Shorten 1 inch",
      photoKey: null,
      costPaise: 40000,
    },
  ],
};

export const alterationPage: AlterationPage = {
  items: [
    {
      id: 9,
      number: "ALT-109",
      clientId: 7,
      clientName: "Meera Iyer",
      clientPhone: "+919876500001",
      status: "READY",
      readyBy: "2026-11-04",
      itemCount: 1,
      itemsLabel: "Silk saree blouse",
      paid: true,
      totalPaise: 40000,
      balancePaise: 40000,
      overdue: false,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

export const serviceProducts: ServiceProduct[] = [
  {
    id: 11,
    templateId: 1,
    garment: "Blouse",
    code: "BL-002",
    name: "3 dart blouse",
    pricePaise: 50000,
    photoKey: null,
    stageCosts: [{ stageId: 2, paise: 15000 }],
    profitPaise: 35000,
  },
];

export type Sent = { method: string; path: string; query: string; body: unknown };

/**
 * Serves the customer, order, alteration and price-list endpoints (plus every settings endpoint) from the
 * fixtures above. Every request is recorded in `sent`; writes answer with the matching fixture.
 */
export function serveTailoring(): { sent: Sent[] } {
  serveSettings();
  const sent: Sent[] = [];
  const record = async (request: Request) => {
    const url = new URL(request.url);
    const text = request.method === "GET" ? "" : await request.text();
    sent.push({
      method: request.method,
      path: url.pathname,
      query: url.search,
      body: text ? JSON.parse(text) : undefined,
    });
  };
  const reply =
    (value: unknown, status = 200) =>
    async ({ request }: { request: Request }) => {
      await record(request);
      return HttpResponse.json(value as never, { status });
    };
  const base = `${API}/api/v1/admin`;
  server.use(
    http.get(`${base}/clients`, reply(clientPage)),
    http.post(`${base}/clients`, reply(client, 201)),
    http.get(`${base}/clients/:id`, reply(client)),
    http.put(`${base}/clients/:id`, reply(client)),
    http.post(`${base}/clients/:id/measurements`, reply(client.measurements[0], 201)),
    http.get(`${base}/orders`, reply(orderPage)),
    http.post(`${base}/orders`, reply(order, 201)),
    http.get(`${base}/orders/:id`, reply(order)),
    http.post(`${base}/orders/:id/stage`, reply({ ...order, status: "IN_PROGRESS" })),
    http.post(`${base}/orders/:id/deliver`, reply({ ...order, status: "DELIVERED" })),
    http.post(`${base}/orders/:id/cancel`, reply({ ...order, status: "CANCELLED" })),
    http.put(`${base}/orders/:id/schedule`, reply(order)),
    http.post(`${base}/orders/:id/payments`, reply({ ...order, paidPaise: 99000, balancePaise: 0 }, 201)),
    http.get(`${base}/service-products`, reply(serviceProducts)),
    http.post(`${base}/service-products`, reply(serviceProducts[0], 201)),
    http.put(`${base}/service-products/:id`, reply(serviceProducts[0])),
    http.delete(`${base}/service-products/:id`, async ({ request }) => {
      await record(request);
      return new HttpResponse(null, { status: 204 });
    }),
    http.get(`${base}/alterations`, reply(alterationPage)),
    http.post(`${base}/alterations`, reply(alteration, 201)),
    http.get(`${base}/alterations/:id`, reply(alteration)),
    http.post(`${base}/alterations/:id/progress`, reply({ ...alteration, status: "DELIVERED", balancePaise: 0 })),
  );
  return { sent };
}

/** The last request to `path` with `method`. */
export function lastSent(sent: Sent[], method: string, path: string): Sent | undefined {
  return sent.filter((s) => s.method === method && s.path === `/api/v1/admin${path}`).at(-1);
}
