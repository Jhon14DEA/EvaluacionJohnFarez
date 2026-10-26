export interface Cuenta {
  id: string;
  numeroCuenta: string;
  titular: string;
  tipo: 'ahorro' | 'corriente';
  saldo: string;
  estado: 'activo' | 'inactivo';
}

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface ConsultaCuentas {
  page: number;
  size: number;
  q?: string;
  sort?: 'saldo' | 'id' | 'numeroCuenta' | 'titular';
  order?: 'asc' | 'desc';
}
