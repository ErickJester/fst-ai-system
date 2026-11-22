import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Acceso } from '../components/ui'

// Aviso de privacidad: visible con o sin sesión. Sin datos de la cuenta; solo el texto.
export default function AvisoPrivacidadPage() {
  const navigate = useNavigate()

  // Con historial, vuelve a la pantalla anterior; si no hay, va al inicio de sesión.
  function volver(e) {
    if (window.history.length > 1) {
      e.preventDefault()
      window.history.back()
    } else {
      e.preventDefault()
      navigate('/login')
    }
  }

  return (
    <Acceso encabezado sub="Análisis de la prueba de nado forzado (Forced Swim Test, FST)">
      <div className="narrow" style={{ padding: '24px 34px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        <h2 style={{ margin: 0, fontSize: 22 }}>Aviso de privacidad</h2>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Responsable</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>Instituto Politécnico Nacional (IPN), por medio del Laboratorio de Bioquímica Estructural, Sección de Posgrado, ENMyH-IPN. Domicilio: Av. Luis Enrique Erro s/n, Unidad Profesional «Adolfo López Mateos», Zacatenco, Alcaldía Gustavo A. Madero, C.P. 07738, Ciudad de México.</p>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Datos que se tratan</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>Identificador institucional, nombre, apellidos y correo electrónico de cada cuenta. El sistema no trata datos sensibles. Los videos son de especímenes y no están destinados a contener datos personales.</p>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Para qué se usan</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>Para que cada persona entre al sistema, para saber quién creó cada experimento y sus videos, y para enviar los avisos de cuenta nueva y de recuperación de contraseña.</p>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Cómo se protegen</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>La contraseña se guarda con hash y sal, nunca en texto plano. Para ver la información hay que iniciar sesión.</p>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Transferencias</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>Durante este periodo de trabajo terminal, los avisos por correo (cuenta nueva y recuperación de contraseña) se envían con una cuenta de Gmail y una contraseña de aplicación; por ese servicio pasan el nombre, el correo y la contraseña temporal. Fuera de eso, los datos no se comparten con terceros.</p>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Derechos</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>Puedes acceder a tus datos, corregirlos, cancelarlos u oponerte a su uso, conforme a la Ley General de Protección de Datos Personales en Posesión de Sujetos Obligados (LGPDPPSO), escribiendo a la Unidad de Transparencia del IPN (Edificio «Adolfo Ruiz Cortines», planta baja, Av. Wilfrido Massieu s/n esq. Luis Enrique Erro, Zacatenco; correo acceinfo@ipn.mx; teléfono 55 5729 6000, ext. 50128) o a través de la Plataforma Nacional de Transparencia.</p>
          <p style={{ margin: 0, lineHeight: 1.6 }}>El sistema no borra cuentas: cancelar una cuenta significa desactivarla. Una cuenta desactivada ya no puede entrar, y los experimentos, resultados y reportes que registró se conservan como parte del historial del laboratorio. Si pides la cancelación de tus datos, la misma unidad atiende tu solicitud conforme a la ley.</p>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Cambios</h3>
          <p style={{ margin: 0, lineHeight: 1.6 }}>Este aviso puede cambiar; la versión vigente es la que se ve en el sistema.</p>
        </section>

        <Link to="/login" onClick={volver} style={{ alignSelf: 'flex-start' }}>Volver</Link>
      </div>
    </Acceso>
  )
}
