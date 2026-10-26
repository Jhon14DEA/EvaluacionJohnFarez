export interface CrearTransferencia { cuentaOrigenId: string; cuentaDestinoId: string; monto: string; }
export interface Transferencia extends CrearTransferencia {
  id: string; fecha: string; estado: 'completada' | 'en_revision'; claveIdempotencia: string;
}
export interface ErrorFinanciero { codigo: string; mensaje: string; montoRestanteDisponible?: string; }
export interface IntentoTransferencia { clave: string; datos: CrearTransferencia; }
