# HOTEL-OS: instrucciones para todos los agentes

## Cuenta GitHub y autoría obligatorias

- No crees ni solicites crear cuentas GitHub nuevas, alternas, compartidas o de bot para este proyecto. Ningún agente abre otra identidad para operar el repositorio.
- Usa únicamente la cuenta GitHub existente `jdsalasca` y la autenticación ya configurada para el usuario. No pidas contraseñas, tokens ni códigos; no leas archivos de credenciales ni cambies permisos o ámbitos de autenticación.
- La identidad Git local canónica verificada para los commits del usuario es `JUAN DAVID SALAS CAMARGO <31742974+jdsalasca@users.noreply.github.com>`. Comprueba `git config user.name` y `git config user.email` antes de crear commits. Si no coinciden, detén la publicación y corrige la identidad local para los commits nuevos; no suplantes otra cuenta.
- Después de cada push, verifica con GitHub que el commit aparece bajo el usuario `jdsalasca` antes de informar que quedó atribuido al usuario.
- No reescribas commits ya publicados ni uses force-push para corregir autoría histórica sin autorización expresa del usuario. Los commits anteriores mantienen su identidad histórica.
- Esta regla aplica a agentes, subagentes y herramientas externas que modifiquen o publiquen el repositorio. Sigue además el contrato de ejecución del plan en `docs/auditoria-producto/plan-mejora-integral-2026-10.md`.