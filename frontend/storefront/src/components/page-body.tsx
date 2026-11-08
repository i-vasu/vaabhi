import type { ReactNode } from "react";

type Block = { kind: "heading" | "paragraph"; text: string } | { kind: "list"; items: string[] };

/**
 * Splits a page body written in the dashboard into blocks: blank lines separate blocks, a block starting with
 * "## " is a heading, and a block whose lines all start with "- " is a bullet list. Text is shown as typed
 * (never as HTML), so nothing written in the dashboard can inject markup.
 */
export function parseBody(body: string): Block[] {
  return body
    .replace(/\r\n?/g, "\n")
    .split(/\n\s*\n/)
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk): Block => {
      if (chunk.startsWith("## ")) return { kind: "heading", text: chunk.slice(3).trim() };
      const lines = chunk.split("\n").map((l) => l.trim());
      if (lines.every((l) => l.startsWith("- "))) return { kind: "list", items: lines.map((l) => l.slice(2).trim()) };
      return { kind: "paragraph", text: lines.join("\n") };
    });
}

export function PageBody({ body }: Readonly<{ body: string }>): ReactNode {
  return (
    <div className="flex flex-col gap-4 text-base leading-relaxed text-text-secondary">
      {parseBody(body).map((block, i) => {
        if (block.kind === "heading")
          return (
            <h2 key={i} className="mt-4 font-serif text-[22px] font-semibold text-text-primary">
              {block.text}
            </h2>
          );
        if (block.kind === "list")
          return (
            <ul key={i} className="flex list-disc flex-col gap-1.5 pl-5">
              {block.items.map((item, j) => (
                <li key={j}>{item}</li>
              ))}
            </ul>
          );
        return (
          <p key={i} className="whitespace-pre-line">
            {block.text}
          </p>
        );
      })}
    </div>
  );
}
