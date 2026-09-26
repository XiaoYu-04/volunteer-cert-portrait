<#
.SYNOPSIS
    一条命令启动后端：唯一需要提供的是**数据库口令**。

.DESCRIPTION
    用途（2026-09-26 起）：演示库口令已明文写在 application.yml 里（`123456`），
    平时**不需要**本脚本 —— clone 后直接 `java -jar` 就能连库。
    它用于**临时换口令 / 换账号**的场景：交互输入，只注入本次进程的环境变量，
    不写任何文件、不进 shell 历史（输入不回显）。

    顺带解决一个经典坑：脚本会把工作目录切到 volunteer-cert-portrait-server/ ——
    application.yml 的三条 import 路径与 uploads/ 都是相对工作目录的，
    从仓库根目录启动会一个都读不到。

    覆盖优先级：环境变量 VCP_DB_PASSWORD > application-local.yml > application.yml 默认值。

.PARAMETER Password
    数据库口令。不传时先用已有的环境变量 VCP_DB_PASSWORD，再交互式输入（不回显）。
    直接写在命令行会进 shell 历史，只建议自动化场景使用。

.PARAMETER Username
    数据库用户名，默认 postgres。

.PARAMETER Jar
    jar 路径，默认 vcp-boot/target/vcp-boot-1.0.0.jar。

.EXAMPLE
    .\start-backend.ps1

.EXAMPLE
    $env:VCP_DB_PASSWORD = 'xxx'; .\start-backend.ps1
#>
[CmdletBinding()]
param(
  [string]$Password,
  [string]$Username = 'postgres',
  [string]$Jar = 'vcp-boot/target/vcp-boot-1.0.0.jar'
)

$ErrorActionPreference = 'Stop'

# 以脚本所在目录为工作目录，别让调用方关心自己在哪
$serverRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $serverRoot

if (-not (Test-Path -LiteralPath $Jar)) {
  Write-Host "找不到 $Jar —— 先构建：mvn package -DskipTests" -ForegroundColor Red
  exit 1
}

if (-not $Password) {
  if ($env:VCP_DB_PASSWORD) {
    Write-Host '使用已有的环境变量 VCP_DB_PASSWORD。' -ForegroundColor DarkGray
    $Password = $env:VCP_DB_PASSWORD
  } else {
    $secure = Read-Host -Prompt '请输入数据库口令（输入时不回显）' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $Password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
  }
}

if (-not $Password) {
  Write-Host '口令为空，已取消。' -ForegroundColor Red
  exit 1
}

$env:VCP_DB_USERNAME = $Username
$env:VCP_DB_PASSWORD = $Password

Write-Host "工作目录：$serverRoot"
Write-Host '启动后端：http://127.0.0.1:8080 （口令只注入本次进程，未写入任何文件）'

java -jar $Jar
exit $LASTEXITCODE
