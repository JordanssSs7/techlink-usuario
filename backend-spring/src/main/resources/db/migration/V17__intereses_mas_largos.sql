-- HU-13: cada interés del perfil puede tener hasta 50 caracteres (antes 40), para escribir cosas como "Soy bueno en JavaScript" o
-- "Me interesa la inteligencia artificial". Siguen siendo hasta 8 intereses por persona.
ALTER TABLE usuario_intereses MODIFY interes VARCHAR(50) NOT NULL;
