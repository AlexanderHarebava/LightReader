


(function () {
  'use strict';
  if (window.readiumColSplitApplied) return;
  window.readiumColSplitApplied = true;

  var VERSION = '5.0.0';
  var SPLIT_CLASS = 'readium-col-split';
  var SPLIT_HEAD_CLASS = 'readium-col-split-head';
  var SPLIT_MARK = 'data-col-split-id';
  var ORIGIN_MARK = 'data-col-split-origin';
  var STYLE_TAG_ID = 'readium-col-split-css';
  var DEBUG_ROOT_CLASS = 'colsplit-debug';
  var EPS = 0.5;
  var YIELD_EVERY = 15;


  var CFG = { widows: 2, orphans: 2, maxChain: 60 };
  try {
    if (window.readiumColSplitConfig) {
      for (var k in window.readiumColSplitConfig) {
        if (Object.prototype.hasOwnProperty.call(window.readiumColSplitConfig, k)) {
          CFG[k] = window.readiumColSplitConfig[k];
        }
      }
    }
  } catch (e) {
 }

  var splitIdCounter = 0;
  var isProcessing = false;
  var selfMutating = false;
  var reflowTimer = null;
  var mutationTimer = null;
  var debugOn = false;
  var stats = {
    runs: 0,
    splits: 0,
    merges: 0,
    skipped: 0,
    selectionSkips: 0,
    mutationRuns: 0,
    lastRunMs: 0,
    lastSplitCount: 0
  };

  function log() {
    if (debugOn && window.console && console.debug) {
      console.debug.apply(console, ['[ColSplit ' + VERSION + ']'].concat([].slice.call(arguments)));
    }
  }

  function isPagedMode() {
    return document.documentElement.style.cssText.indexOf('readium-paged-on') !== -1;
  }


  function clientRectsOf(target) {
    return Array.from(target.getClientRects ? target.getClientRects() : []);
  }

  function rangeRectsOf(node) {
    var range = document.createRange();
    range.selectNodeContents(node);
    return Array.from(range.getClientRects());
  }

  function yieldFrame() {
    return new Promise(function (resolve) {
      if (window.requestAnimationFrame) {
        requestAnimationFrame(function () { resolve(); });
      } else {
        setTimeout(resolve, 0);
      }
    });
  }


  function currentSelectionRange() {
    try {
      var sel = window.getSelection && window.getSelection();
      if (!sel || sel.isCollapsed || !sel.rangeCount) return null;
      var range = sel.getRangeAt(0);
      return (range && !range.collapsed) ? range : null;
    } catch (e) { return null; }
  }

  function rangeIntersectsNode(range, node) {
    try {
      if (range.intersectsNode) return range.intersectsNode(node);

      var nodeRange = document.createRange();
      nodeRange.selectNode(node);
      return range.compareBoundaryPoints(Range.START_TO_END, nodeRange) === -1 &&
             range.compareBoundaryPoints(Range.END_TO_START, nodeRange) === 1;
    } catch (e) {
      return true;
    }
  }





  function detectAxis(rects) {
    var ai = -1;
    for (var i0 = 0; i0 < rects.length; i0++) {
      if (rects[i0].width >= 1 || rects[i0].height >= 1) { ai = i0; break; }
    }
    if (ai === -1) return null;
    var a = rects[ai];
    for (var i = ai + 1; i < rects.length; i++) {
      var r = rects[i];
      if (r.width < 1 && r.height < 1) continue;
      if (r.left >= a.right - EPS) {
        return { mode: 'h', forward: true, boundary: (a.right + r.left) / 2 };
      }
      if (r.right <= a.left + EPS) {
        return { mode: 'h', forward: false, boundary: (r.right + a.left) / 2 };
      }
      if (r.top >= a.bottom - EPS) {
        return { mode: 'v', forward: true, boundary: (a.bottom + r.top) / 2 };
      }
      if (r.bottom <= a.top + EPS) {
        return { mode: 'v', forward: false, boundary: (r.bottom + a.top) / 2 };
      }
    }
    return null;
  }

  function makeColumnClassifier(axis) {
    if (axis.mode === 'h') {
      return axis.forward
        ? function (r) { return r.left >= axis.boundary; }
        : function (r) { return r.right <= axis.boundary; };
    }
    return axis.forward
      ? function (r) { return r.top >= axis.boundary; }
      : function (r) { return r.bottom <= axis.boundary; };
  }

  function isFragmented(el) {
    if (!el || !el.isConnected) return false;
    var rects = clientRectsOf(el);
    if (rects.length < 2) return false;
    return detectAxis(rects) !== null;
  }





  var RISKY_ANCESTORS = 'table, pre, code, math, figure, [contenteditable="true"]';
  var RISKY_TEXT_PARENTS = 'math, svg, script, style, template, audio, video, canvas, iframe, object, embed, noscript, textarea, input, button, select';

  function shouldSkipElement(el, cs) {
    if (el.closest(RISKY_ANCESTORS)) return true;
    var disp = cs.display;
    if (disp !== 'block' && disp !== 'list-item' && disp !== 'flow-root') return true;
    if (cs.columnCount !== 'auto' || cs.columnWidth !== 'auto') return true;
    if (cs.columnSpan && cs.columnSpan !== 'none') return true;
    if (cs.position === 'fixed' || cs.position === 'sticky') return true;
    var ws = cs.whiteSpace;
    if (ws === 'pre' || ws === 'pre-wrap' || ws === 'pre-line') return true;
    if (cs.float !== 'none') return true;
    return false;
  }

  function isRiskyTextNode(node) {
    var p = node.parentElement;
    return !p || !!p.closest(RISKY_TEXT_PARENTS);
  }



  function elementCharOffset(el, node, offset) {
    var range = document.createRange();
    range.selectNodeContents(el);
    range.setEnd(node, offset);
    return range.toString().length;
  }

  var segmenter = null;
  try {
    if (window.Intl && Intl.Segmenter) {
      segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
    }
  } catch (e) {
 }

  function isExtendCode(code) {
    return (code >= 0x0300 && code <= 0x036F) ||
      (code >= 0x1AB0 && code <= 0x1DFF) ||
      (code >= 0x20D0 && code <= 0x20FF) ||
      (code >= 0xFE00 && code <= 0xFE0F) ||
      (code >= 0xFE20 && code <= 0xFE2F) ||
      code === 0x200D ||
      (code >= 0xE0020 && code <= 0xE007F) ||
      (code >= 0xE0100 && code <= 0xE01EF);
  }


  function adjustGraphemeBoundary(node, offset) {
    if (offset <= 0 || offset >= node.length) return offset;
    var text = node.data;
    if (segmenter) {
      var last = null;
      try {
        var it = segmenter.segment(text)[Symbol.iterator]();
        for (var seg = it.next(); !seg.done; seg = it.next()) {
          if (seg.value.index >= offset) break;
          last = seg.value;
        }
      } catch (e) { last = null; }
      if (last && offset > last.index && offset < last.index + last.segment.length) {
        return last.index;
      }
      return offset;
    }

    var off = offset;
    if (off > 0 && off < text.length) {
      var prev = text.charCodeAt(off - 1);
      var cur = text.charCodeAt(off);
      if (prev >= 0xD800 && prev <= 0xDBFF && cur >= 0xDC00 && cur <= 0xDFFF) off--;
    }
    while (off > 0 && off < text.length && isExtendCode(text.charCodeAt(off))) off--;
    return off;
  }



  function caretOffset(node, rect, axis) {
    try {
      var x, y;
      if (axis.mode === 'h') {
        x = axis.forward ? rect.left + 2 : rect.right - 2;
        y = rect.top + rect.height / 2;
      } else {
        x = rect.left + rect.width / 2;
        y = axis.forward ? rect.top + 2 : rect.bottom - 2;
      }
      var point = document.caretRangeFromPoint(x, y);
      if (point && point.startContainer === node) {
        return point.startOffset;
      }
    } catch (e) {
 }
    return null;
  }




  function binaryOffset(node, isBeyond) {
    var low = 0, high = node.length;
    var range = document.createRange();
    while (low < high) {
      var mid = (low + high) >> 1;
      range.setStart(node, mid);
      range.setEnd(node, Math.min(mid + 1, node.length));
      if (isBeyond(range.getBoundingClientRect())) high = mid;
      else low = mid + 1;
    }
    return low;
  }










  function findSplitPoint(el, conservative) {
    var rects = clientRectsOf(el);
    if (rects.length < 2) return null;
    var axis = detectAxis(rects);
    if (!axis) return null;
    var isBeyond = makeColumnClassifier(axis);
    var totalLength = el.textContent.length;
    if (!totalLength) return null;

    function isValid(node, offset) {
      if (offset === null || offset === undefined || offset < 0 || offset > node.length) return false;
      var pos = elementCharOffset(el, node, offset);
      return pos > 0 && pos < totalLength;
    }

    function lineStartPoint(node, rect) {
      var offset = caretOffset(node, rect, axis);
      if (isValid(node, offset)) return { node: node, offset: offset };
      offset = binaryOffset(node, isBeyond);
      if (isValid(node, offset)) return { node: node, offset: offset };
      return null;
    }

    var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        return isRiskyTextNode(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });

    var lineIndex = 0;
    var firstBeyondIndex = -1;
    var prevLine = null;
    var beyondLine = null;
    var node;
    while ((node = walker.nextNode())) {
      if (!node.length) continue;
      var lineRects = rangeRectsOf(node);
      for (var i = 0; i < lineRects.length; i++) {
        if (isBeyond(lineRects[i])) {
          if (firstBeyondIndex === -1) {
            firstBeyondIndex = lineIndex;
            beyondLine = { node: node, rect: lineRects[i] };
          }
        } else {
          prevLine = { node: node, rect: lineRects[i] };
        }
        lineIndex++;
      }
    }

    if (firstBeyondIndex === -1 || firstBeyondIndex === 0) {

      return fallbackChildSplit(el, isBeyond, isValid);
    }

    var chosen = beyondLine;
    if (!conservative) {
      var tailLines = lineIndex - firstBeyondIndex;
      if (tailLines < CFG.widows && prevLine && (firstBeyondIndex - 1) >= CFG.orphans) {
        chosen = prevLine;
      }
    }
    var point = lineStartPoint(chosen.node, chosen.rect);
    if (!point && chosen !== beyondLine) {
      point = lineStartPoint(beyondLine.node, beyondLine.rect);
    }
    if (point) return point;
    return fallbackChildSplit(el, isBeyond, isValid);
  }



  function fallbackChildSplit(el, isBeyond, isValid) {
    for (var child = el.firstChild; child; child = child.nextSibling) {
      var childRects = clientRectsOf(child);
      if (childRects.length && isBeyond(childRects[0])) {
        var anchor = document.createTextNode('');
        el.insertBefore(anchor, child);
        if (isValid(anchor, 0)) return { node: anchor, offset: 0 };
        anchor.remove();
      }
    }
    return null;
  }


  function performSplit(el, node, offset) {
    offset = adjustGraphemeBoundary(node, offset);
    var after = node.splitText(offset);
    var range = document.createRange();
    range.setStart(after, 0);
    range.setEnd(el, el.childNodes.length);
    var fragment = range.extractContents();


    if (fragment.firstChild && fragment.firstChild.nodeType === 3 && !fragment.firstChild.data) {
      fragment.removeChild(fragment.firstChild);
    }
    if (el.lastChild && el.lastChild.nodeType === 3 && !el.lastChild.data) {
      el.removeChild(el.lastChild);
    }
    if (!fragment.firstChild) {

      return null;
    }

    var clone = el.cloneNode(false);
    clone.removeAttribute('id');
    clone.classList.add(SPLIT_CLASS);
    el.classList.add(SPLIT_HEAD_CLASS);
    var id = el.getAttribute(SPLIT_MARK) || ('cs-' + (++splitIdCounter));
    el.setAttribute(SPLIT_MARK, id);
    clone.setAttribute(SPLIT_MARK, id);

    clone.setAttribute(ORIGIN_MARK, el.getAttribute('id') || id);
    clone.appendChild(fragment);
    el.parentNode.insertBefore(clone, el.nextSibling);
    log('Split:', el.tagName, 'id=' + id);
    return clone;
  }




  function splitElementDeep(el) {
    if (!isPagedMode()) return 0;
    var selection = currentSelectionRange();
    if (selection && rangeIntersectsNode(selection, el)) {
      stats.selectionSkips++;
      return 0;
    }
    var conservative = !!selection;
    var splits = 0;
    var current = el;
    while (current && current.isConnected && splits < CFG.maxChain) {
      if (!isFragmented(current)) break;
      var point = findSplitPoint(current, conservative);
      if (!point) break;
      current = performSplit(current, point.node, point.offset);
      splits++;
    }
    return splits;
  }




  function mergeSplits() {
    var marked = document.querySelectorAll('[' + SPLIT_MARK + ']');
    if (!marked.length) return;
    var selection = currentSelectionRange();
    selfMutating = true;
    try {
      var groups = new Map();
      Array.prototype.forEach.call(marked, function (n) {
        var id = n.getAttribute(SPLIT_MARK);
        if (!groups.has(id)) groups.set(id, []);
        groups.get(id).push(n);
      });
      groups.forEach(function (nodes) {
        if (nodes.length < 2) {

          nodes.forEach(function (n) {
            n.classList.remove(SPLIT_HEAD_CLASS, SPLIT_CLASS);
            n.removeAttribute(SPLIT_MARK);
          });
          return;
        }
        nodes.sort(function (a, b) {
          return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) ? -1 : 1;
        });
        var head = nodes[0];
        if (selection && rangeIntersectsNode(selection, head)) return;
        for (var i = 1; i < nodes.length; i++) {
          var next = nodes[i];
          if (next.previousElementSibling !== head) break;
          if (selection && rangeIntersectsNode(selection, next)) break;
          while (next.firstChild) head.appendChild(next.firstChild);
          next.remove();
          stats.merges++;
        }
        var tail = head.nextElementSibling;
        if (!tail || !tail.classList.contains(SPLIT_CLASS) ||
            tail.getAttribute(SPLIT_MARK) !== head.getAttribute(SPLIT_MARK)) {

          head.classList.remove(SPLIT_HEAD_CLASS, SPLIT_CLASS);
          head.removeAttribute(SPLIT_MARK);
          head.normalize();
        }
      });
    } finally {
      selfMutating = false;
    }
  }


  function resetAll() {
    mergeSplits();
    var leftovers = document.querySelectorAll(
      '[' + SPLIT_MARK + '], .' + SPLIT_CLASS + ', .' + SPLIT_HEAD_CLASS
    );
    Array.prototype.forEach.call(leftovers, function (n) {
      n.classList.remove(SPLIT_HEAD_CLASS, SPLIT_CLASS);
      n.removeAttribute(SPLIT_MARK);
    });
    splitIdCounter = 0;
  }







  function injectStylesheet() {
    if (document.getElementById(STYLE_TAG_ID)) return;
    var style = document.createElement('style');
    style.id = STYLE_TAG_ID;
    style.textContent =
      '.readium-col-split-head{margin-bottom:0!important;break-inside:auto!important;' +
      'page-break-inside:auto!important;-webkit-column-break-inside:auto!important;}' +
      '.readium-col-split{margin-top:0!important;text-indent:0!important;list-style:none!important;' +
      'break-inside:auto!important;page-break-inside:auto!important;-webkit-column-break-inside:auto!important;}' +
      '.readium-col-split::first-letter,.readium-col-split::first-line{' +
      'font-size:inherit!important;font-weight:inherit!important;font-style:inherit!important;' +
      'font-variant:inherit!important;color:inherit!important;background:none!important;' +
      'float:none!important;text-decoration:inherit!important;}' +

      'html.' + DEBUG_ROOT_CLASS + ' .readium-col-split-head{outline:1px solid rgba(0,128,255,.55)!important;outline-offset:-1px;}' +
      'html.' + DEBUG_ROOT_CLASS + ' .readium-col-split{outline:1px dashed rgba(255,0,128,.55)!important;outline-offset:-1px;}';
    (document.head || document.documentElement).appendChild(style);
  }

  function setDebug(v) {
    debugOn = !!v;
    try {
      document.documentElement.classList.toggle(DEBUG_ROOT_CLASS, debugOn);
    } catch (e) {
 }
  }


  async function runBulkSplit() {
    if (isProcessing || !isPagedMode()) return;
    isProcessing = true;
    injectStylesheet();
    var startedAt = Date.now();
    var totalSplits = 0;
    var styleCache = new WeakMap();
    try {
      var SELECTOR = 'p, li, blockquote, h1, h2, h3, h4, h5, h6, figcaption, dd, dt';


      for (var pass = 0; pass < 6; pass++) {
        var splitsThisPass = 0;
        var candidates = Array.from(document.body.querySelectorAll(SELECTOR));


        candidates.sort(function (a, b) {
          var da = a.querySelector(SELECTOR) ? 1 : 0;
          var db = b.querySelector(SELECTOR) ? 1 : 0;
          return da - db;
        });
        for (var i = 0; i < candidates.length; i++) {
          var el = candidates[i];
          if (!el.isConnected) continue;
          var cs = styleCache.get(el);
          if (!cs) {
            cs = window.getComputedStyle(el);
            styleCache.set(el, cs);
          }
          if (shouldSkipElement(el, cs)) {
            if (pass === 0) stats.skipped++;
            continue;
          }
          if (isFragmented(el)) {
            splitsThisPass += splitElementDeep(el);
          }

          if (i % YIELD_EVERY === YIELD_EVERY - 1) {
            await yieldFrame();
          }
        }
        totalSplits += splitsThisPass;
        if (!splitsThisPass) break;
      }
    } finally {
      isProcessing = false;
      stats.runs++;
      stats.splits += totalSplits;
      stats.lastSplitCount = totalSplits;
      stats.lastRunMs = Date.now() - startedAt;
      log('Bulk split finished:', totalSplits, 'splits in', stats.lastRunMs + 'ms');
      try {
        document.dispatchEvent(new CustomEvent('readium:colsplit', {
          detail: {
            splits: totalSplits,
            skipped: stats.skipped,
            selectionActive: !!currentSelectionRange(),
            durationMs: stats.lastRunMs,
            version: VERSION
          }
        }));
      } catch (e) {
 }
    }
  }



  async function reflow() {
    if (!isPagedMode() || isProcessing) return;
    mergeSplits();
    await runBulkSplit();
  }

  function scheduleReflow(delay) {
    clearTimeout(reflowTimer);
    reflowTimer = setTimeout(function () {
      if (isProcessing) {
        scheduleReflow(600);
        return;
      }
      reflow();
    }, delay == null ? 400 : delay);
  }




  function initMutationObserver() {
    if (!window.MutationObserver || !document.body) return;
    var SELECTOR = 'p, li, blockquote, h1, h2, h3, h4, h5, h6, figcaption, dd, dt';
    new MutationObserver(function (mutations) {
      if (selfMutating || isProcessing || !isPagedMode()) return;
      var relevant = false;
      for (var i = 0; i < mutations.length && !relevant; i++) {
        var added = mutations[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          var n = added[j];
          if (n.nodeType !== 1) continue;
          if (n.hasAttribute(SPLIT_MARK) || n.classList.contains(SPLIT_CLASS)) continue;
          if ((n.matches && n.matches(SELECTOR)) || (n.querySelector && n.querySelector(SELECTOR))) {
            relevant = true;
            break;
          }
        }
      }
      if (!relevant) return;
      stats.mutationRuns++;
      clearTimeout(mutationTimer);
      mutationTimer = setTimeout(function () { reflow(); }, 300);
    }).observe(document.body, { childList: true, subtree: true });
  }


  function init() {
    injectStylesheet();
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () {
        initMutationObserver();
        runBulkSplit();
      }, { once: true });
    } else {
      initMutationObserver();
      runBulkSplit();
    }

    window.addEventListener('load', function () { scheduleReflow(600); });

    try {
      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(function () { scheduleReflow(300); }, function () {});
      }
    } catch (e) {
 }

    document.addEventListener('load', function (e) {
      var t = e.target;
      if (t && t.tagName && /^(IMG|VIDEO|AUDIO|IFRAME|EMBED|OBJECT)$/.test(t.tagName)) {
        scheduleReflow(400);
      }
    }, true);

    window.addEventListener('resize', function () { scheduleReflow(450); });


    try {
      new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
          if (mutations[i].attributeName === 'style') {
            scheduleReflow(500);
            break;
          }
        }
      }).observe(document.documentElement, { attributes: true, attributeFilter: ['style'] });
    } catch (e) {
 }
  }

  init();


  window.readiumColSplit = {
    version: VERSION,
    run: runBulkSplit,
    reflow: reflow,
    merge: mergeSplits,
    splitElement: splitElementDeep,
    reset: resetAll,
    stats: stats,
    configure: function (opts) {
      if (opts && typeof opts === 'object') {
        for (var key in opts) {
          if (!Object.prototype.hasOwnProperty.call(opts, key)) continue;
          if (key === 'debug') { setDebug(opts[key]); continue; }
          CFG[key] = opts[key];
        }
      }
      return { widows: CFG.widows, orphans: CFG.orphans, maxChain: CFG.maxChain };
    },
    get debug() { return debugOn; },
    set debug(v) { setDebug(v); }
  };

  console.log('%c[Readium ColSplit v' + VERSION + '] Selection-Aware release initialized',
    'color: #0a0; font-weight: bold');
})();