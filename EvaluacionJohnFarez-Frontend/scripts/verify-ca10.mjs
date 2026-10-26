import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
const browser=await chromium.launch({channel:'msedge',headless:true});
const evidence=[];
try {
  for (const [name,port,count] of [['backend',8080,5]]) {
    const page=await browser.newPage({viewport:{width:1440,height:1100}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    const url=`http://127.0.0.1:${port}/q/swagger-ui/`;
    const response=await page.goto(url);assert.equal(response.status(),200);
    await page.locator('.opblock').first().waitFor();
    assert.equal(await page.locator('.opblock').count(),count);
    assert.match(await page.locator('.swagger-ui').first().innerText(),/EvaluacionJohnFarez/);
    assert.equal(await page.locator('.errors-wrapper').count(),0);
    const document=await (await page.request.get(`http://127.0.0.1:${port}/q/openapi?format=json`)).json();
    assert.ok(document.components.schemas.TransferenciaResponse);
    assert.equal(document.components.schemas.TransferenciaResponse.properties.monto.type,'string');
    assert.equal(document.components.schemas.TransferenciaResponse.properties.id.type,'string');
    for (const path of ['/api/cuentas','/api/transferencias']) {
      const operation=page.locator('.opblock-get').filter({has:page.locator(`[data-path="${path}"]`)});
      await operation.locator('.opblock-summary').click();
      await operation.getByRole('button',{name:'Try it out',exact:true}).click();
      const executed=page.waitForResponse(r=>new URL(r.url()).pathname===path && r.request().method()==='GET');
      await operation.getByRole('button',{name:'Execute',exact:true}).click();
      const live=await executed;assert.equal(live.status(),200);assert.ok(Array.isArray(await live.json()));
      await operation.locator('.opblock-summary').click();
    }
    await page.screenshot({path:`tmp/swagger-${name}.png`,fullPage:true});
    assert.deepEqual(errors,[]);
    evidence.push({service:name,url,status:response.status(),operations:count,errors,screenshot:`swagger-${name}.png`});
    await page.close();
  }
  await writeFile('tmp/integration-ca10.json',JSON.stringify(evidence,null,2));
  console.log(JSON.stringify(evidence,null,2));
} finally {await browser.close();}
