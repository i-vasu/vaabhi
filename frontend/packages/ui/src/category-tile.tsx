/** A round category link; shows the category's photo when it has one, else a colour placeholder. */
export function CategoryTile({
  label,
  href,
  colors,
  image,
}: {
  label: string;
  href: string;
  colors: [string, string];
  image?: { src: string; srcSet?: string };
}) {
  const circle =
    "size-[76px] overflow-hidden rounded-full border-[1.5px] border-brand-accent transition-transform group-hover:scale-105";
  return (
    <a href={href} className="group flex min-w-[76px] shrink-0 flex-col items-center gap-2">
      {image ? (
        // eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes; srcset picks one
        <img src={image.src} srcSet={image.srcSet} sizes="76px" alt="" className={`${circle} object-cover`} />
      ) : (
        <span
          aria-hidden="true"
          className={circle}
          style={{ backgroundImage: `linear-gradient(180deg, ${colors[0]}, ${colors[1]})` }}
        />
      )}
      <span className="whitespace-nowrap text-center text-sm text-text-primary">{label}</span>
    </a>
  );
}
