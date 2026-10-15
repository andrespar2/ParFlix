# Busca patrones de credenciales Vimeus/TMDB en el rastreado por git.
# Cualquier coincidencia detiene la ejecución (pre-commit / CI).
$ErrorActionPreference = 'Stop'
$patterns = @(
    'vm_[A-Za-z0-9]{16,}',
    'tmdb[A-Za-z0-9]{16,}',
    'Bearer\s+[A-Za-z0-9._-]{20,}',
    'eyJhbGciOi[A-Za-z0-9_-]{10,}',
    'view_key=[A-Za-z0-9]{16,}'
)
$tracked = git ls-files
$hits = @()
foreach ($file in $tracked) {
    if (-not (Test-Path $file -PathType Leaf)) { continue }
    $lines = Get-Content $file -ErrorAction SilentlyContinue
    foreach ($pattern in $patterns) {
        $matchesFound = $lines | Select-String -Pattern $pattern -SimpleMatch:$false
        foreach ($m in $matchesFound) {
            $hits += "{0}:{1}" -f $file, $m.LineNumber
        }
    }
}
if ($hits.Count -gt 0) {
    Write-Host "Posibles secretos encontrados:" -ForegroundColor Red
    $hits | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
Write-Host "Sin secretos detectados en archivos rastreados." -ForegroundColor Green
