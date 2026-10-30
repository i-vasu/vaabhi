import type { Category } from "@vaabhi/api-client";
import { api } from "@/lib/api";

// Rendered on request and cached for 60 s (ISR), so builds do not need the API.
export const dynamic = "force-dynamic";

async function loadCategories(): Promise<Category[] | null> {
  try {
    return await api.store.listCategories({ next: { revalidate: 60 } });
  } catch {
    return null;
  }
}

export default async function HomePage() {
  const categories = await loadCategories();

  return (
    <section>
      <h1 className="text-3xl font-semibold">New season, new you</h1>
      <p className="mt-2 text-neutral-600">Shop by category</p>
      {categories === null ? (
        <p className="mt-6 text-sm text-red-600">The catalogue is unavailable right now.</p>
      ) : (
        <ul className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {categories.map((category) => (
            <li key={category.id} className="rounded-lg border border-neutral-200 p-6 text-center font-medium">
              {category.name}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
