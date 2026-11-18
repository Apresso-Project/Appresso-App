-- 3.1.1: imagen del producto. Se guarda la dirección (URL) de la imagen, igual que el QR de pago;
-- subir y almacenar archivos queda fuera de este entregable.
alter table productos add column imagen_url varchar(500);
