$ErrorActionPreference = 'Stop'

$packageName = 'javelin-cli'
$toolsDir = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)"

# Verify Java 21+ is available
try {
    $javaVersion = & java -version 2>&1 | Select-Object -First 1
    if ($javaVersion -match '"(\d+)') {
        $majorVersion = [int]$Matches[1]
        if ($majorVersion -lt 21) {
            Write-Warning "Java $majorVersion detected but javelin-cli requires Java 21+. Please install a Java 21+ JDK (any vendor: Temurin, Oracle, Corretto, etc.)."
        }
    }
} catch {
    Write-Warning "Java not found on PATH. javelin-cli requires Java 21+. Please install a Java 21+ JDK (any vendor: Temurin, Oracle, Corretto, etc.)."
}

# Download and extract the distribution zip from GitHub Releases
$version = $env:chocolateyPackageVersion
$url = "https://github.com/DesmondQue/javelin-cli/releases/download/v${version}/javelin-cli-${version}.zip"

$installDir = Join-Path $toolsDir 'javelin-cli'

Install-ChocolateyZipPackage -PackageName $packageName `
    -Url $url `
    -UnzipLocation $toolsDir `
    -Checksum '3e2cafba962a83e600d8346fbb3421c64d15766b0b1b352d96cd56b5f17b3c58' `
    -ChecksumType 'sha256'

# The zip extracts to a folder like javelin-1.0.0/; find the bin directory
$extractedDir = Get-ChildItem -Path $toolsDir -Directory | Where-Object { $_.Name -match '^javelin' -and $_.Name -ne 'javelin-cli' } | Select-Object -First 1

if ($extractedDir) {
    # Rename to a stable path
    if (Test-Path $installDir) { Remove-Item $installDir -Recurse -Force }
    Rename-Item $extractedDir.FullName $installDir
}

# Create a shim for the batch file
$binFile = Join-Path $installDir 'bin\javelin.bat'
Install-BinFile -Name 'javelin' -Path $binFile
