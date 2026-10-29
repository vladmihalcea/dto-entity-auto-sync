# Logs the CPU use of every busy process while the run-matrix driver runs, so that the results show whether anything
# besides the benchmark forks and PostgreSQL ran on the machine during a run window. The driver starts it on Windows,
# at the start of every window, and it stops on its own when the driver exits.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File process-log.ps1 -OutFile <csv file> [-ParentPid <pid>]
#
# Every interval, it writes one row per process that used at least MinPercent of one logical processor, and a _total
# row with the sum over all processes, as percentages of one logical processor and of all logical processors.
# It reads the Process performance data of Windows in one call per interval, which covers the processes of services,
# such as PostgreSQL, whose CPU time an unelevated process cannot read otherwise.
param(
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$ParentPid = 0,
    [int]$IntervalSeconds = 10,
    [double]$MinPercent = 1
)

$logical = [Environment]::ProcessorCount
$category = New-Object System.Diagnostics.PerformanceCounterCategory 'Process'
"time,process,pid,percent_of_one_cpu,percent_of_all_cpus" | Set-Content -Encoding ascii $OutFile

# Maps every process ID to its name and its % Processor Time sample
function Read-Samples {
    $data = $category.ReadCategory()
    $cpu = $data['% Processor Time']
    $ids = $data['ID Process']
    $samples = @{}
    foreach ($instance in $cpu.Values) {
        $name = $instance.InstanceName
        if ($name -eq '_Total' -or $name -eq 'Idle') { continue }
        $samples[[int]$ids[$name].RawValue] = @(($name -replace '#\d+$', ''), $instance.Sample)
    }
    return $samples
}

$previous = Read-Samples
while ($ParentPid -eq 0 -or (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) {
    Start-Sleep -Seconds $IntervalSeconds
    $current = Read-Samples
    $time = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    $total = 0.0
    $rows = foreach ($id in $current.Keys) {
        if (-not $previous.ContainsKey($id) -or $previous[$id][0] -ne $current[$id][0]) { continue }
        $percent = [System.Diagnostics.CounterSample]::Calculate($previous[$id][1], $current[$id][1])
        $total += $percent
        if ($percent -ge $MinPercent) {
            "{0},{1},{2},{3:F1},{4:F2}" -f $time, $current[$id][0], $id, $percent, ($percent / $logical)
        }
    }
    $rows += "{0},_total,,{1:F1},{2:F2}" -f $time, $total, ($total / $logical)
    $rows | Add-Content -Encoding ascii $OutFile
    $previous = $current
}
