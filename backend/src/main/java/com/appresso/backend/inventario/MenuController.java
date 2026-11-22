package com.appresso.backend.inventario;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta del menú (RF-11, entregable 3.3.1). Quién entra lo decide {@code PoliticaPermisos}:
 * administrador, mesero y cliente (403 a cajero y cocinero, 401 sin sesión). Es de solo lectura.
 * Contrato en docs/api-menu-cocina.md.
 */
@RestController
@RequestMapping("/api/menu")
public class MenuController {

	private final MenuService servicio;

	public MenuController(MenuService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<MenuCategoriaRespuesta> consultar() {
		return servicio.consultar();
	}

}
