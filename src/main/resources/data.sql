INSERT INTO especialidades (id, nombre) VALUES
  (1, 'Cardiología'),
  (2, 'Dermatología'),
  (3, 'Pediatría'),
  (4, 'Medicina General'),
  (5, 'Odontología'),
  (6, 'Neurología'),
  (7, 'Oftalmología'),
  (8, 'Traumatología'),
  (9, 'Urología'),
  (10, 'Ginecología'),
  (11, 'Psiquiatría'),
  (12, 'Otorrinolaringología'),
  (13, 'Endocrinología'),
  (14, 'Gastroenterología');

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
  (6, 'Jorge', 'Vargas León', 'CMP10006', 4.5, 18, TRUE, 4, 3),
  (7, 'Patricia', 'Ríos Salazar', 'CMP10007', 4.7, 11, TRUE, 5, 2),
  (8, 'Diego', 'Herrera Lazo', 'CMP10008', 4.6, 14, TRUE, 6, 1),
  (9, 'Carmen', 'Vega Núñez', 'CMP10009', 4.8, 9, TRUE, 7, 3),
  (10, 'Ricardo', 'Salas Ponce', 'CMP10010', 4.5, 16, TRUE, 8, 2),
  (11, 'Eduardo', 'Paredes Luna', 'CMP10011', 4.6, 13, TRUE, 9, 1),
  (12, 'Valeria', 'Chávez Rojas', 'CMP10012', 4.9, 11, TRUE, 10, 2),
  (13, 'Renzo', 'Ibáñez Campos', 'CMP10013', 4.7, 9, TRUE, 11, 3),
  (14, 'Daniela', 'Flores Quiroz', 'CMP10014', 4.5, 7, TRUE, 12, 1),
  (15, 'Hugo', 'Medina Paz', 'CMP10015', 4.6, 17, TRUE, 13, 2),
  (16, 'Pamela', 'Osorio Díaz', 'CMP10016', 4.8, 10, TRUE, 14, 3);

-- Reiniciar los contadores de identidad.
-- Sin estas tres lineas, el primer medico que se registre por el CRUD intenta
-- usar el id 1 y choca con los datos de ejemplo (clave primaria duplicada).
ALTER TABLE especialidades ALTER COLUMN id RESTART WITH 15;
ALTER TABLE establecimientos ALTER COLUMN id RESTART WITH 4;
ALTER TABLE doctores ALTER COLUMN id RESTART WITH 17;
