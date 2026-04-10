$ErrorActionPreference = 'Stop'

$packageName = 'javelin-cli'
$toolsDir = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)"
$installDir = Join-Path $toolsDir 'javelin-cli'

Uninstall-BinFile -Name 'javelin'

if (Test-Path $installDir) {
    Remove-Item $installDir -Recurse -Force
}
