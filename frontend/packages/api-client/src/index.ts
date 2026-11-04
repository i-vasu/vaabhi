import type { components, operations } from "./schema";

type Schemas = components["schemas"];

export type Category = Schemas["CategoryResponse"];
export type ProductListing = Schemas["ProductListingResponse"];
export type ProductSummary = Schemas["ProductSummary"];
export type Badge = NonNullable<ProductSummary["badge"]>;
export type NewArrivalsPage = Schemas["NewArrivalsPage"];
export type FilterGroup = Schemas["FilterGroup"];
export type FilterOption = Schemas["FilterOption"];
export type ProductDetail = Schemas["ProductDetailResponse"];
export type ProductVariant = Schemas["VariantResponse"];
export type ProductAddon = Schemas["AddonResponse"];
export type Media = Schemas["MediaResponse"];

export type BagQuote = Schemas["BagQuote"];
export type BagQuoteLine = Schemas["BagQuoteLine"];
export type BagQuoteRequest = Schemas["BagQuoteRequest"];

export type AdminProduct = Schemas["AdminProduct"];
export type AdminProductSummary = Schemas["AdminProductSummary"];
export type AdminProductPage = Schemas["AdminProductPage"];
export type ProductForm = Schemas["ProductForm"];
export type VariantForm = Schemas["VariantForm"];
export type AddonForm = Schemas["AddonForm"];
export type CatalogOptions = Schemas["CatalogOptions"];
export type ProductStatus = AdminProduct["status"];
export type AdminMedia = Schemas["AdminMedia"];
export type MediaUpload = Schemas["MediaUpload"];
export type MediaUploadRequest = Schemas["MediaUploadRequest"];
export type MediaUpdate = Schemas["MediaUpdate"];
export type VideoUpload = Schemas["VideoUpload"];
export type VideoUploadRequest = Schemas["VideoUploadRequest"];
/** The signed-in staff member with the dashboard areas their role may use. */
export type StaffProfile = Schemas["SignedInStaff"];
export type Permission = StaffProfile["permissions"][number];
export type StaffRole = StaffProfile["role"];
export type StaffSession = Schemas["StaffSession"];
export type StaffLoginRequest = Schemas["StaffLoginRequest"];
export type AdminListParams = NonNullable<operations["listAdminProducts"]["parameters"]["query"]>;
export type PasswordChange = Schemas["PasswordChange"];
export type StaffMember = Schemas["StaffMember"];
export type StaffForm = Schemas["StaffForm"];
export type PayType = StaffForm["payType"];
export type RolePermissions = Schemas["RolePermissions"];
export type DayAttendance = Schemas["DayAttendance"];
export type AttendanceEntry = Schemas["AttendanceEntry"];
export type AttendanceForm = Schemas["AttendanceForm"];
export type AttendanceStatus = AttendanceForm["status"];
/** A person's month; `days` holds null for days nobody marked (the generated schema drops that nullability). */
export type MonthAttendanceRow = Omit<Schemas["Row"], "days"> & { days: (AttendanceStatus | null)[] };
export type MonthAttendance = Omit<Schemas["MonthAttendance"], "rows"> & { rows: MonthAttendanceRow[] };
export type StaffRequest = Schemas["StaffRequest"];
export type RequestForm = Schemas["RequestForm"];
export type RequestKind = RequestForm["kind"];
export type RequestStatus = StaffRequest["status"];
export type Payslip = Schemas["Payslip"];
export type PayrollMonth = Schemas["PayrollMonth"];
export type ProductionLedger = Schemas["ProductionLedger"];
export type ProductionEntry = Schemas["ProductionEntry"];
export type StaffStatement = Schemas["StaffStatement"];

export type ShopProfile = Schemas["ShopProfile"];
export type BusinessType = ShopProfile["businessType"];
export type BillingSettings = Schemas["BillingSettings"];
export type DocumentSettings = Schemas["DocumentSettings"];
export type OrderSettings = Schemas["OrderSettings"];
export type CalendarSettings = Schemas["CalendarSettings"];
export type Holiday = Schemas["Holiday"];
export type NotificationSettings = Schemas["NotificationSettings"];
export type PrintSettings = Schemas["PrintSettings"];
export type PaperFormat = PrintSettings["defaultFormat"];
export type MessageTemplates = Schemas["MessageTemplates"];
export type WatermarkSettings = Schemas["WatermarkSettings"];
export type NumberSequence = Schemas["NumberSequence"];
export type ProductionStage = Schemas["ProductionStage"];
export type DeliveryPackage = Schemas["DeliveryPackage"];
export type GarmentTemplate = Schemas["GarmentTemplate"];
export type GarmentField = Schemas["Field"];
export type SettingsImageUpload = Schemas["SettingsImageUpload"];
export type SettingsImagePurpose = Schemas["SettingsImageRequest"]["purpose"];

export type Client = Schemas["Client"];
export type ClientForm = Schemas["ClientForm"];
export type ClientSummary = Schemas["ClientSummary"];
export type ClientPage = Schemas["ClientPage"];
export type ClientType = ClientForm["clientType"];
export type Gender = ClientForm["gender"];
export type ClientListParams = NonNullable<operations["listClients"]["parameters"]["query"]>;
export type Measurement = Schemas["Measurement"];
export type MeasurementForm = Schemas["MeasurementForm"];
export type ServiceProduct = Schemas["ServiceProduct"];
export type ServiceProductForm = Schemas["ServiceProductForm"];
export type StageCost = Schemas["StageCost"];
export type Order = Schemas["Order"];
export type OrderForm = Schemas["OrderForm"];
export type OrderItem = Schemas["OrderItem"];
export type OrderItemForm = Schemas["OrderItemForm"];
export type OrderPayment = Schemas["OrderPayment"];
export type OrderSummary = Schemas["OrderSummary"];
export type OrderPage = Schemas["OrderPage"];
export type OrderStatus = Order["status"];
export type OrderSchedule = Schemas["OrderSchedule"];
export type OrderListParams = NonNullable<operations["listOrders"]["parameters"]["query"]>;
export type OrderFilter = NonNullable<OrderListParams["filter"]>;
export type DiscountKind = OrderForm["discountKind"];
export type PaymentForm = Schemas["PaymentForm"];
export type PaymentMode = PaymentForm["mode"];
export type StageMove = Schemas["StageMove"];
export type Alteration = Schemas["Alteration"];
export type AlterationForm = Schemas["AlterationForm"];
export type AlterationItemForm = Schemas["AlterationItemForm"];
export type AlterationKind = AlterationItemForm["kind"];
export type AlterationSummary = Schemas["AlterationSummary"];
export type AlterationPage = Schemas["AlterationPage"];
export type AlterationProgress = Schemas["AlterationProgress"];
export type AlterationListParams = NonNullable<operations["listAlterations"]["parameters"]["query"]>;
export type Assignee = Schemas["Assignee"];
export type WorkItem = Schemas["WorkItem"];
export type BoardColumn = Schemas["BoardColumn"];
export type WorkshopOverview = Schemas["WorkshopOverview"];
export type WorkshopStaff = Schemas["WorkshopStaff"];
export type StageLoad = Schemas["StageLoad"];
export type BulkMove = Schemas["BulkMove"];
export type Invoice = Schemas["Invoice"];
export type InvoiceLine = Schemas["InvoiceLine"];
export type InvoiceSummary = Schemas["InvoiceSummary"];
export type InvoicePage = Schemas["InvoicePage"];
export type Expense = Schemas["Expense"];
export type ExpenseForm = Schemas["ExpenseForm"];
export type ExpenseCategory = ExpenseForm["category"];
export type ExpenseList = Schemas["ExpenseList"];
export type Ledger = Schemas["Ledger"];
export type LedgerEntry = Schemas["LedgerEntry"];
export type LedgerKind = LedgerEntry["kind"];
export type Due = Schemas["Due"];
export type Outstanding = Schemas["Outstanding"];
export type TailoringImageUpload = Schemas["TailoringImageUpload"];
export type StockItem = Schemas["StockItem"];
export type StockItemForm = Schemas["StockItemForm"];
export type StockUnit = StockItem["unit"];
export type StockMovement = Schemas["StockMovement"];
export type StockMovementForm = Schemas["StockMovementForm"];
export type Coupon = Schemas["Coupon"];
export type CouponForm = Schemas["CouponForm"];
export type CouponCheck = Schemas["CouponCheck"];
export type RewardSettings = Schemas["RewardSettings"];
export type RewardAccount = Schemas["RewardAccount"];
export type RewardChange = Schemas["RewardChange"];
export type Message = Schemas["Message"];
export type MessageForm = Schemas["MessageForm"];
export type MessagePurpose = MessageForm["purpose"];
export type GalleryPhoto = Schemas["GalleryPhoto"];
export type GalleryForm = Schemas["GalleryForm"];
export type GalleryUpload = Schemas["GalleryUpload"];
export type ActivityEntry = Schemas["ActivityEntry"];
export type ActivityPage = Schemas["ActivityPage"];
export type Notifications = Schemas["Notifications"];
export type Report = Schemas["Report"];
export type ReportColumn = Report["columns"][number];
export type ReportInfo = Schemas["ReportInfo"];
export type ReportsOverview = Schemas["ReportsOverview"];
export type TailoringImagePurpose = Schemas["TailoringImageRequest"]["purpose"];

/** The JSON settings sections, by their API path. */
export type SettingsSections = {
  "shop-profile": ShopProfile;
  billing: BillingSettings;
  documents: DocumentSettings;
  orders: OrderSettings;
  calendar: CalendarSettings;
  notifications: NotificationSettings;
  printing: PrintSettings;
  messages: MessageTemplates;
  watermark: WatermarkSettings;
};

export type OtpSent = Schemas["OtpSent"];
export type AuthSession = Schemas["AuthSession"];
export type CustomerProfile = Schemas["CustomerProfile"];
export type CheckoutRequest = Schemas["CheckoutRequest"];
export type CheckoutStart = Schemas["CheckoutStart"];
export type PaymentConfirmation = Schemas["PaymentConfirmation"];
export type WebOrder = Schemas["WebOrder"];
export type RefundStatus = NonNullable<WebOrder["refundStatus"]>;
export type ShipmentForm = Schemas["ShipmentForm"];
export type CancelWebOrderForm = Schemas["CancelWebOrderForm"];
export type OrderReturn = Schemas["OrderReturn"];
export type ReturnStatus = OrderReturn["status"];
export type ReturnRequestForm = Schemas["ReturnRequestForm"];
export type ReturnDecisionForm = Schemas["ReturnDecisionForm"];
export type ReturnRefundForm = Schemas["ReturnRefundForm"];

/** Field-level message from the API, e.g. `{ field: "variants[0].priceRupees", message: "must be greater than 0" }`. */
export type FieldError = { field: string; message: string };

/** Listing filters and paging. Facet filters (fabric, occasion, …) take comma-separated value codes. */
export type ListingParams = NonNullable<operations["listProducts"]["parameters"]["query"]> &
  Record<string, string | number | boolean | null | undefined>;

/** Serialises listing params, dropping empty values so URLs (and caches) stay canonical. */
export function listingQueryString(params: ListingParams = {}): string {
  return queryString(params);
}

/** Serialises query params (sorted, empty values dropped), e.g. `?page=2&q=meera`. */
export function queryString(params: Record<string, string | number | boolean | null | undefined> = {}): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params).sort(([a], [b]) => a.localeCompare(b))) {
    if (value === undefined || value === null || value === "" || value === false) continue;
    search.set(key, String(value));
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Per-field validation messages from the problem detail, if any. */
    readonly errors: FieldError[] = [],
    /** The problem detail's human-readable explanation, if any. */
    readonly detail?: string,
    /** For 429 answers: seconds to wait before trying again. */
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }
}

type Problem = { detail?: string; errors: FieldError[]; retryAfterSeconds?: number };

async function problemOf(response: Response): Promise<Problem> {
  try {
    const body = (await response.json()) as { detail?: unknown; errors?: unknown; retryAfterSeconds?: unknown };
    const errors = Array.isArray(body.errors)
      ? body.errors.filter((e): e is FieldError => typeof e?.field === "string" && typeof e?.message === "string")
      : [];
    return {
      detail: typeof body.detail === "string" ? body.detail : undefined,
      errors,
      retryAfterSeconds: typeof body.retryAfterSeconds === "number" ? body.retryAfterSeconds : undefined,
    };
  } catch {
    return { errors: [] };
  }
}

export type ApiClientOptions = {
  baseUrl: string;
  /** Returns the current access token, if any (admin, logged-in customer). */
  getToken?: () => string | undefined;
};

type RequestOptions = RequestInit & { next?: { revalidate?: number; tags?: string[] } };

export function createApiClient({ baseUrl, getToken }: ApiClientOptions) {
  async function request<T>(path: string, init?: RequestOptions): Promise<T> {
    const token = getToken?.();
    const response = await fetch(`${baseUrl.replace(/\/$/, "")}${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    });
    if (!response.ok) {
      const problem = await problemOf(response);
      throw new ApiError(
        response.status,
        `${init?.method ?? "GET"} ${path} failed with ${response.status}`,
        problem.errors,
        problem.detail,
        problem.retryAfterSeconds,
      );
    }
    // 204 No Content (deletes, password change, logout) has no body to parse.
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

  const bearer = (accessToken: string) => ({ Authorization: `Bearer ${accessToken}` });

  const send = <T>(method: "POST" | "PUT" | "DELETE", path: string, body?: unknown) =>
    request<T>(path, {
      method,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

  return {
    request,
    store: {
      listCategories: (init?: RequestOptions) => request<Category[]>("/api/v1/store/categories", init),
      listProducts: (categorySlug: string, params?: ListingParams, init?: RequestOptions) =>
        request<ProductListing>(
          `/api/v1/store/categories/${encodeURIComponent(categorySlug)}/products${listingQueryString(params)}`,
          init,
        ),
      getProduct: (slug: string, init?: RequestOptions) =>
        request<ProductDetail>(`/api/v1/store/products/${encodeURIComponent(slug)}`, init),
      /** Products with a badge across the catalogue, most popular first (Bestsellers by default). */
      listFeatured: (params: { badge?: Badge; limit?: number } = {}, init?: RequestOptions) =>
        request<ProductSummary[]>(`/api/v1/store/featured${queryString(params)}`, init),
      /** Every published product, most recently published first. */
      listNewArrivals: (params: { page?: number; pageSize?: number } = {}, init?: RequestOptions) =>
        request<NewArrivalsPage>(`/api/v1/store/new-arrivals${queryString(params)}`, init),
      /** Current prices and stock for the bag kept in the browser. */
      quoteBag: (bag: BagQuoteRequest) => send<BagQuote>("POST", "/api/v1/store/bag/quote", bag),
    },
    /** Sign-in. Customer calls come from the storefront's server, which keeps the tokens in httpOnly cookies. */
    auth: {
      /** Admin dashboard sign-in (email + password). */
      staffLogin: (credentials: StaffLoginRequest) =>
        send<StaffSession>("POST", "/api/v1/auth/staff/login", credentials),
      requestOtp: (phone: string) => send<OtpSent>("POST", "/api/v1/auth/otp/request", { phone }),
      verifyOtp: (phone: string, code: string) => send<AuthSession>("POST", "/api/v1/auth/otp/verify", { phone, code }),
      signInWithGoogle: (credential: string) => send<AuthSession>("POST", "/api/v1/auth/google", { credential }),
      refresh: (refreshToken: string) => send<AuthSession>("POST", "/api/v1/auth/refresh", { refreshToken }),
      logout: (refreshToken: string) => send<void>("POST", "/api/v1/auth/logout", { refreshToken }),
    },
    /** The signed-in customer; each call takes that customer's access token. */
    customer: {
      getProfile: (accessToken: string) =>
        request<CustomerProfile>("/api/v1/customer/me", { headers: bearer(accessToken), cache: "no-store" }),
      updateProfile: (accessToken: string, name: string) =>
        request<CustomerProfile>("/api/v1/customer/me", {
          method: "PUT",
          headers: { ...bearer(accessToken), "Content-Type": "application/json" },
          body: JSON.stringify({ name }),
        }),
      /** Places the order from the bag; the answer opens Razorpay Checkout. */
      startCheckout: (accessToken: string, checkout: CheckoutRequest) =>
        request<CheckoutStart>("/api/v1/customer/checkout", {
          method: "POST",
          headers: { ...bearer(accessToken), "Content-Type": "application/json" },
          body: JSON.stringify(checkout),
          cache: "no-store",
        }),
      /** Confirms the payment Razorpay Checkout returned. */
      confirmPayment: (accessToken: string, number: string, payment: PaymentConfirmation) =>
        request<WebOrder>(`/api/v1/customer/orders/${encodeURIComponent(number)}/payment`, {
          method: "POST",
          headers: { ...bearer(accessToken), "Content-Type": "application/json" },
          body: JSON.stringify(payment),
          cache: "no-store",
        }),
      listOrders: (accessToken: string) =>
        request<WebOrder[]>("/api/v1/customer/orders", { headers: bearer(accessToken), cache: "no-store" }),
      getOrder: (accessToken: string, number: string) =>
        request<WebOrder>(`/api/v1/customer/orders/${encodeURIComponent(number)}`, {
          headers: bearer(accessToken),
          cache: "no-store",
        }),
      /** "Pay now" on an unpaid online order: what Razorpay Checkout needs to open again. */
      payOrderAgain: (accessToken: string, number: string) =>
        request<CheckoutStart>(`/api/v1/customer/orders/${encodeURIComponent(number)}/pay`, {
          method: "POST",
          headers: bearer(accessToken),
          cache: "no-store",
        }),
      /** Cancels an order that has not been shipped yet. */
      cancelOrder: (accessToken: string, number: string, form: CancelWebOrderForm) =>
        request<WebOrder>(`/api/v1/customer/orders/${encodeURIComponent(number)}/cancel`, {
          method: "POST",
          headers: { ...bearer(accessToken), "Content-Type": "application/json" },
          body: JSON.stringify(form),
          cache: "no-store",
        }),
      /** Asks to return items of a delivered order. */
      requestReturn: (accessToken: string, number: string, form: ReturnRequestForm) =>
        request<WebOrder>(`/api/v1/customer/orders/${encodeURIComponent(number)}/returns`, {
          method: "POST",
          headers: { ...bearer(accessToken), "Content-Type": "application/json" },
          body: JSON.stringify(form),
          cache: "no-store",
        }),
    },
    admin: {
      /** Online (storefront) orders, newest first; optionally one status. */
      onlineOrders: (status?: WebOrder["status"], openReturns = false) =>
        request<WebOrder[]>(`/api/v1/admin/online-orders${queryString({ status, openReturns })}`),
      approveReturn: (returnNumber: string, form: ReturnDecisionForm) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/returns/${encodeURIComponent(returnNumber)}/approve`, form),
      rejectReturn: (returnNumber: string, form: ReturnDecisionForm) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/returns/${encodeURIComponent(returnNumber)}/reject`, form),
      /** Books the courier to collect the return (Shiprocket). */
      bookReturnPickup: (returnNumber: string) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/returns/${encodeURIComponent(returnNumber)}/pickup`),
      /** Items received: puts them back in stock and refunds (Razorpay, or records the COD reference). */
      refundReturn: (returnNumber: string, form: ReturnRefundForm) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/returns/${encodeURIComponent(returnNumber)}/refund`, form),
      shipOnlineOrder: (number: string, shipment: ShipmentForm) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/${encodeURIComponent(number)}/ship`, shipment),
      /** Books the courier through Shiprocket (AWB, pickup, label) and marks the order shipped. */
      shipOnlineOrderWithShiprocket: (number: string) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/${encodeURIComponent(number)}/shiprocket`),
      /** Fetches the courier's latest status from Shiprocket. */
      refreshOnlineOrderTracking: (number: string) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/${encodeURIComponent(number)}/tracking`),
      deliverOnlineOrder: (number: string) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/${encodeURIComponent(number)}/deliver`),
      /** Cancels; an order paid online is refunded in full through Razorpay. */
      cancelOnlineOrder: (number: string, form: CancelWebOrderForm) =>
        send<WebOrder>("POST", `/api/v1/admin/online-orders/${encodeURIComponent(number)}/cancel`, form),
      /** The signed-in staff member. */
      me: () => request<StaffProfile>("/api/v1/admin/me"),
      changePassword: (change: PasswordChange) => send<void>("PUT", "/api/v1/admin/me/password", change),
      myRequests: () => request<StaffRequest[]>("/api/v1/admin/me/requests"),
      notifications: () => request<Notifications>("/api/v1/admin/me/notifications"),
      markNotificationsSeen: () => send<void>("POST", "/api/v1/admin/me/notifications/seen"),
      activity: (params: { kind?: string; refType?: string; refId?: number; page?: number; pageSize?: number } = {}) =>
        request<ActivityPage>(`/api/v1/admin/activity${queryString(params)}`),
      reports: {
        list: () => request<ReportInfo[]>("/api/v1/admin/reports"),
        overview: () => request<ReportsOverview>("/api/v1/admin/reports/overview"),
        get: (name: string, params: { from?: string; to?: string } = {}) =>
          request<Report>(`/api/v1/admin/reports/${encodeURIComponent(name)}${queryString(params)}`),
      },
      stock: {
        list: (params: { q?: string; low?: boolean } = {}) =>
          request<StockItem[]>(`/api/v1/admin/stock${queryString(params)}`),
        get: (id: number) => request<StockItem>(`/api/v1/admin/stock/${id}`),
        create: (form: StockItemForm) => send<StockItem>("POST", "/api/v1/admin/stock", form),
        update: (id: number, form: StockItemForm) => send<StockItem>("PUT", `/api/v1/admin/stock/${id}`, form),
        remove: (id: number) => send<void>("DELETE", `/api/v1/admin/stock/${id}`),
        movements: (id: number) => request<StockMovement[]>(`/api/v1/admin/stock/${id}/movements`),
        move: (id: number, form: StockMovementForm) =>
          send<StockMovement>("POST", `/api/v1/admin/stock/${id}/movements`, form),
      },
      engage: {
        coupons: () => request<Coupon[]>("/api/v1/admin/engage/coupons"),
        createCoupon: (form: CouponForm) => send<Coupon>("POST", "/api/v1/admin/engage/coupons", form),
        updateCoupon: (id: number, form: CouponForm) => send<Coupon>("PUT", `/api/v1/admin/engage/coupons/${id}`, form),
        checkCoupon: (code: string, amountRupees: number) =>
          request<CouponCheck>(`/api/v1/admin/engage/coupons/check${queryString({ code, amountRupees })}`),
        rewardSettings: () => request<RewardSettings>("/api/v1/admin/engage/rewards/settings"),
        saveRewardSettings: (settings: RewardSettings) =>
          send<RewardSettings>("PUT", "/api/v1/admin/engage/rewards/settings", settings),
        rewards: (clientId: number) => request<RewardAccount>(`/api/v1/admin/engage/rewards/clients/${clientId}`),
        changeRewards: (clientId: number, change: RewardChange) =>
          send<RewardAccount>("POST", `/api/v1/admin/engage/rewards/clients/${clientId}`, change),
        messages: (params: { clientId?: number; orderId?: number } = {}) =>
          request<Message[]>(`/api/v1/admin/engage/messages${queryString(params)}`),
        prepareMessage: (form: MessageForm) => send<Message>("POST", "/api/v1/admin/engage/messages", form),
      },
      gallery: {
        list: (params: { kind?: GalleryPhoto["kind"]; templateId?: number; clientId?: number; q?: string } = {}) =>
          request<GalleryPhoto[]>(`/api/v1/admin/gallery${queryString(params)}`),
        startUpload: () => send<GalleryUpload>("POST", "/api/v1/admin/gallery/uploads"),
        add: (form: GalleryForm) => send<GalleryPhoto>("POST", "/api/v1/admin/gallery", form),
        update: (id: number, form: GalleryForm) => send<GalleryPhoto>("PUT", `/api/v1/admin/gallery/${id}`, form),
        remove: (id: number) => send<void>("DELETE", `/api/v1/admin/gallery/${id}`),
      },
      createMyRequest: (form: RequestForm) => send<StaffRequest>("POST", "/api/v1/admin/me/requests", form),
      staff: {
        list: (includeInactive = false) =>
          request<StaffMember[]>(`/api/v1/admin/staff${queryString({ includeInactive })}`),
        get: (id: number) => request<StaffMember>(`/api/v1/admin/staff/${id}`),
        create: (form: StaffForm) => send<StaffMember>("POST", "/api/v1/admin/staff", form),
        update: (id: number, form: StaffForm) => send<StaffMember>("PUT", `/api/v1/admin/staff/${id}`, form),
        setActive: (id: number, active: boolean) =>
          send<StaffMember>("POST", `/api/v1/admin/staff/${id}/${active ? "activate" : "deactivate"}`),
        removeSignIn: (id: number) => send<StaffMember>("DELETE", `/api/v1/admin/staff/${id}/sign-in`),
        roles: () => request<RolePermissions[]>("/api/v1/admin/staff/roles"),
        saveRole: (role: StaffRole, permissions: Permission[]) =>
          send<RolePermissions>("PUT", `/api/v1/admin/staff/roles/${role}`, { permissions }),
        day: (day: string) => request<DayAttendance>(`/api/v1/admin/staff/attendance${queryString({ day })}`),
        mark: (day: string, staffId: number, form: AttendanceForm) =>
          send<DayAttendance>("PUT", `/api/v1/admin/staff/attendance/${day}/${staffId}`, form),
        clear: (day: string, staffId: number) =>
          send<DayAttendance>("DELETE", `/api/v1/admin/staff/attendance/${day}/${staffId}`),
        markMany: (day: string, status: AttendanceStatus, staffIds: number[]) =>
          send<DayAttendance>("POST", `/api/v1/admin/staff/attendance/${day}`, { status, staffIds }),
        month: (month: string) => request<MonthAttendance>(`/api/v1/admin/staff/attendance/month/${month}`),
        requests: (params: { status?: RequestStatus; staffId?: number } = {}) =>
          request<StaffRequest[]>(`/api/v1/admin/staff/requests${queryString(params)}`),
        createRequest: (form: RequestForm) => send<StaffRequest>("POST", "/api/v1/admin/staff/requests", form),
        decide: (id: number, approve: boolean, note: string | null) =>
          send<StaffRequest>("POST", `/api/v1/admin/staff/requests/${id}/decision`, { approve, note }),
      },
      payroll: {
        month: (month: string) => request<PayrollMonth>(`/api/v1/admin/payroll/months/${month}`),
        finalize: (month: string) => send<PayrollMonth>("POST", `/api/v1/admin/payroll/months/${month}/finalize`),
        reopen: (month: string) => send<PayrollMonth>("POST", `/api/v1/admin/payroll/months/${month}/reopen`),
        pay: (payslipId: number, mode: PaymentMode) =>
          send<Payslip>("POST", `/api/v1/admin/payroll/payslips/${payslipId}/pay`, { mode }),
        production: (params: { from?: string; to?: string; staffId?: number } = {}) =>
          request<ProductionLedger>(`/api/v1/admin/payroll/production${queryString(params)}`),
        statement: (staffId: number) => request<StaffStatement>(`/api/v1/admin/payroll/staff/${staffId}/statement`),
      },
      settings: {
        get: <K extends keyof SettingsSections>(section: K) =>
          request<SettingsSections[K]>(`/api/v1/admin/settings/${section}`),
        save: <K extends keyof SettingsSections>(section: K, value: SettingsSections[K]) =>
          send<SettingsSections[K]>("PUT", `/api/v1/admin/settings/${section}`, value),
        numbering: () => request<NumberSequence[]>("/api/v1/admin/settings/numbering"),
        saveNumbering: (sequences: NumberSequence[]) =>
          send<NumberSequence[]>("PUT", "/api/v1/admin/settings/numbering", sequences),
        stages: () => request<ProductionStage[]>("/api/v1/admin/settings/stages"),
        saveStages: (stages: ProductionStage[]) =>
          send<ProductionStage[]>("PUT", "/api/v1/admin/settings/stages", stages),
        deliveryPackages: () => request<DeliveryPackage[]>("/api/v1/admin/settings/delivery-packages"),
        saveDeliveryPackages: (packages: DeliveryPackage[]) =>
          send<DeliveryPackage[]>("PUT", "/api/v1/admin/settings/delivery-packages", packages),
        garmentTemplates: () => request<GarmentTemplate[]>("/api/v1/admin/settings/garment-templates"),
        createGarmentTemplate: (template: GarmentTemplate) =>
          send<GarmentTemplate>("POST", "/api/v1/admin/settings/garment-templates", template),
        updateGarmentTemplate: (id: number, template: GarmentTemplate) =>
          send<GarmentTemplate>("PUT", `/api/v1/admin/settings/garment-templates/${id}`, template),
        deleteGarmentTemplate: (id: number) => send<void>("DELETE", `/api/v1/admin/settings/garment-templates/${id}`),
        startImageUpload: (purpose: SettingsImagePurpose) =>
          send<SettingsImageUpload>("POST", "/api/v1/admin/settings/images", { purpose }),
      },
      clients: {
        list: (params: ClientListParams = {}) => request<ClientPage>(`/api/v1/admin/clients${queryString(params)}`),
        get: (id: number) => request<Client>(`/api/v1/admin/clients/${id}`),
        create: (form: ClientForm) => send<Client>("POST", "/api/v1/admin/clients", form),
        update: (id: number, form: ClientForm) => send<Client>("PUT", `/api/v1/admin/clients/${id}`, form),
        addMeasurement: (id: number, form: MeasurementForm) =>
          send<Measurement>("POST", `/api/v1/admin/clients/${id}/measurements`, form),
      },
      orders: {
        list: (params: OrderListParams = {}) => request<OrderPage>(`/api/v1/admin/orders${queryString(params)}`),
        get: (id: number) => request<Order>(`/api/v1/admin/orders/${id}`),
        create: (form: OrderForm) => send<Order>("POST", "/api/v1/admin/orders", form),
        moveItems: (id: number, move: StageMove) => send<Order>("POST", `/api/v1/admin/orders/${id}/stage`, move),
        deliver: (id: number) => send<Order>("POST", `/api/v1/admin/orders/${id}/deliver`),
        cancel: (id: number) => send<Order>("POST", `/api/v1/admin/orders/${id}/cancel`),
        reschedule: (id: number, schedule: OrderSchedule) =>
          send<Order>("PUT", `/api/v1/admin/orders/${id}/schedule`, schedule),
        addPayment: (id: number, payment: PaymentForm) =>
          send<Order>("POST", `/api/v1/admin/orders/${id}/payments`, payment),
      },
      serviceProducts: {
        list: (params: { templateId?: number; q?: string } = {}) =>
          request<ServiceProduct[]>(`/api/v1/admin/service-products${queryString(params)}`),
        create: (form: ServiceProductForm) => send<ServiceProduct>("POST", "/api/v1/admin/service-products", form),
        update: (id: number, form: ServiceProductForm) =>
          send<ServiceProduct>("PUT", `/api/v1/admin/service-products/${id}`, form),
        remove: (id: number) => send<void>("DELETE", `/api/v1/admin/service-products/${id}`),
      },
      alterations: {
        list: (params: AlterationListParams = {}) =>
          request<AlterationPage>(`/api/v1/admin/alterations${queryString(params)}`),
        get: (id: number) => request<Alteration>(`/api/v1/admin/alterations/${id}`),
        create: (form: AlterationForm) => send<Alteration>("POST", "/api/v1/admin/alterations", form),
        progress: (id: number, progress: AlterationProgress) =>
          send<Alteration>("POST", `/api/v1/admin/alterations/${id}/progress`, progress),
      },
      workshop: {
        overview: () => request<WorkshopOverview>("/api/v1/admin/workshop/overview"),
        board: (params: { staffId?: number; q?: string } = {}) =>
          request<BoardColumn[]>(`/api/v1/admin/workshop/board${queryString(params)}`),
        assign: (itemId: number, staffId: number | null) =>
          send<WorkItem>("PUT", `/api/v1/admin/workshop/items/${itemId}/assignee`, { staffId }),
        move: (move: BulkMove) => send<BoardColumn[]>("POST", "/api/v1/admin/workshop/items/move", move),
        staff: () => request<WorkshopStaff[]>("/api/v1/admin/workshop/staff"),
        saveStages: (staffId: number, stageIds: number[]) =>
          send<WorkshopStaff>("PUT", `/api/v1/admin/workshop/staff/${staffId}/stages`, { stageIds }),
      },
      money: {
        invoices: (params: { q?: string; from?: string; to?: string; page?: number; pageSize?: number } = {}) =>
          request<InvoicePage>(`/api/v1/admin/money/invoices${queryString(params)}`),
        invoice: (id: number) => request<Invoice>(`/api/v1/admin/money/invoices/${id}`),
        cancelInvoice: (id: number) => send<Invoice>("POST", `/api/v1/admin/money/invoices/${id}/cancel`),
        /** The order's live invoice, or null when it has none yet (204). */
        orderInvoice: (orderId: number) =>
          request<Invoice | undefined>(`/api/v1/admin/money/orders/${orderId}/invoice`).then((i) => i ?? null),
        issueInvoice: (orderId: number) => send<Invoice>("POST", `/api/v1/admin/money/orders/${orderId}/invoice`),
        expenses: (params: { from?: string; to?: string; category?: ExpenseCategory } = {}) =>
          request<ExpenseList>(`/api/v1/admin/money/expenses${queryString(params)}`),
        createExpense: (form: ExpenseForm) => send<Expense>("POST", "/api/v1/admin/money/expenses", form),
        updateExpense: (id: number, form: ExpenseForm) =>
          send<Expense>("PUT", `/api/v1/admin/money/expenses/${id}`, form),
        deleteExpense: (id: number) => send<void>("DELETE", `/api/v1/admin/money/expenses/${id}`),
        ledger: (params: { from?: string; to?: string; kind?: LedgerKind } = {}) =>
          request<Ledger>(`/api/v1/admin/money/ledger${queryString(params)}`),
        outstanding: (params: { clientId?: number } = {}) =>
          request<Outstanding>(`/api/v1/admin/money/outstanding${queryString(params)}`),
      },
      startTailoringImageUpload: (purpose: TailoringImagePurpose) =>
        send<TailoringImageUpload>("POST", "/api/v1/admin/tailoring/images", { purpose }),
      catalogOptions: () => request<CatalogOptions>("/api/v1/admin/catalog/options"),
      listProducts: (params: AdminListParams = {}) =>
        request<AdminProductPage>(`/api/v1/admin/products${listingQueryString(params)}`),
      getProduct: (id: number) => request<AdminProduct>(`/api/v1/admin/products/${id}`),
      createProduct: (form: ProductForm) => send<AdminProduct>("POST", "/api/v1/admin/products", form),
      updateProduct: (id: number, form: ProductForm) => send<AdminProduct>("PUT", `/api/v1/admin/products/${id}`, form),
      setStatus: (id: number, action: "publish" | "unpublish" | "archive") =>
        send<AdminProduct>("POST", `/api/v1/admin/products/${id}/${action}`),
      startMediaUpload: (id: number, request: MediaUploadRequest) =>
        send<MediaUpload>("POST", `/api/v1/admin/products/${id}/media/uploads`, request),
      startVideoUpload: (id: number, request: VideoUploadRequest) =>
        send<VideoUpload>("POST", `/api/v1/admin/products/${id}/media/video-uploads`, request),
      completeMediaUpload: (id: number, mediaId: number) =>
        send<AdminMedia[]>("POST", `/api/v1/admin/products/${id}/media/${mediaId}/complete`),
      updateMedia: (id: number, updates: MediaUpdate[]) =>
        send<AdminMedia[]>("PUT", `/api/v1/admin/products/${id}/media`, updates),
      deleteMedia: (id: number, mediaId: number) =>
        send<AdminMedia[]>("DELETE", `/api/v1/admin/products/${id}/media/${mediaId}`),
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
