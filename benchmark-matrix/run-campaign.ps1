# Runs several blocks of benchmark cells one after another with the run-matrix driver, and continues where it stopped
# when started again, e.g., after a night with the laptop closed. Run it from the repository root, after
# mvn -P benchmark-bundle package, or from the folder that holds the core and versioning bundles:
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File benchmark-matrix\run-campaign.ps1
#
# The database is the one that the BENCHMARK_DATABASE environment variable names (AIVEN or POSTGRESQL), otherwise the
# database entry of ~/.dto2entity/benchmark.properties, the default of this machine, otherwise the local PostgreSQL
# server, as for the tests and the benchmark forks. The results go to results\<computer>-<database> unless Results
# names another folder, which records its database and refuses another one, so that a resumed campaign never mixes
# the forks of two databases.
# A block is the name of a cell file in benchmark-matrix\cells, or the path of any cell file.
# At every start, it dumps the version and the settings of the PostgreSQL server into the results folder (the local
# server on DbHost, or the Aiven service) and checks the cells of every pending block.
# A block that stops with forks left (Ctrl+C, run-matrix stop, a deadline, or failures) ends the campaign window,
# so the next block never starts before the previous one is complete.
# A fork during which the machine slept or hibernated is discarded, since a measurement iteration spans the sleep,
# and the driver repeats it at the next start: Windows logs every sleep (Kernel-Power event 42) and wake (107).
param(
    [string]$Results = '',
    [string[]]$Blocks = @('rq2-writes', 'rq1-rest', 'typed-msjson', 'w7-sf10', 'w7-concurrency'),
    [string]$DbHost = 'localhost',
    [string[]]$DriverArgs = @()
)

function Get-BenchmarkDatabase {
    if ($env:BENCHMARK_DATABASE) { return $env:BENCHMARK_DATABASE.Trim().ToUpper() }
    $file = Join-Path $env:USERPROFILE '.dto2entity\benchmark.properties'
    if (Test-Path $file) {
        foreach ($line in Get-Content $file) {
            if ($line -match '^\s*database\s*=\s*(\S+)') { return $Matches[1].ToUpper() }
        }
    }
    return 'POSTGRESQL'
}

$database = Get-BenchmarkDatabase
if (-not $Results) {
    $Results = Join-Path 'results' ('{0}-{1}' -f $env:COMPUTERNAME, $database).ToLower()
}
$Host.UI.RawUI.WindowTitle = "Benchmark campaign: $database, $Results (Ctrl+C stops it; the same command continues it)"
$matrix = $PSScriptRoot
$driver = Join-Path $matrix 'RunMatrix.java'
New-Item -ItemType Directory -Force $Results | Out-Null
$marker = Join-Path $Results 'database.txt'
if (Test-Path $marker) {
    $recorded = (Get-Content $marker -Raw).Trim()
    if ($recorded -ne $database) {
        Write-Host "The results in $Results come from the $recorded database, not from ${database}: set BENCHMARK_DATABASE=$recorded to continue them, or choose another Results folder."
        exit 1
    }
} else {
    Set-Content -Path $marker -Value $database -Encoding ascii
}

function Write-Log([string]$message) {
    $line = "{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $message
    Write-Host $line
    Add-Content -Path (Join-Path $Results 'campaign.log') -Value $line
}

function Get-CellsFile([string]$block) {
    if ($block -like '*.txt') { return $block }
    return Join-Path $matrix "cells\$block.txt"
}

function Get-BlockName([string]$block) {
    return [IO.Path]::GetFileNameWithoutExtension((Get-CellsFile $block))
}

function Get-ForksLeft([string]$block) {
    $status = & java $driver status (Get-CellsFile $block) --results $Results 2>&1 | Out-String
    if ($status -match '(\d+) forks left') { return [int]$Matches[1] }
    if ($status -match 'has not started yet: \d+ cells, (\d+) forks') { return [int]$Matches[1] }
    throw "Cannot read the status of block ${block}: $status"
}

# Discards the results of the forks of a block that ran while the machine slept, so that the driver repeats them
function Remove-SleptForks([string]$block, [datetime]$since) {
    $blockDir = Join-Path $Results (Get-BlockName $block)
    $forksCsv = Join-Path $blockDir 'forks.csv'
    if (-not (Test-Path $forksCsv)) { return }
    $sleeps = @(Get-WinEvent -FilterHashtable @{
        LogName = 'System'; ProviderName = 'Microsoft-Windows-Kernel-Power'; Id = 42, 107; StartTime = $since
    } -ErrorAction SilentlyContinue | ForEach-Object { $_.TimeCreated })
    if ($sleeps.Count -eq 0) { return }
    foreach ($fork in Import-Csv $forksCsv) {
        if ($fork.status -ne 'ok') { continue }
        $start = [datetime]$fork.start
        $end = [datetime]$fork.end
        if (-not ($sleeps | Where-Object { $_ -ge $start -and $_ -le $end })) { continue }
        $json = Join-Path $blockDir ("round-{0:D2}\{1}.json" -f [int]$fork.round, $fork.cell)
        if (Test-Path $json) {
            $slept = $json -replace '\.json$', ('.slept-{0:yyyyMMdd-HHmmss}.json' -f (Get-Date))
            Move-Item $json $slept
            Write-Log "Discarded $(Get-BlockName $block) round $($fork.round) $($fork.cell): the machine slept during the fork, which runs again"
        }
    }
}

# The connection of the settings dump: the Aiven service, with the settings of AivenDataSourceProvider, or DbHost
function Get-ServerConnection {
    if ($database -eq 'AIVEN') {
        $settings = @{}
        $file = Join-Path $env:USERPROFILE '.dto2entity\aiven.properties'
        if (Test-Path $file) {
            foreach ($line in Get-Content $file) {
                if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $settings[$Matches[1]] = $Matches[2] }
            }
        }
        foreach ($key in 'host', 'port', 'user', 'password') {
            $value = [Environment]::GetEnvironmentVariable("AIVEN_$($key.ToUpper())")
            if ($value) { $settings[$key] = $value }
        }
        return @{
            Name = "Aiven ($($settings.host))"
            Info = "host=$($settings.host) port=$($settings.port) user=$($settings.user) dbname=dto2entity sslmode=require"
            Password = $settings.password
        }
    }
    $user = if ($env:PGUSER) { $env:PGUSER } else { 'postgres' }
    return @{
        Name = "PostgreSQL on $DbHost"
        Info = "host=$DbHost port=5432 user=$user dbname=dto2entity"
        Password = if ($env:PGPASSWORD) { $env:PGPASSWORD } else { 'admin' }
    }
}

$windowStart = Get-Date
$server = Get-ServerConnection
Write-Log "Campaign window started: blocks $($Blocks -join ', '), database $($server.Name), results in $Results"

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = (Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1).FullName
}
if ($psql) {
    $dump = Join-Path $Results ("pg-settings-{0:yyyyMMdd-HHmmss}.txt" -f (Get-Date))
    $env:PGPASSWORD = $server.Password
    & $psql -X -d $server.Info -c "SELECT version(), now() AS dumped_at" -c "SELECT name, setting, unit, source FROM pg_settings ORDER BY name" 2>&1 |
        Out-File -Encoding utf8 $dump
    Write-Log "PostgreSQL settings of $($server.Name): $dump"
} else {
    Write-Log "psql not found: the fork logs still record the server version and the key settings"
}

foreach ($block in $Blocks) {
    if ((Get-ForksLeft $block) -eq 0) { continue }
    $check = & java $driver check (Get-CellsFile $block) 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        Write-Log "Block $block does not match the bundles: $($check.Trim())"
        exit 1
    }
}

foreach ($block in $Blocks) {
    $name = Get-BlockName $block
    $left = Get-ForksLeft $block
    if ($left -eq 0) {
        Write-Log "Block $name is complete"
        continue
    }
    Write-Log "Block ${name}: $left forks left"
    & java $driver run (Get-CellsFile $block) --results $Results --db-host $DbHost @DriverArgs
    $exitCode = $LASTEXITCODE
    Remove-SleptForks $block $windowStart
    $left = Get-ForksLeft $block
    if ($exitCode -ne 0 -or $left -gt 0) {
        Write-Log "Campaign window ended in block $name (driver exit code $exitCode, $left forks left); start the same command again to continue"
        exit $exitCode
    }
    Write-Log "Block $name is complete"
}
Write-Log "Campaign complete: every block is done"
