# Arma dist\fst-portable-AAAAMMDD.zip con SOLO lo necesario para llevar el sistema a otra PC:
# backend-spring, worker, worker_fake, pipeline, frontend y portable, mas modelos\fst_cnn_seg.pt (el modelo no
# esta en git). Nunca incluye .env, target, node_modules ni dist.
#
#   .\portable\empaquetar.ps1                  toma lo que esta COMMITEADO (git archive HEAD): es el paquete oficial.
#   .\portable\empaquetar.ps1 -DesdeCarpeta    toma lo que hay en la carpeta de trabajo, con las mismas
#                                              exclusiones. Sirve para probar el paquete antes de hacer el commit.
param(
    [switch]$DesdeCarpeta
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$carpetas = @('backend-spring', 'worker', 'worker_fake', 'pipeline', 'frontend', 'portable')
$modelo = Join-Path $raiz 'modelos\fst_cnn_seg.pt'
if (-not (Test-Path $modelo)) {
    throw "Falta el modelo: $modelo"
}

$dist = Join-Path $raiz 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$zip = Join-Path $dist ("fst-portable-{0}.zip" -f (Get-Date -Format 'yyyyMMdd'))
if (Test-Path $zip) { Remove-Item $zip -Force }

# Se arma en una carpeta temporal y de ahi se comprime.
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("fst-empaquetar-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $tmp | Out-Null

try {
    if ($DesdeCarpeta) {
        Write-Host "Copiando desde la carpeta de trabajo (sin .env, target, node_modules ni dist)..."
        foreach ($c in $carpetas) {
            # robocopy devuelve 0 a 7 cuando todo salio bien.
            robocopy (Join-Path $raiz $c) (Join-Path $tmp $c) /E /XD node_modules dist target __pycache__ .git /XF .env *.pyc /NFL /NDL /NJH /NJS /NP | Out-Null
            if ($LASTEXITCODE -ge 8) { throw "robocopy fallo con $c (codigo $LASTEXITCODE)" }
        }
    }
    else {
        Write-Host "Extrayendo de git (HEAD)..."
        $tar = Join-Path $tmp 'git.tar'
        git -C $raiz archive --format=tar -o $tar HEAD @carpetas
        if ($LASTEXITCODE -ne 0) { throw "git archive fallo (codigo $LASTEXITCODE). Si portable/ aun no esta commiteado, usa -DesdeCarpeta." }
        tar -xf $tar -C $tmp
        if ($LASTEXITCODE -ne 0) { throw "tar fallo (codigo $LASTEXITCODE)" }
        Remove-Item $tar -Force
        # git archive trae lo commiteado: por si alguien commiteo algo que no debe ir.
        Get-ChildItem $tmp -Recurse -Force -Include '.env' -File | Remove-Item -Force
        Get-ChildItem $tmp -Recurse -Force -Directory | Where-Object { $_.Name -in 'node_modules', 'dist', 'target', '__pycache__' } | Remove-Item -Recurse -Force
    }

    # El modelo no esta en git: va a modelos\ junto a portable\ (el compose lo busca en ..\modelos).
    New-Item -ItemType Directory -Force (Join-Path $tmp 'modelos') | Out-Null
    Copy-Item $modelo (Join-Path $tmp 'modelos\fst_cnn_seg.pt')

    # La version sale de frontend\package.json, que escribe tools\version.py (la misma que ve el backend en /health).
    $version = (Get-Content (Join-Path $tmp 'frontend\package.json') -Raw | ConvertFrom-Json).version
    Write-Host "Version que se empaqueta: $version"

    Write-Host "Comprimiendo..."
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    # Se agrega archivo por archivo, y no con ZipFile.CreateFromDirectory, porque en Windows PowerShell 5.1 este
    # ultimo guarda las rutas con "\" y otros programas de descompresion las leen mal. Aqui van con "/".
    $flujo = [System.IO.File]::Open($zip, [System.IO.FileMode]::Create)
    $comprimido = New-Object System.IO.Compression.ZipArchive($flujo, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($f in Get-ChildItem $tmp -Recurse -File -Force) {
            $ruta = $f.FullName.Substring($tmp.Length + 1).Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($comprimido, $f.FullName, $ruta, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
    finally {
        $comprimido.Dispose()
        $flujo.Dispose()
    }

    $mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
    Write-Host ""
    Write-Host "Listo: $zip (version $version)"
    Write-Host "Tamano: $mb MB"
    Write-Host "Carpetas que contiene:"
    $archivo = [System.IO.Compression.ZipFile]::OpenRead($zip)
    try {
        $archivo.Entries |
            Where-Object { $_.FullName -notmatch '/$' } |
            Group-Object { ($_.FullName -split '/')[0] } |
            ForEach-Object { Write-Host ("  {0,-16} {1,5} archivos" -f $_.Name, $_.Count) }
    }
    finally {
        $archivo.Dispose()
    }
}
finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
