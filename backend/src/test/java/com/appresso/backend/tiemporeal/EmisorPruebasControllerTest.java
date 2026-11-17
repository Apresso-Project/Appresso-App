package com.appresso.backend.tiemporeal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class EmisorPruebasControllerTest {

	@Test
	void alternaEventosDeComandasYCatalogo() {
		PublicadorTiempoReal publicador = mock(PublicadorTiempoReal.class);
		new EmisorPruebasController(publicador).emitirAhora(10, 0);

		verify(publicador, times(5)).publicarComanda(eq(TipoEvento.COMANDA_CREADA), any());
		verify(publicador, times(5)).publicarProducto(eq(TipoEvento.EXISTENCIAS_ACTUALIZADAS), any());
	}

}
