package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistroClienteDTO {
  @NotBlank @Size(max = 100)
  private String nombres;
  @NotBlank @Size(max = 100)
  private String apellidos;
  @NotBlank @Pattern(regexp = "[0-9]{8}", message = "El DNI debe tener 8 dígitos.")
  private String dni;
  @NotBlank @Email @Size(max = 190)
  private String email;
  @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
  private String password;
  @NotBlank @Pattern(regexp = "[0-9+()\\s-]{7,30}", message = "El teléfono no tiene un formato válido.")
  private String telefono;

  public String getNombres() { return nombres; }
  public void setNombres(String nombres) { this.nombres = nombres; }
  public String getApellidos() { return apellidos; }
  public void setApellidos(String apellidos) { this.apellidos = apellidos; }
  public String getDni() { return dni; }
  public void setDni(String dni) { this.dni = dni; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public String getTelefono() { return telefono; }
  public void setTelefono(String telefono) { this.telefono = telefono; }
}
