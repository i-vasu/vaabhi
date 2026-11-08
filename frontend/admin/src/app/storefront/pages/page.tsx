"use client";

import { useState } from "react";
import type { ContentPage, ContentPageForm, PageSection } from "@vaabhi/api-client";
import { DocumentIcon, PlusIcon, TrashIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  TextAreaField,
  TextField,
  ToggleRow,
  buttonPrimary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { PAGE_SECTIONS, pagePath, slugify, STARTER_PAGES } from "@/lib/storefront";
import { useAction, useLoad } from "@/lib/tailoring";

const emptyForm = (section: PageSection = "HELP"): ContentPageForm => ({
  slug: "",
  section,
  title: "",
  summary: null,
  body: "",
  published: false,
});

/** Policies, guides and about pages for vaabhi.com; the footer lists the published ones. */
export default function StorefrontPagesPage() {
  const pages = useLoad(() => api.admin.storefront.listPages(), []);
  const [editing, setEditing] = useState<{ id: number | null; form: ContentPageForm } | null>(null);
  const missing = STARTER_PAGES.filter((s) => !(pages.value ?? []).some((p) => p.slug === s.slug));

  return (
    <section className="mx-auto max-w-4xl">
      <PageHeader
        title="Website pages"
        backHref="/storefront/"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setEditing({ id: null, form: emptyForm() })}>
            <PlusIcon size={18} /> New page
          </button>
        }
      />
      <p className="mb-6 text-ink-soft">
        Shipping, returns, size guide, about… Published pages appear in the website footer within a minute; drafts stay
        here.
      </p>
      <ErrorNote>{pages.error}</ErrorNote>
      {!pages.value ? (
        <LoadState loading={pages.loading} error={null} />
      ) : (
        <>
          {pages.value.length === 0 && (
            <EmptyState icon={DocumentIcon} title="No pages yet">
              Start with the ones customers look for before buying, listed below.
            </EmptyState>
          )}
          {PAGE_SECTIONS.map(({ section, title }) => {
            const inSection = pages.value!.filter((p) => p.section === section);
            if (inSection.length === 0) return null;
            return (
              <div key={section} className="mb-6">
                <SectionLabel>{title}</SectionLabel>
                <ListCard>
                  {inSection.map((page) => (
                    <PageRow
                      key={page.id}
                      page={page}
                      onEdit={() =>
                        setEditing({
                          id: page.id,
                          form: {
                            slug: page.slug,
                            section: page.section,
                            title: page.title,
                            summary: page.summary,
                            body: page.body,
                            published: page.published,
                          },
                        })
                      }
                      onRemoved={pages.reload}
                    />
                  ))}
                </ListCard>
              </div>
            );
          })}
          {missing.length > 0 && (
            <div className="mb-6">
              <SectionLabel>Suggested</SectionLabel>
              <ListCard>
                {missing.map((starter) => (
                  <button
                    key={starter.slug}
                    type="button"
                    className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-panel-2"
                    onClick={() => setEditing({ id: null, form: { ...emptyForm(starter.section), ...starter } })}
                  >
                    <PlusIcon size={18} className="text-accent-ink" />
                    <span className="min-w-0 flex-1">
                      <span className="block font-medium">{starter.title}</span>
                      <span className="block text-sm text-ink-faint">{pagePath(starter)}</span>
                    </span>
                  </button>
                ))}
              </ListCard>
            </div>
          )}
        </>
      )}
      {editing && (
        <PageSheet
          id={editing.id}
          initial={editing.form}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            pages.reload();
          }}
        />
      )}
    </section>
  );
}

function PageRow({ page, onEdit, onRemoved }: { page: ContentPage; onEdit: () => void; onRemoved: () => void }) {
  const action = useAction();
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <button type="button" onClick={onEdit} className="min-w-0 flex-1 text-left">
        <span className="block truncate font-medium">{page.title}</span>
        <span className={page.published ? "block text-sm text-ok" : "block text-sm text-ink-faint"}>
          {page.published ? "Published" : "Draft"} · {pagePath(page)}
        </span>
      </button>
      <button
        type="button"
        aria-label={`Delete ${page.title}`}
        disabled={action.busy}
        className="text-ink-faint hover:text-bad"
        onClick={async () => {
          if (!confirm(`Delete “${page.title}”? Its link disappears from the website.`)) return;
          if (await action.run(() => api.admin.storefront.removePage(page.id).then(() => true))) onRemoved();
        }}
      >
        <TrashIcon size={18} />
      </button>
    </div>
  );
}

function PageSheet({
  id,
  initial,
  onClose,
  onSaved,
}: {
  id: number | null;
  initial: ContentPageForm;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState(initial);
  // A new page's address follows its title until it is edited by hand.
  const [slugTouched, setSlugTouched] = useState(id !== null || initial.slug !== "");
  const action = useAction();
  const set = (patch: Partial<ContentPageForm>) => setForm((f) => ({ ...f, ...patch }));

  return (
    <Sheet open title={id ? "Edit page" : "New page"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField
          label="Title"
          maxLength={120}
          value={form.title}
          onChange={(e) => set({ title: e.target.value, ...(slugTouched ? {} : { slug: slugify(e.target.value) }) })}
        />
        <SelectField
          label="Shows in"
          hint="A footer column, or the blog (vaabhi.com/blog)"
          options={PAGE_SECTIONS.map(({ section, title }) => ({ value: section, label: title }))}
          value={form.section}
          onChange={(section) => set({ section })}
        />
        <TextField
          label="Web address"
          hint={`vaabhi.com${pagePath(form)}`}
          maxLength={60}
          value={form.slug}
          onChange={(e) => {
            setSlugTouched(true);
            set({ slug: slugify(e.target.value, true) });
          }}
        />
        <TextAreaField
          label="Text"
          rows={12}
          hint="Leave a blank line between paragraphs. Start a line with ## for a heading, or - for a bullet."
          maxLength={50000}
          value={form.body}
          onChange={(e) => set({ body: e.target.value })}
        />
        <TextField
          label="Short summary"
          hint="Shown by Google and in WhatsApp link previews"
          maxLength={300}
          value={form.summary ?? ""}
          onChange={(e) => set({ summary: e.target.value.trim() ? e.target.value : null })}
        />
        <ListCard>
          <ToggleRow
            title="Published"
            subtitle="Shows on the website and in its footer"
            checked={form.published}
            onChange={(published) => set({ published })}
          />
        </ListCard>
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy}
          onClick={async () => {
            if (!form.title.trim()) return action.setError("Give the page a title.");
            if (!form.slug) return action.setError("Give the page a web address.");
            if (!form.body.trim()) return action.setError("Write the page's text.");
            const body = { ...form, slug: form.slug.replace(/-$/, "") };
            const saved = await action.run(() =>
              id ? api.admin.storefront.updatePage(id, body) : api.admin.storefront.createPage(body),
            );
            if (saved) onSaved();
          }}
        >
          {action.busy ? "Saving…" : "Save"}
        </button>
      </div>
    </Sheet>
  );
}
