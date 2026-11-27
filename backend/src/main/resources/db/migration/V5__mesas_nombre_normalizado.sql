alter table mesas add column nombre_normalizado varchar(90);

update mesas set nombre_normalizado = lower(trim(nombre));

alter table mesas alter column nombre_normalizado set not null;
alter table mesas add constraint uq_mesas_nombre_normalizado unique (nombre_normalizado);
