/*
 * User-assisted DOM picker for Android WebView.
 * It reads the clicked public DOM node and selector metadata only; it never submits forms,
 * performs playback, downloads media, or reads input values.
 */
(function () {
  if (window.MusicCrawlerPicker) return;

  const forbidden = /^(SCRIPT|STYLE|IFRAME|INPUT|TEXTAREA|SELECT|OPTION|BUTTON)$/;
  const semantic = /(title|name|artist|album|track|song|lyric|cover|image|photo|genre|date|release|description|intro)/i;
  const unstable = /(^|[-_])[a-f0-9]{8,}($|[-_])|\d{4,}/i;
  let active = false;
  let lastOutline = null;

  function visible(el) {
    if (!el || forbidden.test(el.tagName) || el.isContentEditable) return false;
    const style = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0;
  }

  function esc(value) {
    if (window.CSS && CSS.escape) return CSS.escape(value);
    return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
  }

  function attrEsc(value) {
    return String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  }

  function stableClasses(el) {
    return Array.from(el.classList || [])
      .filter(c => c && !unstable.test(c) && c.length < 50)
      .slice(0, 3);
  }

  function segment(el) {
    const classes = stableClasses(el);
    return el.tagName.toLowerCase() + classes.map(c => "." + esc(c)).join("");
  }

  function makeCandidates(target) {
    const found = new Map();
    function add(selector, score) {
      if (!selector || selector.length > 512 || found.has(selector)) return;
      try {
        const nodes = document.querySelectorAll(selector);
        if (nodes.length === 1 && nodes[0] === target) {
          found.set(selector, { selector, matchCount: 1, selectsClickedElement: true, stabilityScore: Math.max(0, Math.min(100, score)) });
        }
      } catch (_) { /* Invalid CSS candidate is ignored. */ }
    }

    if (target.id && !unstable.test(target.id)) add("#" + esc(target.id), 58);

    ["data-testid", "data-test", "data-qa", "itemprop", "role", "aria-label"].forEach(name => {
      const value = target.getAttribute(name);
      if (!value || value.length > 80) return;
      const score = name.startsWith("data-") || name === "itemprop" ? 94 : 68;
      add("[" + name + '="' + attrEsc(value) + '"]', score);
    });

    const tag = target.tagName.toLowerCase();
    const classes = stableClasses(target);
    classes.forEach(c => add(tag + "." + esc(c), semantic.test(c) ? 88 : 72));
    if (classes.length > 1) {
      add(tag + classes.map(c => "." + esc(c)).join(""), classes.some(c => semantic.test(c)) ? 90 : 74);
    }

    // Short ancestor chains let a second manually selected item confirm the repeated card layout.
    let node = target;
    const chain = [];
    for (let depth = 0; node && node.nodeType === 1 && depth < 4; depth++, node = node.parentElement) {
      chain.push(segment(node));
      if (depth > 0) {
        const selector = chain.slice().reverse().join(" > ");
        const score = Math.max(52, (semantic.test(chain.join(" ")) ? 80 : 62) - depth * 3);
        add(selector, score);
      }
    }
    add(tag, 45);
    return Array.from(found.values()).sort((a, b) => b.stabilityScore - a.stabilityScore || a.selector.length - b.selector.length).slice(0, 30);
  }

  function selectTarget(event) {
    let el = event.target && event.target.nodeType === 1 ? event.target : event.target && event.target.parentElement;
    if (!el) return null;
    const image = el.closest("img");
    if (image && visible(image)) return image;
    const link = el.closest("a[href]");
    if (link && visible(link)) return link;
    const meaningful = el.closest("h1,h2,h3,h4,h5,h6,p,li,dt,dd,time,figcaption");
    return meaningful && visible(meaningful) ? meaningful : (visible(el) ? el : null);
  }

  function onClick(event) {
    if (!active) return;
    const target = selectTarget(event);
    if (!target) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.stopImmediatePropagation) event.stopImmediatePropagation();

    if (lastOutline) lastOutline.style.outline = lastOutline.__mcPreviousOutline || "";
    target.__mcPreviousOutline = target.style.outline;
    target.style.outline = "3px solid #ff7a00";
    lastOutline = target;

    const attributes = {};
    ["src", "data-src", "data-original", "href", "alt", "title", "content", "itemprop", "data-testid", "data-qa", "class", "id", "style"].forEach(name => {
      const value = target.getAttribute(name);
      if (value && value.length < 300) attributes[name] = value;
    });
    const text = (target.innerText || target.textContent || "").replace(/\s+/g, " ").trim().slice(0, 160);
    const payload = {
      pageUrl: location.href,
      tagName: target.tagName.toLowerCase(),
      textSample: text,
      attributes,
      candidates: makeCandidates(target)
    };
    const serialized = JSON.stringify(payload);
    if (window.MusicCrawlerBridge && typeof window.MusicCrawlerBridge.postMessage === "function") {
      window.MusicCrawlerBridge.postMessage(serialized);
    } else {
      window.dispatchEvent(new CustomEvent("music-crawler-selection", { detail: payload }));
    }
  }

  window.MusicCrawlerPicker = {
    enable: function () {
      if (active) return;
      active = true;
      document.addEventListener("click", onClick, true);
      document.documentElement.style.cursor = "crosshair";
    },
    disable: function () {
      active = false;
      document.removeEventListener("click", onClick, true);
      document.documentElement.style.cursor = "";
      if (lastOutline) lastOutline.style.outline = lastOutline.__mcPreviousOutline || "";
      lastOutline = null;
    },
    isEnabled: function () { return active; }
  };
})();
