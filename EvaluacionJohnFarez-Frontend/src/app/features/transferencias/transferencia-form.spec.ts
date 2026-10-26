import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { registerLocaleData } from '@angular/common';
import esEC from '@angular/common/locales/es-EC';
import { TransferenciaForm } from './transferencia-form';
import { TransferenciasService } from '../../core/services/transferencias.service';
registerLocaleData(esEC);
describe('Formulario de transferencias', () => {
  let fixture: ComponentFixture<TransferenciaForm>;
  let http: HttpTestingController;
  const accounts = [{id:'1',numeroCuenta:'0000000001',titular:'Ana',tipo:'ahorro',estado:'activo',saldo:'100'},
    {id:'2',numeroCuenta:'0000000002',titular:'Luis',tipo:'corriente',estado:'activo',saldo:'0'}];
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports:[TransferenciaForm], providers:[provideHttpClient(),provideHttpClientTesting()] });
    http=TestBed.inject(HttpTestingController);fixture=TestBed.createComponent(TransferenciaForm);
    http.expectOne('/api/cuentas').flush(accounts);await fixture.whenStable();
  });
  afterEach(()=>{http.verify();sessionStorage.clear();});
  function valid() {fixture.componentInstance.form.setValue({cuentaOrigenId:'1',cuentaDestinoId:'2',monto:'10.25'});}
  function post() {valid();fixture.componentInstance.enviar();return http.expectOne('/api/transferencias');}
  it('requiere los tres campos y deshabilita envio',async()=>{
    expect(fixture.componentInstance.form.invalid).toBe(true);
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBe(true);
    fixture.componentInstance.enviar();http.expectNone('/api/transferencias');
  });
  it('acepta datos validos y envia identificadores con clave',()=>{
    valid();expect(fixture.componentInstance.form.valid).toBe(true);
    const req=post();expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({cuentaOrigenId:'1',cuentaDestinoId:'2',monto:'10.25'});
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    req.flush({codigo:'SALDO_INSUFICIENTE',mensaje:'Saldo insuficiente'},{status:422,statusText:'Rejected'});
  });
  it.each(['0','-1','1.001','NaN','Infinity','1e-10','1.0000000001'])('rechaza monto %s',(monto)=>{
    valid();fixture.componentInstance.form.controls.monto.setValue(monto);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });
  it('rechaza cuentas iguales',()=>{valid();fixture.componentInstance.form.controls.cuentaDestinoId.setValue('1');expect(fixture.componentInstance.form.hasError('iguales')).toBe(true);});
  it.each(['completada','en_revision'])('confirma %s y refresca cuentas',async estado=>{
    const req=post();req.flush({id:'9',cuentaOrigenId:'1',cuentaDestinoId:'2',monto:'10.25',estado,fecha:'2026-10-23T12:00:00Z',claveIdempotencia:req.request.headers.get('Idempotency-Key')});
    http.expectOne('/api/cuentas').flush(accounts);await fixture.whenStable();
    expect(fixture.componentInstance.confirmacion()?.estado).toBe(estado);
    expect(fixture.nativeElement.textContent).toContain(estado==='completada'?'Transferencia completada':'Transferencia ejecutada');
    expect(TestBed.inject(TransferenciasService).pendiente()).toBeNull();
    fixture.componentInstance.otra();expect(fixture.componentInstance.form.enabled).toBe(true);expect(fixture.componentInstance.form.invalid).toBe(true);
  });
  it.each([[422,'SALDO_INSUFICIENTE'],[422,'CUENTA_INACTIVA'],[404,'CUENTA_NO_EXISTE'],[409,'CONFLICTO_IDEMPOTENCIA']])('muestra rechazo %s %s',async(status,codigo)=>{
    post().flush({codigo,mensaje:'Detalle del backend'},{status:Number(status),statusText:'Rejected'});await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Detalle del backend');
    expect(fixture.componentInstance.form.enabled).toBe(true);
    expect(TestBed.inject(TransferenciasService).pendiente()).toBeNull();
  });
  it('muestra disponible de limite diario',async()=>{
    post().flush({codigo:'LIMITE_DIARIO_EXCEDIDO',mensaje:'Limite diario excedido',montoRestanteDisponible:'2000'},{status:422,statusText:'Rejected'});
    await fixture.whenStable();expect(fixture.nativeElement.textContent).toContain('2.000,00');
  });
  it.each([0,503,500])('preserva datos y clave ante resultado incierto %s',async status=>{
    const first=post();const key=first.request.headers.get('Idempotency-Key');const body=first.request.body;
    first.flush({codigo:'RESULTADO_DESCONOCIDO'},{status,statusText:'Unknown'});await fixture.whenStable();
    expect(fixture.componentInstance.desconocido()).toBe(true);expect(fixture.componentInstance.form.disabled).toBe(true);
    http.expectNone('/api/transferencias');
    fixture.componentInstance.form.patchValue({monto:'99'}); // Even programmatic edits cannot alter the stored attempt.
    fixture.componentInstance.enviar();const retry=http.expectOne('/api/transferencias');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);expect(retry.request.body).toEqual(body);
    retry.flush({codigo:'SALDO_INSUFICIENTE',mensaje:'Saldo insuficiente'},{status:422,statusText:'Rejected'});
  });
  it('impide doble envio mientras esta pendiente',()=>{
    const request=post();fixture.componentInstance.enviar();http.expectNone('/api/transferencias');
    request.flush({codigo:'RESULTADO_DESCONOCIDO'},{status:503,statusText:'Unknown'});
  });
  it('recupera el intento al recrear la pantalla',()=>{
    const request=post();const key=request.request.headers.get('Idempotency-Key');
    request.flush({codigo:'RESULTADO_DESCONOCIDO'},{status:503,statusText:'Unknown'});
    fixture.destroy();fixture=TestBed.createComponent(TransferenciaForm);http.expectOne('/api/cuentas').flush(accounts);
    expect(fixture.componentInstance.form.disabled).toBe(true);expect(fixture.componentInstance.desconocido()).toBe(true);
    expect(TestBed.inject(TransferenciasService).pendiente()?.clave).toBe(key);
    expect(JSON.parse(sessionStorage.getItem('evaluacion.transferencia.pendiente')!).clave).toBe(key);
  });
  it('timeout mantiene clave y no reintenta automaticamente',()=>{
    vi.useFakeTimers();
    try {
      const request=post();const key=request.request.headers.get('Idempotency-Key');
      vi.advanceTimersByTime(20001);
      expect(fixture.componentInstance.desconocido()).toBe(true);
      expect(TestBed.inject(TransferenciasService).pendiente()?.clave).toBe(key);
      expect(request.cancelled).toBe(true);http.expectNone('/api/transferencias');
    } finally {vi.useRealTimers();}
  });

  it.each([null, {}, {id:'1'}, {id:9}, {estado:'rechazada'}, {claveIdempotencia:'other'},
    {cuentaOrigenId:'2'}, {cuentaDestinoId:'1'}, {monto:'10.26'}, {monto:10.25}, {id:'9223372036854775808'}])
    ('conserva intento ante confirmacion invalida %j', patch => {
      const req=post(); const key=req.request.headers.get('Idempotency-Key'); const body=req.request.body;
      const response=patch===null?null:{id:'9',cuentaOrigenId:'1',cuentaDestinoId:'2',monto:'10.25',
        estado:'completada',fecha:'2026-10-23T12:00:00Z',claveIdempotencia:key,...patch};
      // An empty object and id-only payload are explicitly incomplete, not merged confirmations.
      req.flush(patch && Object.keys(patch).length<=1 && (!('estado' in patch) && !('monto' in patch) && !('claveIdempotencia' in patch) && !('cuentaOrigenId' in patch) && !('cuentaDestinoId' in patch)) ? patch : response);
      expect(fixture.componentInstance.confirmacion()).toBeNull();
      expect(fixture.componentInstance.desconocido()).toBe(true);
      expect(TestBed.inject(TransferenciasService).pendiente()?.clave).toBe(key);
      http.expectNone('/api/cuentas');http.expectNone('/api/transferencias');
      fixture.componentInstance.enviar(); const retry=http.expectOne('/api/transferencias');
      expect(retry.request.body).toEqual(body); expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
      retry.flush(null);
    });
  it('confirma identificador BIGINT sin redondeo',()=>{
    const req=post(); req.flush({id:'9223372036854775807',cuentaOrigenId:'1',cuentaDestinoId:'2',monto:'10.25',
      estado:'completada',fecha:'2026-10-23T12:00:00Z',claveIdempotencia:req.request.headers.get('Idempotency-Key')});
    http.expectOne('/api/cuentas').flush(accounts);
    expect(fixture.componentInstance.confirmacion()?.id).toBe('9223372036854775807');
  });

  it('no envia si no puede persistir el intento y conserva clave para reintento explicito',()=>{
    const storage=vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('quota');});
    try {
      valid();fixture.componentInstance.enviar();http.expectNone('/api/transferencias');
      const retained=TestBed.inject(TransferenciasService).pendiente();
      expect(retained).not.toBeNull();expect(fixture.componentInstance.form.disabled).toBe(true);
      expect(fixture.componentInstance.confirmacion()).toBeNull();
      storage.mockRestore();fixture.componentInstance.enviar();const retry=http.expectOne('/api/transferencias');
      expect(retry.request.headers.get('Idempotency-Key')).toBe(retained?.clave);
      expect(retry.request.body).toEqual(retained?.datos);retry.flush(null);
    } finally {storage.mockRestore();}
  });
  it('confirma solo respuesta validada pero bloquea otra operacion si no puede retirar el pendiente',()=>{
    const req=post();const storage=vi.spyOn(Storage.prototype,'removeItem').mockImplementation(()=>{throw new Error('denied');});
    try {
      req.flush({id:'9',cuentaOrigenId:'1',cuentaDestinoId:'2',monto:'10.25',estado:'completada',
        fecha:'2026-10-23T12:00:00Z',claveIdempotencia:req.request.headers.get('Idempotency-Key')});
      http.expectOne('/api/cuentas').flush(accounts);
      expect(fixture.componentInstance.confirmacion()?.id).toBe('9');
      expect(TestBed.inject(TransferenciasService).pendiente()).not.toBeNull();
      expect(TestBed.inject(TransferenciasService).errorAlmacenamiento()).toBeTruthy();
      fixture.componentInstance.otra();expect(fixture.componentInstance.confirmacion()?.id).toBe('9');
      fixture.componentInstance.enviar();http.expectNone('/api/transferencias');
    } finally {storage.mockRestore();}
  });
});
