import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { randomUUID, randomInt } from 'node:crypto';
const base='http://127.0.0.1:8080';
async function account(saldo,estado='activo') {
  const body={numeroCuenta:'7'+String(randomInt(1e9)).padStart(9,'0'),titular:'CA07 '+randomUUID().slice(0,8),tipo:'ahorro',saldoInicial:String(saldo),estado};
  const response=await fetch(base+'/api/cuentas',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  assert.equal(response.status,201);return response.json();
}
const a=await account(20000),b=await account(0),inactive=await account(100,'inactivo'),empty=await account(0);
const browser=await chromium.launch({channel:'msedge',headless:true});
const evidence={accounts:[a.id,b.id,inactive.id,empty.id],cases:[]};
try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:4200');
  await page.getByRole('link',{name:'Nueva transferencia'}).click();
  await page.getByRole('heading',{name:'Nueva transferencia'}).waitFor();
  async function fill(origin,destination,amount) {
    for(const [id,account] of [['origen',origin],['destino',destination]]) {
      await page.locator('#'+id).click();
      await page.locator('.p-select-filter').fill(account.titular);
      await page.getByRole('option',{name:account.numeroCuenta+' · '+account.titular,exact:true}).click();
      await page.locator('.p-select-overlay').waitFor({state:'hidden'});
    }
    await page.locator('#monto').fill(amount);await page.locator('#monto').blur();
  }
  const send=()=>page.getByRole('button',{name:'Enviar transferencia',exact:true}).click();
  await fill(a,b,'10,25');
  await page.screenshot({path:'tmp/transferencia-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'tmp/transferencia-mobile.png',fullPage:true});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:'tmp/transferencia-mobile.png',fullPage:true});
  // Drop the response only AFTER the real microservices have committed the real SQL movement.
  let committed;const attempts=[];
  page.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/api/transferencias')) attempts.push({key:r.headers()['idempotency-key'],body:r.postDataJSON()});});
  await page.route('**/api/transferencias',async route=>{const response=await route.fetch();assert.equal(response.status(),201);committed=await response.json();await route.abort('failed');});
  await send();await page.getByText('El resultado es desconocido.',{exact:false}).waitFor();
  assert.equal(attempts.length,1);
  await page.unroute('**/api/transferencias');
  // Refresh restores the same pending payload from tab storage.
  await page.reload();await page.getByRole('button',{name:'Reintentar misma operación'}).waitFor();
  const retry=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/api/transferencias'));
  await page.getByRole('button',{name:'Reintentar misma operación'}).click();
  const replay=await (await retry).json();assert.equal(replay.id,committed.id);
  await page.getByText('Transferencia completada',{exact:true}).waitFor();
  assert.deepEqual(attempts[0],attempts[1]);assert.equal(attempts.length,2);
  const summary=await (await fetch(base+`/api/cuentas/${a.id}/resumen`)).json();
  assert.equal(summary.saldoActual,'19989.75');assert.equal(summary.cantidadTransferenciasHoy,1);
  evidence.cases.push({case:'lost_response_explicit_replay',attempts:structuredClone(attempts),committed,replay,summary});
  await page.getByRole('button',{name:'Realizar otra transferencia'}).click();
  await fill(a,b,'6000');await send();await page.getByText('Transferencia ejecutada y en revisión',{exact:true}).waitFor();
  evidence.cases.push({case:'review',status:'en_revision'});
  await page.getByRole('button',{name:'Realizar otra transferencia'}).click();await fill(a,b,'5000');await send();
  await page.getByText('Disponible:',{exact:false}).waitFor();
  evidence.cases.push({case:'daily_limit',error:await page.locator('[role=alert]').innerText()});
  await fill(inactive,b,'1');await send();await page.getByText('Ambas cuentas deben estar activas',{exact:false}).waitFor();
  evidence.cases.push({case:'inactive',error:await page.locator('[role=alert]').innerText()});
  await page.getByRole('link',{name:'Cuentas',exact:true}).click();
  await page.getByRole('heading',{name:'Cuentas bancarias'}).waitFor();
  await page.getByRole('link',{name:'Nueva transferencia'}).click();await page.getByRole('heading',{name:'Nueva transferencia'}).waitFor();await fill(empty,a,'1');await send();
  await page.getByText('La cuenta origen no dispone de saldo suficiente',{exact:false}).waitFor();
  evidence.cases.push({case:'insufficient',error:await page.locator('[role=alert]').innerText()});
  await page.getByRole('link',{name:'Cuentas',exact:true}).click();
  await page.getByLabel('Buscar cuenta').fill(a.numeroCuenta);
  await page.getByRole('cell',{name:a.numeroCuenta,exact:true}).waitFor();
  const final=await (await fetch(base+`/api/cuentas/${a.id}/resumen`)).json();
  assert.equal(final.saldoActual,'13989.75');assert.equal(final.cantidadTransferenciasHoy,2);
  assert.deepEqual(errors,[]);evidence.finalSummary=final;evidence.pageErrors=errors;
  await writeFile('tmp/integration-ca07.json',JSON.stringify(evidence,null,2));
  console.log(JSON.stringify(evidence,null,2));
} finally {await browser.close();}
