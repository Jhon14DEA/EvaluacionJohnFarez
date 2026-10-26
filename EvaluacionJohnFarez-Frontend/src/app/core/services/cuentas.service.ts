import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cuenta, ConsultaCuentas, Pagina } from '../models/cuenta';

@Injectable({ providedIn: 'root' })
export class CuentasService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Cuenta[]> {
    return this.http.get<Cuenta[]>(`${environment.cuentasApiUrl}/cuentas`);
  }

  listarPaginado(consulta: ConsultaCuentas): Observable<Pagina<Cuenta>> {
    let params = new HttpParams()
      .set('page', consulta.page)
      .set('size', consulta.size);
    if (consulta.q && consulta.q.trim().length > 0) {
      params = params.set('q', consulta.q.trim());
    }
    if (consulta.sort) {
      params = params.set('sort', consulta.sort);
    }
    if (consulta.order) {
      params = params.set('order', consulta.order);
    }
    return this.http.get<Pagina<Cuenta>>(`${environment.cuentasApiUrl}/cuentas`, { params });
  }
}
