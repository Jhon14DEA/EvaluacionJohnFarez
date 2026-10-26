import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { CuentasService } from '../../core/services/cuentas.service';
import { Cuenta, ConsultaCuentas } from '../../core/models/cuenta';
import { CuentasTable, CargaPagina } from './cuentas-table';

@Component({
  selector: 'app-cuentas-dashboard',
  imports: [ReactiveFormsModule, DatePipe, ButtonModule, InputTextModule, MessageModule, SkeletonModule, CuentasTable],
  templateUrl: './cuentas-dashboard.html',
  styleUrl: './cuentas-dashboard.css'
})
export class CuentasDashboard {
  private readonly service = inject(CuentasService);
  private readonly destroyRef = inject(DestroyRef);
  readonly cuentas = signal<Cuenta[]>([]);
  readonly total = signal(0);
  readonly estado = signal<'loading' | 'ready' | 'error'>('loading');
  readonly actualizado = signal<Date | null>(null);
  readonly busqueda = new FormControl('', { nonNullable: true });
  // Ultima consulta solicitada al servidor; la tabla lazy define page/sort.
  private consulta: ConsultaCuentas = { page: 0, size: 10, sort: 'saldo', order: 'asc' };
  readonly filas = 10;

  constructor() {
    // La busqueda reinicia a la primera pagina y consulta al servidor con debounce.
    this.busqueda.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(texto => {
        this.consulta = { ...this.consulta, q: texto, page: 0 };
        this.consultar();
      });
  }

  // Disparado por la tabla al cambiar pagina u orden.
  alCargar(evento: CargaPagina): void {
    this.consulta = {
      ...this.consulta,
      page: evento.page,
      size: evento.size,
      sort: evento.sort,
      order: evento.order
    };
    this.consultar();
  }

  recargar(): void {
    this.consultar();
  }

  get buscando(): boolean {
    return this.busqueda.value.trim().length > 0;
  }

  private consultar(): void {
    this.estado.set('loading');
    this.service.listarPaginado(this.consulta)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: pagina => {
          this.cuentas.set(pagina.contenido);
          this.total.set(pagina.totalElementos);
          this.actualizado.set(new Date());
          this.estado.set('ready');
        },
        error: () => this.estado.set('error')
      });
  }
}
