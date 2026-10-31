export function CategoryTile({ label, href, colors }: { label: string; href: string; colors: [string, string] }) {
  return (
    <a href={href} className="group flex min-w-[76px] shrink-0 flex-col items-center gap-2">
      <span
        aria-hidden="true"
        className="size-[76px] rounded-full border-[1.5px] border-brand-accent transition-transform group-hover:scale-105"
        style={{ backgroundImage: `linear-gradient(180deg, ${colors[0]}, ${colors[1]})` }}
      />
      <span className="whitespace-nowrap text-center text-sm text-text-primary">{label}</span>
    </a>
  );
}
