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
 * Categorías del catálogo (RF-06). Quién entra lo decide {@code PoliticaPermisos} (2.3.1): leer,
 * administrador y mesero; escribir, solo administrador (403 a los demás, 401 sin sesión).
 * Contrato en docs/api-catalogo.md.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

	private final CategoriaService servicio;

	public CategoriaController(CategoriaService servicio) {
		this.servicio = servicio;
	}

	/** Por defecto, solo las activas. {@code ?todas=true} incluye las desactivadas y es solo del administrador. */
	@GetMapping
	public List<CategoriaRespuesta> listar(@RequestParam(defaultValue = "false") boolean todas,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		if (todas) {
			PermisosCatalogo.exigirAdministrador(yo);
		}
		return servicio.listar(todas);
	}

	@GetMapping("/{id}")
	public CategoriaRespuesta obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.obtener(id, PermisosCatalogo.esAdministrador(yo));
	}

	@PostMapping
	public ResponseEntity<CategoriaRespuesta> crear(@Valid @RequestBody CategoriaRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		CategoriaRespuesta creada = servicio.crear(solicitud, yo.id());
		return ResponseEntity.created(URI.create("/api/categorias/" + creada.id())).body(creada);
	}

	/** Renombra. */
	@PutMapping("/{id}")
	public CategoriaRespuesta renombrar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.renombrar(id, solicitud, yo.id());
	}

	@PostMapping("/{id}/desactivar")
	public CategoriaRespuesta desactivar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.desactivar(id, yo.id());
	}

	@PostMapping("/{id}/activar")
	public CategoriaRespuesta activar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.activar(id, yo.id());
	}

}
