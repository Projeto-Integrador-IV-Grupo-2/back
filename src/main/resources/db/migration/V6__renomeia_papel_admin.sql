-- Renomeia o papel ADMIN para ADMINISTRADOR.
--
-- O nome da role exposta pelo Spring Security é derivado deste valor
-- (ROLE_ + papel), e o padrão definido para o projeto é ROLE_ADMINISTRADOR.
-- Renomear o rótulo do enum atualiza as linhas existentes automaticamente.
ALTER TYPE papel_usuario RENAME VALUE 'ADMIN' TO 'ADMINISTRADOR';
