import { Component, inject, signal } from '@angular/core';
import { UsdExactPipe } from '../../shared/pipes/usd-exact.pipe';
import { decimalInput, validConfirmation } from '../../core/models/exact-financial';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { CuentasService } from '../../core/services/cuentas.service';
import { TransferenciasService } from '../../core/services/transferencias.service';
import { Cuenta } from '../../core/models/cuenta';
import { CrearTransferencia, ErrorFinanciero, Transferencia } from '../../core/models/transferencia';
export function montoValido(control: AbstractControl): ValidationErrors | null {
  if (control.value === null || control.value === '') return null;
  return decimalInput(control.value) !== null ? null : { decimales: true };
}
@Component({ selector: 'app-transferencia-form', imports: [ReactiveFormsModule, UsdExactPipe, SelectModule, InputTextModule, ButtonModule, MessageModule],
  templateUrl: './transferencia-form.html', styleUrl: './transferencia-form.css' })
export class TransferenciaForm {
  private readonly cuentasService = inject(CuentasService);
  readonly service = inject(TransferenciasService);
  readonly cuentas = signal<(Cuenta & { etiqueta: string })[]>([]);
  readonly cargando = signal(true);
  readonly errorCuentas = signal(false);
  readonly enviando = this.service.enviando;
  readonly desconocido = signal(false);
  readonly error = signal<ErrorFinanciero | null>(null);
  readonly confirmacion = signal<Transferencia | null>(null);
  readonly form = new FormGroup({
    cuentaOrigenId: new FormControl<string | null>(null, Validators.required),
    cuentaDestinoId: new FormControl<string | null>(null, Validators.required),
    monto: new FormControl<string | null>(null, [Validators.required, montoValido])
  }, { validators: c => c.value.cuentaOrigenId != null && c.value.cuentaOrigenId === c.value.cuentaDestinoId ? { iguales: true } : null });
  constructor() {
    const pendiente = this.service.pendiente();
    if (pendiente) { this.form.patchValue(pendiente.datos); this.form.disable(); this.desconocido.set(true); }
    this.cargarCuentas();
  }
  cargarCuentas(): void {
    this.cargando.set(true); this.errorCuentas.set(false);
    this.cuentasService.listar().subscribe({
      next: cuentas => { this.cuentas.set(cuentas.map(c => ({ ...c, etiqueta: `${c.numeroCuenta} · ${c.titular}` }))); this.cargando.set(false); },
      error: () => { this.errorCuentas.set(true); this.cargando.set(false); }
    });
  }
  enviar(): void {
    if (this.enviando() || this.confirmacion() || this.service.recuperacionBloqueada()) return;
    if (!this.desconocido() && (this.form.invalid || this.cargando() || this.errorCuentas())) { this.form.markAllAsTouched(); return; }
    const raw = this.form.getRawValue();
    const datos = { ...raw, monto: decimalInput(raw.monto) } as CrearTransferencia;
    const intento = this.service.crearIntento(datos);
    if (!intento) { if (this.service.pendiente()) { this.form.disable(); this.desconocido.set(true); } return; }
    this.enviando.set(true); this.form.disable(); this.error.set(null);
    this.service.enviar(intento).subscribe({
      next: respuesta => {
        this.enviando.set(false);
        if (!validConfirmation(respuesta, intento)) { this.desconocido.set(true); return; }
        this.desconocido.set(false); this.confirmacion.set(respuesta);
        this.service.limpiar(); this.cargarCuentas();
      },
      error: (failure: unknown) => {
        this.enviando.set(false);
        const http = failure instanceof HttpErrorResponse ? failure : null;
        const body = http?.error as Partial<ErrorFinanciero> | undefined;
        // Only definitive domain/client errors release a key. All technical failures are uncertain.
        const definitivo = http != null && [400,404,409,422].includes(http.status) && typeof body?.codigo === 'string' && typeof body?.mensaje === 'string' && body.codigo !== 'RESULTADO_DESCONOCIDO';
        if (!definitivo) { this.desconocido.set(true); return; }
        this.error.set({ codigo: body?.codigo ?? `HTTP_${http.status}`, mensaje: body?.mensaje ?? 'No se pudo procesar la solicitud.', montoRestanteDisponible: body?.montoRestanteDisponible });
        if (this.service.limpiar()) { this.desconocido.set(false); this.form.enable(); } else { this.desconocido.set(true); }
      }
    });
  }
  otra(): void { if (this.service.pendiente() || this.service.recuperacionBloqueada()) return; this.confirmacion.set(null); this.error.set(null); this.form.reset(); this.form.enable(); }
}
