import type {
  ActivityPage,
  Coupon,
  GalleryPhoto,
  Message,
  Notifications,
  Report,
  ReportInfo,
  ReportsOverview,
  RewardAccount,
  StockItem,
  StockMovement,
} from "@vaabhi/api-client";
import { http, HttpResponse } from "msw";
import { API, server } from "./server";
import type { Sent } from "./tailoring-fixtures";

export const lining: StockItem = {
  id: 31,
  name: "Silk lining",
  sku: "LN-01",
  category: "Lining",
  unit: "M",
  reorderLevel: 5,
  costRupees: 120,
  onHand: 3.5,
  low: true,
  valueRupees: 420,
  supplierName: "Sri Lakshmi Silks",
  supplierPhone: "98450 12345",
  material: "Silk",
  colour: null,
  gsm: 60,
  threadCount: null,
  widthIn: 44,
  notes: null,
  photos: [],
};

export const movement: StockMovement = {
  id: 1,
  itemId: 31,
  itemName: "Silk lining",
  kind: "OUT",
  quantity: -16.5,
  balance: 3.5,
  orderId: 42,
  orderNumber: "ORD-2042",
  note: "Blouse lining",
  movedBy: "Asha Rao",
  movedAt: "2026-11-02T10:00:00+05:30",
};

export const coupon: Coupon = {
  id: 4,
  code: "DIWALI10",
  description: "Festival",
  kind: "PERCENT",
  value: 10,
  minOrderRupees: 500,
  validFrom: null,
  validTo: "2026-11-15",
  maxUses: 100,
  active: true,
  uses: 3,
};

export const rewards: RewardAccount = {
  clientId: 7,
  balance: 24,
  valueRupees: 24,
  entries: [
    {
      id: 1,
      kind: "EARNED",
      points: 24,
      orderId: 42,
      orderNumber: "ORD-2042",
      note: null,
      createdBy: null,
      createdAt: "2026-11-01T10:00:00+05:30",
    },
  ],
};

export const message: Message = {
  id: 8,
  channel: "WHATSAPP",
  purpose: "ORDER_READY",
  clientId: 7,
  clientName: "Meera Iyer",
  orderId: 42,
  orderNumber: "ORD-2042",
  toPhone: "+919876500002",
  body: "Hi Meera, your order ORD-2042 is ready for pickup at vaabhi. Balance due: ₹690.",
  status: "LINK",
  link: "https://wa.me/919876500002?text=Hi%20Meera",
  error: null,
  createdBy: "Asha Rao",
  createdAt: "2026-11-02T10:00:00+05:30",
};

export const photo: GalleryPhoto = {
  id: 5,
  kind: "DESIGN",
  title: "Boat neck",
  tags: "boat neck, silk",
  templateId: 1,
  garment: "Blouse",
  clientId: null,
  clientName: null,
  orderId: null,
  photoKey: "gallery/abc",
  createdBy: "Asha Rao",
  createdAt: "2026-11-02T10:00:00+05:30",
};

export const activity: ActivityPage = {
  items: [
    {
      id: 2,
      at: "2026-11-02T11:00:00+05:30",
      kind: "PAYMENT_RECEIVED",
      title: "₹300 received for ORD-2042 from Meera Iyer",
      actor: "Asha Rao",
      refType: "ORDER",
      refId: 42,
    },
    {
      id: 1,
      at: "2026-11-01T11:00:00+05:30",
      kind: "SETTINGS_CHANGED",
      title: "Updated billing and tax",
      actor: "Asha Rao",
      refType: "SETTINGS",
      refId: null,
    },
  ],
  total: 2,
  page: 1,
  pageSize: 50,
};

export const reportList: ReportInfo[] = [
  { name: "sales", title: "Sales", description: "Invoiced per day: taxable value, GST and total" },
  { name: "gst", title: "GST register", description: "Every invoice with GSTIN" },
];

export const reportsOverview: ReportsOverview = {
  bookedRupees: 2500,
  collectedRupees: 1800,
  expensesRupees: 400,
  ordersBooked: 4,
  ordersDelivered: 2,
  averageOrderRupees: 625,
  newCustomers: 3,
  outstandingRupees: 700,
  months: ["2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"].map((month, i) => ({
    month,
    bookedRupees: 1000 * i,
    collectedRupees: 800 * i,
    expensesRupees: 200 * i,
  })),
  ageing: [
    { label: "Not due yet", count: 1, rupees: 500 },
    { label: "1–7 days late", count: 1, rupees: 200 },
    { label: "8–30 days late", count: 0, rupees: 0 },
    { label: "31–60 days late", count: 0, rupees: 0 },
    { label: "Over 60 days late", count: 0, rupees: 0 },
  ],
};

export const salesReport: Report = {
  name: "sales",
  title: "Sales",
  from: "2026-11-01",
  to: "2026-11-02",
  columns: [
    { key: "day", label: "Day", type: "DATE" },
    { key: "invoices", label: "Invoices", type: "NUMBER" },
    { key: "total", label: "Total", type: "MONEY" },
  ],
  rows: [
    { day: "2026-11-01", invoices: 2, total: 1500 },
    { day: "2026-11-02", invoices: 1, total: 990 },
  ],
  totals: { invoices: 3, total: 2490 },
  note: "Cancelled invoices are left out.",
};

export const notifications: Notifications = {
  alerts: [{ kind: "LOW_STOCK", title: "1 stock items running low", count: 1, href: "/stock/?low=1" }],
  recent: [activity.items[0]!],
  unread: 1,
};

/** Serves the stock, engage, gallery and activity endpoints, recording every request in `sent`. */
export function serveExtras(sent: Sent[], account: RewardAccount = rewards): void {
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
      return value === null ? new HttpResponse(null, { status: 204 }) : HttpResponse.json(value as never, { status });
    };
  const base = `${API}/api/v1/admin`;
  server.use(
    http.get(`${base}/stock`, reply([lining])),
    http.post(`${base}/stock`, reply(lining, 201)),
    http.get(`${base}/stock/:id`, reply(lining)),
    http.put(`${base}/stock/:id`, reply(lining)),
    http.delete(`${base}/stock/:id`, reply(null)),
    http.get(`${base}/stock/:id/movements`, reply([movement])),
    http.post(`${base}/stock/:id/movements`, reply(movement, 201)),
    http.get(`${base}/engage/coupons`, reply([coupon])),
    http.post(`${base}/engage/coupons`, reply(coupon, 201)),
    http.put(`${base}/engage/coupons/:id`, reply(coupon)),
    http.get(
      `${base}/engage/coupons/check`,
      reply({ code: "DIWALI10", valid: true, reason: null, kind: "PERCENT", value: 10, discountRupees: 110 }),
    ),
    http.get(`${base}/engage/rewards/settings`, reply({ enabled: true, pointsPer100: 1, rupeesPerPoint: 1 })),
    http.put(`${base}/engage/rewards/settings`, async ({ request }) => {
      const body = await request.clone().json();
      await record(request);
      return HttpResponse.json(body as never);
    }),
    http.get(`${base}/engage/rewards/clients/:id`, reply(account)),
    http.post(`${base}/engage/rewards/clients/:id`, reply({ ...account, balance: account.balance - 10 })),
    http.get(`${base}/engage/messages`, reply([message])),
    http.post(`${base}/engage/messages`, reply(message, 201)),
    http.get(`${base}/gallery`, reply([photo])),
    http.post(
      `${base}/gallery/uploads`,
      reply({ key: "gallery/new", uploadUrl: "https://r2.test/gallery/new", expiresAt: "2026-11-02T12:00:00Z" }),
    ),
    http.post(`${base}/gallery`, reply(photo, 201)),
    http.delete(`${base}/gallery/:id`, reply(null)),
    http.get(`${base}/reports`, reply(reportList)),
    http.get(`${base}/reports/overview`, reply(reportsOverview)),
    http.get(`${base}/reports/:name`, reply(salesReport)),
    http.get(`${base}/activity`, reply(activity)),
    http.get(`${base}/me/notifications`, reply(notifications)),
    http.post(`${base}/me/notifications/seen`, reply(null)),
  );
}
