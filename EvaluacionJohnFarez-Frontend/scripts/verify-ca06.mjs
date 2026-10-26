import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors=[];
  page.on('pageerror', e => { errors.push(e.message); console.log('PAGE ERROR',e.message); });
  page.on('console', m => { if(m.type()==='error') console.log('CONSOLE',m.text()); });
  const responsePromise=page.waitForResponse(r=>r.url().endsWith('/api/cuentas'));
  await page.goto('http://127.0.0.1:4200');
  const response=await responsePromise;
  assert.equal(response.status(),200);
  const accounts=await response.json();
  await page.getByRole('heading',{name:'Cuentas bancarias'}).waitFor();
  await page.locator('[aria-busy="false"]').waitFor();
  assert.equal(await page.locator('tbody .account-number').count(),Math.min(10,accounts.length));
  if(accounts.length) {
    const cents=v=>{const [i,f='']=v.split('.');return BigInt(i)*100n+BigInt(f.padEnd(2,'0'));};
    const sorted=[...accounts].sort((a,b)=>cents(a.saldo)<cents(b.saldo)?-1:cents(a.saldo)>cents(b.saldo)?1:0);
    assert.equal((await page.locator('tbody .account-number').first().textContent()).trim(),sorted[0].numeroCuenta);
    await page.getByRole('columnheader',{name:/Saldo actual/}).click();
    assert.equal(await page.getByRole('columnheader',{name:/Saldo actual/}).getAttribute('aria-sort'),'descending');
    assert.equal((await page.locator('tbody .account-number').first().textContent()).trim(),[...accounts].sort((a,b)=>cents(a.saldo)>cents(b.saldo)?-1:cents(a.saldo)<cents(b.saldo)?1:0)[0].numeroCuenta);
  }
  await page.screenshot({ path:'tmp/dashboard-desktop.png',fullPage:true });
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  await page.screenshot({path:'tmp/dashboard-mobile.png',fullPage:true});
  const refreshed=page.waitForResponse(r=>r.url().endsWith('/api/cuentas'));
  await page.getByRole('button',{name:'Actualizar',exact:true}).click();
  assert.equal((await refreshed).status(),200);
  assert.deepEqual(errors,[]);
  const direct=await fetch('http://127.0.0.1:8080/api/cuentas',{headers:{Origin:'http://127.0.0.1:4200'}});
  const result={ browser:'Microsoft Edge headless', backend:'Quarkus + SQL Server real',
    proxyStatus:response.status(), accounts:accounts.length, directStatus:direct.status,
    corsAllowOrigin:direct.headers.get('access-control-allow-origin'),
    browserConsoleErrors:errors, mobileNoPageOverflow:true,
    screenshots:['dashboard-desktop.png','dashboard-mobile.png'] };
  await writeFile('tmp/integration-ca06.json',JSON.stringify(result,null,2));
  console.log(JSON.stringify(result,null,2));
} finally { await browser.close(); }
