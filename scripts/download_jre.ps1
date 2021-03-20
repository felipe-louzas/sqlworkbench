# $url = "https://api.adoptopenjdk.net/v3/binary/latest/16/ga/windows/x64/jre/hotspot/normal/adoptopenjdk?project=jdk"
$url = "https://www.sql-workbench.eu/jre/jre_win64.zip"

$filename = "OpenJDK.zip";

Write-Host "Downloading $filename (approx. 50MB)"

[Net.ServicePointManager]::SecurityProtocol = "tls12, tls11, tls"
Invoke-WebRequest -Uri $url -OutFile $filename

Write-Host "Extracting $filename to $PSScriptRoot"
Add-Type –assembly System.IO.Compression.Filesystem
[io.compression.zipfile]::ExtractToDirectory($filename, "$PSScriptRoot\jre")

