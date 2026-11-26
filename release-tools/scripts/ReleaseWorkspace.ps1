#requires -Version 7.0

function Get-ChessReleaseWorkspace {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$ScriptDirectory)
    $directory = Get-Item -LiteralPath ([IO.Path]::GetFullPath($ScriptDirectory))
    if (!$directory.PSIsContainer) { $directory = $directory.Directory }
    for ($candidate = $directory; $null -ne $candidate; $candidate = $candidate.Parent) {
        $root = $candidate.FullName
        if ((Test-Path -LiteralPath (Join-Path $root 'pom.xml') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $root 'release-tools/pom.xml') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $root 'chess-core') -PathType Container) -and
            (Test-Path -LiteralPath (Join-Path $root 'release/index.json') -PathType Leaf)) {
            return [pscustomobject]@{
                ProjectRoot = $root
                ModuleRoot = Join-Path $root 'release-tools'
                Scripts = Join-Path $root 'release-tools/scripts'
                Signing = Join-Path $root 'release-tools/.atf-signing'
            }
        }
    }
    throw "No Chess release workspace above $ScriptDirectory"
}

function Assert-ChessExternalDirectory {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$ProjectRoot, [switch]$MustBeFresh)
    $full = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/')
    $systemTemp = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Temp'
    foreach ($excluded in @($root, $systemTemp)) {
        $prefix = $excluded.TrimEnd('\', '/')
        if ($full.Equals($prefix, [StringComparison]::OrdinalIgnoreCase) -or
            $full.StartsWith($prefix + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Release output must stay outside the checkout and Windows Temp.'
        }
    }
    if ($MustBeFresh -and (Test-Path -LiteralPath $full)) { throw 'Use a fresh external output directory.' }
    return $full
}

function Get-ChessReleaseClasspath {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$ModuleRoot)
    $classes = Join-Path $ModuleRoot 'target/classes'
    $yaml = Join-Path $ModuleRoot 'target/lib/snakeyaml-2.2.jar'
    if (!(Test-Path -LiteralPath (Join-Path $classes 'ro/atf/chess/release/ReleaseGate.class') -PathType Leaf) -or
        !(Test-Path -LiteralPath $yaml -PathType Leaf)) {
        throw 'Build the release-tools module before invoking its Java commands.'
    }
    return $classes + [IO.Path]::PathSeparator + $yaml
}
