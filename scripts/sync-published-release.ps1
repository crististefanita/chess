param(
    [Parameter(Mandatory)][string]$PublicationCommit,
    [Parameter(Mandatory)][string]$Destination,
    [Parameter(Mandatory)][string]$WorkRoot,
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$Classpath,
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
$source = Split-Path $PSScriptRoot -Parent
$mode = if ($VerifyOnly) { 'verify' } else { 'sync' }
& java -cp $Classpath ro.atf.chess.release.PublishedReleaseArchive $mode $source $PublicationCommit $Destination $WorkRoot $Apk
if ($LASTEXITCODE -ne 0) { throw "Published release $mode failed; delivery is incomplete" }
