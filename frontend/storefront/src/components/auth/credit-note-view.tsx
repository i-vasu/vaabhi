import type { StoreCreditNote } from "@vaabhi/api-client";
import { formatPrice } from "@/lib/format";

const day = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});

/** A customer's credit note, laid out to read on screen and print on paper. */
export function CreditNoteView({ note }: Readonly<{ note: StoreCreditNote }>) {
  const { seller, buyer } = note;
  const sellerState = seller.state ? `State: ${seller.state}${seller.stateCode ? ` (${seller.stateCode})` : ""}` : null;
  const sellerLines = [seller.address, seller.cin ? `CIN: ${seller.cin}` : null, sellerState].filter(Boolean);
  const buyerLines = [buyer.address, buyer.state, buyer.phone].filter(Boolean);
  const taxed = note.cgstRupees + note.sgstRupees + note.igstRupees > 0;
  const inter = note.supplyType === "INTER";

  return (
    <article className="text-left">
      <h1 className="mb-1 font-serif text-[32px] font-semibold">{note.title}</h1>
      <p className="mb-1 text-sm text-text-secondary">
        Credit note {note.number} · Order {note.orderNumber} · {day.format(new Date(note.issuedOn))}
      </p>
      <p className="mb-6 text-sm text-text-secondary">Against invoice {note.invoiceNumber}</p>

      <div className="mb-6 grid gap-6 text-sm sm:grid-cols-2">
        <section>
          <h2 className="mb-1 font-medium">Issued by</h2>
          <p>{seller.name}</p>
          {sellerLines.map((line, i) => (
            <p key={`${i}-${line}`} className="text-text-secondary">
              {line}
            </p>
          ))}
        </section>
        <section>
          <h2 className="mb-1 font-medium">Issued to</h2>
          <p>{buyer.name}</p>
          {buyerLines.map((line, i) => (
            <p key={`${i}-${line}`} className="text-text-secondary">
              {line}
            </p>
          ))}
        </section>
      </div>

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border-default text-left">
            <th className="py-2 font-medium">Item</th>
            {taxed && <th className="py-2 font-medium">HSN/SAC</th>}
            <th className="py-2 text-right font-medium">Qty</th>
            <th className="py-2 text-right font-medium">Rate</th>
            {taxed && <th className="py-2 text-right font-medium">GST</th>}
            <th className="py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody>
          {note.lines.map((line, index) => (
            <tr key={index} className="border-b border-border-default">
              <td className="py-2">{line.description}</td>
              {taxed && <td className="py-2">{line.hsnCode ?? ""}</td>}
              <td className="py-2 text-right tabular-nums">{line.quantity}</td>
              <td className="py-2 text-right tabular-nums">{formatPrice(line.unitRupees)}</td>
              {taxed && <td className="py-2 text-right tabular-nums">{line.gstRate}%</td>}
              <td className="py-2 text-right tabular-nums">{formatPrice(line.amountRupees)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <dl className="ml-auto mt-4 flex max-w-xs flex-col gap-1 text-sm">
        <Row label="Taxable value" value={note.taxableRupees} />
        {inter ? (
          note.igstRupees > 0 && <Row label="IGST" value={note.igstRupees} />
        ) : (
          <>
            {note.cgstRupees > 0 && <Row label="CGST" value={note.cgstRupees} />}
            {note.sgstRupees > 0 && <Row label="SGST" value={note.sgstRupees} />}
          </>
        )}
        <Row label="Total" value={note.totalRupees} strong />
      </dl>
    </article>
  );
}

function Row({ label, value, strong }: Readonly<{ label: string; value: number; strong?: boolean }>) {
  return (
    <div className={`flex justify-between gap-4 ${strong ? "border-t border-border-default pt-1 font-semibold" : ""}`}>
      <dt>{label}</dt>
      <dd className="tabular-nums">{formatPrice(value)}</dd>
    </div>
  );
}
