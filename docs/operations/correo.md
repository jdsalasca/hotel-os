# Envío de correo con OAuth 2.0 de Google (Gmail API)

El sistema envía correo con la **Gmail API** usando un `refresh token` de Google. El refresh token no
caduca mientras no revoques el acceso, así que es lo que se guarda en el servidor. El `access token`
de corta duración se pide en cada envío y **no se guarda en ningún sitio**.

**Los valores van en el `.env` real, nunca en este repositorio.**

## 1. Crear el proyecto y habilitar la API

1. Abre <https://console.cloud.google.com/>.
2. Crea un proyecto (por ejemplo `hotel-reservas`).
3. Ve a **APIs y servicios → Biblioteca** y habilita **Gmail API**.

## 2. Configurar la pantalla de consentimiento OAuth

1. **APIs y servicios → Pantalla de consentimiento OAuth**.
2. Tipo: **Externo** (o Interno si la cuenta del hotel pertenece a una organización de Google).
3. Añade el correo del hotel como usuario de prueba mientras haces la configuración.

## 3. Crear las credenciales

1. **APIs y servicios → Credenciales → Crear credenciales → ID de cliente OAuth**.
2. Tipo **Aplicación web** (para esta integración, que actúa como servicio).
3. En **URI de redireccionamiento autorizado** pon: `http://localhost:8080/oauth2/callback`
   (Google la exige; el sistema no usa la redirección porque el canje se hace servidor a servidor).
4. Anota el **Client ID** y el **Client Secret**.

## 4. Obtener el refresh token

Desde PowerShell, sustituyendo los valores:

```powershell
$clientId = "TU_CLIENT_ID.apps.googleusercontent.com"
$clientSecret = "TU_CLIENT_SECRET"

# Redirect URI: debe coincidir con el del paso 3.
$redirectUri = "http://localhost:8080/oauth2/callback"
$scope = "https://www.googleapis.com/auth/gmail.send"

# Abre el navegador, acepta los permisos y pega aquí la URL a la que te redirigió:
$url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=$clientId" +
       "&redirect_uri=$([uri]::EscapeDataString($redirectUri))" +
       "&response_type=code&scope=$([uri]::EscapeDataString($scope))&access_type=offline&prompt=consent"
Start-Process $url
Write-Host "Pega aquí el valor de 'code' de la URL del navegador:"
$code = Read-Host "code"

# Canje: authorization_code -> refresh_token
$resp = Invoke-RestMethod -Method Post -Uri "https://oauth2.googleapis.com/token" -Body @{
  code = $code
  client_id = $clientId
  client_secret = $clientSecret
  redirect_uri = $redirectUri
  grant_type = "authorization_code"
}
$resp.refresh_token
```

`access_type=offline` + `prompt=consent` son los que hacen que Google devuelva el `refresh_token`.
Si no aparece, revisa esos dos parámetros: es el error más común.

## 5. Configurar el entorno

En el `.env` real (ignorado por git):

```dotenv
CORREO_HABILITADO=true
CORREO_REMITENTE=reservas@tu-dominio.com
CORREO_REMITENTE_NOMBRE=Nombre del Hotel
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GOOGLE_REFRESH_TOKEN=...
```

## 6. Probarlo desde el panel

```powershell
# Estado de la configuración (no envía nada)
curl -b cookies.txt http://localhost:8080/api/admin/correo/estado

# Envío de prueba
curl -b cookies.txt -X POST http://localhost:8080/api/admin/correo/prueba \
  -H "Content-Type: application/json" \
  -d '{"destinatario":"tu-correo@ejemplo.com","asunto":"Prueba","cuerpo":"Mensaje de prueba"}'
```

Sin configurar, la respuesta es `502` con el motivo exacto (`falta hotel.correo.refresh-token`, etc.).
Con la configuración completa, responde `200` y el `idMensaje` que devuelve Gmail.

## Qué NO hace este sistema

- No envía correos de reserva automáticamente: el envío queda como acción explícita del panel. El
  flujo público avisa al huésped por pantalla y le entrega el código; el correo es un complemento.
- No guarda el `access token`: se pide en cada envío.
- No registra tokens ni secretos en los logs ni en la base de datos.