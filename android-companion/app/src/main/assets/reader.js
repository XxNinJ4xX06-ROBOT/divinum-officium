(function () {
  'use strict';
  var root = document.documentElement;
  var body = document.body;
  if (!body) return false;
  var viewport = document.querySelector('meta[name="viewport"]');
  if (!viewport) {
    viewport = document.createElement('meta');
    viewport.name = 'viewport';
    document.head.appendChild(viewport);
  }
  viewport.content = 'width=device-width, initial-scale=1';

  // table_start() in the upstream webdia.pl uses cellpadding=8 for prayer text.
  // Recognition is conservative: unfamiliar pages retain all their controls.
  var tables = Array.from(document.querySelectorAll('table[cellpadding="8"]'))
    .filter(function (table) { return !table.parentElement.closest('table[cellpadding="8"]'); });
  var form = document.querySelector('form');
  if (form && tables.length) {
    tables.forEach(function (table) {
      table.classList.add('dl-prayer');
      Array.from(table.rows).forEach(function (row) {
        var cells = Array.from(row.cells);
        var hasContent = function (cell) { return !!cell.textContent.trim() || !!cell.querySelector('img,svg,input,a'); };
        if (cells.length === 2 && cells[0].colSpan === 1 && cells[1].colSpan === 1 && cells.every(hasContent)) {
          cells[0].classList.add('dl-latin');
          cells[1].classList.add('dl-portugues');
          cells[0].lang = 'la';
          cells[1].lang = 'pt';
        } else cells.forEach(function (cell) {
          cell.classList.add('dl-shared');
          if (!hasContent(cell)) cell.classList.add('dl-empty');
        });
      });
    });
    // Keep the liturgical day's original heading, all warnings, and every text node.
    var firstParagraph = Array.from(form.children).find(function (node) { return node.tagName === 'P'; });
    Array.from(form.children).forEach(function (node) {
      if (node === firstParagraph) node.classList.add('dl-day');
      else if (node.classList.contains('dl-prayer') || node.querySelector('.dl-prayer')) return;
      else if (node.tagName === 'H2' || (node.tagName === 'P' && Array.from(node.querySelectorAll('font[color]')).some(function (font) { return font.getAttribute('color').toLowerCase() === 'red'; }) && !node.querySelector('input,select'))) node.classList.add('dl-notice');
      else if (node.tagName === 'A' && node.id) return;
      else node.classList.add('dl-chrome');
    });
    body.classList.add('dl-adapted');
  }

  window.divinumReader = {
    setDark: function (dark) {
      root.setAttribute('data-dl-theme', dark ? 'dark' : 'light');
      root.style.colorScheme = dark ? 'dark' : 'light';
    },
    setMode: function (mode) {
      if (['portugues', 'latin', 'both'].indexOf(mode) === -1) mode = 'both';
      // Preserve the user's place when switching the language of aligned sections.
      var rows = Array.from(document.querySelectorAll('.dl-prayer tr'));
      var anchor = rows.find(function (row) { return row.getBoundingClientRect().bottom > 0; });
      var offset = anchor ? anchor.getBoundingClientRect().top : 0;
      body.setAttribute('data-dl-mode', mode);
      if (anchor) window.scrollBy(0, anchor.getBoundingClientRect().top - offset);
    },
    setOriginal: function (original) {
      body.classList.toggle('dl-original', !!original);
      var style = document.getElementById('divinum-mobile-style');
      if (style) style.disabled = !!original;
      viewport.content = original ? 'width=980' : 'width=device-width, initial-scale=1';
    }
  };
  body.classList.add('dl-mobile');
  root.style.colorScheme = 'light';
  return { adapted: tables.length > 0, bilingual: !!document.querySelector('.dl-portugues') };
})();
