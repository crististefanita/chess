param(
    [Parameter(Mandatory=$true)][string]$VendorRoot,
    [Parameter(Mandatory=$true)][string]$NdkRoot,
    [Parameter(Mandatory=$true)][string]$BuildRoot,
    [string]$GitShell = 'C:\tools\Git\usr\bin\sh.exe',
    [ValidateRange(1,32)][int]$Jobs = 4
)
$ErrorActionPreference = 'Stop'
$sourceCommit = '0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6'
$archiveName = 'stockfish-js-19.0.1-lite-source.tar.gz'
$archiveSha = 'e5289a920554bf0069a6601c228e81d957c00522bb31c4343588dc5d7d251879'
$netName = 'nn-61e7af4bb97d.nnue'
$netSha = '61e7af4bb97d51eeeb25d322916f86513b5cd3a827ce189c98c6e31946f99e5b'
# The disclosed native-entry patch is part of this reproducible Lite recipe.
$nativePatch = Join-Path $VendorRoot 'source\native-main-unique-ptr.patch'
function Sha([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function RequirePin([string]$Path,[string]$Hash,[long]$Bytes) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or (Get-Item -LiteralPath $Path).Length -ne $Bytes -or (Sha $Path) -ne $Hash) { throw "Pin mismatch: $Path" }
}
RequirePin (Join-Path $VendorRoot $archiveName) $archiveSha 426352
RequirePin (Join-Path $VendorRoot $netName) $netSha 1166381
RequirePin $nativePatch 'e5b023f014e4644dbcbf87a328ff8cd897530877621e8a5b2a4626f9f16236ef' 236
if (!(Get-Content -LiteralPath (Join-Path $NdkRoot 'source.properties') -Raw).Contains('Pkg.Revision = 30.0.16248370')) { throw 'Use the pinned Android NDK r30 30.0.16248370' }
if (!(Test-Path -LiteralPath $GitShell -PathType Leaf)) { throw 'A POSIX shell is required by the upstream Makefile' }
if (Test-Path -LiteralPath $BuildRoot) { throw 'Use a fresh BuildRoot to preserve previous native logs and artifacts' }
New-Item -ItemType Directory -Path $BuildRoot,(Join-Path $BuildRoot 'tmp'),(Join-Path $BuildRoot 'vendor') | Out-Null
$env:TEMP = Join-Path $BuildRoot 'tmp'; $env:TMP = $env:TEMP
$bin = Join-Path $NdkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$env:PATH = $bin + ';' + (Split-Path -Parent $GitShell) + ';' + $env:PATH
$make = Join-Path $NdkRoot 'prebuilt\windows-x86_64\bin\make.exe'
# Windows tar cannot create this Web-example symlink; it is unrelated to native inputs.
$tar = Join-Path ([Environment]::GetFolderPath('System')) 'tar.exe'
& $tar -xf (Join-Path $VendorRoot $archiveName) -C (Join-Path $BuildRoot 'vendor') --exclude='*/examples/src'
if ($LASTEXITCODE -ne 0) { throw 'Cannot extract the pinned upstream source' }
$source = Join-Path $BuildRoot ('vendor\stockfish.js-' + $sourceCommit)
# The upstream non-WASM entry point calls a unique_ptr with '.', which cannot compile.
# Keep the original archive intact and apply only the disclosed native-entry correction.
& git.exe -C $source apply --check -- $nativePatch
if ($LASTEXITCODE -ne 0) { throw 'The native-entry patch does not match the pinned source' }
& git.exe -C $source apply -- $nativePatch
if ($LASTEXITCODE -ne 0) { throw 'Cannot apply the disclosed native-entry patch' }
$targets = @(
    @{abi='arm64-v8a';arch='armv8';triple='aarch64-linux-android26'},
    @{abi='x86_64';arch='x86-64';triple='x86_64-linux-android26'},
    @{abi='armeabi-v7a';arch='armv7-neon';triple='armv7a-linux-androideabi26'}
)
$binaries = @()
foreach ($target in $targets) {
    $phase = Join-Path $BuildRoot $target.abi
    New-Item -ItemType Directory -Path $phase | Out-Null
    Copy-Item -LiteralPath $source -Destination (Join-Path $phase 'source') -Recurse
    Copy-Item -LiteralPath (Join-Path $VendorRoot $netName) -Destination (Join-Path $phase ('source\src\' + $netName))
    $cxx = (Join-Path $bin 'clang++.exe').Replace('\','/') + ' --target=' + $target.triple
    $makeArgs = @("-j$Jobs",'build',('ARCH='+$target.arch),'COMP=ndk','LITE_NET=yes','ENGINE_VERSION=19.0.1-Lite',('COMPCXX='+$cxx),('SHELL='+$GitShell.Replace('\','/')),
        'EXTRACXXFLAGS=-DNNUE_EMBEDDING_OFF','EXTRALDFLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384')
    Push-Location (Join-Path $phase 'source\src')
    try { & $make @makeArgs *> (Join-Path $phase 'build.log'); $code = $LASTEXITCODE }
    finally { Pop-Location }
    [ordered]@{abi=$target.abi;nativeExit=$code;sourceCommit=$sourceCommit;api=26;ndk='30.0.16248370';args=$makeArgs;logSha256=(Sha (Join-Path $phase 'build.log'))} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $phase 'exit.json') -Encoding utf8
    if ($code -ne 0) { throw "Native build failed for $($target.abi); preserve build.log and exit.json" }
    $output = Join-Path $BuildRoot ('output\' + $target.abi)
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $executable = Join-Path $output 'libstockfish.so'
    & (Join-Path $bin 'llvm-strip.exe') --strip-all -o $executable (Join-Path $phase 'source\src\stockfish')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot strip the native executable' }
    & (Join-Path $bin 'llvm-readelf.exe') --file-header --program-headers --notes --dynamic $executable > (Join-Path $phase 'elf.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the native executable' }
    $binaries += [ordered]@{abi=$target.abi;providerID=('stockfish-19.0.1-android-lite-'+$target.abi);path=($target.abi+'/libstockfish.so');bytes=(Get-Item $executable).Length;sha256=(Sha $executable)}
    Write-Output ("Built " + $target.abi + ': ' + (Sha $executable))
}
[ordered]@{schema=1;buildStatus='BUILT_AND_PINNED';version='19.0.1';flavor='lite';sourceCommit=$sourceCommit;minSdk=26;elfPageAlignment=16384;ndkVersion='30.0.16248370';
    sourceArchive=[ordered]@{path=$archiveName;bytes=426352;sha256=$archiveSha;url=('https://github.com/nmrugg/stockfish.js/archive/'+$sourceCommit+'.tar.gz')};
    sourcePatch=[ordered]@{path='source/native-main-unique-ptr.patch';sha256=(Sha $nativePatch);bytes=(Get-Item $nativePatch).Length;scope='NATIVE_ONLY_UNIQUE_PTR_ENTRY_CORRECTION';patchedMainSha256=(Sha (Join-Path $source 'src\main.cpp'))};
    network=[ordered]@{path=$netName;bytes=1166381;sha256=$netSha;url=('https://tests.stockfishchess.org/api/nn/'+$netName)};binaries=$binaries} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $BuildRoot 'output\manifest.json') -Encoding utf8
# All generated artifacts remain in BuildRoot. Copy output ABI folders into jniLibs only after ELF and device tests.
