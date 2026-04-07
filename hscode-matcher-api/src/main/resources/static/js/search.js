(function () {
  const SUGGEST_LIMIT = 8;

  const qInput = document.getElementById("q");
  const langSelect = document.getElementById("lang");
  const suggestionsEl = document.getElementById("suggestions");
  const resultsEl = document.getElementById("results");
  const metaEl = document.getElementById("meta");
  const errorEl = document.getElementById("error");
  const explainWrap = document.getElementById("explain-wrap");
  const explainPre = document.getElementById("explain-pre");
  const abPanel = document.getElementById("ab-panel");
  const abResultsHybrid = document.getElementById("ab-results-hybrid");
  const abResultsLexical = document.getElementById("ab-results-lexical");
  const abMetaHybrid = document.getElementById("ab-meta-hybrid");
  const abMetaLexical = document.getElementById("ab-meta-lexical");

  const labHybrid = document.getElementById("lab-hybrid");
  const labHybridMinLen = document.getElementById("lab-hybrid-min-len");
  const labLimit = document.getElementById("lab-limit");
  const labFuzzy = document.getElementById("lab-fuzzy");
  const labMinFuzzyTokens = document.getElementById("lab-min-fuzzy-tokens");
  const labExplain = document.getElementById("lab-explain");
  const labMinHybridServer = document.getElementById("lab-min-hybrid-server");
  const labEmbedTimeout = document.getElementById("lab-embed-timeout");
  const labDebounce = document.getElementById("lab-debounce");
  const labRrfK = document.getElementById("lab-rrf-k");
  const labPoolMult = document.getElementById("lab-pool-mult");
  const labAb = document.getElementById("lab-ab");

  const LANG_KEY = "hs-matcher-ui-lang";
  const LAB_KEYS = {
    hybrid: "hs-matcher-lab-hybrid",
    hybridMinLen: "hs-matcher-lab-hybrid-min-len",
    limit: "hs-matcher-lab-limit",
    fuzzy: "hs-matcher-lab-fuzzy",
    minFuzzyTokens: "hs-matcher-lab-min-fuzzy-tokens",
    explain: "hs-matcher-lab-explain",
    minHybridServer: "hs-matcher-lab-min-hybrid-server",
    embedTimeout: "hs-matcher-lab-embed-timeout",
    debounce: "hs-matcher-lab-debounce",
    rrfK: "hs-matcher-lab-rrf-k",
    poolMult: "hs-matcher-lab-pool-mult",
    ab: "hs-matcher-lab-ab",
  };

  let debounceTimer = null;
  let lastController = null;
  let abAbort = null;
  let hideSuggestionsTimer = null;

  function getLimit() {
    const n = parseInt(String(labLimit.value || "20"), 10);
    if (Number.isFinite(n)) {
      return Math.min(50, Math.max(1, n));
    }
    return 20;
  }

  function getDebounceMs() {
    const n = parseInt(String(labDebounce.value || "280"), 10);
    if (Number.isFinite(n) && n >= 0) {
      return Math.min(2000, n);
    }
    return 280;
  }

  function loadLang() {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved && ["FR", "EN", "DE"].includes(saved)) {
      langSelect.value = saved;
    }
  }

  function saveLang() {
    localStorage.setItem(LANG_KEY, langSelect.value);
  }

  function loadLab() {
    if (localStorage.getItem(LAB_KEYS.hybrid) === "0") {
      labHybrid.checked = false;
    }
    if (localStorage.getItem(LAB_KEYS.fuzzy) === "0") {
      labFuzzy.checked = false;
    }
    if (localStorage.getItem(LAB_KEYS.explain) === "1") {
      labExplain.checked = true;
    }
    if (localStorage.getItem(LAB_KEYS.ab) === "1") {
      labAb.checked = true;
    }
    [
      [LAB_KEYS.hybridMinLen, labHybridMinLen],
      [LAB_KEYS.limit, labLimit],
      [LAB_KEYS.minFuzzyTokens, labMinFuzzyTokens],
      [LAB_KEYS.minHybridServer, labMinHybridServer],
      [LAB_KEYS.embedTimeout, labEmbedTimeout],
      [LAB_KEYS.debounce, labDebounce],
      [LAB_KEYS.rrfK, labRrfK],
      [LAB_KEYS.poolMult, labPoolMult],
    ].forEach(([k, el]) => {
      const v = localStorage.getItem(k);
      if (v != null && v !== "") {
        el.value = v;
      }
    });
  }

  function saveLab() {
    localStorage.setItem(LAB_KEYS.hybrid, labHybrid.checked ? "1" : "0");
    localStorage.setItem(LAB_KEYS.fuzzy, labFuzzy.checked ? "1" : "0");
    localStorage.setItem(LAB_KEYS.explain, labExplain.checked ? "1" : "0");
    localStorage.setItem(LAB_KEYS.ab, labAb.checked ? "1" : "0");
    localStorage.setItem(LAB_KEYS.hybridMinLen, labHybridMinLen.value);
    localStorage.setItem(LAB_KEYS.limit, labLimit.value);
    localStorage.setItem(LAB_KEYS.minFuzzyTokens, labMinFuzzyTokens.value);
    localStorage.setItem(LAB_KEYS.minHybridServer, labMinHybridServer.value);
    localStorage.setItem(LAB_KEYS.embedTimeout, labEmbedTimeout.value);
    localStorage.setItem(LAB_KEYS.debounce, labDebounce.value);
    localStorage.setItem(LAB_KEYS.rrfK, labRrfK.value);
    localStorage.setItem(LAB_KEYS.poolMult, labPoolMult.value);
  }

  function debounce(fn) {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(fn, getDebounceMs());
  }

  function escapeHtml(s) {
    if (!s) return "";
    const div = document.createElement("div");
    div.textContent = s;
    return div.innerHTML;
  }

  function formatHierarchy(h) {
    if (!h) return "";
    const parts = [];
    if (h.chapter) {
      parts.push(`${h.chapter.code} — ${h.chapter.description || ""}`);
    }
    if (h.heading) {
      parts.push(`${h.heading.code} — ${h.heading.description || ""}`);
    }
    return parts.length ? parts.join(" · ") : "";
  }

  function effectiveQueryLength(q) {
    return (q || "").trim().length;
  }

  function shouldSendHybrid(qTrimmed) {
    if (!labHybrid.checked) {
      return false;
    }
    const minH = parseInt(String(labHybridMinLen.value || "0"), 10);
    const need = Number.isFinite(minH) && minH > 0 ? minH : 0;
    if (need <= 0) {
      return true;
    }
    return effectiveQueryLength(qTrimmed) >= need;
  }

  /**
   * @param {"auto"|true|false} hybridMode auto = client gate ; true/false = force API
   */
  function buildSearchParams(q, lang, hybridMode) {
    const params = new URLSearchParams();
    params.set("q", q);
    params.set("lang", lang);
    params.set("limit", String(getLimit()));

    if (hybridMode === false) {
      params.set("hybrid", "false");
    } else if (hybridMode === true) {
      params.set("hybrid", "true");
    } else if (!shouldSendHybrid(q)) {
      params.set("hybrid", "false");
    }

    if (!labFuzzy.checked) {
      params.set("fuzzy", "false");
    }
    const mft = String(labMinFuzzyTokens.value || "").trim();
    if (mft !== "") {
      const n = parseInt(mft, 10);
      if (Number.isFinite(n) && n > 0) {
        params.set(
          "minFuzzyTokenLength",
          String(Math.min(32, Math.max(2, n)))
        );
      }
    }
    if (labExplain.checked) {
      params.set("explain", "true");
    }
    const mhs = String(labMinHybridServer.value || "").trim();
    if (mhs !== "") {
      const n = parseInt(mhs, 10);
      if (Number.isFinite(n) && n >= 0) {
        params.set("minHybridChars", String(Math.min(256, n)));
      }
    }
    const et = String(labEmbedTimeout.value || "").trim();
    if (et !== "") {
      const n = parseInt(et, 10);
      if (Number.isFinite(n) && n > 0) {
        params.set("embedTimeoutMs", String(Math.min(120000, n)));
      }
    }

    const rk = String(labRrfK.value || "").trim();
    if (rk !== "") {
      const n = parseInt(rk, 10);
      if (Number.isFinite(n)) {
        params.set("rrfK", String(Math.min(200, Math.max(5, n))));
      }
    }
    const pm = String(labPoolMult.value || "").trim();
    if (pm !== "") {
      const n = parseInt(pm, 10);
      if (Number.isFinite(n)) {
        params.set("poolMultiplier", String(Math.min(24, Math.max(1, n))));
      }
    }
    return params;
  }

  function renderExplain(data) {
    if (labExplain.checked && data.explain) {
      explainWrap.hidden = false;
      explainPre.textContent = JSON.stringify(data.explain, null, 2);
    } else {
      explainWrap.hidden = true;
      explainPre.textContent = "";
    }
  }

  function renderDebugParts(data) {
    const parts = [];
    const d = data.debug;
    if (!d) {
      return parts;
    }
    if (d.hybridSkippedShortQuery) {
      parts.push('<span class="badge">hybrid coupé (longueur serveur)</span>');
    }
    if (d.embeddingTimedOut) {
      parts.push('<span class="badge warn">embed timeout</span>');
    } else if (d.embeddingFallback) {
      parts.push('<span class="badge">fallback embed → lexical</span>');
    }
    if (d.fuzzyDisabledByRequest) {
      parts.push('<span class="badge">fuzzy désactivé</span>');
    }
    if (d.effectiveMinFuzzyTokenLength > 0) {
      parts.push("min token fuzzy=" + d.effectiveMinFuzzyTokenLength);
    }
    if (d.serverMinHybridQueryChars > 0) {
      parts.push("srv minHybridChars=" + d.serverMinHybridQueryChars);
    }
    return parts;
  }

  function renderMeta(data, targetEl) {
    const parts = [];
    parts.push(
      (data.returned != null ? data.returned : 0) +
        " résultat(s) · langue " +
        (data.language || langSelect.value)
    );
    if (data.hybridEnabled) {
      parts.push('<span class="badge hybrid">hybride (RRF)</span>');
    } else if (data.hybridSuppressedByRequest) {
      parts.push('<span class="badge">lexical (hybrid=false)</span>');
    }
    if (data.effectiveRrfK != null) {
      parts.push("RRF k=" + data.effectiveRrfK);
    }
    if (data.fuzzyEnabled && data.fuzzyTerms > 0) {
      parts.push(
        '<span class="badge fuzzy">fuzzy (' +
          data.fuzzyTerms +
          " terme(s))</span>"
      );
    }
    if (data.candidatePool != null) {
      parts.push("pool " + data.candidatePool);
    }
    parts.push(...renderDebugParts(data));
    targetEl.innerHTML = parts.join(" ");
  }

  function renderResults(data, containerEl) {
    containerEl.innerHTML = "";
    const rows = data.results || [];
    if (rows.length === 0) {
      containerEl.innerHTML =
        '<p class="empty">Aucun résultat. Essayez un autre libellé ou code.</p>';
      return;
    }
    rows.forEach((row) => {
      const card = document.createElement("article");
      card.className = "card";
      const hier = formatHierarchy(row.hierarchy);
      card.innerHTML =
        '<div class="title"><code>' +
        escapeHtml(row.code) +
        "</code>" +
        (row.matchType
          ? '<span class="badge">' + escapeHtml(row.matchType) + "</span>"
          : "") +
        "</div>" +
        '<p class="desc">' +
        escapeHtml(row.description || "") +
        "</p>" +
        (hier ? '<p class="hier">' + escapeHtml(hier) + "</p>" : "") +
        '<div class="foot">score ' +
        (row.score != null ? Number(row.score).toFixed(4) : "—") +
        (row.level != null ? " · niveau " + row.level : "") +
        "</div>";
      containerEl.appendChild(card);
    });
  }

  function setAbVisible(on) {
    abPanel.hidden = !on;
    resultsEl.style.display = on ? "none" : "";
    if (on) {
      explainWrap.hidden = true;
    }
  }

  async function fetchSearch(params, signal) {
    const url = "/api/v1/search?" + params.toString();
    const res = await fetch(url, {
      signal,
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || res.statusText);
    }
    return res.json();
  }

  async function runAbSearch(q, lang, signal) {
    const baseH = buildSearchParams(q, lang, true);
    const baseL = buildSearchParams(q, lang, false);
    const [dh, dl] = await Promise.all([
      fetchSearch(baseH, signal),
      fetchSearch(baseL, signal),
    ]);
    renderMeta(dh, abMetaHybrid);
    renderMeta(dl, abMetaLexical);
    renderResults(dh, abResultsHybrid);
    renderResults(dl, abResultsLexical);
  }

  async function runSearch(immediate) {
    errorEl.textContent = "";
    const q = (qInput.value || "").trim();
    const lang = langSelect.value;

    if (lastController) {
      lastController.abort();
    }
    if (abAbort) {
      abAbort.abort();
    }
    lastController = new AbortController();
    abAbort = new AbortController();

    if (q.length < 2) {
      resultsEl.innerHTML =
        '<p class="empty">Saisissez au moins 2 caractères.</p>';
      metaEl.textContent = "";
      abResultsHybrid.innerHTML = "";
      abResultsLexical.innerHTML = "";
      abMetaHybrid.textContent = "";
      abMetaLexical.textContent = "";
      suggestionsEl.classList.remove("visible");
      suggestionsEl.innerHTML = "";
      explainWrap.hidden = true;
      return;
    }

    setAbVisible(labAb.checked);

    try {
      if (labAb.checked) {
        await runAbSearch(q, lang, abAbort.signal);
        if (!immediate) {
          const suggParams = buildSearchParams(q, lang, "auto");
          const sd = await fetchSearch(suggParams, lastController.signal);
          renderSuggestions(sd.results);
        }
      } else {
        const params = buildSearchParams(q, lang, "auto");
        const data = await fetchSearch(params, lastController.signal);
        renderMeta(data, metaEl);
        renderResults(data, resultsEl);
        renderExplain(data);
        if (!immediate) {
          renderSuggestions(data.results);
        }
      }
    } catch (e) {
      if (e.name === "AbortError") return;
      errorEl.textContent =
        "Erreur réseau ou serveur : " + (e.message || String(e));
      resultsEl.innerHTML = "";
      metaEl.textContent = "";
      abResultsHybrid.innerHTML = "";
      abResultsLexical.innerHTML = "";
      suggestionsEl.classList.remove("visible");
    }
  }

  function renderSuggestions(results) {
    suggestionsEl.innerHTML = "";
    const slice = (results || []).slice(0, SUGGEST_LIMIT);
    if (slice.length === 0) {
      suggestionsEl.classList.remove("visible");
      return;
    }
    slice.forEach((row) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.innerHTML =
        '<span class="code">' +
        escapeHtml(row.code) +
        "</span>" +
        escapeHtml(row.description || "");
      btn.addEventListener("mousedown", (e) => {
        e.preventDefault();
        qInput.value = row.description || row.code || "";
        suggestionsEl.classList.remove("visible");
        runSearch(true);
      });
      suggestionsEl.appendChild(btn);
    });
    suggestionsEl.classList.add("visible");
  }

  function scheduleSearch() {
    debounce(() => runSearch(false));
  }

  qInput.addEventListener("input", () => {
    scheduleSearch();
  });

  qInput.addEventListener("focus", () => {
    if (suggestionsEl.children.length) {
      suggestionsEl.classList.add("visible");
    }
  });

  qInput.addEventListener("blur", () => {
    clearTimeout(hideSuggestionsTimer);
    hideSuggestionsTimer = setTimeout(() => {
      suggestionsEl.classList.remove("visible");
    }, 180);
  });

  langSelect.addEventListener("change", () => {
    saveLang();
    runSearch(true);
  });

  const labEls = [
    labHybrid,
    labHybridMinLen,
    labLimit,
    labFuzzy,
    labMinFuzzyTokens,
    labExplain,
    labMinHybridServer,
    labEmbedTimeout,
    labDebounce,
    labRrfK,
    labPoolMult,
    labAb,
  ];
  labEls.forEach((el) => {
    el.addEventListener("change", () => {
      saveLab();
      runSearch(true);
    });
  });

  loadLang();
  saveLang();
  loadLab();
  saveLab();
  runSearch(true);
})();
