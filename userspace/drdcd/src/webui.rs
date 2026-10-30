// Root-side web console, served by drdcd on loopback.
//
// This deliberately lives in the daemon and not in the manager APK: root is carried by the
// boot image and drdc.ko, so uninstalling the APK leaves drdcd (and this page) working.
// Bound to 127.0.0.1 only; nothing is exposed to the network.
//
// Routes:
//   GET /              single-page console
//   GET /api/status    device / kernel / selinux / manager presence
//   GET /api/modules   installed modules parsed from /data/adb/modules/*/module.prop
//   GET /api/grants    allowlist entries read from /data/adb/drdc/allowlist (if present)

#![allow(clippy::all, clippy::pedantic)]

use anyhow::{Context, Result};
use std::fs;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant};

const MODULES_DIR: &str = "/data/adb/modules";
const CONFIG_DIR: &str = "/data/adb/drdc";
const KEYS_FILE: &str = "/data/adb/drdc/calc_keys";
/// Keys the calibration tour asks the user to tap, in this exact order.
const CALIB_ORDER: &str = "0123456789=";
const CALIB_TIMEOUT: Duration = Duration::from_secs(120);
pub const DEFAULT_PORT: u16 = 8555;

#[derive(Default)]
struct Calibration {
    active: bool,
    expected: usize,
    collected: usize,
    message: String,
}

fn calibration() -> &'static Mutex<Calibration> {
    static CELL: OnceLock<Mutex<Calibration>> = OnceLock::new();
    CELL.get_or_init(|| Mutex::new(Calibration::default()))
}

pub fn run(port: u16) -> Result<()> {
    let bind = format!("127.0.0.1:{port}");
    let listener = TcpListener::bind(&bind).with_context(|| format!("bind {bind}"))?;
    log::info!("webui: listening on http://{bind}/");

    let live = Arc::new(AtomicUsize::new(0));
    for stream in listener.incoming() {
        let Ok(stream) = stream else { continue };
        // One thread per request, capped: this is a local console, not a public site, and a
        // stuck browser tab must not exhaust the daemon.
        if live.load(Ordering::Relaxed) > 32 {
            let mut s = stream;
            let _ = s.write_all(b"HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n");
            continue;
        }
        let live = Arc::clone(&live);
        live.fetch_add(1, Ordering::Relaxed);
        thread::spawn(move || {
            let _ = handle(stream);
            live.fetch_sub(1, Ordering::Relaxed);
        });
    }
    Ok(())
}

fn handle(mut stream: TcpStream) -> Result<()> {
    let mut buf = [0u8; 4096];
    let mut filled = 0usize;
    // Read until the end of the request head; a browser sends headers in one or two reads.
    while filled < buf.len() {
        let n = stream.read(&mut buf[filled..])?;
        if n == 0 {
            break;
        }
        filled += n;
        if buf[..filled].windows(4).any(|w| w == b"\r\n\r\n") {
            break;
        }
    }
    let head = String::from_utf8_lossy(&buf[..filled]);
    let mut request = head.lines().next().unwrap_or_default().split_whitespace();
    let method = request.next().unwrap_or("GET").to_string();
    let path = request
        .next()
        .unwrap_or("/")
        .split('?')
        .next()
        .unwrap_or("/")
        .to_string();

    let (status, ctype, body) = match (method.as_str(), path.as_str()) {
        ("POST", "/api/calibrate/start") => (
            "200 OK",
            "application/json; charset=utf-8",
            start_calibration(),
        ),
        (_, "/api/calibrate") => ("200 OK", "application/json; charset=utf-8", calibration_json()),
        (_, "/" | "/index.html") => ("200 OK", "text/html; charset=utf-8", index_html()),
        (_, "/api/status") => ("200 OK", "application/json; charset=utf-8", status_json()),
        (_, "/api/modules") => ("200 OK", "application/json; charset=utf-8", modules_json()),
        (_, "/api/grants") => ("200 OK", "application/json; charset=utf-8", grants_json()),
        ("POST", other) if other.starts_with("/api/modules/") => {
            let r = module_action(&other["/api/modules/".len()..]);
            ("200 OK", "application/json; charset=utf-8", r)
        }
        ("POST", other) if other.starts_with("/api/grants/") => {
            let r = grant_action(&other["/api/grants/".len()..]);
            ("200 OK", "application/json; charset=utf-8", r)
        }
        _ => (
            "404 Not Found",
            "application/json; charset=utf-8",
            "{\"error\":\"not_found\"}".to_string(),
        ),
    };

    let response = format!(
        "HTTP/1.1 {status}\r\nContent-Type: {ctype}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream.write_all(response.as_bytes())?;
    Ok(())
}

fn read_trim(path: &str) -> String {
    fs::read_to_string(path)
        .map(|s| s.trim().to_string())
        .unwrap_or_default()
}

/// Module ids become path components and are passed to a child process, so they are
/// restricted to the characters AOSP actually uses.
fn valid_module_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 64
        && !id.starts_with('.')
        && id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
        && !id.contains("..")
}

fn err_json(message: &str) -> String {
    format!("{{\"ok\":false,\"error\":\"{}\"}}", json_escape(message))
}

fn run_self(args: &[&str]) -> Result<String, String> {
    let exe = std::env::current_exe().map_err(|e| format!("cannot resolve drdcd: {e}"))?;
    let out = std::process::Command::new(exe)
        .args(args)
        .output()
        .map_err(|e| format!("spawn failed: {e}"))?;
    let mut text = String::from_utf8_lossy(&out.stdout).trim().to_string();
    if !out.status.success() {
        let stderr = String::from_utf8_lossy(&out.stderr).trim().to_string();
        return Err(if stderr.is_empty() { text } else { stderr });
    }
    if text.len() > 2000 {
        text.truncate(2000);
    }
    Ok(text)
}

/// `POST /api/modules/<id>/<enable|disable|uninstall>`
fn module_action(rest: &str) -> String {
    let mut parts = rest.split('/');
    let (Some(id), Some(action), None) = (parts.next(), parts.next(), parts.next()) else {
        return err_json("bad path");
    };
    if !valid_module_id(id) {
        return err_json("bad module id");
    }
    if !matches!(action, "enable" | "disable" | "uninstall") {
        return err_json("unknown action");
    }
    match run_self(&["module", action, id]) {
        Ok(output) => format!(
            "{{\"ok\":true,\"id\":\"{}\",\"action\":\"{}\",\"output\":\"{}\"}}",
            json_escape(id),
            action,
            json_escape(&output)
        ),
        Err(e) => {
            log::warn!("webui: module {action} {id} failed: {e}");
            err_json(&e)
        }
    }
}

/// `POST /api/grants/<uid>/<grant|revoke>`
fn grant_action(rest: &str) -> String {
    let mut parts = rest.split('/');
    let (Some(uid), Some(action), None) = (parts.next(), parts.next(), parts.next()) else {
        return err_json("bad path");
    };
    let Ok(uid) = uid.parse::<i32>() else {
        return err_json("bad uid");
    };
    if uid < 0 {
        return err_json("bad uid");
    }
    let allow = match action {
        "grant" => true,
        "revoke" => false,
        _ => return err_json("unknown action"),
    };
    match crate::drdccalls::set_allow_su(uid, allow) {
        Ok(()) => format!(
            "{{\"ok\":true,\"uid\":{uid},\"allowed\":{allow}}}"
        ),
        Err(e) => {
            log::warn!("webui: set_allow_su({uid}, {allow}) failed: {e:#}");
            err_json(&format!("{e:#}"))
        }
    }
}

fn calibration_json() -> String {
    let state = match calibration().lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    format!(
        "{{\"active\":{},\"expected\":{},\"collected\":{},\"order\":\"{}\",\"message\":\"{}\"}}",
        state.active,
        state.expected,
        state.collected,
        json_escape(CALIB_ORDER),
        json_escape(&state.message)
    )
}

fn start_calibration() -> String {
    {
        let mut state = match calibration().lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        if state.active {
            return "{\"ok\":false,\"error\":\"already_running\"}".to_string();
        }
        state.active = true;
        state.expected = CALIB_ORDER.chars().count();
        state.collected = 0;
        state.message = String::new();
    }

    thread::spawn(|| {
        let rx = crate::calcwatch::tap_source();
        let order: Vec<char> = CALIB_ORDER.chars().collect();
        let mut points: Vec<(i32, i32)> = Vec::with_capacity(order.len());
        let deadline = Instant::now() + CALIB_TIMEOUT;

        while points.len() < order.len() && Instant::now() < deadline {
            match rx.recv_timeout(Duration::from_millis(400)) {
                Ok(point) => {
                    points.push(point);
                    let mut state = match calibration().lock() {
                        Ok(guard) => guard,
                        Err(poisoned) => poisoned.into_inner(),
                    };
                    state.collected = points.len();
                }
                Err(std::sync::mpsc::RecvTimeoutError::Timeout) => continue,
                Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => break,
            }
        }

        let mut state = match calibration().lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        state.active = false;

        if points.len() < order.len() {
            state.message = format!("只收到 {} 个点，超时未完成", points.len());
            return;
        }

        let mut out = String::new();
        for (ch, (x, y)) in order.iter().zip(points.iter()) {
            out.push_str(&format!("{ch} {x} {y}\n"));
        }
        match write_keys(&out) {
            Ok(()) => state.message = format!("已写入 {KEYS_FILE}，共 {} 个键", order.len()),
            Err(e) => state.message = format!("写入失败：{e}"),
        }
    });

    "{\"ok\":true}".to_string()
}

fn write_keys(content: &str) -> std::io::Result<()> {
    fs::create_dir_all(CONFIG_DIR)?;
    let tmp = format!("{KEYS_FILE}.tmp");
    fs::write(&tmp, content)?;
    fs::rename(&tmp, KEYS_FILE)
}

fn json_escape(value: &str) -> String {
    let mut out = String::with_capacity(value.len() + 8);
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push(' '),
            c => out.push(c),
        }
    }
    out
}

fn manager_installed() -> bool {
    std::process::Command::new("/system/bin/cmd")
        .args(["package", "list", "packages", "com.drdc.winter"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).contains("com.drdc.winter"))
        .unwrap_or(false)
}

fn status_json() -> String {
    let model = read_trim("/proc/sys/kernel/hostname");
    let kernel = read_trim("/proc/sys/kernel/osrelease");
    let enforce = read_trim("/sys/fs/selinux/enforce");
    let selinux = match enforce.as_str() {
        "0" => "Permissive",
        "1" => "Enforcing",
        _ => "unknown",
    };
    let abi = read_trim("/proc/cpuinfo")
        .lines()
        .find(|l| l.starts_with("CPU architecture"))
        .map(|l| l.rsplit(':').next().unwrap_or("").trim().to_string())
        .unwrap_or_default();
    format!(
        "{{\"model\":\"{}\",\"kernel\":\"{}\",\"selinux\":\"{}\",\"abi\":\"{}\",\"manager\":{},\"version\":\"{}\",\"pid\":{}}}",
        json_escape(&model),
        json_escape(&kernel),
        selinux,
        json_escape(&abi),
        manager_installed(),
        crate::defs::VERSION_NAME,
        std::process::id()
    )
}

fn module_props(dir: &Path) -> Option<(String, Vec<(String, String)>)> {
    let prop = dir.join("module.prop");
    let text = fs::read_to_string(&prop).ok()?;
    let mut id = dir
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default();
    let mut fields = Vec::new();
    for line in text.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let key = key.trim().to_string();
        let value = value.trim().to_string();
        if key == "id" {
            id = value.clone();
        }
        fields.push((key, value));
    }
    Some((id, fields))
}

fn field<'a>(fields: &'a [(String, String)], key: &str) -> &'a str {
    fields
        .iter()
        .find(|(k, _)| k == key)
        .map(|(_, v)| v.as_str())
        .unwrap_or("")
}

fn modules_json() -> String {
    let mut entries: Vec<String> = Vec::new();
    if let Ok(read) = fs::read_dir(MODULES_DIR) {
        let mut dirs: Vec<PathBuf> = read.flatten().map(|e| e.path()).collect();
        dirs.sort();
        for dir in dirs {
            if !dir.is_dir() {
                continue;
            }
            let Some((id, fields)) = module_props(&dir) else {
                continue;
            };
            let disabled = dir.join("disable").exists();
            let remove = dir.join("remove").exists();
            let pending_update = dir.join("update").exists();
            entries.push(format!(
                "{{\"id\":\"{}\",\"name\":\"{}\",\"version\":\"{}\",\"versionCode\":\"{}\",\"author\":\"{}\",\"description\":\"{}\",\"disabled\":{},\"remove\":{},\"update\":{}}}",
                json_escape(&id),
                json_escape(field(&fields, "name")),
                json_escape(field(&fields, "version")),
                json_escape(field(&fields, "versionCode")),
                json_escape(field(&fields, "author")),
                json_escape(field(&fields, "description")),
                disabled,
                remove,
                pending_update
            ));
        }
    }
    format!("{{\"modules\":[{}]}}", entries.join(","))
}

fn grants_json() -> String {
    // The kernel keeps the authoritative allowlist; the on-disk copy is a cache. Read it if
    // it exists, otherwise report an empty list so the page still renders.
    let mut entries: Vec<String> = Vec::new();
    if let Ok(data) = fs::read("/data/adb/drdc/allowlist") {
        for chunk in data.chunks(4) {
            if chunk.len() < 4 {
                continue;
            }
            let uid = u32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
            if uid == 0 || uid == u32::MAX {
                continue;
            }
            entries.push(format!("{uid}"));
        }
    }
    format!("{{\"uids\":[{}]}}", entries.join(","))
}

fn index_html() -> String {
    INDEX.to_string()
}

const INDEX: &str = r#"<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>冬日渡川 · 控制台</title>
<style>
  :root { color-scheme: dark; --bg:#0b0e14; --card:#141a26; --line:#24304a; --fg:#e8edf6; --dim:#8ea0bb; --ok:#4ade80; --warn:#fbbf24; --err:#f87171; }
  * { box-sizing: border-box; }
  body { margin:0; min-height:100vh; background:radial-gradient(120% 100% at 20% 0%, #1b2333 0%, var(--bg) 55%); color:var(--fg);
         font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans SC",sans-serif; padding:20px 16px 40px; }
  .wrap { max-width: 720px; margin: 0 auto; }
  h1 { font-size:20px; margin:0 0 4px; letter-spacing:.5px; }
  .sub { color:var(--dim); font-size:13px; margin:0 0 20px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:18px; padding:16px 16px; margin-bottom:14px; }
  .card h2 { font-size:14px; margin:0 0 12px; color:var(--dim); font-weight:600; letter-spacing:.4px; }
  table { width:100%; border-collapse:collapse; font-size:14px; }
  td { padding:7px 0; border-bottom:1px solid #1e2739; vertical-align:top; }
  tr:last-child td { border-bottom:none; }
  td:first-child { color:var(--dim); width:38%; }
  td:last-child { text-align:right; word-break:break-word; }
  .pill { display:inline-block; padding:2px 8px; border-radius:999px; font-size:12px; border:1px solid var(--line); }
  .on { color:var(--ok); border-color:#1f5136; }
  .off { color:var(--dim); }
  .mod { padding:10px 0; border-bottom:1px solid #1e2739; }
  .mod:last-child { border-bottom:none; }
  .mod .name { font-weight:600; }
  .mod .meta { color:var(--dim); font-size:12px; margin-top:2px; }
  .mod .desc { color:#b9c6da; font-size:12.5px; margin-top:6px; line-height:1.6; }
  .modbtns { display:flex; gap:8px; margin-top:9px; }
  .modbtns button { padding:6px 12px; font-size:12px; }
  .toolbar { display:flex; gap:8px; align-items:center; margin-bottom:14px; flex-wrap:wrap; }
  button { background:#1d2740; color:var(--fg); border:1px solid var(--line); border-radius:12px; padding:8px 14px; font-size:13px; }
  button:active { transform:scale(.98); }
  .time { color:var(--dim); font-size:12px; margin-left:auto; }
  .empty { color:var(--dim); font-size:13px; padding:6px 0; }
  code { background:#0f1522; border:1px solid var(--line); border-radius:8px; padding:1px 6px; font-size:12px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>冬日渡川</h1>
  <p class="sub">root 侧控制台 · 由 drdcd 提供，卸载管理器也不影响</p>

  <div class="toolbar">
    <button id="reload">刷新</button>
    <span class="time" id="ts"></span>
  </div>

  <div class="card">
    <h2>设备</h2>
    <table id="status"><tr><td>读取中…</td><td></td></tr></table>
  </div>

  <div class="card">
    <h2>计算器解锁 · 键位校准</h2>
    <div class="empty" id="calibMsg">未校准。点下面的按钮，然后按 0 1 2 3 4 5 6 7 8 9 = 的顺序在系统计算器上各点一次。</div>
    <button id="calibBtn" style="margin-top:10px">开始校准</button>
  </div>

  <div class="card">
    <h2>模块</h2>
    <div id="modules"><div class="empty">读取中…</div></div>
  </div>

  <div class="card">
    <h2>Root 授权</h2>
    <div class="toolbar" style="margin-bottom:8px">
      <input id="uidInput" placeholder="UID，例如 10123" inputmode="numeric"
             style="flex:1;min-width:140px;background:#0f1522;color:var(--fg);border:1px solid var(--line);border-radius:12px;padding:8px 12px;font-size:13px">
      <button id="grantBtn">授权</button>
      <button id="revokeBtn">撤销</button>
    </div>
    <div id="grants"><div class="empty">读取中…</div></div>
  </div>
</div>

<script>
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

async function getJSON(url) {
  const r = await fetch(url, { cache: "no-store" });
  if (!r.ok) throw new Error(url + " -> " + r.status);
  return r.json();
}

function row(k, v) { return "<tr><td>" + esc(k) + "</td><td>" + v + "</td></tr>"; }

async function loadStatus() {
  const el = document.getElementById("status");
  try {
    const s = await getJSON("/api/status");
    const mgr = s.manager
      ? '<span class="pill on">已安装</span>'
      : '<span class="pill off">未安装</span>';
    const sel = s.selinux === "Enforcing"
      ? '<span class="pill on">Enforcing</span>'
      : '<span class="pill off">' + esc(s.selinux) + "</span>";
    el.innerHTML =
      row("设备", esc(s.model || "未知")) +
      row("内核", esc(s.kernel || "未知")) +
      row("SELinux", sel) +
      row("ABI", esc(s.abi || "未知")) +
      row("管理器", mgr) +
      row("drdcd", esc(s.version) + " · pid " + esc(s.pid));
  } catch (e) {
    el.innerHTML = row("错误", esc(e.message));
  }
}

async function loadModules() {
  const el = document.getElementById("modules");
  try {
    const d = await getJSON("/api/modules");
    if (!d.modules.length) { el.innerHTML = '<div class="empty">没有已安装模块</div>'; return; }
    el.innerHTML = d.modules.map(m => {
      const flag = m.disabled ? '<span class="pill off">已禁用</span>'
                 : m.remove ? '<span class="pill off">待删除</span>'
                 : m.update ? '<span class="pill on">待更新</span>'
                 : '<span class="pill on">启用</span>';
      const id = esc(m.id);
      const toggle = m.disabled ? "enable" : "disable";
      const toggleLabel = m.disabled ? "启用" : "禁用";
      return '<div class="mod"><div class="name">' + esc(m.name || m.id) + " " + flag + "</div>" +
             '<div class="meta">' + id + " · " + esc(m.version || "?") +
             (m.author ? " · " + esc(m.author) : "") + "</div>" +
             (m.description ? '<div class="desc">' + esc(m.description) + "</div>" : "") +
             '<div class="modbtns">' +
               '<button data-act="' + toggle + '" data-id="' + id + '">' + toggleLabel + "</button>" +
               '<button data-act="uninstall" data-id="' + id + '">卸载</button>' +
             "</div></div>";
    }).join("");
  } catch (e) {
    el.innerHTML = '<div class="empty">' + esc(e.message) + "</div>";
  }
}

async function loadGrants() {
  const el = document.getElementById("grants");
  try {
    const d = await getJSON("/api/grants");
    if (!d.uids.length) { el.innerHTML = '<div class="empty">读取不到授权列表（内核侧为准）</div>'; return; }
    el.innerHTML = d.uids.map(u => '<div class="mod"><div class="meta">UID <code>' + esc(u) + "</code></div></div>").join("");
  } catch (e) {
    el.innerHTML = '<div class="empty">' + esc(e.message) + "</div>";
  }
}

function refresh() {
  document.getElementById("ts").textContent = new Date().toLocaleTimeString();
  loadStatus(); loadModules(); loadGrants(); pollCalib();
}

async function pollCalib() {
  const el = document.getElementById("calibMsg");
  const btn = document.getElementById("calibBtn");
  try {
    const c = await getJSON("/api/calibrate");
    if (c.active) {
      btn.disabled = true;
      el.textContent = "校准中：已收到 " + c.collected + " / " + c.expected + " 个点，按顺序点 " + c.order;
    } else {
      btn.disabled = false;
      el.textContent = c.message || "未校准。点下面的按钮，然后按 0 1 2 3 4 5 6 7 8 9 = 的顺序在系统计算器上各点一次。";
    }
  } catch (e) {
    el.textContent = "读取校准状态失败：" + e.message;
  }
}

document.getElementById("calibBtn").addEventListener("click", async () => {
  const el = document.getElementById("calibMsg");
  try {
    await fetch("/api/calibrate/start", { method: "POST" });
    el.textContent = "已开始，请现在去系统计算器按顺序点键盘…";
    // Poll fast while a session is running.
    const timer = setInterval(async () => {
      await pollCalib();
      const c = await getJSON("/api/calibrate").catch(() => null);
      if (!c || !c.active) clearInterval(timer);
    }, 700);
  } catch (e) {
    el.textContent = "启动失败：" + e.message;
  }
});

document.getElementById("reload").addEventListener("click", refresh);
refresh();

// Module actions (enable / disable / uninstall) are plain POSTs to the console.
document.addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-act]");
  if (!btn) return;
  const id = btn.dataset.id;
  const act = btn.dataset.act;
  if (act === "uninstall" && !confirm("确定卸载模块 " + id + "？")) return;
  btn.disabled = true;
  try {
    const r = await fetch("/api/modules/" + encodeURIComponent(id) + "/" + act, { method: "POST" });
    const j = await r.json();
    if (!j.ok) alert("操作失败：" + (j.error || "未知错误"));
  } catch (e) {
    alert("请求失败：" + e.message);
  }
  refresh();
});

async function setGrant(allow) {
  const input = document.getElementById("uidInput");
  const uid = (input.value || "").trim();
  if (!/^[0-9]+$/.test(uid)) { alert("请输入数字 UID"); return; }
  try {
    const r = await fetch("/api/grants/" + uid + "/" + (allow ? "grant" : "revoke"), { method: "POST" });
    const j = await r.json();
    if (!j.ok) alert("操作失败：" + (j.error || "未知错误"));
  } catch (e) {
    alert("请求失败：" + e.message);
  }
  refresh();
}

document.getElementById("grantBtn").addEventListener("click", () => setGrant(true));
document.getElementById("revokeBtn").addEventListener("click", () => setGrant(false));
</script>
</body>
</html>
"#;
