-- HU 4.1.1 (RF-17): la recompensa que dio el descuento de la comanda. Se guarda para recalcular
-- el descuento (p. ej. un porcentaje) cada vez que cambian los productos.
alter table comandas add column recompensa_id bigint references recompensas (id);
