#Requires -Version 7.0
<#
.SYNOPSIS
    部署验收脚本：照 docs/部署文档.md 第七节「验收清单」逐条实跑。

.DESCRIPTION
    每一项输出 [PASS] / [FAIL] / [SKIP] / [INFO]，末尾给出统计与退出码（有 FAIL 则退出码 1）。
    SKIP 一定带原因 —— 本机跑不了的项（nginx -t、systemd、公网暴露）**不伪装成 PASS**。

    两个容易踩的坑，脚本已按实测行为处理：
      1) 后端未登录时返回的是 **HTTP 200 + body {"code":20001,...}**，不是 HTTP 401。
         所以「反代通不通」必须看响应体，不能只看状态码。
      2) 反代没配好时，`/api/**` 会被 SPA 的 try_files 回退成 **200 + index.html**，
         看起来也是 200。脚本用 Content-Type 把这种情况单独判为 FAIL。

.PARAMETER SiteUrl
    站点根地址（Nginx 对外地址）。本机演练用 http://127.0.0.1:4173。
.PARAMETER BackendUrl
    后端直连地址，用于「prod 收口」这类不能经 Nginx 验的项。
.PARAMETER DistPath
    前端产物目录，用于核对「产物是不是真后端口径（不含 mock）」。
    留空时依次尝试 deploy/../volunteer-cert-portrait-web/dist、/var/www/vcp。
.PARAMETER UploadsSamplePath
    站点上取一张活动图片的相对路径。2026-09-27 起图片存数据库，
    默认取 /api/v1/attachments/1/content（演示数据集里 id 1 一定存在）。
.PARAMETER ProdProfile
    后端确实以 SPRING_PROFILES_ACTIVE=prod 启动时加上：文档页未被拒就判 FAIL。
    不加则该检查输出 SKIP（说明当前不是 prod 口径）。
.PARAMETER ExpectedSignups / ExpectedHours
    看板预期口径（2026-09-27 数据集为 2349 / 9163.0）。为 0 表示只打印不断言。
.PARAMETER PublicBackendUrl
    后端端口的公网地址（如 http://1.2.3.4:8080）。给了就验「8080 不该对外可达」。

.EXAMPLE
    pwsh deploy/verify-deploy.ps1 -SiteUrl http://127.0.0.1:4173
.EXAMPLE
    pwsh deploy/verify-deploy.ps1 -SiteUrl http://vcp.example.edu.cn -ProdProfile -ExpectedSignups 2349 -ExpectedHours 9163.0
#>
[CmdletBinding()]
param(
    [string]$SiteUrl = 'http://127.0.0.1',
    [string]$BackendUrl = 'http://127.0.0.1:8080',
    [string]$DistPath = '',
    [string]$UploadsSamplePath = '/api/v1/attachments/1/content',
    [switch]$ProdProfile,
    [int]$ExpectedSignups = 0,
    [decimal]$ExpectedHours = 0,
    [string]$PublicBackendUrl = ''
)

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$SiteUrl = $SiteUrl.TrimEnd('/')
$BackendUrl = $BackendUrl.TrimEnd('/')

$script:Results = [System.Collections.Generic.List[object]]::new()

function Add-Result {
    param([string]$Name, [ValidateSet('PASS', 'FAIL', 'SKIP', 'INFO')][string]$Status, [string]$Detail = '')
    $color = switch ($Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } 'SKIP' { 'Yellow' } default { 'DarkGray' } }
    Write-Host ("[{0}] {1}" -f $Status, $Name) -ForegroundColor $color
    if ($Detail) { Write-Host ("        {0}" -f $Detail) -ForegroundColor DarkGray }
    $script:Results.Add([pscustomobject]@{ Name = $Name; Status = $Status; Detail = $Detail })
}

function Invoke-Probe {
    <# 统一探测：返回 @{Ok; Status; Content; ContentType; Headers; Length; Error} #>
    param([string]$Url, [string]$Method = 'GET', $Body = $null, $Headers = $null, [int]$TimeoutSec = 20)
    $p = @{ Uri = $Url; Method = $Method; TimeoutSec = $TimeoutSec; SkipHttpErrorCheck = $true; MaximumRedirection = 0 }
    if ($Body) { $p.Body = $Body; $p.ContentType = 'application/json' }
    if ($Headers) { $p.Headers = $Headers }
    try {
        $r = Invoke-WebRequest @p
        return [pscustomobject]@{
            Ok = $true; Status = [int]$r.StatusCode; Content = [string]$r.Content
            ContentType = ($r.Headers['Content-Type'] -join ','); Length = $r.RawContentLength
            CacheControl = ($r.Headers['Cache-Control'] -join ','); Error = ''
        }
    } catch {
        return [pscustomobject]@{
            Ok = $false; Status = 0; Content = ''; ContentType = ''; Length = 0
            CacheControl = ''; Error = $_.Exception.Message
        }
    }
}

function Get-JsonCode {
    param($Probe)
    if (-not $Probe.Ok) { return $null }
    try { return (ConvertFrom-Json $Probe.Content).code } catch { return $null }
}

Write-Host ''
Write-Host '===== 部署验收（deploy/verify-deploy.ps1）=====' -ForegroundColor Cyan
Write-Host ("站点      : {0}" -f $SiteUrl)
Write-Host ("后端直连  : {0}" -f $BackendUrl)
Write-Host ("时间      : {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Host ("pwsh      : {0}" -f $PSVersionTable.PSVersion)
Write-Host ''

# ---------------------------------------------------------------- 环境类
if (-not $SkipSystemChecks) {
    Write-Host '--- 环境 ---' -ForegroundColor Cyan
    $nginx = Get-Command nginx -ErrorAction SilentlyContinue
    if ($nginx) {
        $out = (& nginx -t 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -eq 0) { Add-Result '环境 · nginx -t 语法校验' 'PASS' ($out -replace "`r?`n", ' | ') }
        else { Add-Result '环境 · nginx -t 语法校验' 'FAIL' ($out -replace "`r?`n", ' | ') }
    } else {
        Add-Result '环境 · nginx -t 语法校验' 'SKIP' '本机没有 nginx（部署机执行本脚本时会自动跑）—— 这是「未实测」项，别当成通过'
    }

    $systemctl = Get-Command systemctl -ErrorAction SilentlyContinue
    if ($systemctl) {
        $state = (& systemctl is-active vcp 2>&1 | Out-String).Trim()
        if ($state -eq 'active') { Add-Result '环境 · systemd 单元 vcp 在跑' 'PASS' "systemctl is-active vcp → $state" }
        else { Add-Result '环境 · systemd 单元 vcp 在跑' 'FAIL' "systemctl is-active vcp → $state（未装单元或启动失败）" }
    } else {
        Add-Result '环境 · systemd 单元 vcp 在跑' 'SKIP' '本机不是 systemd 环境（Windows 开发机）—— 真机才可验'
    }
}

# ---------------------------------------------------------------- 产物口径
Write-Host '--- 产物 ---' -ForegroundColor Cyan
if (-not $DistPath) {
    foreach ($cand in @((Join-Path $PSScriptRoot '../volunteer-cert-portrait-web/dist'), '/var/www/vcp')) {
        if (Test-Path $cand) { $DistPath = $cand; break }
    }
}
if ($DistPath -and (Test-Path $DistPath)) {
    $mockChunk = Get-ChildItem (Join-Path $DistPath 'assets') -Filter 'mock-*.js' -ErrorAction SilentlyContinue
    $hits = @()
    Get-ChildItem $DistPath -Recurse -File -Include *.js, *.html, *.css -ErrorAction SilentlyContinue | ForEach-Object {
        if (Select-String -Path $_.FullName -Pattern 'vcp_mock_extra_users' -SimpleMatch -Quiet) { $hits += $_.Name }
    }
    if ($mockChunk -or $hits.Count -gt 0) {
        Add-Result '产物 · 不含 mock（真后端口径）' 'FAIL' ("发现 mock 残留：chunk={0} 命中文件={1} —— 说明构建时 VITE_USE_MOCK 不是 false" -f ($mockChunk.Name -join ','), ($hits -join ','))
    } else {
        Add-Result '产物 · 不含 mock（真后端口径）' 'PASS' ("{0}：无 mock-*.js，grep vcp_mock_extra_users 无命中" -f $DistPath)
    }
} else {
    Add-Result '产物 · 不含 mock（真后端口径）' 'SKIP' '没找到产物目录（用 -DistPath 指定，如服务器上的 /var/www/vcp）'
}

# ---------------------------------------------------------------- 前端
Write-Host '--- 前端 ---' -ForegroundColor Cyan
$homeProbe = Invoke-Probe "$SiteUrl/"
if (-not $homeProbe.Ok) {
    Add-Result '前端 · 首页可访问' 'FAIL' ("GET {0}/ 失败：{1}" -f $SiteUrl, $homeProbe.Error)
} elseif ($homeProbe.Status -ne 200) {
    Add-Result '前端 · 首页可访问' 'FAIL' ("HTTP {0}" -f $homeProbe.Status)
} elseif ($homeProbe.Content -notmatch '<div id="app">') {
    Add-Result '前端 · 首页可访问' 'FAIL' '返回 200 但不是前端 index.html（没有 <div id="app">）'
} else {
    Add-Result '前端 · 首页可访问' 'PASS' ('HTTP 200，{0} 字节，含 <div id="app">' -f $homeProbe.Length)
}

# index.html 不缓存：发版后拿旧 index 请求已删 chunk 会白屏
# 注意 `/` 与深层路由都是经 try_files 内部重定向到 /index.html，两条都要查
if ($homeProbe.Ok -and $homeProbe.Status -eq 200) {
    $deepHtml = Invoke-Probe "$SiteUrl/student/profile"
    $ccRoot = $homeProbe.CacheControl
    $ccDeep = if ($deepHtml.Ok) { $deepHtml.CacheControl } else { '(深层路由探测失败)' }
    if ($ccRoot -match 'no-cache|no-store' -and $ccDeep -match 'no-cache|no-store') {
        Add-Result '前端 · index.html 不缓存' 'PASS' ("Cache-Control  / →「{0}」  深层路由 →「{1}」" -f $ccRoot, $ccDeep)
    } else {
        Add-Result '前端 · index.html 不缓存' 'FAIL' ("Cache-Control  / →「{0}」  深层路由 →「{1}」；缺 no-cache 时发版后可能白屏（nginx.conf 的 location = /index.html）" -f $ccRoot, $ccDeep)
    }
}

# 深层路由回退
if ($homeProbe.Ok -and $homeProbe.Status -eq 200) {
    $entry = [regex]::Match($homeProbe.Content, 'src="(/assets/[^"]+\.js)"').Groups[1].Value
    $deepPaths = @('/student/profile', '/student/activities/389', '/admin/colleges', '/org/activities')
    $bad = @()
    foreach ($p in $deepPaths) {
        $r = Invoke-Probe "$SiteUrl$p"
        if (-not $r.Ok) { $bad += "$p(连不上)"; continue }
        if ($r.Status -ne 200) { $bad += "$p(HTTP $($r.Status))"; continue }
        if ($r.Content -notmatch '<div id="app">') { $bad += "$p(不是 index.html)"; continue }
        if ($entry -and $r.Content -notmatch [regex]::Escape($entry)) { $bad += "$p(入口 script 与首页不一致)"; continue }
    }
    if ($bad.Count -eq 0) {
        Add-Result '前端 · 深层路由刷新不 404' 'PASS' ("{0} 全部 200 且内容 = index.html（入口 {1}）" -f ($deepPaths -join '、'), $entry)
    } else {
        Add-Result '前端 · 深层路由刷新不 404' 'FAIL' ("{0} —— Nginx 少了 try_files \$uri \$uri/ /index.html" -f ($bad -join '；'))
    }

    # 入口资源逐个可取
    $assets = [regex]::Matches($homeProbe.Content, '(?:src|href)="(/assets/[^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    $badAssets = @()
    foreach ($a in $assets) {
        $r = Invoke-Probe "$SiteUrl$a"
        if (-not $r.Ok) { $badAssets += "$a(连不上)"; continue }
        if ($r.Status -ne 200) { $badAssets += "$a(HTTP $($r.Status))"; continue }
        if ($a -like '*.js' -and $r.ContentType -notmatch 'javascript') { $badAssets += "$a(Content-Type=$($r.ContentType))" }
    }
    if ($assets.Count -eq 0) {
        Add-Result '前端 · 入口静态资源可取' 'FAIL' 'index.html 里没解析出 /assets/* 引用（产物异常）'
    } elseif ($badAssets.Count -eq 0) {
        Add-Result '前端 · 入口静态资源可取' 'PASS' ("{0} 个入口资源全部 200 且 MIME 正确" -f $assets.Count)
    } else {
        Add-Result '前端 · 入口静态资源可取' 'FAIL' ($badAssets -join '；')
    }

    # 带 hash 产物应长缓存（不达标只是性能问题，不判 FAIL）
    if ($assets.Count -gt 0) {
        $one = Invoke-Probe "$SiteUrl$($assets[0])"
        Add-Result '前端 · /assets/ 缓存策略' 'INFO' ("Cache-Control: {0}（nginx.conf 期望 public, immutable；vite preview 会给 no-cache，属替身差异）" -f $one.CacheControl)
    }
}

# ---------------------------------------------------------------- 反代 + 后端
Write-Host '--- 反代与后端 ---' -ForegroundColor Cyan
$cat = Invoke-Probe "$SiteUrl/api/v1/categories"
if (-not $cat.Ok) {
    Add-Result '反代 · /api 到达后端' 'FAIL' ("GET {0}/api/v1/categories 失败：{1} —— 后端没起或 proxy_pass 配错。若此刻后端正被另一进程重建（如 mvn package 必须先停实例），这不算部署缺陷，等它起来后复跑本脚本即可；部署验收场景下后端必须起着。" -f $SiteUrl, $cat.Error)
} elseif ($cat.ContentType -match 'html') {
    Add-Result '反代 · /api 到达后端' 'FAIL' ("返回 200 + text/html：/api 没被反代，被 try_files 回退成了 index.html（检查 nginx.conf 的 location /api/ 是否在、proxy_pass 结尾不能带斜杠）")
} else {
    $code = Get-JsonCode $cat
    if ($code -eq 20001 -or $code -eq 401) {
        Add-Result '反代 · /api 到达后端' 'PASS' ("HTTP {0} + body code={1}（未登录口径，说明请求确实到了后端）" -f $cat.Status, $code)
    } elseif ($null -eq $code) {
        Add-Result '反代 · /api 到达后端' 'FAIL' ("响应不是后端统一格式：HTTP {0} CT={1} body={2}" -f $cat.Status, $cat.ContentType, $cat.Content.Substring(0, [Math]::Min(120, $cat.Content.Length)))
    } else {
        Add-Result '反代 · /api 到达后端' 'PASS' ("HTTP {0} + body code={1}（非预期但确实是后端响应）" -f $cat.Status, $code)
    }
}

$token = $null
$login = Invoke-Probe "$SiteUrl/api/v1/auth/login" -Method POST -Body '{"username":"admin","password":"123456"}'
if (-not $login.Ok) {
    Add-Result '后端 · 登录链路（连库）' 'FAIL' ("POST /api/v1/auth/login 失败：{0}（后端没起？若正被重建则等它起来后复跑；部署验收时后端必须起着）" -f $login.Error)
} else {
    try { $lj = ConvertFrom-Json $login.Content } catch { $lj = $null }
    if ($lj -and $lj.code -eq 0 -and $lj.data.token) {
        $token = $lj.data.token
        Add-Result '后端 · 登录链路（连库）' 'PASS' ("admin 登录 code=0，token 已下发，角色 {0}" -f $lj.data.user.role)
    } else {
        Add-Result '后端 · 登录链路（连库）' 'FAIL' ("登录未成功：HTTP {0} body={1}（口令被改过？库没起？凭证文件不在？）" -f $login.Status, $login.Content.Substring(0, [Math]::Min(160, $login.Content.Length)))
    }
}

if ($token) {
    $dash = Invoke-Probe "$SiteUrl/api/v1/analytics/dashboard" -Headers @{ Authorization = "Bearer $token" }
    try { $dj = ConvertFrom-Json $dash.Content } catch { $dj = $null }
    if ($dj -and $dj.code -eq 0) {
        $stat = @{}
        foreach ($s in $dj.data.stats) { $stat[$s.key] = $s.value }
        $detail = ("活动 {0} / 报名 {1} / 时长 {2} 小时（mock 口径是 12,480 / 86,420，对不上说明产物或反代有问题）" -f $stat['activities'], $stat['enrolled'], $stat['hours'])
        $mismatch = @()
        if ($ExpectedSignups -gt 0 -and [int]$stat['enrolled'] -ne $ExpectedSignups) { $mismatch += ("报名 期望 {0} 实际 {1}" -f $ExpectedSignups, $stat['enrolled']) }
        if ($ExpectedHours -gt 0 -and [decimal]$stat['hours'] -ne $ExpectedHours) { $mismatch += ("时长 期望 {0} 实际 {1}" -f $ExpectedHours, $stat['hours']) }
        if ($mismatch.Count -gt 0) { Add-Result '后端 · 看板数字来自真库' 'FAIL' ("{0}；{1}" -f $detail, ($mismatch -join '；')) }
        else { Add-Result '后端 · 看板数字来自真库' 'PASS' $detail }
    } else {
        Add-Result '后端 · 看板数字来自真库' 'FAIL' ("/api/v1/analytics/dashboard 未返回 code=0：{0}" -f $dash.Content.Substring(0, [Math]::Min(160, $dash.Content.Length)))
    }
}

# ---------------------------------------------------------------- 活动图片（数据库存储）
Write-Host '--- 活动图片（存数据库）---' -ForegroundColor Cyan
if (-not $UploadsSamplePath) {
    Add-Result '图片 · 内容接口可取' 'SKIP' '未指定样例路径'
} else {
    $img = Invoke-Probe "$SiteUrl$UploadsSamplePath"
    if (-not $img.Ok) {
        Add-Result '图片 · 内容接口可取' 'FAIL' ("GET {0}{1} 失败：{2}。图片存 attachment.file_data，经后端 /api/v1/attachments/{{id}}/content 输出，所以要经反代打到后端；后端没起时会失败。" -f $SiteUrl, $UploadsSamplePath, $img.Error)
    } elseif ($img.Status -eq 200 -and $img.ContentType -match '^image/') {
        Add-Result '图片 · 内容接口可取' 'PASS' ("HTTP 200，{0}，{1:N0} 字节，Cache-Control「{2}」（期望 image/* + 长缓存 + ETag）" -f $img.ContentType, $img.Length, $img.CacheControl)
    } elseif ($img.Status -eq 404) {
        Add-Result '图片 · 内容接口可取' 'FAIL' ("HTTP 404 —— 该 id 的 attachment 行不存在或 file_data 为空（演示数据集跑完 12 + 13 后 id 1~40 应全有值）")
    } else {
        Add-Result '图片 · 内容接口可取' 'FAIL' ("HTTP {0} CT={1}（用 -UploadsSamplePath 换成库里真实存在的图片，如 /api/v1/attachments/12/content）" -f $img.Status, $img.ContentType)
    }
}

# ---------------------------------------------------------------- prod 收口
Write-Host '--- 上线收口 ---' -ForegroundColor Cyan
$doc = Invoke-Probe "$BackendUrl/doc.html"
$api = Invoke-Probe "$BackendUrl/v3/api-docs"
$docOpen = $doc.Ok -and $doc.Status -eq 200 -and $doc.ContentType -match 'html'
$apiOpen = $api.Ok -and $api.Status -eq 200 -and $api.Content -match '"openapi"'
if ($docOpen -or $apiOpen) {
    $detail = ("后端直连 {0}：/doc.html HTTP {1}、/v3/api-docs HTTP {2} —— 文档路径仍免登录，说明后端**没有激活 prod profile**" -f $BackendUrl, $doc.Status, $api.Status)
    if ($ProdProfile) { Add-Result '收口 · prod 下文档页被拒' 'FAIL' ($detail + '（已加 -ProdProfile，故判 FAIL）') }
    else { Add-Result '收口 · prod 下文档页被拒' 'SKIP' ($detail + '（未加 -ProdProfile，按开发口径 SKIP；真机 prod 下这两项应返回 401/403）') }
} elseif (-not $doc.Ok -and -not $api.Ok) {
    Add-Result '收口 · prod 下文档页被拒' 'SKIP' ("后端直连不可达（{0}），无法判定" -f $doc.Error)
} else {
    Add-Result '收口 · prod 下文档页被拒' 'PASS' ("/doc.html HTTP {0}、/v3/api-docs HTTP {1}，均被拒（prod 收口生效）" -f $doc.Status, $api.Status)
}

if ($PublicBackendUrl) {
    $pub = Invoke-Probe "$($PublicBackendUrl.TrimEnd('/'))/api/v1/categories" -TimeoutSec 8
    if ($pub.Ok) { Add-Result '收口 · 后端端口未对外暴露' 'FAIL' ("从本机可以连上 {0} —— 8080 应被防火墙/安全组挡掉，或加 server.address=127.0.0.1" -f $PublicBackendUrl) }
    else { Add-Result '收口 · 后端端口未对外暴露' 'PASS' ("连不上（预期）：{0}" -f $pub.Error) }
} else {
    Add-Result '收口 · 后端端口未对外暴露' 'SKIP' '没给 -PublicBackendUrl（要验这条得从外网打后端端口，本机自测无意义）'
}

# ---------------------------------------------------------------- 汇总
$pass = @($script:Results | Where-Object Status -eq 'PASS').Count
$fail = @($script:Results | Where-Object Status -eq 'FAIL').Count
$skip = @($script:Results | Where-Object Status -eq 'SKIP').Count
$info = @($script:Results | Where-Object Status -eq 'INFO').Count
Write-Host ''
Write-Host ("===== 汇总：PASS {0} / FAIL {1} / SKIP {2} / INFO {3} =====" -f $pass, $fail, $skip, $info) -ForegroundColor $(if ($fail) { 'Red' } else { 'Green' })
if ($fail) {
    Write-Host '未通过项：' -ForegroundColor Red
    $script:Results | Where-Object Status -eq 'FAIL' | ForEach-Object { Write-Host ("  · {0} —— {1}" -f $_.Name, $_.Detail) -ForegroundColor Red }
}
if ($skip) {
    Write-Host '跳过项（本机测不了，别当成通过）：' -ForegroundColor Yellow
    $script:Results | Where-Object Status -eq 'SKIP' | ForEach-Object { Write-Host ("  · {0} —— {1}" -f $_.Name, $_.Detail) -ForegroundColor Yellow }
}
Write-Host ''
exit $(if ($fail) { 1 } else { 0 })
