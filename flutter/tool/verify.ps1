param(
    [string]$FlutterSdk = '',
    [switch]$Release
)

$ErrorActionPreference = 'Stop'

$flutterCommand = if ($FlutterSdk) {
    Join-Path -Path $FlutterSdk -ChildPath 'bin\flutter.bat'
} else {
    (Get-Command flutter -ErrorAction Stop).Source
}
$dartCommand = if ($FlutterSdk) {
    Join-Path -Path $FlutterSdk -ChildPath 'bin\dart.bat'
} else {
    (Get-Command dart -ErrorAction Stop).Source
}
$projectDirectory = Split-Path -Parent $PSScriptRoot

Push-Location $projectDirectory
try {
    & $flutterCommand pub get
    if ($LASTEXITCODE -ne 0) { throw 'flutter pub get failed' }

    & $dartCommand format --output=none --set-exit-if-changed .
    if ($LASTEXITCODE -ne 0) { throw 'dart format failed' }

    & $flutterCommand analyze
    if ($LASTEXITCODE -ne 0) { throw 'flutter analyze failed' }

    & $flutterCommand test
    if ($LASTEXITCODE -ne 0) { throw 'flutter test failed' }

    & $flutterCommand build apk --debug
    if ($LASTEXITCODE -ne 0) { throw 'flutter build apk --debug failed' }

    if ($Release) {
        & $flutterCommand build apk --release
        if ($LASTEXITCODE -ne 0) { throw 'flutter build apk --release failed' }
    }
} finally {
    Pop-Location
}
