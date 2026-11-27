// Turning the phone sideways while a video plays goes fullscreen, like the
// YouTube app; turning it back upright leaves fullscreen.
const area = (el) => {
  const r = el.getBoundingClientRect();
  return r.width * r.height;
};

const playingVideo = () =>
  [...document.querySelectorAll("video")]
    .filter((v) => !v.paused && !v.ended && v.readyState > 2 && area(v) > 0)
    .sort((a, b) => area(b) - area(a))[0];

// Prefer the site's player box (it keeps the site's own controls) when it hugs the video.
const playerOf = (video) => {
  let target = video;
  for (let el = video.parentElement; el && el !== document.body; el = el.parentElement) {
    if (area(el) > area(video) * 1.3) break;
    target = el;
  }
  return target;
};

// Entering fullscreen while the page is still laid out for portrait leaves it
// frozen at the old size on some sites, so wait for the landscape layout first.
const landscapeLayout = () =>
  new Promise((resolve) => {
    if (innerWidth > innerHeight) return resolve();
    const done = () => {
      removeEventListener("resize", check);
      resolve();
    };
    const check = () => innerWidth > innerHeight && done();
    addEventListener("resize", check);
    setTimeout(done, 600);
  });

// The site's own button sets the player up for fullscreen; covering a wrapper ourselves can leave it grey.
const FULLSCREEN_LABEL = /full ?screen|plein [ée]cran|vollbild|pantalla completa/i;
const fullscreenButton = (video) => {
  for (let el = video.parentElement; el && el !== document.body; el = el.parentElement) {
    const button = [...el.querySelectorAll("button, [role=button]")].find((b) =>
      FULLSCREEN_LABEL.test([b.getAttribute("aria-label"), b.title, b.className, b.dataset?.titleNoTooltip].join(" ")),
    );
    if (button) return button;
    if (area(el) > area(video) * 1.5) return null;
  }
  return null;
};

const showsVideo = (video) => {
  const r = video.getBoundingClientRect();
  return document.fullscreenElement?.contains(video) && r.width > innerWidth / 2 && r.height > 0;
};

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

// Each fullscreen request is announced: the app only lets it through right after a fresh notice.
const announce = () => browser.runtime.sendNativeMessage("zen", { type: "videoRotated" }).catch(() => {});

const enterFullscreen = async (video) => {
  await announce();
  const button = fullscreenButton(video);
  if (button) button.click();
  else await playerOf(video).requestFullscreen().catch(() => {});
  await wait(500);
  if (showsVideo(video)) return;
  // Still grey or nothing happened: the bare video always draws itself.
  if (document.fullscreenElement) await document.exitFullscreen().catch(() => {});
  await announce();
  await video.requestFullscreen().catch(() => {});
};

screen.orientation.addEventListener("change", async () => {
  if (screen.orientation.type.startsWith("landscape")) {
    if (document.fullscreenElement) return;
    const video = playingVideo();
    if (!video) return;
    await landscapeLayout();
    // Players still resize for a moment after the layout turns; going fullscreen during that shows grey.
    await wait(700);
    if (!video.paused) await enterFullscreen(video);
  } else if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => {});
  }
});
