/* SC: uniform scale for short or narrow phones. The screens are laid out for REF_W × REF_H CSS px; a smaller viewport
 * (20:9 phones are ≈ 720 × 324 at a large «display size», Felix 28-10: buttons and texts were cut) draws the same
 * layout at that size and scales it down as a whole, so the look stays the desktop launcher's and nothing is cut.
 * A transform (not CSS zoom) because older Android WebViews size zoomed pages differently. Load it first in <body>. */
(function () {
  'use strict'
  const REF_W = 780, REF_H = 384, MIN = 0.7
  function ajusta() {
    const w = window.innerWidth, h = window.innerHeight, b = document.body
    if (!b || !w || !h) return
    const s = Math.max(MIN, Math.min(1, w / REF_W, h / REF_H))
    if (s >= 1) { b.style.removeProperty('width'); b.style.removeProperty('height'); b.style.removeProperty('transform'); return }
    b.style.width = (w / s) + 'px'
    b.style.height = (h / s) + 'px'
    b.style.transformOrigin = '0 0'
    b.style.transform = 'scale(' + s + ')'
  }
  ajusta()
  window.addEventListener('resize', ajusta)
  document.addEventListener('DOMContentLoaded', ajusta)
})()
