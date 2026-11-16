(function () {
  'use strict';
  var tables = Array.from(document.querySelectorAll('table.dl-prayer'));
  var textLength = tables.reduce(function (sum, table) { return sum + table.textContent.trim().length; }, 0);
  if (!tables.length || textLength < 80) return { valid: false, reason: 'Aguardando o texto da oração.' };
  var source = document.querySelector('form') || document.body;
  var clone = source.cloneNode(true);
  clone.querySelectorAll('.dl-chrome,script,iframe,object,embed,foreignObject,input,select,textarea,button,link,meta,base').forEach(function (node) { node.remove(); });
  var missingImages = 0;
  var originals = Array.from(source.querySelectorAll('img'));
  clone.querySelectorAll('img').forEach(function (image) {
    var origin = originals.find(function (node) { return node.src === image.src; });
    if (/^data:image\//i.test(image.src)) return;
    try {
      if (!origin || !origin.complete || !origin.naturalWidth) throw new Error('not loaded');
      var scale = Math.min(1, 1024 / Math.max(origin.naturalWidth, origin.naturalHeight));
      var canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(origin.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(origin.naturalHeight * scale));
      canvas.getContext('2d').drawImage(origin, 0, 0, canvas.width, canvas.height);
      image.src = canvas.toDataURL('image/png');
    } catch (error) {
      image.replaceWith(document.createTextNode(image.alt || '[imagem não incluída]'));
      missingImages++;
    }
  });
  clone.querySelectorAll('*').forEach(function (node) {
    Array.from(node.attributes).forEach(function (attribute) {
      var name = attribute.name.toLowerCase(), value = attribute.value.trim();
      if (/^on/.test(name) || ['srcset','action','formaction','target','background','poster'].indexOf(name) >= 0) node.removeAttribute(attribute.name);
      if ((name === 'href' || name === 'xlink:href') && !/^#/.test(value) && !/^data:image\//i.test(value)) node.removeAttribute(attribute.name);
      if (name === 'src' && !/^data:image\//i.test(value)) node.removeAttribute(attribute.name);
    });
  });
  // A partial response may lack a form. Keep only actual prayer tables and headings in that case.
  if (source === document.body) {
    clone.querySelectorAll('*').forEach(function (node) {
      if (node.parentElement === clone && !node.matches('.dl-prayer,.dl-day,.dl-notice,h2') && !node.querySelector('.dl-prayer')) node.remove();
    });
  }
  return {
    valid: true, source: location.href, title: document.title,
    textLength: textLength, fragment: clone.innerHTML,
    css: Array.from(document.querySelectorAll('style')).map(function (node) { return node.textContent; }).join('\n'),
    bilingual: !!clone.querySelector('.dl-portugues'), missingImages: missingImages,
    dayHeading: clone.querySelector('.dl-day') ? clone.querySelector('.dl-day').textContent.trim() : ''
  };
})();
