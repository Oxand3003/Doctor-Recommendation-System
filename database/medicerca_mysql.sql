CREATE DATABASE IF NOT EXISTS medicerca
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE medicerca;

CREATE TABLE IF NOT EXISTS usuarios (
  id BIGINT NOT NULL AUTO_INCREMENT,
  nombres VARCHAR(100) NOT NULL,
  apellidos VARCHAR(100) NOT NULL,
  dni VARCHAR(12) NOT NULL,
  email VARCHAR(190) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  telefono VARCHAR(30) NOT NULL,
  rol VARCHAR(20) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  creado_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_usuarios_dni (dni),
  UNIQUE KEY uq_usuarios_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS especialidades (
  id BIGINT NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_especialidades_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS establecimientos (
  id BIGINT NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(150) NOT NULL,
  direccion VARCHAR(200),
  distrito VARCHAR(100),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS doctores (
  id BIGINT NOT NULL AUTO_INCREMENT,
  nombres VARCHAR(100) NOT NULL,
  apellidos VARCHAR(100) NOT NULL,
  cmp VARCHAR(20) NOT NULL,
  rating DOUBLE NOT NULL DEFAULT 0,
  anios_experiencia INT NOT NULL DEFAULT 0,
  disponible BOOLEAN NOT NULL DEFAULT FALSE,
  rne VARCHAR(20),
  titulo_profesional VARCHAR(150),
  universidad VARCHAR(180),
  anio_egreso INT,
  sustento_url VARCHAR(500),
  estado_verificacion VARCHAR(20) NOT NULL DEFAULT 'APROBADO',
  motivo_rechazo VARCHAR(500),
  especialidad_id BIGINT NOT NULL,
  establecimiento_id BIGINT,
  usuario_id BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_doctores_cmp (cmp),
  UNIQUE KEY uq_doctores_usuario (usuario_id),
  CONSTRAINT fk_doctores_especialidad FOREIGN KEY (especialidad_id) REFERENCES especialidades(id),
  CONSTRAINT fk_doctores_establecimiento FOREIGN KEY (establecimiento_id) REFERENCES establecimientos(id),
  CONSTRAINT fk_doctores_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS citas (
  id BIGINT NOT NULL AUTO_INCREMENT,
  paciente_nombre VARCHAR(150) NOT NULL,
  paciente_dni VARCHAR(12) NOT NULL,
  paciente_telefono VARCHAR(30) NOT NULL,
  motivo VARCHAR(500),
  fecha_hora DATETIME(6) NOT NULL,
  modalidad VARCHAR(20) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  doctor_id BIGINT NOT NULL,
  cliente_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_citas_doctor_fecha (doctor_id, fecha_hora),
  CONSTRAINT fk_citas_doctor FOREIGN KEY (doctor_id) REFERENCES doctores(id),
  CONSTRAINT fk_citas_cliente FOREIGN KEY (cliente_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
