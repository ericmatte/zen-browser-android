// Google picks its theme on the server from the Sec-CH-Prefers-Color-Scheme
// client hint, which Gecko never sends, so private tabs announce dark here.
browser.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    if (!details.incognito) return {};
    const headers = details.requestHeaders.filter((h) => h.name.toLowerCase() !== "sec-ch-prefers-color-scheme");
    headers.push({ name: "Sec-CH-Prefers-Color-Scheme", value: "dark" });
    return { requestHeaders: headers };
  },
  { urls: ["*://*.google.com/*", "*://*.google.ca/*", "*://*.google.fr/*", "*://*.google.co.uk/*"], types: ["main_frame", "sub_frame", "xmlhttprequest"] },
  ["blocking", "requestHeaders"]
);
