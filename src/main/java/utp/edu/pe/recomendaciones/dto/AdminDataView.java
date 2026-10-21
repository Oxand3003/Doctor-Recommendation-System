package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** JSON que consume el panel de administración (admin.js). Los nombres de campo deben coincidir con el JS. */
public record AdminDataView(List<ApplicationView> applications,
                            List<DoctorView> doctors,
                            long patients,
                            List<ActivityView> activity,
                            List<PatientView> patientList) {

    public record ApplicationView(Long id, String nombre, String email, String dni, String tel,
                                  String cmp, String rne, String titulo, String universidad,
                                  Integer egreso, int exp, String especialidad, String establecimiento,
                                  String sustento, String fecha, String estado, String motivo) { }

    public record DoctorView(Long id, String nombre, String email, String especialidad,
                             String establecimiento, double rating, String estado) { }

    /** Cuenta de paciente para la sección «Cuentas de pacientes». */
    public record PatientView(Long id, String nombre, String email, String dni, String tel,
                              String registro, String ultimoAcceso, String estado) { }

    public record ActivityView(String titulo, String detalle, String color, String fecha) { }
}
