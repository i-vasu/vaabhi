import { registerOTel } from "@vercel/otel";

/**
 * Server-side traces to Grafana Cloud. Configured through the standard
 * OTEL_EXPORTER_OTLP_ENDPOINT / OTEL_EXPORTER_OTLP_HEADERS environment variables.
 */
export function register() {
  if (!process.env.OTEL_EXPORTER_OTLP_ENDPOINT) return;
  registerOTel({ serviceName: "vaabhi-storefront" });
}
