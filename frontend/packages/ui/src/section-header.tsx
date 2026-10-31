import { ChevronRightIcon } from "./icons";

export function SectionHeader({
  title,
  action,
  id,
}: {
  title: string;
  action?: { label: string; href: string };
  id?: string;
}) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex flex-col gap-1.5">
        <h2 id={id} className="font-serif text-[26px] font-semibold leading-tight text-text-primary">
          {title}
        </h2>
        <span aria-hidden="true" className="h-[1.5px] w-10 bg-brand-accent" />
      </div>
      {action && (
        <a href={action.href} className="flex items-center gap-0.5 text-sm text-brand-primary hover:underline">
          {action.label}
          <ChevronRightIcon size={16} />
        </a>
      )}
    </div>
  );
}
