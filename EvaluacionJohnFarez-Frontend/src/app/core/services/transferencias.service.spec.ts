import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TransferenciasService } from './transferencias.service';
const key='evaluacion.transferencia.pendiente';
const attempt={clave:'audit-key',datos:{cuentaOrigenId:'9007199254740993',cuentaDestinoId:'9223372036854775807',monto:'10.25'}};
describe('Recuperacion segura del intento',()=>{
  beforeEach(()=>{sessionStorage.clear();TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});});
  afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();TestBed.inject(HttpTestingController).verify();sessionStorage.clear();});
  it('envia y recupera la misma clave cuando randomUUID no esta disponible en HTTP',()=>{
    vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) });
    const service=TestBed.inject(TransferenciasService);
    const intento=service.crearIntento(attempt.datos)!;
    expect(intento.clave).toMatch(/^[0-9a-f]{32}$/);
    expect(JSON.parse(sessionStorage.getItem(key)!)).toEqual(intento);
    service.enviar(intento).subscribe();
    const request=TestBed.inject(HttpTestingController).expectOne('/api/transferencias');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe(intento.clave);
    expect(request.request.body).toEqual(attempt.datos);
    request.flush({});
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
    const recovered=TestBed.inject(TransferenciasService);
    expect(recovered.crearIntento(attempt.datos)).toEqual(intento);
  });
  it('recupera despues de recrear el inyector sin alterar datos',()=>{
    sessionStorage.setItem(key,JSON.stringify(attempt));
    const service=TestBed.inject(TransferenciasService);
    expect(service.pendiente()).toEqual(attempt);
    const reused=service.crearIntento({...attempt.datos,monto:'999'});
    expect(reused).toEqual(attempt);expect(Object.isFrozen(reused?.datos)).toBe(true);
    expect(Object.keys(JSON.parse(sessionStorage.getItem(key)!).datos).sort()).toEqual(['cuentaDestinoId','cuentaOrigenId','monto']);
  });
  it.each([null,{}, {clave:'bad key',datos:attempt.datos}, {clave:'x',datos:{...attempt.datos,monto:10.25}},
    {clave:'x',datos:{...attempt.datos,cuentaOrigenId:'0'}}, {clave:'x',datos:{...attempt.datos,cuentaOrigenId:attempt.datos.cuentaDestinoId}},
    {clave:'x',datos:{...attempt.datos,monto:'0'}}, {clave:'x',datos:{...attempt.datos,monto:'1.001'}},
    {clave:'x',datos:{...attempt.datos,cuentaOrigenId:'9223372036854775808'}}, {...attempt,titular:'unnecessary'},
    {clave:'x',datos:{...attempt.datos,saldo:'10.25'}}])('bloquea esquema invalido %j',value=>{
      const raw=JSON.stringify(value);sessionStorage.setItem(key,raw);
      const service=TestBed.inject(TransferenciasService);
      expect(service.recuperacionBloqueada()).toBe(true);expect(service.errorAlmacenamiento()).toBeTruthy();
      expect(service.crearIntento(attempt.datos)).toBeNull();expect(sessionStorage.getItem(key)).toBe(raw);
    });
  it('mantiene incertidumbre ante JSON truncado',()=>{
    sessionStorage.setItem(key,'{');expect(TestBed.inject(TransferenciasService).recuperacionBloqueada()).toBe(true);
  });
  it('bloquea nuevas operaciones si falla la lectura',()=>{
    vi.spyOn(Storage.prototype,'getItem').mockImplementation(()=>{throw new Error('denied');});
    const service=TestBed.inject(TransferenciasService);
    expect(service.recuperacionBloqueada()).toBe(true);expect(service.crearIntento(attempt.datos)).toBeNull();
  });
  it('falla al guardar sin enviar ni descartar clave',()=>{
    const service=TestBed.inject(TransferenciasService);
    const spy=vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('quota');});
    expect(service.crearIntento(attempt.datos)).toBeNull();const retained=service.pendiente();
    expect(service.errorAlmacenamiento()).toBeTruthy();TestBed.inject(HttpTestingController).expectNone('/api/transferencias');
    spy.mockRestore();expect(service.crearIntento({...attempt.datos,monto:'20'})).toEqual(retained);
  });
  it('no descarta intento cuando falla su retirada',()=>{
    sessionStorage.setItem(key,JSON.stringify(attempt));const service=TestBed.inject(TransferenciasService);
    vi.spyOn(Storage.prototype,'removeItem').mockImplementation(()=>{throw new Error('denied');});
    expect(service.limpiar()).toBe(false);expect(service.pendiente()).toEqual(attempt);expect(service.errorAlmacenamiento()).toBeTruthy();
  });
});
