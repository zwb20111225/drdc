// Root-side "calculator unlock" watcher.
//
// The kernel never exposes what an app draws, only raw input events, so this module reads
// the touch panel straight from /dev/input and turns taps back into keys using a keypad
// calibration table. It runs as root (started by init_event), which is why the manager APK
// needs no accessibility grant and no injected code in the calculator process.
//
// Files under /data/adb/drdc:
//   calc_code   one line, the unlock code (default 112233)
//   calc_keys   one "char x y" per line, RAW touch coordinates of each key
//   calc_page   one line, URL handed to the browser when the manager is not installed
//
// Run `drdcd calc-watch --calibrate` and tap the keypad in order to capture coordinates.

// The crate root denies clippy::all + clippy::pedantic; this module is a low-level device
// reader full of intentional casts and raw byte parsing, so it opts out as a whole rather
// than carrying thirty individual allows.
#![allow(clippy::all, clippy::pedantic)]

use anyhow::{Context, Result};
use std::collections::HashMap;
use std::fs::{self, File};
use std::io::Read;
use std::path::Path;
use std::process::{Command, Stdio};
use std::sync::mpsc::{self, Receiver, Sender};
use std::thread;

const CONFIG_DIR: &str = "/data/adb/drdc";
const CODE_FILE: &str = "/data/adb/drdc/calc_code";
const KEYS_FILE: &str = "/data/adb/drdc/calc_keys";
const PAGE_FILE: &str = "/data/adb/drdc/calc_page";
const MANAGER_PACKAGE: &str = "com.drdc.winter";
const MANAGER_ACTIVITY: &str = "com.drdc.winter/me.weishu.kernelsu.ui.MainActivity";
const DEFAULT_CODE: &str = "112233";
const DEFAULT_PAGE: &str = "http://127.0.0.1:8555/";
const MAX_TYPED: usize = 32;

// linux/input-event-codes.h
const EV_KEY: u16 = 0x01;
const EV_ABS: u16 = 0x03;
const BTN_TOUCH: u16 = 0x14a;
const ABS_MT_POSITION_X: u16 = 0x35;
const ABS_MT_POSITION_Y: u16 = 0x36;
const ABS_MT_TRACKING_ID: u16 = 0x39;

/// 64-bit `struct input_event`: timeval (16 bytes) + type + code + value.
const INPUT_EVENT_SIZE: usize = 24;

#[repr(C)]
#[derive(Clone, Copy, Debug)]
struct InputEvent {
    sec: i64,
    usec: i64,
    type_: u16,
    code: u16,
    value: i32,
}

fn parse_event(raw: &[u8; INPUT_EVENT_SIZE]) -> InputEvent {
    let mut sec = [0u8; 8];
    let mut usec = [0u8; 8];
    let mut ty = [0u8; 2];
    let mut code = [0u8; 2];
    let mut value = [0u8; 4];
    sec.copy_from_slice(&raw[0..8]);
    usec.copy_from_slice(&raw[8..16]);
    ty.copy_from_slice(&raw[16..18]);
    code.copy_from_slice(&raw[18..20]);
    value.copy_from_slice(&raw[20..24]);
    InputEvent {
        sec: i64::from_ne_bytes(sec),
        usec: i64::from_ne_bytes(usec),
        type_: u16::from_ne_bytes(ty),
        code: u16::from_ne_bytes(code),
        value: i32::from_ne_bytes(value),
    }
}

fn read_line(path: &str, fallback: &str) -> String {
    fs::read_to_string(path)
        .map(|s| s.trim().to_string())
        .ok()
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| fallback.to_string())
}

fn load_keys() -> HashMap<char, (i32, i32)> {
    let mut map = HashMap::new();
    let Ok(text) = fs::read_to_string(KEYS_FILE) else {
        return map;
    };
    for line in text.lines() {
        let mut parts = line.split_whitespace();
        let (Some(ch), Some(x), Some(y)) = (parts.next(), parts.next(), parts.next()) else {
            continue;
        };
        let Some(ch) = ch.chars().next() else {
            continue;
        };
        if let (Ok(x), Ok(y)) = (x.parse::<i32>(), y.parse::<i32>()) {
            map.insert(ch, (x, y));
        }
    }
    map
}

/// Raw tap stream: one reader thread per /dev/input/event* node.
///
/// Exposed so the web console can drive keypad calibration without adb — the console starts
/// a session with an unknown key order, collects the taps, and writes calc_keys itself.
pub fn tap_source() -> Receiver<(i32, i32)> {
    let (tx, rx) = mpsc::channel();
    spawn_readers(tx);
    rx
}

/// Every /dev/input/event* node gets its own reader thread; taps come back over a channel.
fn spawn_readers(tx: Sender<(i32, i32)>) {
    let Ok(entries) = fs::read_dir("/dev/input") else {
        log::error!("calcwatch: cannot read /dev/input");
        return;
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if !name.starts_with("event") {
            continue;
        }
        let path = entry.path();
        let tx = tx.clone();
        thread::spawn(move || read_device(&path, &tx));
    }
}

fn read_device(path: &Path, tx: &Sender<(i32, i32)>) {
    let Ok(mut file) = File::open(path) else {
        return;
    };
    let mut buf = [0u8; INPUT_EVENT_SIZE * 64];
    let mut x = 0i32;
    let mut y = 0i32;
    let mut have_xy = false;
    loop {
        let mut filled = 0usize;
        while filled < buf.len() {
            match file.read(&mut buf[filled..]) {
                Ok(0) => return,
                Ok(n) => filled += n,
                Err(_) => return,
            }
        }
        for chunk in buf[..filled].chunks_exact(INPUT_EVENT_SIZE) {
            let mut raw = [0u8; INPUT_EVENT_SIZE];
            raw.copy_from_slice(chunk);
            let ev = parse_event(&raw);
            if ev.type_ == EV_ABS {
                match ev.code {
                    ABS_MT_POSITION_X => {
                        x = ev.value;
                        have_xy = true;
                    }
                    ABS_MT_POSITION_Y => {
                        y = ev.value;
                        have_xy = true;
                    }
                    ABS_MT_TRACKING_ID if ev.value == -1 && have_xy => {
                        let _ = tx.send((x, y));
                        have_xy = false;
                    }
                    _ => {}
                }
            } else if ev.type_ == EV_KEY && ev.code == BTN_TOUCH && ev.value == 0 && have_xy {
                let _ = tx.send((x, y));
                have_xy = false;
            }
        }
    }
}

fn foreground_package() -> String {
    let Ok(out) = Command::new("/system/bin/dumpsys")
        .args(["window", "windows"])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .output()
    else {
        return String::new();
    };
    let text = String::from_utf8_lossy(&out.stdout);
    for line in text.lines() {
        if !line.contains("mCurrentFocus") {
            continue;
        }
        let Some(slash) = line.find('/') else {
            continue;
        };
        let head = &line[..slash];
        let start = head
            .rfind(|c: char| !(c.is_ascii_alphanumeric() || c == '.'))
            .map_or(0, |i| i + 1);
        return head[start..].to_string();
    }
    String::new()
}

fn is_calculator(pkg: &str) -> bool {
    if pkg.is_empty() {
        return false;
    }
    pkg.contains("calculator") || pkg.ends_with(".calc")
}

fn manager_installed() -> bool {
    Command::new("/system/bin/cmd")
        .args(["package", "list", "packages", MANAGER_PACKAGE])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).contains(MANAGER_PACKAGE))
        .unwrap_or(false)
}

fn run_am(args: &[&str]) {
    let _ = Command::new("/system/bin/am")
        .arg("start")
        .args(args)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
}

fn unlock() {
    log::info!("calcwatch: unlock code matched");
    if manager_installed() {
        run_am(&["-n", MANAGER_ACTIVITY, "--activity-single-top"]);
    } else {
        let page = read_line(PAGE_FILE, DEFAULT_PAGE);
        run_am(&["-a", "android.intent.action.VIEW", "-d", &page]);
    }
}

fn nearest_key(keys: &HashMap<char, (i32, i32)>, x: i32, y: i32, radius: i64) -> Option<char> {
    let mut best: Option<(i64, char)> = None;
    for (ch, (kx, ky)) in keys {
        let dx = i64::from(*kx - x);
        let dy = i64::from(*ky - y);
        let dist = dx * dx + dy * dy;
        if dist > radius * radius {
            continue;
        }
        if best.is_none_or(|(b, _)| dist < b) {
            best = Some((dist, *ch));
        }
    }
    best.map(|(_, ch)| ch)
}

/// Match radius derived from the calibration itself: half the smallest gap between two
/// keys. Raw coordinates differ per panel (1080, 1440, 4095 ...), so a fixed number would
/// be either way too tight or swallow neighbouring keys.
fn key_radius(keys: &HashMap<char, (i32, i32)>) -> i64 {
    let points: Vec<(i32, i32)> = keys.values().copied().collect();
    let mut best = i64::MAX;
    for i in 0..points.len() {
        for j in (i + 1)..points.len() {
            let dx = i64::from(points[i].0 - points[j].0);
            let dy = i64::from(points[i].1 - points[j].1);
            best = best.min(dx * dx + dy * dy);
        }
    }
    if best == i64::MAX {
        return 80;
    }
    // Integer sqrt by trial is overkill; compare squared distances and return half the gap.
    #[allow(clippy::cast_precision_loss)]
    let gap = (best as f64).sqrt() as i64;
    (gap / 2).clamp(20, 400)
}

fn run_matcher(rx: Receiver<(i32, i32)>, keys: HashMap<char, (i32, i32)>, code: String) {
    let mut typed: Vec<char> = Vec::with_capacity(MAX_TYPED);
    let radius = key_radius(&keys);
    while let Ok((x, y)) = rx.recv() {
        if !is_calculator(&foreground_package()) {
            typed.clear();
            continue;
        }
        let Some(ch) = nearest_key(&keys, x, y, radius) else {
            continue;
        };
        if ch == '=' {
            let n = code.chars().count();
            if typed.len() >= n && typed[typed.len() - n..].iter().collect::<String>() == code {
                typed.clear();
                unlock();
            }
            continue;
        }
        if typed.len() == MAX_TYPED {
            typed.remove(0);
        }
        typed.push(ch);
    }
}

pub fn run(calibrate: bool) -> Result<()> {
    fs::create_dir_all(CONFIG_DIR).with_context(|| format!("create {CONFIG_DIR}"))?;

    let (tx, rx) = mpsc::channel();
    spawn_readers(tx);

    if calibrate {
        println!("tap each key in order; one 'x y' line per tap (Ctrl-C to stop)");
        while let Ok((x, y)) = rx.recv() {
            println!("{x} {y}");
        }
        return Ok(());
    }

    let keys = load_keys();
    if keys.is_empty() {
        anyhow::bail!("{KEYS_FILE} missing or empty - run `drdcd calc-watch --calibrate` first");
    }
    let code = read_line(CODE_FILE, DEFAULT_CODE);

    log::info!(
        "calcwatch: watching, {} keys, code length {}",
        keys.len(),
        code.chars().count()
    );

    run_matcher(rx, keys, code);
    Ok(())
}
