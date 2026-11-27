$ErrorActionPreference = 'Stop'

$root = "C:\Users\X1\Documents\deepseek-harness\default-workspace\toolchain"
$app  = "C:\Users\X1\Documents\deepseek-harness\default-workspace\tools\app"
$jdk  = Join-Path $root "jdk\jdk-17.0.2"
$sdk  = Join-Path $root "sdk"

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"

$androidJar = (Get-ChildItem "$sdk\platforms" -Filter android.jar -Recurse -File | Select-Object -First 1).FullName
$bt = (Get-ChildItem "$sdk\build-tools" -Directory | Select-Object -First 1).FullName
$aapt2 = Join-Path $bt "aapt2.exe"
$zipalign = Join-Path $bt "zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"
$d8 = Join-Path $bt "d8.bat"

$out = Join-Path $app "build"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null
New-Item -ItemType Directory -Path "$out\classes" | Out-Null
New-Item -ItemType Directory -Path "$out\dex" | Out-Null

Write-Host "[1/6] aapt2 link"
& $aapt2 link -o "$out\base.apk" --manifest "$app\AndroidManifest.xml" -I $androidJar --min-sdk-version 23 --target-sdk-version 30
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[2/6] javac"
$sources = (Get-ChildItem "$app\src" -Filter *.java -Recurse -File).FullName
& "$jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $androidJar -d "$out\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[3/6] d8"
$classes = (Get-ChildItem "$out\classes" -Filter *.class -Recurse -File).FullName
& "$jdk\bin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --min-api 23 --lib $androidJar --output "$out\dex" $classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[4/6] package"
Copy-Item "$out\base.apk" "$out\app-unsigned.apk" -Force
& "$jdk\bin\jar.exe" uf "$out\app-unsigned.apk" -C "$out\dex" classes.dex
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

& $zipalign -f 4 "$out\app-unsigned.apk" "$out\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[5/6] keystore"
$ks = "$out\debug.keystore"
if (-not (Test-Path $ks)) {
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -alias androiddebugkey -storepass android `
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

Write-Host "[6/6] sign"
& cmd /c "`"$apksigner`" sign --ks `"$ks`" --ks-pass pass:android --key-pass pass:android --out `"$out\gravrot.apk`" `"$out\app-aligned.apk`""
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& cmd /c "`"$apksigner`" verify --print-certs `"$out\gravrot.apk`""

$size = (Get-Item "$out\gravrot.apk").Length
Write-Host ""
Write-Host "BUILD OK -> $out\gravrot.apk ($([math]::Round($size/1024,1)) KB)"
