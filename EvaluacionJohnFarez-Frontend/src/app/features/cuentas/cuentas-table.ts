import { Component, input, output } from '@angular/core';
import { UsdExactPipe } from '../../shared/pipes/usd-exact.pipe';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Cuenta } from '../../core/models/cuenta';

export interface CargaPagina {
  page: number;
  size: number;
  sort: 'saldo' | 'id' | 'numeroCuenta' | 'titular';
  order: 'asc' | 'desc';
}

@Component({
  selector: 'app-cuentas-table',
  imports: [TableModule, TagModule, UsdExactPipe],
  templateUrl: './cuentas-table.html',
  styleUrl: './cuentas-table.css'
})
export class CuentasTable {
  readonly cuentas = input.required<Cuenta[]>();
  readonly total = input(0);
  readonly cargando = input(false);
  readonly buscando = input(false);
  readonly filas = input(10);
  readonly cargar = output<CargaPagina>();

  onLazyLoad(event: TableLazyLoadEvent): void {
    const size = event.rows ?? this.filas();
    const page = Math.floor((event.first ?? 0) / size);
    const campo = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
    const sort = campo === 'saldo' ? 'saldo' : 'id';
    const order: 'asc' | 'desc' = (event.sortOrder ?? 1) < 0 ? 'desc' : 'asc';
    this.cargar.emit({ page, size, sort, order });
  }
}
