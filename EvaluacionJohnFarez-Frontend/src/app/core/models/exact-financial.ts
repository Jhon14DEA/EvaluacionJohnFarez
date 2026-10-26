import { CrearTransferencia, IntentoTransferencia, Transferencia } from './transferencia';
export function validId(value: unknown): value is string {
  return typeof value === 'string' && value.trim() === value && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}
export function validDecimal(value: unknown): value is string {
  return typeof value === 'string' && value.trim() === value && /^(0|[1-9][0-9]{0,16})(\.[0-9]{1,2})?$/.test(value);
}
export function cents(value: string): bigint {
  if (!validDecimal(value)) throw new Error('Invalid decimal');
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
}
export function decimalInput(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const normalized = value.replace(',', '.');
  return validDecimal(normalized) && cents(normalized) > 0n ? normalized : null;
}
function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
export function validRequest(value: unknown): value is CrearTransferencia {
  return record(value) && Object.keys(value).sort().join(',') === 'cuentaDestinoId,cuentaOrigenId,monto'
    && validId(value['cuentaOrigenId']) && validId(value['cuentaDestinoId'])
    && value['cuentaOrigenId'] !== value['cuentaDestinoId']
    && validDecimal(value['monto']) && cents(value['monto']) > 0n;
}
export function validAttempt(value: unknown): value is IntentoTransferencia {
  return record(value) && Object.keys(value).sort().join(',') === 'clave,datos'
    && typeof value['clave'] === 'string' && value['clave'].trim() === value['clave'] && /^[A-Za-z0-9._:-]{1,128}$/.test(value['clave'])
    && validRequest(value['datos']);
}
export function validConfirmation(value: unknown, attempt: IntentoTransferencia): value is Transferencia {
  return record(value) && validId(value['id'])
    && (value['estado'] === 'completada' || value['estado'] === 'en_revision')
    && value['claveIdempotencia'] === attempt.clave
    && value['cuentaOrigenId'] === attempt.datos.cuentaOrigenId
    && value['cuentaDestinoId'] === attempt.datos.cuentaDestinoId
    && validDecimal(value['monto']) && cents(value['monto']) === cents(attempt.datos.monto)
    && typeof value['fecha'] === 'string' && value['fecha'].trim() === value['fecha'] && /^\d{4}-\d{2}-\d{2}T.*(Z|[+-]\d{2}:\d{2})$/.test(value['fecha'])
    && Number.isFinite(Date.parse(value['fecha']));
}
