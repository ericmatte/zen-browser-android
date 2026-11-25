// Google decides its theme on the server and serves GeckoView the light one,
// so prefers-color-scheme alone never turns it dark. Invert the page and
// re-invert media: hues survive (invert + 180° hue rotation), lightness flips.
const query = window.matchMedia("(prefers-color-scheme: dark)");
const style = document.createElement("style");
style.textContent = `
  html { filter: invert(1) hue-rotate(180deg); background: #fff !important; }
  img, video, picture, canvas, iframe, svg image, [style*="background-image"] {
    filter: invert(1) hue-rotate(180deg);
  }
`;
const apply = () => {
  const root = document.documentElement;
  if (!root) return;
  if (query.matches && !style.isConnected) root.appendChild(style);
  if (!query.matches && style.isConnected) style.remove();
};
apply();
document.addEventListener("DOMContentLoaded", apply);
query.addEventListener("change", apply);
