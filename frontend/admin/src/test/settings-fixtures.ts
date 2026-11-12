import type {
  AdminCategory,
  BillingSettings,
  CalendarSettings,
  DeliveryPackage,
  DocumentSettings,
  GarmentTemplate,
  MessageTemplates,
  NotificationSettings,
  NumberSequence,
  OrderSettings,
  PrintSettings,
  ProductionStage,
  SettingsSections,
  ShopProfile,
  TaxClass,
  WatermarkSettings,
} from "@vaabhi/api-client";
import { http, HttpResponse } from "msw";
import { API, server } from "./server";

export const shopProfile: ShopProfile = {
  ownerName: "Asha Rao",
  shopName: "vaabhi",
  businessType: "LADIES_BOUTIQUE",
  phone: "7013468353",
  publicEmail: null,
  googleReviewLink: null,
  website: null,
  mapsLink: null,
  instagram: null,
  facebook: null,
  youtube: null,
  threads: null,
  whatsappChannel: null,
  address: "Andhra Pradesh, India",
  doorNo: null,
  street: null,
  area: null,
  city: null,
  district: null,
  state: "Andhra Pradesh",
  country: "IN",
  pinCode: null,
  currency: "INR",
  opensAt: "09:00:00",
  closesAt: "18:00:00",
  logoKey: null,
};

export const billing: BillingSettings = {
  chargeGst: false,
  gstin: null,
  defaultGstRate: 5,
  pricesIncludeTax: true,
  showHsnCodes: false,
  discountAppliesTo: "ORDER_TOTAL",
  autoInvoice: true,
  company: { legalName: null, cin: null, registeredOffice: null, state: null },
  defaultTaxClassId: 1,
};

export const taxClasses: TaxClass[] = [
  {
    id: 1,
    name: "Apparel",
    kind: "GOODS",
    hsnCode: "6211",
    ratePercent: 5,
    slabLimitRupees: 2500,
    rateAbovePercent: 18,
    inUse: true,
  },
  {
    id: 2,
    name: "Tailoring service",
    kind: "SERVICE",
    hsnCode: "9988",
    ratePercent: 5,
    slabLimitRupees: null,
    rateAbovePercent: null,
    inUse: false,
  },
];

export const adminCategories: AdminCategory[] = [{ id: 1, name: "Sarees", taxClassId: null }];

export const states = [
  { name: "Andhra Pradesh", code: "37" },
  { name: "Telangana", code: "36" },
];

export const documents: DocumentSettings = {
  slogan: null,
  bankName: null,
  branch: null,
  accountNumber: null,
  ifsc: null,
  upiId: null,
  upiQrKey: null,
  alterationUndertaking: null,
  terms: null,
};

export const orderSettings: OrderSettings = {
  trialDate: false,
  subCategories: false,
  barcodes: false,
  lazyProductList: false,
  tailorChargeOnAddons: false,
};

export const calendar: CalendarSettings = { weeklyOff: [], holidays: [], googleCalendarId: null };

export const notifications: NotificationSettings = {
  newOrder: true,
  orderReady: true,
  orderDelivered: true,
  paymentReceived: true,
  dataExported: false,
  morningSchedule: false,
  dailySummary: false,
};

export const printing: PrintSettings = {
  formats: ["A4", "THERMAL_80MM"],
  defaultFormat: "A4",
  showLogo: true,
  showMeasurements: true,
  showClothPhotos: false,
  showBankDetails: true,
  showUpiQr: true,
  showTerms: true,
  showSignature: true,
  showStaffName: false,
};

export const messages: MessageTemplates = {
  orderPlaced: "Hi {customer}, order {order} booked at {shop}.",
  orderReady: "Hi {customer}, {order} is ready.",
  orderDelivered: "Delivered {order}.",
  paymentReceived: "Received {amount}.",
  reviewRequest: "Review us: {link}",
  reward: "Use {code} {unknown}",
};

export const watermark: WatermarkSettings = {
  enabled: false,
  text: null,
  position: "BOTTOM_RIGHT",
  opacityPercent: 60,
};

export const numbering: NumberSequence[] = [
  { kind: "ALTERATION", prefix: "ALT-", nextValue: 1001, suffix: "", pad: 0, reset: "NEVER", preview: "ALT-1001" },
  { kind: "INVOICE", prefix: "INV-", nextValue: 2042, suffix: "", pad: 0, reset: "NEVER", preview: "INV-2042" },
  { kind: "ITEM", prefix: "", nextValue: 1, suffix: "", pad: 3, reset: "NEVER", preview: "001" },
  { kind: "ORDER", prefix: "ORD-", nextValue: 2042, suffix: "", pad: 0, reset: "NEVER", preview: "ORD-2042" },
];

export const stages: ProductionStage[] = [
  { id: 1, name: "Cutting", kind: "WORK" },
  { id: 2, name: "Stitching", kind: "WORK" },
  { id: 3, name: "Finishing", kind: "WORK" },
  { id: 4, name: "Ready for pickup", kind: "READY" },
  { id: 5, name: "Delivered", kind: "DELIVERED" },
];

export const deliveryPackages: DeliveryPackage[] = [];

export const garmentTemplates: GarmentTemplate[] = [
  {
    id: 1,
    name: "Blouse",
    businessType: "LADIES",
    fields: [
      { key: "bust", label: "Bust", unit: "in" },
      { key: "waist", label: "Waist", unit: "in" },
    ],
    taxClassId: null,
  },
];

const sections: SettingsSections = {
  "shop-profile": shopProfile,
  billing,
  documents,
  orders: orderSettings,
  calendar,
  notifications,
  printing,
  messages,
  watermark,
};

/**
 * Serves every settings endpoint from the fixtures above. PUTs echo the body back and are recorded in `saved`
 * (path → last body) so tests can assert what was sent.
 */
export function serveSettings(): { saved: Record<string, unknown> } {
  const saved: Record<string, unknown> = {};
  const echo = (path: string) =>
    http.put(`${API}/api/v1/admin/settings/${path}`, async ({ request }) => {
      const body = await request.json();
      saved[path] = body;
      return HttpResponse.json(body as never);
    });
  server.use(
    ...Object.entries(sections).flatMap(([path, value]) => [
      http.get(`${API}/api/v1/admin/settings/${path}`, () => HttpResponse.json(value as never)),
      echo(path),
    ]),
    http.get(`${API}/api/v1/admin/settings/states`, () => HttpResponse.json(states)),
    http.get(`${API}/api/v1/admin/settings/numbering`, () => HttpResponse.json(numbering)),
    http.put(`${API}/api/v1/admin/settings/numbering`, async ({ request }) => {
      const body = (await request.json()) as NumberSequence[];
      saved.numbering = body;
      return HttpResponse.json(body.map((s) => ({ ...s, preview: `${s.prefix}${s.nextValue}${s.suffix}` })));
    }),
    http.get(`${API}/api/v1/admin/settings/stages`, () => HttpResponse.json(stages)),
    echo("stages"),
    http.get(`${API}/api/v1/admin/settings/delivery-packages`, () => HttpResponse.json(deliveryPackages)),
    echo("delivery-packages"),
    http.get(`${API}/api/v1/admin/settings/garment-templates`, () => HttpResponse.json(garmentTemplates)),
    http.get(`${API}/api/v1/admin/money/tax-classes`, () => HttpResponse.json(taxClasses)),
    http.get(`${API}/api/v1/admin/categories`, () => HttpResponse.json(adminCategories)),
  );
  return { saved };
}
