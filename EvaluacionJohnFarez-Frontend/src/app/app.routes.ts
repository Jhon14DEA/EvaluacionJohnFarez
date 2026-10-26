import { inject } from '@angular/core';
import { TransferenciasService } from './core/services/transferencias.service';
import { Routes } from '@angular/router';
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/cuentas/cuentas-dashboard').then(m => m.CuentasDashboard), title: 'Cuentas bancarias | John Farez' },
  { path: 'transferencias/nueva', canDeactivate: [() => !inject(TransferenciasService).enviando()], loadComponent: () => import('./features/transferencias/transferencia-form').then(m => m.TransferenciaForm), title: 'Nueva transferencia | John Farez' },
  { path: '**', redirectTo: '' }
];
