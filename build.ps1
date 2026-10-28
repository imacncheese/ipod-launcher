$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = 'F:\dsh\ipod-launcher'
$tc   = "$root\toolchain"
$jdk  = "$tc\jdk\jdk-17.0.20.1+1"
$bt   = "$tc\bt\android-15"
$jar  = "$tc\plat\android-35\android.jar"
$app  = "$root\app"
$out  = "$root\build"

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$bt;$env:PATH"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$out\classes", "$out\dex" | Out-Null

function Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }

# ---- 1. compile -----------------------------------------------------------
Step 'javac'
$srcs = Get-ChildItem "$app\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options `
    -bootclasspath $jar -d "$out\classes" $srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }
Write-Host "classes: $((Get-ChildItem "$out\classes" -Recurse -Filter *.class).Count)"

# ---- 2. dex ---------------------------------------------------------------
Step 'jar + d8'
& "$jdk\bin\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

& "$bt\d8.bat" --release --min-api 26 --lib $jar --output "$out\dex" "$out\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed ($LASTEXITCODE)" }
Get-ChildItem "$out\dex" | ForEach-Object { "  dex: $($_.Name) $([math]::Round($_.Length/1KB,1)) KB" }

# ---- 3. resources ---------------------------------------------------------
Step 'aapt2 compile/link'
& "$bt\aapt2.exe" compile --dir "$app\res" -o "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar `
    --manifest "$app\AndroidManifest.xml" -R "$out\res.zip" --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ---- 4. put dex into the apk ---------------------------------------------
Step 'merge classes.dex'
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.apk", 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, "$out\dex\classes.dex", 'classes.dex',
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $zip.Dispose() }

# ---- 5. align -------------------------------------------------------------
Step 'zipalign'
& "$bt\zipalign.exe" -p -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ---- 6. key + sign --------------------------------------------------------
Step 'sign'
New-Item -ItemType Directory -Force -Path "$root\signing" | Out-Null
if (-not (Test-Path "$root\signing\ipod.keystore")) {
    & "$jdk\bin\keytool.exe" -genkeypair -keystore "$root\signing\ipod.keystore" -alias ipod `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass ipodpass -keypass ipodpass `
        -dname "CN=iPod Launcher, OU=DSH, O=DSH, C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

& "$bt\apksigner.bat" sign --ks "$root\signing\ipod.keystore" `
    --ks-pass pass:ipodpass --key-pass pass:ipodpass `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out "$out\iPodLauncher.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

& "$bt\apksigner.bat" verify --print-certs "$out\iPodLauncher.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

$f = Get-Item "$out\iPodLauncher.apk"
Step 'done'
"APK : $($f.FullName)"
"Size: $([math]::Round($f.Length/1KB,1)) KB"
"SHA256: $((Get-FileHash -Algorithm SHA256 $f.FullName).Hash.ToLower())"
