import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import esEC from '@angular/common/locales/es-EC';
import { providePrimeNG } from 'primeng/config';
import { BancaAura } from './core/theme';
import { routes } from './app.routes';
registerLocaleData(esEC);
export const appConfig: ApplicationConfig = {
  providers: [provideBrowserGlobalErrorListeners(), provideRouter(routes), provideHttpClient(),
    { provide: LOCALE_ID, useValue: 'es-EC' },
    providePrimeNG({ theme: { preset: BancaAura, options: { darkModeSelector: false } },
      translation: { emptyMessage: 'No hay resultados', aria: { firstPageLabel: 'Primera página',
        lastPageLabel: 'Última página', nextPageLabel: 'Página siguiente', previousPageLabel: 'Página anterior',
        pageLabel: 'Página {page}', rowsPerPageLabel: 'Cuentas por página' } } })]
};
