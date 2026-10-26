import { cents, decimalInput, validId, validAttempt, validConfirmation } from './exact-financial';
import { UsdExactPipe } from '../../shared/pipes/usd-exact.pipe';
const attempt={clave:'audit-key',datos:{cuentaOrigenId:'9007199254740993',cuentaDestinoId:'9223372036854775807',monto:'99999999999999999.99'}};
describe('Contrato financiero exacto',()=>{
  it('preserva todos los centavos DECIMAL(19,2)',()=>{
    expect(cents(attempt.datos.monto)).toBe(9999999999999999999n);
    expect(new UsdExactPipe().transform(attempt.datos.monto)).toBe('$99.999.999.999.999.999,99');
    expect(cents('99999999999999999.99')-cents('99999999999999999.98')).toBe(1n);
  });
  it('preserva IDs mayores que Number.MAX_SAFE_INTEGER',()=>{
    expect(validId(attempt.datos.cuentaOrigenId)).toBe(true);
    expect(validId(attempt.datos.cuentaDestinoId)).toBe(true);
    expect(validId('9223372036854775808')).toBe(false);
    expect(validId(9007199254740992)).toBe(false);
    expect(validId('1\n')).toBe(false);
    expect(validAttempt({...attempt,clave:'key\n'})).toBe(false);
  });
  it.each(['0','-1','0.001','1e-10','1.0000000001','NaN','Infinity','100000000000000000',' 1','1.2.3','1\n'])('rechaza decimal %s',v=>expect(decimalInput(v)).toBeNull());
  it('admite entrada con coma sin redondear',()=>expect(decimalInput('99999999999999999,99')).toBe('99999999999999999.99'));
  it('valida intento y confirmacion exactos',()=>{
    expect(validAttempt(attempt)).toBe(true);
    expect(validConfirmation({...attempt.datos,id:'9007199254740993',estado:'en_revision',fecha:'2026-10-23T12:00:00Z',claveIdempotencia:attempt.clave},attempt)).toBe(true);
  });
});
