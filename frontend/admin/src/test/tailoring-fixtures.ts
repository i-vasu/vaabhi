import type {
  Alteration,
  ExpenseList,
  Invoice,
  InvoicePage,
  Ledger,
  Outstanding,
  BoardColumn,
  WorkItem,
  WorkshopOverview,
  WorkshopStaff,
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
import { serveExtras } from "./extras-fixtures";
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
      assignedTo: null,
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

export const workItem: WorkItem = {
  itemId: 101,
  orderId: 42,
  orderNumber: "ORD-2042",
  clientId: 7,
  clientName: "Meera Iyer",
  description: "3 dart blouse",
  quantity: 2,
  colour: "Maroon",
  garment: "Blouse",
  clothPhotoKey: null,
  stage: stages[0]!,
  stageSince: "2026-11-01T11:00:00+05:30",
  deliveryDate: "2026-10-30",
  trialDate: null,
  dueToday: false,
  overdue: true,
  assignedTo: null,
};

export const board: BoardColumn[] = [
  { stage: stages[0]!, removed: false, items: [workItem] },
  { stage: stages[1]!, removed: false, items: [] },
  { stage: stages[2]!, removed: false, items: [] },
  { stage: stages[3]!, removed: false, items: [] },
];

export const workshopStaff: WorkshopStaff[] = [
  { id: 1, name: "Asha Rao", role: "OWNER", stageIds: [], workingOn: 0 },
  { id: 2, name: "Sita Cutter", role: "STAFF", stageIds: [1], workingOn: 3 },
  { id: 3, name: "Ravi Tailor", role: "STAFF", stageIds: [2], workingOn: 1 },
];

export const overview: WorkshopOverview = {
  inProduction: 6,
  unassigned: 2,
  readyItems: 1,
  dueToday: 2,
  overdue: 1,
  trialsToday: 0,
  stageLoad: [
    { stage: stages[0]!, items: 4, overdue: 2 },
    { stage: stages[1]!, items: 2, overdue: 0 },
  ],
  staffLoad: workshopStaff,
};

export const invoice: Invoice = {
  id: 5,
  number: "INV-305",
  orderId: 42,
  orderNumber: "ORD-2042",
  client: clientSummary,
  issuedOn: "2026-11-02",
  lines: [
    { description: "3 dart blouse (Maroon)", quantity: 2, unitPaise: 50000, amountPaise: 100000 },
    { description: "Fall & pico", quantity: 1, unitPaise: 10000, amountPaise: 10000 },
  ],
  discountPaise: 11000,
  orderTotalPaise: 99000,
  gstRate: 5,
  pricesIncludeTax: true,
  taxablePaise: 94286,
  cgstPaise: 2357,
  sgstPaise: 2357,
  totalPaise: 99000,
  paidPaise: 30000,
  balancePaise: 69000,
  shopGstin: "27ABCDE1234F1Z5",
  clientGstin: null,
  createdBy: "Asha Rao",
  createdAt: "2026-11-02T12:00:00+05:30",
  cancelledAt: null,
  cancelledBy: null,
};

export const invoicePage: InvoicePage = {
  items: [
    {
      id: 5,
      number: "INV-305",
      orderId: 42,
      orderNumber: "ORD-2042",
      clientId: 7,
      clientName: "Meera Iyer",
      issuedOn: "2026-11-02",
      taxablePaise: 94286,
      taxPaise: 4714,
      totalPaise: 99000,
      balancePaise: 69000,
      cancelled: false,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
  totalPaise: 99000,
  taxPaise: 4714,
};

export const expenseList: ExpenseList = {
  items: [
    {
      id: 3,
      spentOn: "2026-11-01",
      category: "RENT",
      description: "Shop rent",
      amountPaise: 1500000,
      mode: "BANK",
      paidTo: "Landlord",
      createdBy: "Asha Rao",
      createdAt: "2026-11-01T10:00:00+05:30",
    },
  ],
  totalPaise: 1500000,
  byCategory: [{ category: "RENT", amountPaise: 1500000 }],
};

export const ledger: Ledger = {
  from: "2026-11-01",
  to: "2026-11-30",
  openingPaise: 500000,
  inPaise: 30000,
  outPaise: 1505000,
  closingPaise: -975000,
  byMode: [
    { mode: "UPI", inPaise: 30000, outPaise: 5000 },
    { mode: "BANK", inPaise: 0, outPaise: 1500000 },
  ],
  entries: [
    {
      at: "2026-11-01T10:00:00+05:30",
      kind: "EXPENSE",
      sourceId: 3,
      reference: "Shop rent",
      party: "Landlord",
      mode: "BANK",
      amountPaise: -1500000,
      balancePaise: -1000000,
    },
    {
      at: "2026-11-01T11:00:00+05:30",
      kind: "ORDER_PAYMENT",
      sourceId: 42,
      reference: "ORD-2042",
      party: "Meera Iyer",
      mode: "UPI",
      amountPaise: 30000,
      balancePaise: -970000,
    },
    {
      at: "2026-11-02T09:00:00+05:30",
      kind: "REFUND",
      sourceId: 42,
      reference: "ORD-2042",
      party: "Meera Iyer",
      mode: "UPI",
      amountPaise: -5000,
      balancePaise: -975000,
    },
  ],
};

export const outstanding: Outstanding = {
  totalPaise: 109000,
  handedOverPaise: 40000,
  items: [
    {
      kind: "ALTERATION",
      id: 9,
      number: "ALT-109",
      clientId: 7,
      clientName: "Meera Iyer",
      clientPhone: "+919876500001",
      date: "2026-10-28",
      totalPaise: 40000,
      paidPaise: 0,
      duePaise: 40000,
      daysLate: 5,
      handedOver: true,
      invoiceNumber: null,
    },
    {
      kind: "ORDER",
      id: 42,
      number: "ORD-2042",
      clientId: 7,
      clientName: "Meera Iyer",
      clientPhone: "+919876500001",
      date: "2026-11-08",
      totalPaise: 99000,
      paidPaise: 30000,
      duePaise: 69000,
      daysLate: 0,
      handedOver: false,
      invoiceNumber: "INV-305",
    },
  ],
};

export type Sent = { method: string; path: string; query: string; body: unknown };

/**
 * Serves the customer, order, alteration and price-list endpoints (plus every settings endpoint) from the
 * fixtures above. Every request is recorded in `sent`; writes answer with the matching fixture.
 */
export function serveTailoring(): { sent: Sent[] } {
  serveSettings();
  const sent: Sent[] = [];
  serveExtras(sent);
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
    http.get(`${base}/money/invoices`, reply(invoicePage)),
    http.get(`${base}/money/invoices/:id`, reply(invoice)),
    http.post(
      `${base}/money/invoices/:id/cancel`,
      reply({ ...invoice, cancelledAt: "2026-11-02T13:00:00+05:30", cancelledBy: "Asha Rao" }),
    ),
    http.get(`${base}/money/orders/:id/invoice`, async ({ request }) => {
      await record(request);
      return new HttpResponse(null, { status: 204 });
    }),
    http.post(`${base}/money/orders/:id/invoice`, reply(invoice, 201)),
    http.get(`${base}/money/expenses`, reply(expenseList)),
    http.post(`${base}/money/expenses`, reply(expenseList.items[0], 201)),
    http.put(`${base}/money/expenses/:id`, reply(expenseList.items[0])),
    http.delete(`${base}/money/expenses/:id`, async ({ request }) => {
      await record(request);
      return new HttpResponse(null, { status: 204 });
    }),
    http.get(`${base}/money/ledger`, reply(ledger)),
    http.get(`${base}/money/outstanding`, reply(outstanding)),
    http.get(`${base}/workshop/overview`, reply(overview)),
    http.get(`${base}/workshop/board`, reply(board)),
    http.put(`${base}/workshop/items/:id/assignee`, reply({ ...workItem, assignedTo: { id: 2, name: "Sita Cutter" } })),
    http.post(
      `${base}/workshop/items/move`,
      reply([
        { ...board[0], items: [] },
        { ...board[1], items: [{ ...workItem, stage: stages[1] }] },
        board[2],
        board[3],
      ]),
    ),
    http.get(`${base}/workshop/staff`, reply(workshopStaff)),
    http.put(`${base}/workshop/staff/:id/stages`, reply({ ...workshopStaff[1], stageIds: [1, 2] })),
  );
  return { sent };
}

/** The last request to `path` with `method`. */
export function lastSent(sent: Sent[], method: string, path: string): Sent | undefined {
  return sent.filter((s) => s.method === method && s.path === `/api/v1/admin${path}`).at(-1);
}
