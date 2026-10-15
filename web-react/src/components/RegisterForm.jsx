import { useState } from 'react';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const SOLO_LETRAS = /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü]+(?:[ '-][A-Za-zÁÉÍÓÚáéíóúÑñÜü]+)*$/;

// Input de contraseña con botón para mostrar u ocultar el texto
function PasswordInput({ id, name, value, onChange }) {
  const [visible, setVisible] = useState(false);
  return (
    <div style={styles.passwordWrapper}>
      <input
        type={visible ? 'text' : 'password'}
        id={id}
        name={name}
        value={value}
        onChange={onChange}
        style={{ ...styles.input, ...styles.passwordInput }}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        style={styles.eyeButton}
        aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        aria-pressed={visible}
      >
        {visible ? (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
            <line x1="1" y1="1" x2="23" y2="23" />
          </svg>
        ) : (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    </div>
  );
}

// Definir modelo de datos inicial, y formulario institucional
export default function RegisterForm() {
  const [formData, setFormData] = useState({
    nombres: '',
    apellidos: '',
    correoInstitucional: '',
    ciclo: '1',
    password: '',
    confirmPassword: ''
  });


//Errores de control y validacion
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [registerSuccess, setRegisterSuccess] = useState(false);



  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
    if (errors[e.target.name]) {
      setErrors({ ...errors, [e.target.name]: null });
    }
  };

  const validate = () => {
    const newErrors = {};

    const nombres = formData.nombres.trim();
    const apellidos = formData.apellidos.trim();
    if (!nombres) newErrors.nombres = 'El nombre es obligatorio.';
    else if (nombres.length < 2 || nombres.length > 100) newErrors.nombres = 'Debe tener entre 2 y 100 caracteres.';
    else if (!SOLO_LETRAS.test(nombres)) newErrors.nombres = 'Solo se permiten letras, espacios, guion y apóstrofo.';

    if (!apellidos) newErrors.apellidos = 'El apellido es obligatorio.';
    else if (apellidos.length < 2 || apellidos.length > 100) newErrors.apellidos = 'Debe tener entre 2 y 100 caracteres.';
    else if (!SOLO_LETRAS.test(apellidos)) newErrors.apellidos = 'Solo se permiten letras, espacios, guion y apóstrofo.';

    // Criterio CA-01: Dominio obligatorio @tecsup.edu.pe
    const tecsupEmailRegex = /^[a-zA-Z0-9._%+-]+@tecsup\.edu\.pe$/i;
    const correo = formData.correoInstitucional.trim();
    if (!correo) {
      newErrors.correoInstitucional = 'El correo institucional es obligatorio.';
    } else if (!tecsupEmailRegex.test(correo)) {
      newErrors.correoInstitucional = 'Solo se permiten correos institucionales de Tecsup (@tecsup.edu.pe).';
    }

    const pwd = formData.password;
    if (!pwd) {
      newErrors.password = 'La contraseña es requerida.';
    } else if (pwd.length < 8 || pwd.length > 72) {
      newErrors.password = 'Debe tener entre 8 y 72 caracteres.';
    } else if (!/[A-Za-z]/.test(pwd) || !/\d/.test(pwd)) {
      newErrors.password = 'Debe incluir al menos una letra y un número.';
    }

    if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = 'Las contraseñas no coinciden.';
    }

    return newErrors;
  };


  const handleSubmit = async (e) => {
    e.preventDefault();
    const validationErrors = validate();

    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setIsSubmitting(true);

    // confirmPassword solo se valida en el formulario, no se envía
    const payload = {
      nombres: formData.nombres.trim(),
      apellidos: formData.apellidos.trim(),
      correoInstitucional: formData.correoInstitucional.trim(),
      password: formData.password,
      ciclo: Number(formData.ciclo)
    };

    try {
      const response = await fetch(`${API_URL}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (response.status === 201) {
        setRegisterSuccess(true);
        return;
      }

      const data = await response.json().catch(() => ({}));

      if (response.status === 400 && data.campos) {
        setErrors(data.campos);
      } else if (response.status === 400 && data.error) {
        setErrors({ correoInstitucional: data.error });
      } else if (response.status === 409) {
        setErrors({ correoInstitucional: data.error || data.mensaje || 'El correo o código ya está registrado.' });
      } else {
        setErrors({ global: 'No se pudo completar el registro. Inténtalo nuevamente.' });
      }
    } catch {
      setErrors({ global: 'Error al conectar con el servidor de autenticación.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (registerSuccess) {
    return (
      <div style={styles.card}>
        <h2 style={{ color: '#0052cc', marginBottom: '10px' }}>¡Registro Exitoso!</h2>
        <p>Tu cuenta fue creada con el correo institucional:</p>
        <p style={{ fontWeight: 'bold', color: '#333' }}>{formData.correoInstitucional}</p>
      </div>
    );
  }

    // Renderizado del formulario de registro
  return (
    <div style={styles.container}>
      <form onSubmit={handleSubmit} style={styles.card} noValidate>
        <h2 style={{ textAlign: 'center', marginBottom: '1.5rem', color: '#0052cc' }}>
          Registro de Estudiante
        </h2>
        <p style={{ textAlign: 'center', color: '#666', fontSize: '13px', marginTop: '-1rem', marginBottom: '1.5rem' }}>
          Plataforma de Mentorías TechLink - Tecsup
        </p>

        {errors.global && <div style={styles.errorAlert}>{errors.global}</div>}

        <div style={styles.inputGroup}>
          <label htmlFor="nombres" style={styles.label}>Nombres</label>
          <input
            type="text"
            id="nombres"
            name="nombres"
            value={formData.nombres}
            onChange={handleChange}
            style={styles.input}
            placeholder="Ej. Jordan"
          />
          {errors.nombres && <span style={styles.errorText}>{errors.nombres}</span>}
        </div>

      
        <div style={styles.inputGroup}>
          <label htmlFor="apellidos" style={styles.label}>Apellidos</label>
          <input
            type="text"
            id="apellidos"
            name="apellidos"
            value={formData.apellidos}
            onChange={handleChange}
            style={styles.input}
            placeholder="Ej. Reyes"
          />
          {errors.apellidos && <span style={styles.errorText}>{errors.apellidos}</span>}
        </div>

 
        <div style={styles.inputGroup}>
          <label htmlFor="correoInstitucional" style={styles.label}>Correo Institucional</label>
          <input
            type="email"
            id="correoInstitucional"
            name="correoInstitucional"
            value={formData.correoInstitucional}
            onChange={handleChange}
            style={styles.input}
            placeholder="ejemplo@tecsup.edu.pe"
          />
          {errors.correoInstitucional && <span style={styles.errorText}>{errors.correoInstitucional}</span>}
        </div>


        <div style={styles.inputGroup}>
          <label htmlFor="ciclo" style={styles.label}>Ciclo Académico</label>
          <select id="ciclo" name="ciclo" value={formData.ciclo} onChange={handleChange} style={styles.input}>
            <option value="1">I Ciclo</option>
            <option value="2">II Ciclo</option>
            <option value="3">III Ciclo</option>
            <option value="4">IV Ciclo</option>
            <option value="5">V Ciclo</option>
            <option value="6">VI Ciclo</option>
          </select>
          {errors.ciclo && <span style={styles.errorText}>{errors.ciclo}</span>}
        </div>

        <div style={styles.inputGroup}>
          <label htmlFor="password" style={styles.label}>Contraseña</label>
          <PasswordInput
            id="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
          />
          {errors.password && <span style={styles.errorText}>{errors.password}</span>}
        </div>

        <div style={styles.inputGroup}>
          <label htmlFor="confirmPassword" style={styles.label}>Confirmar Contraseña</label>
          <PasswordInput
            id="confirmPassword"
            name="confirmPassword"
            value={formData.confirmPassword}
            onChange={handleChange}
          />
          {errors.confirmPassword && <span style={styles.errorText}>{errors.confirmPassword}</span>}
        </div>

        <button type="submit" disabled={isSubmitting} style={styles.button}>
          {isSubmitting ? 'Verificando datos...' : 'Crear Cuenta'}
        </button>
      </form>
    </div>
  );
}




const styles = {
  container: { display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#eef2f6', padding: '1rem' },
  card: { background: '#ffffff', padding: '2rem', borderRadius: '10px', boxShadow: '0 4px 14px rgba(0,0,0,0.1)', width: '100%', maxWidth: '420px', fontFamily: 'system-ui, sans-serif' },
  inputGroup: { display: 'flex', flexDirection: 'column', marginBottom: '0.9rem' },
  label: { fontSize: '13px', fontWeight: '600', color: '#333' },
  input: { padding: '0.6rem 0.8rem', marginTop: '0.3rem', border: '1px solid #ccc', borderRadius: '6px', fontSize: '14px' },
  button: { width: '100%', padding: '0.8rem', background: '#0052cc', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '15px', marginTop: '0.5rem' },
  passwordWrapper: { position: 'relative', display: 'flex', flexDirection: 'column' },
  passwordInput: { paddingRight: '2.6rem' },
  eyeButton: { position: 'absolute', right: '0.5rem', top: '0.3rem', bottom: 0, height: '2.3rem', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'none', border: 'none', cursor: 'pointer', color: '#666', padding: '0 0.3rem' },
  errorText: { color: '#d32f2f', fontSize: '12px', marginTop: '4px' },
  errorAlert: { background: '#ffebee', color: '#c62828', padding: '0.6rem', borderRadius: '6px', marginBottom: '1rem', fontSize: '13px', textAlign: 'center' }
};