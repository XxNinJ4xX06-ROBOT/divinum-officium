const { chromium } = require('playwright');
const fs = require('fs');
const assert = require('assert/strict');
const path = require('path');
const assets = path.resolve(__dirname, '../app/src/main/assets') + path.sep;
const output = path.resolve(__dirname, '../build/previews/browser');
fs.mkdirSync(output, {recursive:true});
const css = fs.readFileSync(assets + 'reader.css', 'utf8');
const script = fs.readFileSync(assets + 'reader.js', 'utf8');
// Representative upstream table_start/print_content markup, including unpaired text.
const fixture = `<!doctype html><html><head><meta charset="UTF-8"><style>
body {background:#fff;color:black}h1{color:maroon}table{width:80%}td{width:50%}
@media(prefers-color-scheme:dark){table,.nigra{color:white}body{background:black}}
</style></head><body><form action="Pofficium.pl" method="post">
<p align="center"><font color="maroon">Feria secunda</font><br><i>Officium de feria</i></p>
<h1>Divinum Officium Rubrics 1960</h1><p><a href="?command=next">next</a> <select name="lang2"><option>Portugues</option></select></p>
<h2><font color="red">Aviso do calendário</font></h2>
<table border="1" align="center" cellpadding="8" width="80%"><tbody>
<tr><td valign="top"><font color="red">Incipit</font><br>V. Deus, in adjutórium meum inténde.<br>R. Dómine, ad adjuvándum me festína.</td>
<td valign="top"><font color="red">Invocação inicial</font><br>V. Vinde, ó Deus, em meu auxílio.<br>R. Socorrei-me sem demora.</td></tr>
<tr><td><font color="red">Pater noster</font><br>Pater noster, qui es in cælis: sanctificétur nomen tuum.</td>
<td><font color="red">Pai nosso</font><br>Pai nosso, que estais nos céus, santificado seja o vosso nome.</td></tr>
<tr><td><span class="nigra">Texto sem coluna correspondente · ℣. ℟. ✠</span></td><td> </td></tr>
<tr><td colspan="2">Rubrica comum às duas línguas.</td></tr>
</tbody></table><a id="Laudes0"></a><p><font color="red">Aviso adicional do servidor</font></p>
<p><a href="?command=prayVesperae">Vesperae</a></p><input name="command" type="hidden" value="Laudes">
</form></body></html>`;
(async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined), headless:true,
    args:['--no-sandbox','--disable-dev-shm-usage'],
    env:{...process.env,XDG_CONFIG_HOME:path.join(output,'config'),XDG_CACHE_HOME:path.join(output,'cache')}});
  let checks = 0;
  try {
    for (const width of [320,393,600]) {
      const page = await browser.newPage({viewport:{width,height:852},deviceScaleFactor:2,colorScheme:'dark'});
      await page.setContent(fixture);
      const before = await page.locator('body').textContent();
      await page.addStyleTag({content:css}).then(el=>el.evaluate(el=>el.id='divinum-mobile-style'));
      const metadata = await page.evaluate(script);
      assert.equal(metadata.adapted,true); assert.equal(metadata.bilingual,true); checks += 2;
      assert.equal(await page.locator('body').textContent(),before); checks++;
      await page.evaluate(()=>window.divinumReader.setMode('portugues'));
      assert.equal(await page.locator('.dl-latin').first().isVisible(),false);
      assert.equal(await page.locator('.dl-portugues').first().isVisible(),true);
      assert.equal(await page.getByText('Texto sem coluna correspondente', {exact:false}).isVisible(),true);
      assert.equal(await page.getByText('Aviso adicional do servidor').isVisible(),true);
      assert.equal(await page.getByText('Aviso do calendário').isVisible(),true); checks += 5;
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth), 'horizontal overflow'); checks++;
      assert.equal(await page.locator('.nigra').evaluate(el=>getComputedStyle(el).color),'rgb(48, 45, 41)'); checks++;
      await page.evaluate(()=>window.divinumReader.setDark(true));
      assert.equal(await page.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(21, 22, 25)');
      assert.equal(await page.locator('.dl-portugues').first().evaluate(el=>getComputedStyle(el).color),'rgb(240, 234, 226)');
      if(width===393) await page.screenshot({path:path.join(output,'leitura-escuro.png'),fullPage:true});
      await page.evaluate(()=>window.divinumReader.setDark(false));
      assert.equal(await page.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(247, 246, 242)'); checks+=3;
      if(width===393) {
        await page.screenshot({path:path.join(output,'leitura-portugues.png'),fullPage:true});
        const cdp = await page.context().newCDPSession(page);
        const snapshot = await cdp.send('Page.captureSnapshot',{format:'mhtml'});
        fs.writeFileSync(path.join(output,'leitura-salva.mht'),snapshot.data);
        const decoded = snapshot.data.replace(/=\r?\n/g, '').replace(/=([0-9a-f]{2})/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
        assert.ok(decoded.includes('body.dl-mobile'));
        assert.ok(decoded.includes('dl-adapted'));
        checks += 2;
        fs.writeFileSync(path.join(output,'leitura-salva.mht'),snapshot.data);
      }
      await page.evaluate(()=>window.divinumReader.setMode('latin'));
      assert.equal(await page.locator('.dl-latin').first().isVisible(),true);
      assert.equal(await page.locator('.dl-portugues').first().isVisible(),false); checks += 2;
      await page.evaluate(()=>window.divinumReader.setMode('both'));
      assert.equal(await page.locator('.dl-latin').first().isVisible(),true);
      assert.equal(await page.locator('.dl-portugues').first().isVisible(),true); checks += 2;
      await page.evaluate(()=>window.divinumReader.setOriginal(true));
      assert.equal(await page.getByText('Divinum Officium Rubrics 1960').isVisible(),true); checks++;
      await page.evaluate(()=>window.divinumReader.setOriginal(false));
      await page.evaluate(script);
      assert.equal(await page.locator('body').textContent(),before); checks++;
      await page.close();
    }
    const page = await browser.newPage({viewport:{width:393,height:852}});
    await page.setContent('<form><h1>Opções avançadas</h1><input name="date" value="11-16-2026"><button>Confirmar</button></form>');
    await page.addStyleTag({content:css});
    const result = await page.evaluate(script);
    assert.equal(result.adapted,false);
    assert.equal(await page.getByText('Opções avançadas').isVisible(),true);
    assert.equal(await page.locator('input').isVisible(),true); checks += 3;
    const report = {status:'passed',checks, widths:[320,393,600], darkMode:true, fixture:'representative upstream markup, not a live server response'};
    fs.writeFileSync(path.join(output,'resultado.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify(report));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
