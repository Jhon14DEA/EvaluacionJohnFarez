package ec.johnfarez.evaluacion.cuentas.repository;

import ec.johnfarez.evaluacion.cuentas.entity.Cuenta;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CuentaRepository implements PanacheRepository<Cuenta> {
    public boolean existeNumero(String numero) {
        return count("numeroCuenta", numero) > 0;
    }

    public List<Cuenta> listarPagina(CriterioOrden criterio, int pagina, int tamano) {
        boolean ascendente = criterio.ascendente();
        String campo = criterio.campo();
        var orden = ascendente ? Sort.by(campo).ascending() : Sort.by(campo).descending();
        // Desempate estable por id para que paginas consecutivas no repitan ni omitan filas.
        if (!"id".equals(campo)) {
            orden = orden.and("id", ascendente ? Sort.Direction.Ascending : Sort.Direction.Descending);
        }
        return buscar(criterio.texto(), orden).page(Page.of(pagina, tamano)).list();
    }

    public long contar(String texto) {
        return buscar(texto, null).count();
    }

    private io.quarkus.hibernate.orm.panache.PanacheQuery<Cuenta> buscar(String texto, Sort orden) {
        if (texto == null || texto.isBlank()) {
            return orden == null ? findAll() : findAll(orden);
        }
        var filtro = "lower(numeroCuenta) like :q or lower(titular) like :q";
        var params = Map.<String, Object>of("q", "%" + texto.toLowerCase() + "%");
        return orden == null ? find(filtro, params) : find(filtro, orden, params);
    }
}
