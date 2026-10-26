import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
describe('App', () => {
  it('muestra el marco y el enlace accesible', async () => {
    TestBed.configureTestingModule({providers: [provideRouter([])]});
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('John Farez');
    expect(fixture.nativeElement.querySelector('a[href="#contenido"]')).toBeTruthy();
  });
});
