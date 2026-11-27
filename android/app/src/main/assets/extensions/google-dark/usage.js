// The site panel shows how much this site keeps on the phone; GeckoView has no API for it, the page does.
const report = async () => {
  let bytes = 0;
  try {
    bytes = (await navigator.storage.estimate()).usage || 0;
  } catch {}
  bytes += new Blob([document.cookie]).size;
  browser.runtime.sendNativeMessage("zen", { type: "siteUsage", bytes }).catch(() => {});
};
report();
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) report();
});
