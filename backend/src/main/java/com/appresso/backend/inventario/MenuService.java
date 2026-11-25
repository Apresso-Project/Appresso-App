package com.appresso.backend.inventario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menú de solo lectura (RF-11, RF-13, RNF-17): los productos que se pueden pedir ahora, agrupados por
 * categoría. Un producto sale solo si está activo, su categoría está activa y le quedan existencias.
 * Se arma en cada consulta con los datos vigentes (sin caché), para que el precio y las existencias
 * nunca estén desactualizados.
 */
@Service
public class MenuService {

	private final ProductoRepository productos;

	public MenuService(ProductoRepository productos) {
		this.productos = productos;
	}

	/** Categorías con productos disponibles, por nombre; dentro de cada una, productos por nombre. */
	@Transactional(readOnly = true)
	public List<MenuCategoriaRespuesta> consultar() {
		// La consulta ya viene filtrada y ordenada (categoría y luego producto), así que agrupar en
		// orden de aparición conserva ese orden.
		Map<Long, List<MenuProductoRespuesta>> porCategoria = new LinkedHashMap<>();
		Map<Long, String> nombres = new LinkedHashMap<>();
		for (Producto p : productos.findDelMenu()) {
			Long categoriaId = p.getCategoria().getId();
			nombres.putIfAbsent(categoriaId, p.getCategoria().getNombre());
			porCategoria.computeIfAbsent(categoriaId, id -> new ArrayList<>()).add(MenuProductoRespuesta.de(p));
		}
		List<MenuCategoriaRespuesta> menu = new ArrayList<>(porCategoria.size());
		porCategoria.forEach((id, lista) -> menu.add(new MenuCategoriaRespuesta(id, nombres.get(id), List.copyOf(lista))));
		return List.copyOf(menu);
	}

}
