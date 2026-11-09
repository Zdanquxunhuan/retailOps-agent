param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]{10,32}$')]
    [string]$OrderId,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Username = 'operator',
    [string]$OutputFile
)

$ErrorActionPreference = 'Stop'
$uri = [Uri]$BaseUrl
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http', 'https')) {
    throw 'BaseUrl must be an absolute HTTP(S) URL.'
}
if ($uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw 'Do not place credentials, query parameters or fragments in BaseUrl.'
}
if ($uri.Scheme -eq 'http' -and -not $uri.IsLoopback) {
    throw 'Remote Basic authentication requires HTTPS.'
}
if ($OutputFile -and (Test-Path -LiteralPath $OutputFile)) {
    throw 'OutputFile already exists; choose a new path.'
}

$credential = Get-Credential -UserName $Username -Message 'RetailOps operator credentials'
if (-not $credential) { throw 'Credentials were not provided.' }
try {
    $pair = '{0}:{1}' -f $credential.UserName, $credential.GetNetworkCredential().Password
    $headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair)) }
    $body = @{ message = "Diagnose Meituan sale order $OrderId" } | ConvertTo-Json
    # Real read-only business requests and model usage. This is not a test or a replay command.
    $report = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + '/api/v1/diagnoses/meituan-sales') `
        -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 180 -MaximumRedirection 0
    $json = $report | ConvertTo-Json -Depth 20
    $json
    if ($OutputFile) {
        $fullPath = [IO.Path]::GetFullPath($OutputFile)
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($fullPath)) | Out-Null
        $stream = [IO.File]::Open($fullPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
        try {
            $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes($json)
            $stream.Write($bytes, 0, $bytes.Length)
        } finally { $stream.Dispose() }
        Write-Host ('Report saved: ' + $fullPath)
    }
} finally {
    Remove-Variable pair, headers, credential -ErrorAction SilentlyContinue
}
