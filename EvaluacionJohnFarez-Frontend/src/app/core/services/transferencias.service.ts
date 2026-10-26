import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { CrearTransferencia, IntentoTransferencia } from '../models/transferencia';
import { validAttempt, validRequest } from '../models/exact-financial';
import { environment } from '../../../environments/environment';
@Injectable({ providedIn: 'root' })
export class TransferenciasService {
  private readonly http = inject(HttpClient);
  private readonly storageKey = 'evaluacion.transferencia.pendiente';
  readonly errorAlmacenamiento = signal<string | null>(null);
  readonly recuperacionBloqueada = signal(false);
  private readonly actual = signal<IntentoTransferencia | null>(this.recuperar());
  readonly pendiente = this.actual.asReadonly();
  readonly enviando = signal(false);
  private immutable(value: IntentoTransferencia): IntentoTransferencia {
    return Object.freeze({ clave: value.clave, datos: Object.freeze({ ...value.datos }) });
  }
  private recuperar(): IntentoTransferencia | null {
    try {
      const raw = sessionStorage.getItem(this.storageKey);
      if (raw === null) return null;
      const value: unknown = JSON.parse(raw);
      if (!validAttempt(value)) throw new Error('Invalid pending schema');
      return this.immutable(value);
    } catch {
      this.recuperacionBloqueada.set(true);
      this.errorAlmacenamiento.set('No se puede recuperar con seguridad la operacion pendiente. El resultado es incierto. No inicies otra operacion; conserva esta pestana y verifica el almacenamiento.');
      return null;
    }
  }
  limpiar(): boolean {
    try {
      sessionStorage.removeItem(this.storageKey);
      this.actual.set(null); this.errorAlmacenamiento.set(null); return true;
    } catch {
      this.errorAlmacenamiento.set('No se pudo retirar el intento del almacenamiento. Conservamos su clave y bloqueamos una nueva operacion para evitar duplicados.');
      return false;
    }
  }
  crearIntento(datos: CrearTransferencia): IntentoTransferencia | null {
    if (this.recuperacionBloqueada()) return null;
    const previous = this.actual();
    if (!previous && !validRequest(datos)) return null;
    const intento = previous ?? this.immutable({ clave: this.generarClave(), datos });
    this.actual.set(intento);
    try {
      sessionStorage.setItem(this.storageKey, JSON.stringify(intento));
      this.errorAlmacenamiento.set(null); return intento;
    } catch {
      this.errorAlmacenamiento.set('No se pudo guardar el intento. No se ha enviado esta solicitud. Conserva la pestana abierta y reintenta con los mismos datos y clave cuando el almacenamiento este disponible.');
      return null;
    }
  }
  private generarClave(): string {
    if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    // getRandomValues también está disponible en HTTP fuera de localhost.
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  }
  enviar(intento: IntentoTransferencia) {
    return this.http.post<unknown>(`${environment.transferenciasApiUrl}/transferencias`, intento.datos,
      { headers: { 'Idempotency-Key': intento.clave } }).pipe(timeout(20000));
  }
}
