// Makes Vaabhi Admin installable. Pages always come from the network (orders, stock and prices change all the time);
// when there is no network, a short offline page shows instead of the browser's error.
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));
self.addEventListener("fetch", (event) => {
  if (event.request.mode !== "navigate") return;
  event.respondWith(
    fetch(event.request).catch(
      () =>
        new Response(
          '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Offline</title>' +
            '<body style="font-family:system-ui;background:#faf7f2;color:#2b2622;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center">' +
            "<div><h1>You are offline</h1><p>Check the internet connection and try again.</p></div>",
          { headers: { "Content-Type": "text/html; charset=utf-8" } },
        ),
    ),
  );
});
