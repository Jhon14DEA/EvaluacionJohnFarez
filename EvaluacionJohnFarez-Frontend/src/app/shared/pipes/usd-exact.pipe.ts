import { Pipe, PipeTransform } from '@angular/core';
import { validDecimal } from '../../core/models/exact-financial';
@Pipe({ name: 'usdExact' })
export class UsdExactPipe implements PipeTransform {
  transform(value: string): string {
    if (!validDecimal(value)) return 'Importe no disponible';
    const [whole, fraction = ''] = value.split('.');
    return '$' + whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + fraction.padEnd(2, '0');
  }
}
