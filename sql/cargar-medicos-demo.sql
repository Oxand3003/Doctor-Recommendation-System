/*
  Carga de 30 medicos ficticios para llenar la base real de MediCerca.
  Compatible con las entidades JPA del proyecto.

  Relaciones creadas:
  usuarios -> roles
  medicos -> usuarios, especialidades, establecimientos
  establecimientos -> distritos
  formaciones_medico -> medicos
  servicios_medico -> medicos
  horarios_atencion -> medicos

  Ejecutar despues de que Hibernate haya creado las tablas.
  Los datos son ficticios y solo sirven para pruebas.
*/

SET NAMES utf8mb4;
START TRANSACTION;

INSERT IGNORE INTO roles (nombre) VALUES ('MEDICO');
SET @rol_medico = (SELECT id FROM roles WHERE nombre = 'MEDICO' LIMIT 1);
SET @ahora = CURRENT_TIMESTAMP;

/* Si ya existe un medico, se reutiliza su hash y su contrasena actual.
   Si no existe, se usa el hash de respaldo para la contrasena: password */
SET @hash_medico = COALESCE(
  (SELECT u.password_hash FROM usuarios u JOIN roles r ON r.id = u.rol_id
   WHERE r.nombre = 'MEDICO' ORDER BY u.id LIMIT 1),
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
);

INSERT IGNORE INTO especialidades (nombre, descripcion, activa) VALUES
('Cardiologia', 'Corazon, presion arterial y sistema circulatorio', true),
('Pediatria', 'Salud de ninos y adolescentes', true),
('Dermatologia', 'Piel, cabello y unas', true),
('Ginecologia y Obstetricia', 'Salud integral de la mujer y embarazo', true),
('Endocrinologia', 'Hormonas, diabetes y metabolismo', true),
('Neurologia', 'Sistema nervioso, memoria, sueno y cefaleas', true),
('Traumatologia y Ortopedia', 'Huesos, articulaciones y lesiones', true),
('Psiquiatria', 'Salud mental, ansiedad, animo y sueno', true),
('Gastroenterologia', 'Sistema digestivo, higado e intestino', true),
('Oftalmologia', 'Prevencion y cuidado de la salud visual', true),
('Medicina Interna', 'Atencion integral del adulto', true);

INSERT IGNORE INTO distritos (nombre, provincia, departamento) VALUES
('Trujillo', 'Trujillo', 'La Libertad'),
('Victor Larco Herrera', 'Trujillo', 'La Libertad'),
('Huanchaco', 'Trujillo', 'La Libertad'),
('La Esperanza', 'Trujillo', 'La Libertad'),
('El Porvenir', 'Trujillo', 'La Libertad'),
('Laredo', 'Trujillo', 'La Libertad'),
('Moche', 'Trujillo', 'La Libertad');

INSERT INTO establecimientos
    (nombre, direccion, referencia, latitud, longitud, distrito_id, propietario_id)
SELECT x.nombre, x.direccion, x.referencia, x.latitud, x.longitud, d.id, NULL
FROM (
  SELECT 'Clinica Vida Norte' nombre, 'Av. America Oeste 720, consultorio 204' direccion, 'Cerca del ovalo Papal' referencia, -8.1088 latitud, -79.0335 longitud, 'Trujillo' distrito
  UNION ALL SELECT 'Centro Medico Los Cedros', 'Av. Fatima 1180, torre A', 'Ingreso por la avenida principal', -8.1304, -79.0439, 'Victor Larco Herrera'
  UNION ALL SELECT 'Policlinico Costanera', 'Av. La Ribera 245, consultorio 6', 'Cerca del malecon', -8.0867, -79.1162, 'Huanchaco'
  UNION ALL SELECT 'Centro Integral La Esperanza', 'Av. Gran Chimu 455, segundo piso', 'Cerca del mercado central', -8.0815, -79.0520, 'La Esperanza'
  UNION ALL SELECT 'Clinica El Porvenir', 'Av. Pumacurco 640, segundo piso', 'Cerca del parque principal', -8.0810, -78.9987, 'El Porvenir'
  UNION ALL SELECT 'Centro Medico Valle Verde', 'Carretera Industrial 510', 'Ingreso por la puerta norte', -8.0895, -78.9662, 'Laredo'
  UNION ALL SELECT 'Policlinico Moche Salud', 'Av. Independencia 415', 'Frente a la plaza del distrito', -8.1702, -79.0095, 'Moche'
  UNION ALL SELECT 'Centro Medico La Merced', 'Av. Mansiche 1550, consultorio 9', 'Cerca del complejo deportivo', -8.1014, -79.0448, 'Trujillo'
  UNION ALL SELECT 'Centro Medico Norte Salud', 'Av. Espana 1840, consultorio 302', 'Cerca de la Plaza de Armas', -8.1116, -79.0287, 'Trujillo'
  UNION ALL SELECT 'Clinica Primavera', 'Av. Larco 620, torre B, piso 3', 'Frente al parque principal', -8.1321, -79.0462, 'Victor Larco Herrera'
  UNION ALL SELECT 'Centro Medico Santa Rosa', 'Av. Tahuantinsuyo 980', 'Al lado de la comisaria', -8.0746, -79.0541, 'La Esperanza'
  UNION ALL SELECT 'Consultorios Santa Isabel', 'Av. Huaman 280, oficina 301', 'Cerca del ovalo', -8.1401, -79.0478, 'Victor Larco Herrera'
) x
JOIN distritos d ON d.nombre = x.distrito AND d.provincia = 'Trujillo'
WHERE NOT EXISTS (SELECT 1 FROM establecimientos e WHERE e.nombre = x.nombre);

CREATE TEMPORARY TABLE tmp_medicos (
  email VARCHAR(190) PRIMARY KEY,
  nombres VARCHAR(100) NOT NULL,
  apellidos VARCHAR(100) NOT NULL,
  dni VARCHAR(12) NOT NULL,
  telefono VARCHAR(30) NOT NULL,
  cmp VARCHAR(20) NOT NULL,
  rne VARCHAR(20) NOT NULL,
  experiencia INT NOT NULL,
  universidad VARCHAR(180) NOT NULL,
  egreso INT NOT NULL,
  especialidad VARCHAR(100) NOT NULL,
  sede VARCHAR(150) NOT NULL,
  costo DECIMAL(10,2) NOT NULL,
  teleconsulta DECIMAL(10,2) NOT NULL,
  biografia VARCHAR(2000) NOT NULL
);

INSERT INTO tmp_medicos VALUES
('rosa.medina.01@medicerca.local','Rosa','Medina Cardenas','91000001','901000001','91001','91001',15,'Universidad Nacional Mayor de San Marcos',2008,'Cardiologia','Clinica Vida Norte',120.00,100.00,'Cardiologa con experiencia en hipertension, prevencion cardiovascular y seguimiento de pacientes adultos.'),
('luis.quispe.01@medicerca.local','Luis','Quispe Huaman','91000002','901000002','91002','91002',10,'Universidad Peruana Cayetano Heredia',2013,'Pediatria','Centro Medico La Merced',95.00,80.00,'Pediatra dedicado al control del nino sano, crecimiento, desarrollo y enfermedades respiratorias.'),
('ana.torres.01@medicerca.local','Ana','Torres Vega','91000003','901000003','91003','91003',8,'Universidad de San Martin de Porres',2016,'Dermatologia','Centro Medico Los Cedros',110.00,95.00,'Dermatologa clinica especializada en acne, dermatitis y evaluacion preventiva de lunares.'),
('valeria.salas.01@medicerca.local','Valeria','Salas Paredes','91000004','901000004','91004','91004',12,'Universidad Nacional de Trujillo',2011,'Ginecologia y Obstetricia','Centro Integral La Esperanza',105.00,90.00,'Ginecologa dedicada a controles preventivos, salud reproductiva y control prenatal.'),
('marco.aguilar.01@medicerca.local','Marco','Aguilar Rios','91000005','901000005','91005','91005',11,'Universidad Peruana Cayetano Heredia',2012,'Endocrinologia','Clinica El Porvenir',130.00,110.00,'Endocrinologo enfocado en diabetes, tiroides, obesidad y prevencion metabolica.'),
('lucia.cabrera.01@medicerca.local','Lucia','Cabrera Leon','91000006','901000006','91006','91006',14,'Universidad Nacional Mayor de San Marcos',2009,'Neurologia','Centro Medico La Merced',150.00,125.00,'Neurologa dedicada a migrena, vertigo, trastornos del sueno y memoria.'),
('diego.mendoza.01@medicerca.local','Diego','Mendoza Ruiz','91000007','901000007','91007','91007',10,'Universidad Nacional de Trujillo',2013,'Traumatologia y Ortopedia','Policlinico Moche Salud',120.00,100.00,'Traumatologo con experiencia en lesiones deportivas, rodilla, hombro y rehabilitacion.'),
('sofia.navarro.01@medicerca.local','Sofia','Navarro Castillo','91000008','901000008','91008','91008',9,'Universidad de San Martin de Porres',2014,'Psiquiatria','Centro Integral La Esperanza',120.00,100.00,'Psiquiatra orientada a ansiedad, depresion, estres, sueno y bienestar emocional.'),
('raul.fuentes.01@medicerca.local','Raul','Fuentes Salazar','91000009','901000009','91009','91009',16,'Universidad Nacional Federico Villarreal',2007,'Gastroenterologia','Centro Medico Los Cedros',140.00,120.00,'Gastroenterologo con experiencia en reflujo, colon irritable e higado graso.'),
('paola.vera.01@medicerca.local','Paola','Vera Cardenas','91000010','901000010','91010','91010',8,'Universidad Peruana Cayetano Heredia',2016,'Oftalmologia','Policlinico Costanera',110.00,95.00,'Oftalmologa dedicada a evaluacion visual, ojo seco y prevencion de enfermedades oculares.'),
('jorge.celis.01@medicerca.local','Jorge','Celis Benites','91000011','901000011','91011','91011',13,'Universidad Nacional de Trujillo',2010,'Medicina Interna','Centro Medico Valle Verde',115.00,95.00,'Internista con experiencia en hipertension, colesterol y seguimiento de enfermedades cronicas.'),
('natalia.rojas.01@medicerca.local','Natalia','Rojas Valdivia','91000012','901000012','91012','91012',10,'Universidad Nacional de Trujillo',2013,'Ginecologia y Obstetricia','Clinica Vida Norte',108.00,92.00,'Ginecologa con enfoque en prevencion y acompanamiento prenatal.'),
('fernando.leon.01@medicerca.local','Fernando','Leon Castillo','91000013','901000013','91013','91013',17,'Universidad Peruana Cayetano Heredia',2006,'Endocrinologia','Centro Medico Los Cedros',145.00,120.00,'Endocrinologo con experiencia en diabetes, metabolismo y enfermedades tiroideas.'),
('elena.paz.01@medicerca.local','Elena','Paz Mendoza','91000014','901000014','91014','91014',9,'Universidad Peruana Cayetano Heredia',2014,'Neurologia','Centro Medico Norte Salud',155.00,130.00,'Neurologa dedicada a cefaleas, mareos, sueno y evaluacion de memoria.'),
('rodrigo.soto.01@medicerca.local','Rodrigo','Soto Villanueva','91000015','901000015','91015','91015',12,'Universidad Nacional de Trujillo',2011,'Traumatologia y Ortopedia','Clinica Vida Norte',125.00,105.00,'Traumatologo enfocado en lesiones musculoesqueleticas y recuperacion funcional.'),
('gabriela.mora.01@medicerca.local','Gabriela','Mora Espinoza','91000016','901000016','91016','91016',11,'Universidad de San Martin de Porres',2012,'Psiquiatria','Clinica El Porvenir',125.00,105.00,'Psiquiatra orientada a ansiedad, depresion, insomnio y estres laboral.'),
('oscar.cueva.01@medicerca.local','Oscar','Cueva Salinas','91000017','901000017','91017','91017',18,'Universidad Peruana Cayetano Heredia',2005,'Gastroenterologia','Clinica Primavera',150.00,125.00,'Gastroenterologo con experiencia en reflujo, gastritis, colon irritable e higado graso.'),
('ines.martinez.01@medicerca.local','Ines','Martinez Ponce','91000018','901000018','91018','91018',8,'Universidad Nacional de Trujillo',2017,'Oftalmologia','Policlinico Moche Salud',115.00,98.00,'Oftalmologa dedicada a controles visuales, ojo seco y presion ocular.'),
('raul.carranza.01@medicerca.local','Raul','Carranza Torres','91000019','901000019','91019','91019',14,'Universidad Nacional Federico Villarreal',2009,'Medicina Interna','Centro Medico Valle Verde',120.00,100.00,'Internista orientado al control integral del adulto y sus enfermedades cronicas.'),
('camila.ortiz.01@medicerca.local','Camila','Ortiz Valverde','91000020','901000020','91020','91020',7,'Universidad Catolica de Santa Maria',2018,'Pediatria','Policlinico Costanera',100.00,85.00,'Pediatra dedicada a nutricion, vacunacion, crecimiento y desarrollo infantil.'),
('miguel.rios.01@medicerca.local','Miguel','Rios Herrera','91000021','901000021','91021','91021',13,'Universidad Nacional de Trujillo',2010,'Dermatologia','Clinica Vida Norte',125.00,105.00,'Dermatologo con experiencia en acne, dermatitis y enfermedades del cabello.'),
('carolina.silva.01@medicerca.local','Carolina','Silva Nunez','91000022','901000022','91022','91022',15,'Universidad Nacional Mayor de San Marcos',2008,'Ginecologia y Obstetricia','Centro Medico Los Cedros',110.00,95.00,'Ginecologa dedicada a prevencion, salud reproductiva y menopausia.'),
('tomas.espinoza.01@medicerca.local','Tomas','Espinoza Leon','91000023','901000023','91023','91023',20,'Universidad Peruana Cayetano Heredia',2003,'Endocrinologia','Centro Medico La Merced',155.00,130.00,'Endocrinologo con amplia experiencia en diabetes, osteoporosis y tiroides.'),
('beatriz.paredes.01@medicerca.local','Beatriz','Paredes Castaneda','91000024','901000024','91024','91024',7,'Universidad de Piura',2017,'Neurologia','Centro Integral La Esperanza',150.00,125.00,'Neurologa dedicada a migrena, neuropatias, sueno y memoria.'),
('julio.cardenas.01@medicerca.local','Julio','Cardenas Huaman','91000025','901000025','91025','91025',16,'Universidad Nacional de Trujillo',2007,'Traumatologia y Ortopedia','Policlinico Moche Salud',130.00,110.00,'Traumatologo dedicado a lesiones deportivas, fracturas y rehabilitacion.'),
('mariana.lozano.01@medicerca.local','Mariana','Lozano Reyes','91000026','901000026','91026','91026',10,'Universidad de San Martin de Porres',2013,'Psiquiatria','Centro Medico Norte Salud',130.00,110.00,'Psiquiatra con enfoque en ansiedad, duelo, sueno y salud emocional.'),
('esteban.galvez.01@medicerca.local','Esteban','Galvez Ruiz','91000027','901000027','91027','91027',19,'Universidad Nacional Mayor de San Marcos',2003,'Gastroenterologia','Clinica El Porvenir',155.00,130.00,'Gastroenterologo orientado a enfermedades digestivas y seguimiento nutricional.'),
('veronica.castro.01@medicerca.local','Veronica','Castro Flores','91000028','901000028','91028','91028',12,'Universidad Peruana Cayetano Heredia',2011,'Oftalmologia','Centro Medico Santa Rosa',120.00,100.00,'Oftalmologa dedicada a controles visuales, ojo seco y cuidado ocular preventivo.'),
('sergio.noriega.01@medicerca.local','Sergio','Noriega Ramos','91000029','901000029','91029','91029',21,'Universidad Nacional de Trujillo',2001,'Medicina Interna','Centro Medico Valle Verde',125.00,105.00,'Internista con experiencia en adultos mayores y enfermedades cronicas.'),
('alejandra.mejia.01@medicerca.local','Alejandra','Mejia Ortiz','91000030','901000030','91030','91030',5,'Universidad Nacional de Trujillo',2019,'Pediatria','Centro Medico La Merced',100.00,85.00,'Pediatra dedicada a lactancia, nutricion, vacunacion y desarrollo infantil.');

INSERT IGNORE INTO usuarios
 (nombres, apellidos, dni, email, telefono, password_hash, estado, rol_id, ultimo_acceso, creado_en, actualizado_en, puede_gestionar_administradores)
SELECT nombres, apellidos, dni, email, telefono, @hash_medico, 'ACTIVO', @rol_medico, NULL, @ahora, @ahora, false
FROM tmp_medicos;

INSERT IGNORE INTO medicos
 (usuario_id, cmp, rne, anios_experiencia, titulo_profesional, universidad, anio_egreso, biografia, mensaje_predeterminado_chat, costo_consulta, costo_teleconsulta, costo_por_hora, rating_promedio, total_valoraciones, especialidad_id, establecimiento_id, creado_en, actualizado_en)
SELECT u.id, t.cmp, t.rne, t.experiencia, 'Medico cirujano', t.universidad, t.egreso, t.biografia,
       CONCAT('Hola, soy el Dr./Dra. ', t.apellidos, '. Cuentame el motivo de tu consulta.'),
       t.costo, t.teleconsulta, t.costo * 2, 0, 0, e.id, s.id, @ahora, @ahora
FROM tmp_medicos t
JOIN usuarios u ON u.email = t.email
JOIN especialidades e ON e.nombre = t.especialidad
JOIN establecimientos s ON s.nombre = t.sede
WHERE NOT EXISTS (SELECT 1 FROM medicos m WHERE m.cmp = t.cmp);

INSERT INTO formaciones_medico (medico_id, tipo, titulo, institucion, anio)
SELECT m.id, 'TITULO', 'Medico cirujano', t.universidad, t.egreso
FROM medicos m JOIN tmp_medicos t ON t.cmp = m.cmp
WHERE NOT EXISTS (SELECT 1 FROM formaciones_medico f WHERE f.medico_id = m.id AND f.tipo = 'TITULO');

INSERT INTO formaciones_medico (medico_id, tipo, titulo, institucion, anio)
SELECT m.id, 'ESPECIALIDAD', CONCAT('Especialista en ', t.especialidad), CONCAT('Programa de especializacion en ', t.especialidad), t.egreso + 4
FROM medicos m JOIN tmp_medicos t ON t.cmp = m.cmp
WHERE NOT EXISTS (SELECT 1 FROM formaciones_medico f WHERE f.medico_id = m.id AND f.tipo = 'ESPECIALIDAD');

INSERT INTO servicios_medico (medico_id, nombre, descripcion, precio, activo)
SELECT m.id, CONCAT('Consulta de ', t.especialidad), CONCAT('Evaluacion y seguimiento profesional en ', t.especialidad, '.'), t.costo, true
FROM medicos m JOIN tmp_medicos t ON t.cmp = m.cmp
WHERE NOT EXISTS (SELECT 1 FROM servicios_medico s WHERE s.medico_id = m.id AND s.nombre = CONCAT('Consulta de ', t.especialidad));

INSERT INTO servicios_medico (medico_id, nombre, descripcion, precio, activo)
SELECT m.id, 'Seguimiento y control', 'Revision de evolucion, recomendaciones y plan de seguimiento.', t.teleconsulta, true
FROM medicos m JOIN tmp_medicos t ON t.cmp = m.cmp
WHERE NOT EXISTS (SELECT 1 FROM servicios_medico s WHERE s.medico_id = m.id AND s.nombre = 'Seguimiento y control');

INSERT INTO horarios_atencion (medico_id, dia_semana, hora_inicio, hora_fin, duracion_cita_min, modalidad, activo)
SELECT m.id, MOD(m.id, 5) + 1, '08:00:00', '13:00:00', 40, 'AMBAS', true
FROM medicos m JOIN tmp_medicos t ON t.cmp = m.cmp
WHERE NOT EXISTS (SELECT 1 FROM horarios_atencion h WHERE h.medico_id = m.id AND h.dia_semana = MOD(m.id, 5) + 1 AND h.hora_inicio = '08:00:00');

INSERT INTO horarios_atencion (medico_id, dia_semana, hora_inicio, hora_fin, duracion_cita_min, modalidad, activo)
SELECT m.id, MOD(m.id + 2, 5) + 1, '15:00:00', '19:00:00', 40, 'TELECONSULTA', true
FROM medicos m JOIN tmp_medicos t ON t.cmp = m.cmp
WHERE NOT EXISTS (SELECT 1 FROM horarios_atencion h WHERE h.medico_id = m.id AND h.dia_semana = MOD(m.id + 2, 5) + 1 AND h.hora_inicio = '15:00:00');

DROP TEMPORARY TABLE tmp_medicos;
COMMIT;

SELECT COUNT(*) AS medicos_insertados
FROM medicos m JOIN usuarios u ON u.id = m.usuario_id
WHERE u.email LIKE '%@medicerca.local';
