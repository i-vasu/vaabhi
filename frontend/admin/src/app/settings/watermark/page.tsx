"use client";

import type { WatermarkSettings } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { ListCard, LoadState, PageHeader, SaveBar, SelectField, TextField, ToggleRow } from "@/components/ui";
import { orNull, useSection } from "@/lib/settings";

const positions: { value: WatermarkSettings["position"]; label: string }[] = [
  { value: "BOTTOM_RIGHT", label: "Bottom right" },
  { value: "BOTTOM_LEFT", label: "Bottom left" },
  { value: "CENTER", label: "Centre" },
  { value: "TOP_RIGHT", label: "Top right" },
];

const placement: Record<WatermarkSettings["position"], string> = {
  BOTTOM_RIGHT: "bottom-3 right-3",
  BOTTOM_LEFT: "bottom-3 left-3",
  CENTER: "inset-0 m-auto h-fit w-fit",
  TOP_RIGHT: "top-3 right-3",
};

export default function WatermarkPage() {
  const section = useSection("watermark");
  const w = section.value;

  if (!w) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Gallery watermark" backHref="/settings/printing/" />
        <LoadState loading={section.loading} error={section.loadError} />
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Gallery watermark" backHref="/settings/printing/" />
      <ListCard>
        <ToggleRow
          title="Watermark shared photos"
          subtitle="Adds your mark to design photos you share from the gallery"
          checked={w.enabled}
          onChange={(enabled) => section.update({ enabled })}
        />
      </ListCard>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <TextField
          label="Text"
          hint="Leave blank to use the shop name"
          maxLength={60}
          value={w.text ?? ""}
          onChange={(event) => section.update({ text: orNull(event.target.value) })}
        />
        <SelectField
          label="Position"
          options={positions}
          value={w.position}
          onChange={(position) => section.update({ position })}
        />
        <TextField
          label={`Opacity: ${w.opacityPercent}%`}
          type="range"
          min={10}
          max={100}
          step={5}
          value={w.opacityPercent}
          onChange={(event) => section.update({ opacityPercent: Number(event.target.value) })}
          className="sm:col-span-2"
        />
      </div>
      <div
        aria-label="Watermark preview"
        role="img"
        className="relative mt-6 aspect-[4/3] max-w-sm overflow-hidden rounded-2xl bg-gradient-to-br from-panel-2 to-accent-soft"
      >
        <span
          className={cn("absolute font-semibold tracking-[0.2em] text-white", placement[w.position])}
          style={{ opacity: w.enabled ? w.opacityPercent / 100 : 0.15 }}
        >
          {w.text || "VAABHI"}
        </span>
      </div>
      <SaveBar saving={section.saving} saved={section.saved} error={section.error} onSave={() => void section.save()} />
    </section>
  );
}
