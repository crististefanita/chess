param(
    [Parameter(Mandatory)][string]$PublicationCommit,
    [Parameter(Mandatory)][string]$Destination,
    [Parameter(Mandatory)][string]$WorkRoot,
    [Parameter(Mandatory)][string]$Apk,
    [string]$Classpath,
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReleaseWorkspace.ps1')
$workspace = Get-ChessReleaseWorkspace -ScriptDirectory $PSScriptRoot
$source = $workspace.ProjectRoot
if (!$Classpath) { $Classpath = Get-ChessReleaseClasspath -ModuleRoot $workspace.ModuleRoot }
$mode = if ($VerifyOnly) { 'verify' } else { 'sync' }
& java -cp $Classpath ro.atf.chess.release.PublishedReleaseArchive $mode $source $PublicationCommit $Destination $WorkRoot $Apk
if ($LASTEXITCODE -ne 0) { throw "Published release $mode failed; delivery is incomplete" }
