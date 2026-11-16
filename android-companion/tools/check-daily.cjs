const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const assert = require('assert/strict');
const project = path.resolve(__dirname, '..');
const assets = path.join(project, 'app/src/main/assets');
const output = path.join(project, 'build/previews/daily');
fs.mkdirSync(output, {recursive:true});
const fixture = `<!doctype html><html><head><meta charset="utf-8"><style>
body{background:white;color:black}table{width:80%}td{width:50%}
.decoration{background-image:url(https://example.com/no-offline-image.png)}
</style></head><body><form action="Pofficium.pl"><p>Feria secunda · Officium de feria</p><h1>Divinum Officium</h1>
<select><option>Português</option></select><h2>Aviso de rubrica incompleta da fonte</h2>
<table cellpadding="8"><tr><td>Deus, in adjutórium meum inténde. Dómine, ad adjuvándum me festína.</td>
<td onclick="alert(1)">Vinde, ó Deus, em meu auxílio. Socorrei-me sem demora. Glória ao Pai e ao Filho e ao Espírito Santo.</td></tr>
<tr><td>Pater noster, qui es in cælis: sanctificétur nomen tuum.</td><td>Pai nosso, que estais nos céus, santificado seja o vosso nome.</td></tr>
<tr><td colspan="2" class="decoration">Rubrica comum às duas línguas. <img src="https://example.com/image.png" alt="Ilustração da fonte"></td></tr></table>
<p><font color="red">Aviso adicional do servidor</font></p><a href="https://example.com">Site externo</a>
<script>window.originalScript=true;</script><input type="hidden" name="command"></form></body></html>`;
(async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],
    env:{...process.env,XDG_CONFIG_HOME:path.join(output,'config'),XDG_CACHE_HOME:path.join(output,'cache')}});
  let checks=0;
  try {
    if (process.argv.includes('--capture')) {
      const page = await browser.newPage();
      const url = 'https://www.divinumofficium.com/cgi-bin/horas/Pofficium.pl?date1=11-16-2026&command=prayLaudes&version=Rubrics+1960+-+1960&lang1=Latin&lang2=Portugues&interlinear=0&expand=tota&votive=Hodie&dioecesis=Generale&priest=0&psalmvar=0&oldhymns=0';
      await page.route('**/*', route => route.request().url() === url ? route.fulfill({contentType:'text/html',body:fixture}) : route.abort());
      await page.goto(url); await page.evaluate(fs.readFileSync(path.join(assets,'reader.js'),'utf8'));
      const snapshot = await page.evaluate(fs.readFileSync(path.join(assets,'daily-capture.js'),'utf8'));
      assert.equal(snapshot.valid,true); assert.equal(snapshot.source,url); assert.equal(snapshot.bilingual,true); assert.equal(snapshot.missingImages,1); assert.ok(snapshot.dayHeading.includes("Feria secunda")); checks+=5;
      for (const text of ['Deus, in adjutórium','Vinde, ó Deus','Pater noster','Pai nosso','Rubrica comum','Aviso de rubrica incompleta','Aviso adicional','Ilustração da fonte']) {assert.ok(snapshot.fragment.includes(text)); checks++;}
      assert.ok(!/<script|<select|<input|onclick=|href="https:/i.test(snapshot.fragment)); checks++;
      const resources = path.join(project,'app/src/test/resources'); fs.mkdirSync(resources,{recursive:true});
      fs.writeFileSync(path.join(resources,'daily-captured.json'),JSON.stringify(snapshot,null,2));
      for (const bad of ['<h1>Sem conexão</h1>','<h1>Verifique seu navegador</h1>','<form><table cellpadding="8"><tr><td>Erro</td></tr></table></form>']) {
        await page.setContent(bad); await page.evaluate(fs.readFileSync(path.join(assets,'reader.js'),'utf8'));
        assert.equal((await page.evaluate(fs.readFileSync(path.join(assets,'daily-capture.js'),'utf8'))).valid,false); checks++;
      }
      console.log(JSON.stringify({stage:'capture',status:'passed',checks,source:'representative fixture, not live site'})); await page.close();
    }
    if (process.argv.includes('--verify')) {
      const context = await browser.newContext({javaScriptEnabled:false,offline:true,viewport:{width:393,height:852}});
      const page = await context.newPage(); let requests=0; page.on('request',()=>requests++);
      for (const dark of [false,true]) for (const lang of [0,1,2]) {
        const html = fs.readFileSync(path.join(project,`app/build/previews/daily-${dark}-${lang}.html`),'utf8');
        assert.ok(html.includes("default-src 'none'")); checks++;
        await page.setContent(html);
        assert.equal(await page.locator('.dl-latin').first().isVisible(),lang!==0);
        assert.equal(await page.locator('.dl-portugues').first().isVisible(),lang!==1); checks+=2;
        assert.equal(await page.getByText('Rubrica comum às duas línguas.',{exact:false}).isVisible(),true);
        assert.equal(await page.getByText('Aviso de rubrica incompleta da fonte').isVisible(),true);
        assert.equal(await page.getByText('Aviso adicional do servidor').isVisible(),true); checks+=3;
        assert.equal(await page.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),dark?'rgb(21, 22, 25)':'rgb(247, 246, 242)');
        assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)); checks+=2;
        if(lang===0) await page.screenshot({path:path.join(output,`offline-${dark?'escuro':'claro'}.png`),fullPage:true});
      }
      assert.equal(requests,0); checks++;
      const report={stage:'offline',status:'passed',checks,networkRequests:requests,javascriptEnabled:false,networkOffline:true,document:'Java DailyOfficeStore renderer using captured fixture'};
      fs.writeFileSync(path.join(output,'resultado.json'),JSON.stringify(report,null,2)); console.log(JSON.stringify(report)); await context.close();
    }
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exit(1)});
