<#
Verificación del control de acceso con los cinco roles (entregable 2.3.1, SCRUM-51).

Inicia sesión con un usuario de cada rol y llama a cada endpoint de la política de permisos
(docs/permisos-por-rol.md) contra el backend REAL. Compara lo que responde con lo esperado:
  - rol no permitido  -> debe responder 403
  - rol permitido     -> no debe responder 401 ni 403 (404 está bien si el endpoint aún no existe)
  - sin token         -> debe responder 401
Al final guarda los resultados en un CSV como evidencia.

Requisitos: backend encendido con el perfil dev y DEV_ADMIN_CONTRASENA y DEV_USUARIOS_CONTRASENA
en backend/.env (crean los cinco usuarios de prueba).

Uso (desde la carpeta backend, en PowerShell):
  .\scripts\verificar-roles.ps1 -ClaveAdmin "la_de_DEV_ADMIN_CONTRASENA" -ClaveUsuarios "la_de_DEV_USUARIOS_CONTRASENA"

Las reglas de abajo se copiaron de PoliticaPermisos.java: si cambias una regla allá, cámbiala aquí.
#>
param(
    [Parameter(Mandatory = $true)] [string] $ClaveAdmin,
    [Parameter(Mandatory = $true)] [string] $ClaveUsuarios,
    [string] $UrlBase = 'http://localhost:8080',
    [string] $Salida = 'verificacion-roles.csv'
)

$ErrorActionPreference = 'Stop'

$usuarios = [ordered]@{
    ADMINISTRADOR = @{ Correo = 'admin@appresso.test'; Clave = $ClaveAdmin }
    CAJERO        = @{ Correo = 'cajero@appresso.test'; Clave = $ClaveUsuarios }
    MESERO        = @{ Correo = 'mesero@appresso.test'; Clave = $ClaveUsuarios }
    COCINERO      = @{ Correo = 'cocinero@appresso.test'; Clave = $ClaveUsuarios }
    CLIENTE       = @{ Correo = 'cliente@appresso.test'; Clave = $ClaveUsuarios }
}

$reglas = @(
    @{ Metodo = 'DELETE'; Ruta = '/api/usuarios'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-02' },
    @{ Metodo = 'GET'; Ruta = '/api/categorias'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-06, RF-11' },
    @{ Metodo = 'DELETE'; Ruta = '/api/categorias/1'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-06' },
    @{ Metodo = 'GET'; Ruta = '/api/productos'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-06, RF-11, RF-13' },
    @{ Metodo = 'DELETE'; Ruta = '/api/productos/1'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-06, RF-09' },
    @{ Metodo = 'DELETE'; Ruta = '/api/inventario/movimientos'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-07, RF-10, RF-14' },
    @{ Metodo = 'GET'; Ruta = '/api/mesas'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-15' },
    @{ Metodo = 'PATCH'; Ruta = '/api/comandas/1/estado'; Permitidos = @('ADMINISTRADOR', 'COCINERO'); RF = 'RF-25' },
    @{ Metodo = 'POST'; Ruta = '/api/comandas'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-15' },
    @{ Metodo = 'POST'; Ruta = '/api/comandas/1/productos'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-16, RF-20' },
    @{ Metodo = 'POST'; Ruta = '/api/comandas/1/confirmar'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-18' },
    @{ Metodo = 'PUT'; Ruta = '/api/comandas/1/cliente'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-22' },
    @{ Metodo = 'POST'; Ruta = '/api/comandas/1/recompensa'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-58' },
    @{ Metodo = 'GET'; Ruta = '/api/comandas'; Permitidos = @('ADMINISTRADOR', 'MESERO', 'CAJERO', 'COCINERO'); RF = 'RF-19, RF-23' },
    @{ Metodo = 'DELETE'; Ruta = '/api/comandas/1'; Permitidos = @(); RF = '—' },
    @{ Metodo = 'POST'; Ruta = '/api/pagos/efectivo'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-29' },
    @{ Metodo = 'POST'; Ruta = '/api/pagos/transferencia'; Permitidos = @('ADMINISTRADOR', 'MESERO', 'CAJERO'); RF = 'RF-30, RF-41, RF-43' },
    @{ Metodo = 'GET'; Ruta = '/api/pagos'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-38, RF-39' },
    @{ Metodo = 'DELETE'; Ruta = '/api/pagos/1'; Permitidos = @(); RF = '—' },
    @{ Metodo = 'GET'; Ruta = '/api/caja/qr'; Permitidos = @('ADMINISTRADOR', 'MESERO', 'CAJERO'); RF = 'RF-40' },
    @{ Metodo = 'GET'; Ruta = '/api/caja/saldos'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-39' },
    @{ Metodo = 'GET'; Ruta = '/api/caja/movimientos'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-38, RF-39' },
    @{ Metodo = 'POST'; Ruta = '/api/caja/ingresos'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-32' },
    @{ Metodo = 'POST'; Ruta = '/api/caja/retiros'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-33' },
    @{ Metodo = 'DELETE'; Ruta = '/api/caja/otra'; Permitidos = @(); RF = '—' },
    @{ Metodo = 'PATCH'; Ruta = '/api/solicitudes-retiro/1/decision'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-36' },
    @{ Metodo = 'POST'; Ruta = '/api/solicitudes-retiro'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-34' },
    @{ Metodo = 'GET'; Ruta = '/api/solicitudes-retiro'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-36' },
    @{ Metodo = 'DELETE'; Ruta = '/api/solicitudes-retiro/1'; Permitidos = @(); RF = '—' },
    @{ Metodo = 'POST'; Ruta = '/api/jornadas/cierre'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-44' },
    @{ Metodo = 'PATCH'; Ruta = '/api/jornadas/1/base'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-47' },
    @{ Metodo = 'POST'; Ruta = '/api/jornadas'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-49' },
    @{ Metodo = 'GET'; Ruta = '/api/jornadas'; Permitidos = @('ADMINISTRADOR', 'CAJERO'); RF = 'RF-45, RF-48' },
    @{ Metodo = 'DELETE'; Ruta = '/api/jornadas/1'; Permitidos = @(); RF = '—' },
    @{ Metodo = 'GET'; Ruta = '/api/clientes/yo'; Permitidos = @('CLIENTE'); RF = 'RF-55' },
    @{ Metodo = 'POST'; Ruta = '/api/clientes'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-50' },
    @{ Metodo = 'POST'; Ruta = '/api/clientes/1/solicitar-eliminacion'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'Ley 1581' },
    @{ Metodo = 'DELETE'; Ruta = '/api/clientes/1'; Permitidos = @('ADMINISTRADOR'); RF = 'Ley 1581' },
    @{ Metodo = 'GET'; Ruta = '/api/clientes/1/consentimiento'; Permitidos = @('ADMINISTRADOR'); RF = 'Ley 1581' },
    @{ Metodo = 'GET'; Ruta = '/api/clientes/1234567890'; Permitidos = @('ADMINISTRADOR', 'MESERO'); RF = 'RF-51, RF-57' },
    @{ Metodo = 'DELETE'; Ruta = '/api/clientes/1/otra'; Permitidos = @(); RF = '—' },
    @{ Metodo = 'GET'; Ruta = '/api/recompensas'; Permitidos = @('ADMINISTRADOR', 'MESERO', 'CLIENTE'); RF = 'RF-56, RF-57, RF-58' },
    @{ Metodo = 'DELETE'; Ruta = '/api/recompensas/1'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-54, RF-60' },
    @{ Metodo = 'GET'; Ruta = '/api/promociones'; Permitidos = @('ADMINISTRADOR', 'CLIENTE'); RF = 'RF-62' },
    @{ Metodo = 'DELETE'; Ruta = '/api/promociones/1'; Permitidos = @('ADMINISTRADOR'); RF = 'RF-60' }
)

function Invocar([string] $metodo, [string] $ruta, [string] $token) {
    $encabezados = @{}
    if ($token) { $encabezados['Authorization'] = "Bearer $token" }
    try {
        $r = Invoke-WebRequest -Uri ($UrlBase + $ruta) -Method $metodo -Headers $encabezados -UseBasicParsing
        return [int] $r.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int] $_.Exception.Response.StatusCode }
        throw "No se pudo conectar con $UrlBase. ¿Está encendido el backend? ($($_.Exception.Message))"
    }
}

# 1. Iniciar sesión con los cinco roles
$tokens = @{}
foreach ($rol in $usuarios.Keys) {
    $cuerpo = @{ correo = $usuarios[$rol].Correo; contrasena = $usuarios[$rol].Clave } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Uri "$UrlBase/api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $cuerpo
    } catch {
        Write-Host "No se pudo iniciar sesión como $rol ($($usuarios[$rol].Correo)). Revisa la contraseña y que el usuario exista." -ForegroundColor Red
        exit 1
    }
    if ($r.rol -ne $rol) { Write-Host "El usuario $($usuarios[$rol].Correo) tiene rol $($r.rol), se esperaba $rol." -ForegroundColor Red; exit 1 }
    $tokens[$rol] = $r.token
    Write-Host "Sesión iniciada: $rol"
}

# 2. Cada regla con cada rol, y sin token
$resultados = @()
foreach ($regla in $reglas) {
    foreach ($rol in $usuarios.Keys) {
        $permitido = $regla.Permitidos -contains $rol
        $estado = Invocar $regla.Metodo $regla.Ruta $tokens[$rol]
        $correcto = if ($permitido) { $estado -ne 401 -and $estado -ne 403 } else { $estado -eq 403 }
        $resultados += [pscustomobject]@{
            Metodo = $regla.Metodo; Ruta = $regla.Ruta; Rol = $rol
            Esperado = if ($permitido) { 'permitido' } else { '403' }
            Obtenido = $estado; Resultado = if ($correcto) { 'OK' } else { 'FALLA' }; RF = $regla.RF
        }
    }
    $estado = Invocar $regla.Metodo $regla.Ruta $null
    $resultados += [pscustomobject]@{
        Metodo = $regla.Metodo; Ruta = $regla.Ruta; Rol = 'SIN TOKEN'; Esperado = '401'
        Obtenido = $estado; Resultado = if ($estado -eq 401) { 'OK' } else { 'FALLA' }; RF = $regla.RF
    }
}

# 3. Resumen y evidencia
$fallas = @($resultados | Where-Object Resultado -eq 'FALLA')
$resultados | Export-Csv -Path $Salida -NoTypeInformation -Encoding UTF8

Write-Host ''
Write-Host 'Resumen por rol:'
$resultados | Group-Object Rol | ForEach-Object {
    $ok = @($_.Group | Where-Object Resultado -eq 'OK').Count
    Write-Host ("  {0,-14} {1,3} / {2,3} correctos" -f $_.Name, $ok, $_.Count)
}
Write-Host ''
if ($fallas.Count -eq 0) {
    Write-Host "TODO CORRECTO: $($resultados.Count) verificaciones. Evidencia en $Salida" -ForegroundColor Green
} else {
    Write-Host "$($fallas.Count) FALLAS de $($resultados.Count) verificaciones:" -ForegroundColor Red
    $fallas | Format-Table Metodo, Ruta, Rol, Esperado, Obtenido, RF -AutoSize
    exit 1
}
