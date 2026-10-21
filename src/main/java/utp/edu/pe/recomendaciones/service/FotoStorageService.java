package utp.edu.pe.recomendaciones.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import utp.edu.pe.recomendaciones.exception.NegocioException;

/**
 * Guarda y lee archivos de fotos en disco (carpeta medicerca.uploads.dir, por defecto ./uploads).
 * La base de datos guarda solo los metadatos (FotoMedico).
 * Seguridad: el tipo se decide por los primeros bytes del archivo (no por el nombre ni el Content-Type
 * que envía el navegador), el nombre en disco lo genera el servidor y nunca se usa el original.
 */
@Service
public class FotoStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Resultado de guardar un archivo. */
    public record Guardado(String archivo, String tipoContenido, long tamano) { }

    private final Path base;

    public FotoStorageService(@Value("${medicerca.uploads.dir:./uploads}") String dir) {
        this.base = Path.of(dir).toAbsolutePath().normalize().resolve("medicos");
    }

    public Guardado guardar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("Selecciona una imagen.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new NegocioException("La imagen supera el tamaño máximo de 5 MB.");
        }
        byte[] cabecera = new byte[12];
        int leidos;
        try (InputStream in = file.getInputStream()) {
            leidos = in.readNBytes(cabecera, 0, cabecera.length);
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer la imagen.");
        }
        String[] tipo = detectar(Arrays.copyOf(cabecera, leidos));
        if (tipo == null) {
            throw new NegocioException("Formato no permitido. Usa una imagen JPG, PNG o WEBP.");
        }
        String nombre = UUID.randomUUID() + tipo[1];
        try {
            Files.createDirectories(base);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, base.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen en el servidor.", e);
        }
        return new Guardado(nombre, tipo[0], file.getSize());
    }

    /** null si el archivo no existe o el nombre no es seguro. */
    public Resource cargar(String archivo) {
        Path p = resolver(archivo);
        if (p == null || !Files.isRegularFile(p)) return null;
        return new FileSystemResource(p);
    }

    public void eliminar(String archivo) {
        Path p = resolver(archivo);
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // El registro ya se borró de la base de datos; un archivo huérfano no afecta al usuario.
        }
    }

    private Path resolver(String archivo) {
        if (archivo == null || !archivo.matches("[0-9a-f-]{36}\\.(jpg|png|webp)")) return null;
        Path p = base.resolve(archivo).normalize();
        return p.startsWith(base) ? p : null;
    }

    /** {Content-Type, extensión} según los bytes iniciales, o null si no es JPG/PNG/WEBP. */
    private static String[] detectar(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new String[]{"image/jpeg", ".jpg"};
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return new String[]{"image/png", ".png"};
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new String[]{"image/webp", ".webp"};
        }
        return null;
    }
}
