// Mobile-first pages often have one element a few pixels too wide, which lets
// the whole page slide sideways onto nothing. Pages laid out for the phone's
// width never mean to scroll sideways, so the page clips that overflow
// ("clip" keeps position: sticky working, unlike "hidden"). The root alone is not
// enough: Gecko widens the phone viewport to fit the body's overflow, so the body
// clips too. Pinch-zoom still pans.
const laidOutForPhone = () =>
  !!document.querySelector('meta[name="viewport" i][content*="device-width" i]') &&
  document.documentElement.clientWidth <= screen.width + 1;

const clipSideways = () => {
  if (window !== window.top || !laidOutForPhone()) return;
  for (const el of [document.documentElement, document.body]) el?.style.setProperty("overflow-x", "clip", "important");
};

clipSideways();
addEventListener("load", clipSideways, { once: true });
