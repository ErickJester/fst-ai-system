# Arma en dist\ lo que se lleva a la PC de produccion (encargo 42):
#
#   fst-portable-<version>.zip        CHICO: docker-compose.yml, VERSION, portable\, modelos\fst_cnn_seg.pt y el esquema de la base
#                                     (backend-spring\db\init\001_esquema.sql, que monta la base). NO lleva el codigo fuente:
#                                     para correr no hace falta, porque el sistema corre con imagenes ya construidas.
#   fst-imagenes-<version>.tar        las imagenes: fst-api, fst-worker, fst-web (con la version) y la de postgres, tal como
#                                     las construyo esta PC (con -Comprimir sale .tar.gz). Pesan unos GB.
#
# La PC de destino NO necesita internet ni construir nada: copia el zip, lo descomprime, deja el .tar junto a docker-compose.yml
# (o dentro de portable\) y ejecuta portable\iniciar.bat, que hace "docker load" del .tar y "docker compose up --no-build".
# Corre exactamente lo que se construyo y probo aqui.
#
# La version sale del archivo VERSION de la raiz (la escribe tools\version.py). Las imagenes se construyen DESDE EL MISMO
# arbol que va al zip, asi que imagenes y paquete no se pueden desalinear.
#
#   .\portable\empaquetar.ps1                  toma lo que esta COMMITEADO (git archive HEAD): es el paquete oficial.
#   .\portable\empaquetar.ps1 -DesdeCarpeta    toma lo que hay en la carpeta de trabajo, con las mismas exclusiones. Sirve
#                                              para probar el paquete antes de hacer el commit.
#   -SinImagenes                               solo el zip (no construye ni guarda imagenes).
#   -Comprimir                                 el archivo de imagenes sale como .tar.gz (mas chico, tarda mas).
#   -ConCodigoFuente                           el zip lleva tambien el codigo fuente (por omision no).
#
# Nunca incluye .env, target, node_modules ni dist. Necesita Docker Desktop corriendo (para construir y guardar las imagenes).
param(
    [switch]$DesdeCarpeta,
    [switch]$SinImagenes,
    [switch]$Comprimir,
    [switch]$ConCodigoFuente
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$carpetas = @('backend-spring', 'worker', 'pipeline', 'frontend', 'portable')
# Archivos sueltos de la raiz que tambien se extraen.
$archivos = @('docker-compose.yml', 'VERSION')
$modelo = Join-Path $raiz 'modelos\fst_cnn_seg.pt'
if (-not (Test-Path $modelo)) {
    throw "Falta el modelo: $modelo (se copia a mano desde fst_auto; ver pipeline\README.md)"
}
# Ejecuta un comando de docker sin que su salida de error (que en Windows PowerShell 5.1 se vuelve un error terminante con
# $ErrorActionPreference = 'Stop') tumbe el script, y dice si salio bien.
function Docker-Salio-Bien {
    param([string[]]$Argumentos)
    $antes = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker @Argumentos *> $null; return ($LASTEXITCODE -eq 0) }
    finally { $ErrorActionPreference = $antes }
}

if (-not $SinImagenes) {
    if (-not (Docker-Salio-Bien @('info'))) { throw "Docker no esta respondiendo: abre Docker Desktop (o usa -SinImagenes)." }
}

$dist = Join-Path $raiz 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null

# Se arma en una carpeta temporal y de ahi se construye y se comprime.
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
        foreach ($a in $archivos) {
            Copy-Item (Join-Path $raiz $a) (Join-Path $tmp $a)
        }
    }
    else {
        Write-Host "Extrayendo de git (HEAD)..."
        $tar = Join-Path $tmp 'git.tar'
        git -C $raiz archive --format=tar -o $tar HEAD @carpetas @archivos
        if ($LASTEXITCODE -ne 0) { throw "git archive fallo (codigo $LASTEXITCODE). Si portable/ o docker-compose.yml aun no estan commiteados, usa -DesdeCarpeta." }
        tar -xf $tar -C $tmp
        if ($LASTEXITCODE -ne 0) { throw "tar fallo (codigo $LASTEXITCODE)" }
        Remove-Item $tar -Force
        # git archive trae lo commiteado: por si alguien commiteo algo que no debe ir.
        Get-ChildItem $tmp -Recurse -Force -Include '.env' -File | Remove-Item -Force
        Get-ChildItem $tmp -Recurse -Force -Directory | Where-Object { $_.Name -in 'node_modules', 'dist', 'target', '__pycache__' } | Remove-Item -Recurse -Force
    }

    # El modelo no esta en git: va a modelos\ junto a portable\ (el compose lo busca en .\modelos).
    New-Item -ItemType Directory -Force (Join-Path $tmp 'modelos') | Out-Null
    Copy-Item $modelo (Join-Path $tmp 'modelos\fst_cnn_seg.pt')

    # La version sale de VERSION (la misma que ve el backend en /health y la web en el menu de usuario).
    $version = (Get-Content (Join-Path $tmp 'VERSION') -Raw).Trim()
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "VERSION no tiene el formato X.Y.Z: '$version'" }
    Write-Host "Version que se empaqueta: $version"

    # --- Imagenes -----------------------------------------------------------------------------------------------------
    $imagenesTar = $null
    if (-not $SinImagenes) {
        # La imagen de la base es la que declara el compose (version exacta).
        $compose = Get-Content (Join-Path $tmp 'docker-compose.yml') -Raw
        $m = [regex]::Match($compose, '(?m)^\s*image:\s*(postgres:\S+)\s*$')
        if (-not $m.Success) { throw "No encontre la imagen de postgres en docker-compose.yml" }
        $postgres = $m.Groups[1].Value

        $api = "fst-api:$version"
        $worker = "fst-worker:$version"
        $web = "fst-web:$version"

        Write-Host ""
        Write-Host "Construyendo $api ..."
        docker build -t $api (Join-Path $tmp 'backend-spring')
        if ($LASTEXITCODE -ne 0) { throw "docker build de $api fallo (codigo $LASTEXITCODE)" }

        Write-Host ""
        Write-Host "Construyendo $worker (la primera vez baja torch: tarda) ..."
        docker build -t $worker -f (Join-Path $tmp 'worker\Dockerfile') $tmp
        if ($LASTEXITCODE -ne 0) { throw "docker build de $worker fallo (codigo $LASTEXITCODE)" }

        Write-Host ""
        Write-Host "Construyendo $web ..."
        # Los mismos argumentos que declara el compose para el servicio web. Se graban al compilar.
        docker build -t $web -f (Join-Path $tmp 'frontend\Dockerfile.prod') --build-arg VITE_USE_MOCKS=false --build-arg VITE_API_BASE=/api --build-arg VITE_MAX_UPLOAD_MB=2048 (Join-Path $tmp 'frontend')
        if ($LASTEXITCODE -ne 0) { throw "docker build de $web fallo (codigo $LASTEXITCODE)" }

        if (-not (Docker-Salio-Bien @('image', 'inspect', $postgres))) {
            Write-Host ""
            Write-Host "Descargando $postgres (una sola vez) ..."
            docker pull $postgres
            if ($LASTEXITCODE -ne 0) { throw "docker pull de $postgres fallo (codigo $LASTEXITCODE)" }
        }

        $imagenesTar = Join-Path $dist ("fst-imagenes-{0}.tar" -f $version)
        if (Test-Path $imagenesTar) { Remove-Item $imagenesTar -Force }
        Write-Host ""
        Write-Host "Guardando las imagenes en $imagenesTar ..."
        docker save -o $imagenesTar $api $worker $web $postgres
        if ($LASTEXITCODE -ne 0) { throw "docker save fallo (codigo $LASTEXITCODE)" }

        if ($Comprimir) {
            Write-Host "Comprimiendo (tarda unos minutos) ..."
            $gz = "$imagenesTar.gz"
            if (Test-Path $gz) { Remove-Item $gz -Force }
            $entrada = [System.IO.File]::OpenRead($imagenesTar)
            $salida = [System.IO.File]::Create($gz)
            try {
                $compresor = New-Object System.IO.Compression.GZipStream($salida, [System.IO.Compression.CompressionLevel]::Optimal)
                try { $entrada.CopyTo($compresor) } finally { $compresor.Dispose() }
            }
            finally { $salida.Dispose(); $entrada.Dispose() }
            Remove-Item $imagenesTar -Force
            $imagenesTar = $gz
        }
    }

    # --- Zip ----------------------------------------------------------------------------------------------------------
    $zip = Join-Path $dist ("fst-portable-{0}.zip" -f $version)
    if (Test-Path $zip) { Remove-Item $zip -Force }

    # Sin -ConCodigoFuente, solo lo que hace falta para CORRER: el compose, la version, portable\, el modelo y el esquema que
    # monta la base. El codigo fuente de las imagenes ya esta dentro de ellas.
    $soloParaCorrer = '^(docker-compose\.yml|VERSION)$|^portable/|^modelos/|^backend-spring/db/init/001_esquema\.sql$'

    Write-Host ""
    Write-Host "Comprimiendo el paquete..."
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    # Se agrega archivo por archivo, y no con ZipFile.CreateFromDirectory, porque en Windows PowerShell 5.1 este
    # ultimo guarda las rutas con "\" y otros programas de descompresion las leen mal. Aqui van con "/".
    $flujo = [System.IO.File]::Open($zip, [System.IO.FileMode]::Create)
    $comprimido = New-Object System.IO.Compression.ZipArchive($flujo, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($f in Get-ChildItem $tmp -Recurse -File -Force) {
            $ruta = $f.FullName.Substring($tmp.Length + 1).Replace('\', '/')
            if (-not $ConCodigoFuente -and $ruta -notmatch $soloParaCorrer) { continue }
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($comprimido, $f.FullName, $ruta, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
    finally {
        $comprimido.Dispose()
        $flujo.Dispose()
    }

    $mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
    Write-Host ""
    Write-Host "Listo (version $version):"
    Write-Host "  Paquete:  $zip  ($mb MB)"
    if ($imagenesTar) {
        $gb = [math]::Round((Get-Item $imagenesTar).Length / 1GB, 2)
        Write-Host "  Imagenes: $imagenesTar  ($gb GB)"
        Write-Host ""
        Write-Host "En la PC de destino: descomprime el zip, copia el archivo de imagenes junto a docker-compose.yml y"
        Write-Host "ejecuta portable\iniciar.bat (carga las imagenes y prende el sistema, sin internet)."
    }
    else {
        Write-Host "  (sin imagenes: -SinImagenes)"
    }
    Write-Host "Carpetas que contiene el zip:"
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
