// Mobile-first pages often have one element a few pixels too wide, which lets
// the whole page slide sideways onto nothing. Pages laid out for the phone's
// width never mean to scroll sideways, so the page clips that overflow
// ("clip" keeps position: sticky working, unlike "hidden"). Both the root and
// the body clip: Gecko widens the phone viewport to fit the body's overflow
// otherwise. Pinch-zoom still pans.
//
// Pages that set overflow on <html> or <body> themselves are left alone:
// adding a clip there made some of them (e.g. Wikipedia) scroll the <html>
// element instead of the window, which hid scrolling from the app.
const laidOutForPhone = () =>
  !!document.querySelector('meta[name="viewport" i][content*="device-width" i]') &&
  document.documentElement.clientWidth <= screen.width + 1;

const leavesOverflowAlone = (el) => {
  const style = getComputedStyle(el);
  return style.overflowX === "visible" && style.overflowY === "visible";
};

const clipSideways = () => {
  const root = document.documentElement;
  const body = document.body;
  if (window !== window.top || !body || !laidOutForPhone()) return;
  if (root.style.overflowX === "clip") return;
  if (!leavesOverflowAlone(root) || !leavesOverflowAlone(body)) return;
  for (const el of [root, body]) el.style.setProperty("overflow-x", "clip", "important");
};

clipSideways();
addEventListener("load", clipSideways, { once: true });
