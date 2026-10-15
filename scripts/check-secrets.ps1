# Busca patrones de credenciales Vimeus/TMDB en los archivos rastreados por git.
# Cualquier coincidencia detiene la ejecución (pre-commit / CI).
#
# Se busca una *forma* de secreto real (clave identificable + valor literal
# entrecomillado o asignado), para evitar falsos positivos con identificadores
# de código como `vimeusApiKey = BuildTimeSecrets.vimeusApiKey`.
$ErrorActionPreference = 'Stop'

# (patrón, aplica a rutas que cumplen el subpatrón) — "" = cualquier archivo
$rules = @(
    @{ re = 'Bearer\s+[A-Za-z0-9._\-]{24,}'; ext = '' },
    @{ re = 'eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.'; ext = '' },
    @{ re = 'view_key\s*=\s*[A-Za-z0-9]{16,}'; ext = '' },
    # Cadenas literales de Kotlin/JSON con aspecto de token
    @{ re = '(?i)\b(?:vimeus|tmdb|vm)[a-z0-9_]*\s*[=:]\s*[''"][A-Za-z0-9_\-\.]{16,}[''"]'; ext = '' },
    # Archivos de propiedades: valores asignados no vacíos de claves sensibles
    @{ re = '(?i)^(?:vimeus|tmdb|keystore|store|key|password|secret|api|token)[a-z0-9_]*\s*[=:]\s*\S{16,}'; ext = '\.properties$' }
)

$tracked = git ls-files
$hits = @()
foreach ($file in $tracked) {
    if (-not (Test-Path $file -PathType Leaf)) { continue }
    if ($file -match '\.(png|jpg|jpeg|webp|gif|apk|jar|keystore|jks|ttf|otf|so|zip|pdf)$') { continue }
    $lines = Get-Content $file -ErrorAction SilentlyContinue
    foreach ($rule in $rules) {
        if ($rule.ext -and ($file -notmatch $rule.ext)) { continue }
        $n = 0
        foreach ($line in $lines) {
            $n++
            if ($line -match $rule.re) { $hits += "{0}:{1}  [{2}]" -f $file, $n, $rule.re.Substring(0, 30) }
        }
    }
}

if ($hits.Count -gt 0) {
    Write-Host "Posibles secretos encontrados:" -ForegroundColor Red
    $hits | Sort-Object -Unique | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
Write-Host "Sin secretos detectados en archivos rastreados." -ForegroundColor Green
exit 0
