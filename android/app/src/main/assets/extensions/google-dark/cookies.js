// Says no to cookie banners, in three steps. A known consent platform is
// told to reject everything through its own API; otherwise a button inside
// the banner that refuses (or keeps only what is necessary) is clicked;
// otherwise the banner is left for the person to answer.
(async () => {
  if (window !== window.top) return;
  const enabled = await browser.runtime.sendNativeMessage("zen", { type: "cookieBanners" }).catch(() => false);
  if (enabled !== true) return;

  const page = window.wrappedJSObject;

  // Step 1: consent platforms whose API can refuse on the visitor's behalf.
  const platforms = [
    () => page.OneTrust?.RejectAll && (page.OneTrust.RejectAll(), true),
    () => page.Didomi?.setUserDisagreeToAll && (page.Didomi.setUserDisagreeToAll(), true),
    () => page.Cookiebot?.decline && (page.Cookiebot.decline(), true),
    () => page.CookieConsent?.decline && (page.CookieConsent.decline(), true),
    () => page.UC_UI?.denyAllConsents && (page.UC_UI.denyAllConsents(), page.UC_UI.closeCMP?.(), true),
    () => page.axeptioSDK?.requestConsents && page.axeptioSDK.rejectAll && (page.axeptioSDK.rejectAll(), true),
    () => page.__ucCmp?.denyAllConsents && (page.__ucCmp.denyAllConsents(), true),
    () => page.Osano?.cm?.denyAll && (page.Osano.cm.denyAll(), true),
  ];

  const viaPlatform = () => {
    for (const reject of platforms) {
      try {
        if (reject()) return true;
      } catch (_) {}
    }
    return false;
  };

  // Step 2: a refusing button inside something that is clearly a cookie banner.
  const refuse = [
    /^(tout |tous )?refuser( tout| tous| les cookies)?$/,
    /^reject( all)?( cookies)?$/,
    /^decline( all)?( cookies)?$/,
    /^deny( all)?$/,
    /^(i )?disagree$/,
    /^continuer sans accepter$/,
    /^continue without accepting$/,
    /^(only|use only|accept only) (strictly )?necessary( cookies)?$/,
    /^(strictly )?necessary (cookies )?only$/,
    /^(n'accepter que|accepter uniquement) les (cookies )?(strictement )?n[ée]cessaires$/,
    /^(uniquement|seulement) (les )?(cookies )?n[ée]cessaires$/,
    /^ablehnen$/, /^alle ablehnen$/, /^rechazar( todo| todas)?$/, /^rifiuta( tutto)?$/,
  ];
  const aboutCookies = /cookie|consent|consentement|témoins|traceurs|privacy|confidentialit|rgpd|gdpr/i;

  const isBanner = (el) => {
    for (let node = el; node && node !== document.body; node = node.parentElement) {
      const style = getComputedStyle(node);
      const floating = style.position === "fixed" || style.position === "sticky" || node.getAttribute("role") === "dialog" || node.tagName === "DIALOG";
      if (floating && aboutCookies.test(node.textContent || "")) return true;
    }
    return false;
  };

  const roots = () => {
    const found = [document];
    for (const el of document.querySelectorAll("*")) if (el.shadowRoot) found.push(el.shadowRoot);
    return found;
  };

  const viaButton = () => {
    for (const root of roots()) {
      for (const button of root.querySelectorAll("button, [role=button], a, input[type=button], input[type=submit]")) {
        const label = (button.innerText || button.value || button.getAttribute("aria-label") || "").trim().toLowerCase().replace(/\s+/g, " ");
        if (!label || label.length > 60 || !refuse.some((r) => r.test(label))) continue;
        if (!button.offsetParent && getComputedStyle(button).position !== "fixed") continue;
        if (!isBanner(button)) continue;
        button.click();
        return true;
      }
    }
    return false;
  };

  // Banners often appear a little after the page; look again for a few seconds, then leave it be.
  let tries = 0;
  const attempt = () => {
    if (viaPlatform() || viaButton()) return;
    if (++tries < 12) setTimeout(attempt, 700);
  };
  if (document.readyState === "loading") addEventListener("DOMContentLoaded", attempt, { once: true });
  else attempt();
})();
