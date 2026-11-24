package com.appresso.backend.inventario;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/** Operaciones de inventario reservadas al administrador por PoliticaPermisos. */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

	private final ProductoService productos;
	private final MovimientoInventarioRepository movimientos;
	private final ZoneId zona;

	public InventarioController(ProductoService productos, MovimientoInventarioRepository movimientos, ZoneId zona) {
		this.productos = productos;
		this.movimientos = movimientos;
		this.zona = zona;
	}

	@PostMapping("/productos/{id}/ajustes")
	public ProductoRespuesta ajustar(@PathVariable Long id, @Valid @RequestBody AjusteExistenciasRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return productos.ajustar(id, solicitud, actor.id());
	}

	@GetMapping("/movimientos")
	public List<MovimientoInventarioRespuesta> historial(
			@RequestParam(defaultValue = "") String producto,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			throw new CampoInvalidoCatalogoException("hasta", "La fecha final debe ser igual o posterior a la inicial");
		}
		return movimientos.consultar(desde == null ? null : desde.atStartOfDay(zona).toInstant(),
				hasta == null || hasta.equals(LocalDate.MAX) ? null
						: hasta.plusDays(1).atStartOfDay(zona).toInstant().minusNanos(1_000),
				producto.strip()).stream().map(MovimientoInventarioRespuesta::de).toList();
	}

	@GetMapping("/alertas")
	public List<ProductoRespuesta> alertas() {
		return productos.listarStockBajo();
	}
}
