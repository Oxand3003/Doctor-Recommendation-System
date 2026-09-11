INSERT INTO especialidades (id, nombre) VALUES
  (1, 'Cardiología'),
  (2, 'Dermatología'),
  (3, 'Pediatría'),
  (4, 'Medicina General');

INSERT INTO establecimientos (id, nombre, direccion, distrito) VALUES
  (1, 'Clínica San Felipe', 'Av. Gregorio Escobedo 650', 'Jesús María'),
  (2, 'Clínica Internacional', 'Av. Guardia Civil 385', 'San Borja'),
  (3, 'Centro Médico Salud Norte', 'Av. Carlos Izaguirre 920', 'Los Olivos');

INSERT INTO doctores
  (id, nombres, apellidos, cmp, rating, anios_experiencia, disponible, especialidad_id, establecimiento_id)
VALUES
  (1, 'María', 'Fernández Torres', 'CMP10001', 4.9, 15, TRUE, 1, 1),
  (2, 'Carlos', 'Ramírez Soto', 'CMP10002', 4.7, 20, TRUE, 1, 2),
  (3, 'Ana', 'Gómez Pérez', 'CMP10003', 4.8, 10, TRUE, 2, 2),
  (4, 'Luis', 'Castillo Vega', 'CMP10004', 4.6, 12, FALSE, 2, 3),
  (5, 'Sofía', 'Mendoza Ruiz', 'CMP10005', 4.9, 8, TRUE, 3, 1),
  (6, 'Jorge', 'Vargas León', 'CMP10006', 4.5, 18, TRUE, 4, 3);