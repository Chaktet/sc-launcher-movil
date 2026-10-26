# Historial · SC Launcher Móvil

## Estado actual
- Fase 0 en curso: fork `Chaktet/sc-launcher-movil` creado (de FCL 1.3.4.2) y clonado; clave de firma propia generada
  (secretos de GitHub + copia en `E:\SECRETOS\sc-launcher-movil\`); secreto `OAUTH_API_KEY` = client ID de Helios.
- Pendiente: revisión del plan por Codex, CI firmado con nuestra clave, registro de app propia en Microsoft Entra
  (Felix tiene que iniciar sesión).

## 2026-10-26 · Arranque del proyecto
- Felix aprobó el plan `sc-launcher/docs/PLAN-APP-ANDROID.md` (app Android propia, fork de FCL, solo desde la web).
- Hecho: fork público, clon en `E:/PROYECTOS-MC/sc-launcher-movil` (remote `upstream`), clave `sc-release.jks`
  (RSA 4096, alias `sc-launcher`, válida hasta 2126) y secretos `SC_KEYSTORE_BASE64`, `SC_KEYSTORE_PASSWORD`,
  `OAUTH_API_KEY` en el repo.
