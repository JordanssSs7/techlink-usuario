-- Apodo del perfil: el nombre no se puede cambiar, pero cada persona puede poner un apodo (como el nombre de usuario de GitHub).
ALTER TABLE usuarios ADD COLUMN apodo VARCHAR(40) NULL;
