import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { registerLocaleData } from '@angular/common';
import esEC from '@angular/common/locales/es-EC';
import { providePrimeNG } from 'primeng/config';
import { CuentasDashboard } from './cuentas-dashboard';
import { Cuenta, Pagina } from '../../core/models/cuenta';
registerLocaleData(esEC);

const datos: Cuenta[] = [
  { id: '2', numeroCuenta: '0000000002', titular: 'Luis Vera', tipo: 'corriente', saldo: '20', estado: 'inactivo' },
  { id: '3', numeroCuenta: '0000000003', titular: 'Maria Paz', tipo: 'ahorro', saldo: '300', estado: 'activo' },
  { id: '1', numeroCuenta: '0000000001', titular: 'Ana Torres', tipo: 'ahorro', saldo: '1500.5', estado: 'activo' }
];

function pagina(contenido: Cuenta[], total = contenido.length): Pagina<Cuenta> {
  return { contenido, pagina: 0, tamano: 10, totalElementos: total, totalPaginas: Math.max(1, Math.ceil(total / 10)) };
}

describe('Dashboard de cuentas (paginacion de servidor)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CuentasDashboard>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CuentasDashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), providePrimeNG({ ripple: false })]
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CuentasDashboard);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  // La tabla lazy dispara una consulta inicial al renderizar.
  function resolverConsulta(contenido: Cuenta[] = datos, total = contenido.length): TestRequest {
    const request = http.expectOne(r => r.url === '/api/cuentas');
    request.flush(pagina(contenido, total));
    return request;
  }
  async function cargar(contenido: Cuenta[] = datos, total = contenido.length) {
    resolverConsulta(contenido, total);
    await fixture.whenStable();
    fixture.detectChanges();
  }
  function rows(): HTMLTableRowElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
  }

  it('consulta GET paginada y muestra los campos de la pagina', async () => {
    const request = resolverConsulta();
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBeTruthy();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ana Torres');
    expect(fixture.nativeElement.textContent).toContain('0000000001');
    expect(fixture.nativeElement.textContent).toContain('Ahorro');
    expect(fixture.nativeElement.textContent).toContain('Corriente');
  });

  it('muestra en el orden que entrega el servidor', async () => {
    await cargar();
    expect(rows().map(r => r.querySelector('.holder')?.textContent?.trim()))
      .toEqual(['Luis Vera', 'Maria Paz', 'Ana Torres']);
  });

  it('pide al servidor orden descendente al pulsar el encabezado de saldo', async () => {
    await cargar();
    const header = fixture.nativeElement.querySelector('th[pSortableColumn]') as HTMLElement;
    header.click();
    await fixture.whenStable();
    const request = http.expectOne(r => r.url === '/api/cuentas');
    expect(request.request.params.get('sort')).toBe('saldo');
    expect(request.request.params.get('order')).toBe('desc');
    request.flush(pagina([...datos].reverse()));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(rows()[0].textContent).toContain('Ana Torres');
  });

  it('identifica activo e inactivo con texto', async () => {
    await cargar();
    expect(rows()[0].querySelector('p-tag')?.textContent?.trim()).toBe('Inactivo');
    expect(rows()[2].querySelector('p-tag')?.textContent?.trim()).toBe('Activo');
  });

  it('formatea USD con dos decimales en locale Ecuador', async () => {
    await cargar();
    expect(rows()[2].querySelector('.saldo')?.textContent?.replace(/\s/g, '')).toBe('$1.500,50');
  });

  it('presenta centavos grandes sin Number', async () => {
    await cargar([{ ...datos[2], id: '9007199254740993', saldo: '99999999999999999.99' }]);
    expect(rows()[0].querySelector('.saldo')?.textContent?.trim()).toBe('$99.999.999.999.999.999,99');
  });

  it('muestra estado vacio real', async () => {
    await cargar([], 0);
    expect(fixture.nativeElement.textContent).toContain('no hay cuentas');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('muestra error y permite reintentar', async () => {
    http.expectOne(r => r.url === '/api/cuentas').flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar');
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find(b => b.textContent?.includes('Reintentar'))!.click();
    await cargar();
    expect(rows()).toHaveLength(3);
  });

  it('envia el filtro de texto al servidor con debounce y reinicia a la primera pagina', async () => {
    await cargar();
    fixture.componentInstance.busqueda.setValue('ana');
    await new Promise(r => setTimeout(r, 350));
    const request = http.expectOne(r => r.url === '/api/cuentas');
    expect(request.request.params.get('q')).toBe('ana');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(pagina([datos[2]], 1));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain('Ana Torres');
  });

  it('refleja el total de elementos informado por el servidor', async () => {
    await cargar(datos, 42);
    expect(fixture.nativeElement.textContent).toContain('42');
  });
});
