param()

# -- Constants

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $projectRoot 'build/s12/assets'
$downloads = @(
    @{
        Name = 'u2netp.onnx'
        Url = 'https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx'
        Sha256 = '309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8'
    },
    @{
        Name = 'sample.jpg'
        Url = 'https://raw.githubusercontent.com/xuebinqin/U-2-Net/master/test_data/test_images/0003.jpg'
        Sha256 = '8d13d397fcbc4c3742d1d30d00cabe53c7b1ecd0a8cbf5a73ab0f78330fca12f'
    }
)

# -- Functions

New-Item -ItemType Directory -Force -Path $assetRoot | Out-Null
foreach ($download in $downloads) {
    $target = Join-Path $assetRoot $download.Name
    if ((Test-Path -LiteralPath $target) -and
        (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $download.Sha256) {
        Write-Output "Verified $($download.Name)"
        continue
    }
    $temporary = "$target.download"
    try {
        Invoke-WebRequest -Uri $download.Url -OutFile $temporary
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $download.Sha256) {
            throw "SHA-256 mismatch: $($download.Name)"
        }
        Move-Item -LiteralPath $temporary -Destination $target -Force
        Write-Output "Downloaded and verified $($download.Name)"
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
    }
}
