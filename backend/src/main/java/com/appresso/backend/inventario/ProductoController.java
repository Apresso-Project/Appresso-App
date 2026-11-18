package com.appresso.backend.inventario;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * Productos del catálogo (RF-06). Quién entra lo decide {@code PoliticaPermisos} (2.3.1): leer,
 * administrador y mesero; escribir, solo administrador (403 a los demás, 401 sin sesión).
 * Los productos de una categoría desactivada no salen en el menú. Contrato en docs/api-catalogo.md.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	private final ProductoService servicio;

	public ProductoController(ProductoService servicio) {
		this.servicio = servicio;
	}

	/**
	 * Por defecto, solo lo que está en el menú. {@code ?todos=true} incluye desactivados y los de categorías
	 * desactivadas, y es solo del administrador. {@code ?categoriaId=} filtra por categoría.
	 */
	@GetMapping
	public List<ProductoRespuesta> listar(@RequestParam(required = false) Long categoriaId,
			@RequestParam(defaultValue = "false") boolean todos, @AuthenticationPrincipal UsuarioAutenticado yo) {
		if (todos) {
			PermisosCatalogo.exigirAdministrador(yo);
		}
		return servicio.listar(categoriaId, todos);
	}

	@GetMapping("/{id}")
	public ProductoRespuesta obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.obtener(id, PermisosCatalogo.esAdministrador(yo));
	}

	@PostMapping
	public ResponseEntity<ProductoRespuesta> crear(@Valid @RequestBody ProductoCrearRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		ProductoRespuesta creado = servicio.registrar(solicitud, yo.id());
		return ResponseEntity.created(URI.create("/api/productos/" + creado.id())).body(creado);
	}

	@PutMapping("/{id}")
	public ProductoRespuesta editar(@PathVariable Long id, @Valid @RequestBody ProductoEditarRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.modificar(id, solicitud, yo.id());
	}

	@PostMapping("/{id}/desactivar")
	public ProductoRespuesta desactivar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.desactivar(id, yo.id());
	}

	@PostMapping("/{id}/activar")
	public ProductoRespuesta activar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.activar(id, yo.id());
	}

}
