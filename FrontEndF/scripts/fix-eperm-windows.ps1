param(
  [string]$ProjectRoot = "."
)

$ErrorActionPreference = "Stop"

Write-Host "== EPERM remediation (Windows) ==" -ForegroundColor Cyan
$resolvedRoot = Resolve-Path $ProjectRoot
Set-Location $resolvedRoot

Write-Host "Project root: $resolvedRoot"
Write-Host ""

if ($resolvedRoot.Path -like "*OneDrive*") {
  Write-Warning "Workspace is under OneDrive. This is a frequent root cause of esbuild spawn EPERM."
  Write-Warning "Recommended durable fix: move project to a non-synced path (example: C:\dev\PFECODING)."
}

Write-Host "1) Toolchain versions"
node -v
npm -v

Write-Host ""
Write-Host "2) Stop Node processes that may lock cache/node_modules"
Get-Process -Name node -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "3) Clean Angular and npm caches"
if (Test-Path ".angular\cache") {
  Remove-Item -Recurse -Force ".angular\cache"
}
npx ng cache clean | Out-Host
npm cache verify | Out-Host

Write-Host ""
Write-Host "4) Reinstall dependencies"
if (Test-Path "node_modules") {
  Remove-Item -Recurse -Force "node_modules"
}
if (Test-Path "package-lock.json") {
  Remove-Item -Force "package-lock.json"
}
npm install | Out-Host

Write-Host ""
Write-Host "5) Quick esbuild spawn diagnosis"
node -e "require('esbuild').transform('const x: number = 1',{loader:'ts'}).then(()=>console.log('esbuild ok')).catch(e=>{console.error(e);process.exit(1);})"

Write-Host ""
Write-Host "6) Build"
npm run build

Write-Host ""
Write-Host "Done." -ForegroundColor Green
