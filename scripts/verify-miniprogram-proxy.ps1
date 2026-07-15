$ErrorActionPreference = 'Stop'
$files = @(
  'deploy/nginx/brainos.conf',
  'deploy/nginx/templates/brainos.conf.template'
)
foreach ($file in $files) {
  $content = Get-Content -Raw $file
  if ($content -match 'chunked_transfer_encoding\s+off') {
    throw "$file disables chunked transfer required by WeChat onChunkReceived"
  }
  if ($content -notmatch 'proxy_buffering\s+off') {
    throw "$file does not disable SSE proxy buffering"
  }
  if ($content -notmatch 'proxy_cache\s+off') {
    throw "$file does not disable SSE proxy caching"
  }
}
Write-Output 'Miniprogram SSE proxy configuration is valid.'
