import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import AuthProvider from './auth/AuthProvider.jsx'
import EventosProvider from './eventos/EventosProvider.jsx'
import { guardarSalaDeLaDireccion } from './enlaceSala.js'
import './tema-mono.css' // al final, para que el tema blanco y negro prevalezca sobre las hojas de cada pantalla

guardarSalaDeLaDireccion()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <EventosProvider>
        <App />
      </EventosProvider>
    </AuthProvider>
  </StrictMode>,
)
