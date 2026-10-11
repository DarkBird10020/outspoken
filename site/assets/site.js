(() => {
  const REPO = "DarkBird10020/outspoken";
  const API = `https://api.github.com/repos/${REPO}`;
  const calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const nav = document.getElementById("nav");
  const onScroll = () => nav && nav.classList.toggle("scrolled", window.scrollY > 8);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // Reveal on scroll
  const reveals = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window && !calm) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    reveals.forEach((el) => io.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add("in"));
  }

  // Screens gallery arrows
  const gallery = document.querySelector("[data-gallery]");
  document.querySelectorAll("[data-scroll]").forEach((b) => b.addEventListener("click", () => {
    if (gallery) gallery.scrollBy({ left: Number(b.dataset.scroll) * 520, behavior: calm ? "auto" : "smooth" });
  }));

  // The phone mock plays a conversation: the visitor asks, the highlight steps, a blink says it.
  const cardsEl = document.querySelector("[data-cards]");
  const qEl = document.querySelector("[data-q]");
  const hint = document.querySelector("[data-hint-text]");
  const scenes = [
    { q: "Are you in pain?", cards: ["Yes, my back hurts", "A little, I can manage", "No pain right now", "Please call the nurse"], pick: 1 },
    { q: "Would you like some water?", cards: ["Yes please, I'm thirsty", "Maybe a little later", "Some tea instead", "No, thank you"], pick: 2 },
    { q: "How are you feeling today?", cards: ["Better than yesterday", "A bit tired", "Happy you are here", "I want to rest"], pick: 2 },
  ];
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));

  async function typeQuestion(text) {
    qEl.textContent = "";
    const caret = document.createElement("span");
    caret.className = "caret";
    for (let i = 1; i <= text.length; i++) {
      qEl.textContent = text.slice(0, i);
      qEl.appendChild(caret);
      await wait(42);
    }
    await wait(350);
    caret.remove();
  }

  async function play() {
    const cards = [...cardsEl.children];
    let s = 0;
    for (;;) {
      const scene = scenes[s % scenes.length];
      cards.forEach((c) => c.classList.remove("on", "said"));
      hint.textContent = "Listening";
      await typeQuestion(scene.q);
      cards.forEach((c, i) => { c.firstElementChild.textContent = scene.cards[i]; });
      cards[0].classList.add("on");
      hint.textContent = "Look up to move";
      await wait(1300);
      for (let i = 1; i <= scene.pick; i++) {
        cards[i - 1].classList.remove("on");
        cards[i].classList.add("on");
        await wait(1300);
      }
      hint.textContent = "Blink to say it";
      const badge = cards[scene.pick].querySelector(".badge");
      badge.classList.add("blink");
      await wait(520);
      badge.classList.remove("blink");
      cards[scene.pick].classList.add("said");
      badge.textContent = "Said";
      hint.textContent = "Spoken out loud";
      await wait(2200);
      badge.textContent = "Blink";
      s++;
    }
  }
  if (cardsEl && qEl && hint && !calm) play();

  // Latest release: the download links already point at it; show its build and date.
  const fmtDate = (iso) => new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
  const releaseLines = document.querySelectorAll("[data-release]");
  if (releaseLines.length) {
    fetch(`${API}/releases/latest`).then((r) => (r.ok ? r.json() : Promise.reject())).then((rel) => {
      const apk = (rel.assets || []).find((a) => a.name.endsWith(".apk"));
      const size = apk ? ` · ${Math.round(apk.size / 1e6)} MB` : "";
      const name = rel.name || rel.tag_name;
      releaseLines.forEach((el) => {
        el.innerHTML = "";
        const dot = document.createElement("span");
        dot.className = "dot";
        el.append(dot, `${name} · ${fmtDate(rel.published_at)}${size} · Android 12 or later`);
      });
    }).catch(() => {});
  }

  // Changelog: changelog.json is written when the site is published; GitHub's API is the fallback.
  const logEl = document.querySelector("[data-log]");
  if (!logEl) return;
  const limit = Number(logEl.dataset.limit) || Infinity;
  const search = document.querySelector("[data-search]");
  const hidden = /claude|anthropic|generated with|co-authored|🤖|^#+\s*test plan/i;

  function cleanBody(body) {
    const points = [];
    for (const raw of (body || "").split(/\r?\n/)) {
      const line = raw.trim();
      if (hidden.test(line) || /^#+\s*(test|testing|checks?|how to test|verification|notes?)/i.test(line) || (line.startsWith("#") && points.length)) break;
      const m = line.match(/^[-*]\s+(?:\[[ x]\]\s+)?(.+)/);
      if (m && m[1].length > 3) {
        const text = m[1].replace(/\*\*|`/g, "");
        points.push(text.length <= 160 ? text : text.split(/(?<=[.!?])\s/)[0].slice(0, 180));
      }
      if (points.length >= 5) break;
    }
    return points;
  }

  function fromApi(prs) {
    return prs.filter((p) => p.merged_at).map((p) => ({
      number: p.number, title: p.title, mergedAt: p.merged_at,
      author: p.user && p.user.login, url: p.html_url, points: cleanBody(p.body),
    }));
  }

  function render(entries) {
    const q = (search && search.value.trim().toLowerCase()) || "";
    const shown = entries.filter((e) => !q || (e.title + " " + e.points.join(" ")).toLowerCase().includes(q)).slice(0, limit);
    logEl.innerHTML = "";
    if (!shown.length) {
      const empty = document.createElement("div");
      empty.className = "log-empty glass";
      empty.textContent = q ? "No change matches that search." : "No changes to show yet.";
      logEl.append(empty);
      return;
    }
    shown.forEach((e, i) => {
      const art = document.createElement("article");
      art.className = "entry " + (i === 0 && !q ? "first" : "glass");
      const time = document.createElement("time");
      time.dateTime = e.mergedAt;
      time.textContent = fmtDate(e.mergedAt);
      const body = document.createElement("div");
      const h = document.createElement("h3");
      h.textContent = e.title;
      body.append(h);
      if (e.points.length) {
        const ul = document.createElement("ul");
        e.points.forEach((p) => { const li = document.createElement("li"); li.textContent = p; ul.append(li); });
        body.append(ul);
      }
      if (e.author) {
        const by = document.createElement("div");
        by.className = "by";
        const img = document.createElement("img");
        img.src = `https://github.com/${e.author}.png?size=44`;
        img.alt = "";
        img.loading = "lazy";
        by.append(img, e.author);
        body.append(by);
      }
      const tag = document.createElement("a");
      tag.className = "tag";
      tag.href = e.url;
      tag.target = "_blank";
      tag.rel = "noopener";
      tag.textContent = `#${e.number}`;
      tag.setAttribute("aria-label", `Pull request ${e.number} on GitHub`);
      art.append(time, body, tag);
      logEl.append(art);
    });
  }

  fetch("changelog.json", { cache: "no-cache" })
    .then((r) => (r.ok ? r.json() : Promise.reject()))
    .catch(() => fetch(`${API}/pulls?state=closed&per_page=100`).then((r) => (r.ok ? r.json() : Promise.reject())).then(fromApi))
    .then((entries) => {
      entries.sort((a, b) => b.mergedAt.localeCompare(a.mergedAt));
      render(entries);
      if (search) search.addEventListener("input", () => render(entries));
    })
    .catch(() => {
      logEl.innerHTML = "";
      const empty = document.createElement("div");
      empty.className = "log-empty glass";
      empty.innerHTML = 'The changelog could not load. See it on <a href="https://github.com/DarkBird10020/outspoken/pulls?q=is%3Apr+is%3Amerged" style="text-decoration:underline">GitHub</a>.';
      logEl.append(empty);
    });
})();
