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

screen.orientation.addEventListener("change", async () => {
  if (screen.orientation.type.startsWith("landscape")) {
    if (document.fullscreenElement) return;
    const video = playingVideo();
    if (!video) return;
    // The app only lets a page cover the browser after a touch; this tells it a video asked, even a silent one.
    await browser.runtime.sendNativeMessage("zen", { type: "videoRotated" }).catch(() => {});
    await landscapeLayout();
    if (!video.paused) playerOf(video).requestFullscreen().catch(() => {});
  } else if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => {});
  }
});
