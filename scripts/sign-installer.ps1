<#
  Signs the installer with a PERSONAL certificate named "Dev.Badr".

  Important to know:
    - The private key is created on THIS computer and cannot be exported. Never share it.
    - A personal (self-signed) certificate is only trusted on computers where it is installed.
      On this computer the script installs it for you (Windows asks you to confirm).
      On other computers Windows still shows "Unknown publisher" unless they install dist\Dev.Badr.cer.
      Removing the warning for everybody needs a certificate from a recognised authority (paid, or free
      programs for some open-source projects).
    - Nothing is sent anywhere unless you add -Timestamp (that contacts the timestamp server you give).

  Usage:  powershell -ExecutionPolicy Bypass -File scripts\sign-installer.ps1
#>
param(
    [string]$File,
    [string]$TimestampServer = ''
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $File) {
    $found = Get-ChildItem dist -Filter 'PocketDrop-*.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $found) { throw 'No installer found in dist\. Run scripts\build-installer.ps1 first.' }
    $File = $found.FullName
}

$subject = 'CN=Dev.Badr'
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert |
        Where-Object { $_.Subject -eq $subject -and $_.NotAfter -gt (Get-Date).AddDays(30) } |
        Select-Object -First 1

if (-not $cert) {
    Write-Host "Creating a code-signing certificate for $subject (valid 3 years)..."
    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject $subject `
            -KeyAlgorithm RSA -KeyLength 3072 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable `
            -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddYears(3)
}

New-Item -ItemType Directory -Force dist | Out-Null
$cer = 'dist\Dev.Badr.cer'
Export-Certificate -Cert $cert -FilePath $cer | Out-Null   # public part only

Write-Host 'Installing the public certificate on this computer. Windows will ask you to confirm: choose Yes.'
Import-Certificate -FilePath $cer -CertStoreLocation Cert:\CurrentUser\Root | Out-Null
Import-Certificate -FilePath $cer -CertStoreLocation Cert:\CurrentUser\TrustedPublisher | Out-Null

if ($TimestampServer) {
    $sig = Set-AuthenticodeSignature -FilePath $File -Certificate $cert -HashAlgorithm SHA256 -TimestampServer $TimestampServer
} else {
    $sig = Set-AuthenticodeSignature -FilePath $File -Certificate $cert -HashAlgorithm SHA256
}
Write-Host ("Signature status: " + $sig.Status)
if ($sig.Status -ne 'Valid') { Write-Warning $sig.StatusMessage }
Write-Host "Signed: $File"
Write-Host "Public certificate for other computers (optional): $((Resolve-Path $cer).Path)"
