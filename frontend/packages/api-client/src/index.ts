import type { components, operations } from "./schema";

type Schemas = components["schemas"];

export type Category = Schemas["CategoryResponse"];
export type ProductListing = Schemas["ProductListingResponse"];
export type ProductSummary = Schemas["ProductSummary"];
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
export type StaffProfile = Schemas["StaffProfile"];
export type StaffRole = StaffProfile["role"];
export type StaffSession = Schemas["StaffSession"];
export type StaffLoginRequest = Schemas["StaffLoginRequest"];
export type AdminListParams = NonNullable<operations["listAdminProducts"]["parameters"]["query"]>;
export type PasswordChange = Schemas["PasswordChange"];

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
export type TailoringImageUpload = Schemas["TailoringImageUpload"];
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

/** Field-level message from the API, e.g. `{ field: "variants[0].pricePaise", message: "must be greater than 0" }`. */
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
  ) {
    super(message);
  }
}

async function problemOf(response: Response): Promise<{ detail?: string; errors: FieldError[] }> {
  try {
    const body = (await response.json()) as { detail?: unknown; errors?: unknown };
    const errors = Array.isArray(body.errors)
      ? body.errors.filter((e): e is FieldError => typeof e?.field === "string" && typeof e?.message === "string")
      : [];
    return { detail: typeof body.detail === "string" ? body.detail : undefined, errors };
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
      );
    }
    // 204 No Content (deletes, password change) has no body to parse.
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

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
      /** Current prices and stock for the bag kept in the browser. */
      quoteBag: (bag: BagQuoteRequest) => send<BagQuote>("POST", "/api/v1/store/bag/quote", bag),
    },
    auth: {
      /** Admin dashboard sign-in (email + password). */
      staffLogin: (credentials: StaffLoginRequest) =>
        send<StaffSession>("POST", "/api/v1/auth/staff/login", credentials),
    },
    admin: {
      /** The signed-in staff member. */
      me: () => request<StaffProfile>("/api/v1/admin/me"),
      changePassword: (change: PasswordChange) => send<void>("PUT", "/api/v1/admin/me/password", change),
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
