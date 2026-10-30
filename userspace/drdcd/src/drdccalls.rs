#![allow(clippy::unreadable_literal)]
use anyhow::{Result, bail};

use crate::drdc_uapi;
use std::cell::Cell;
use std::fs;
use std::io;
use std::os::fd::RawFd;
use std::sync::OnceLock;

// sigsys handler
std::thread_local! {
    #[allow(clippy::missing_const_for_thread_local)]
    static SVC_IN_FLIGHT: Cell<bool> = const { Cell::new(false) };
    #[allow(clippy::missing_const_for_thread_local)]
    static SIGSYS_OCCURRED: Cell<bool> = const { Cell::new(false) };
}

const SYS_SECCOMP: libc::c_int = 1;

fn with_svc_call<F, R>(call: F) -> R
where
    F: FnOnce() -> R,
{
    SVC_IN_FLIGHT.with(|in_flight| in_flight.set(true));
    let result = call();
    SVC_IN_FLIGHT.with(|in_flight| in_flight.set(false));
    result
}

fn take_sigsys_occurred() -> bool {
    SIGSYS_OCCURRED.with(|occurred| occurred.replace(false))
}

extern "C" fn sigsys_handler(
    _sig: libc::c_int,
    info: *mut libc::siginfo_t,
    ctx: *mut libc::c_void,
) {
    unsafe {
        if info.is_null() || ctx.is_null() || (*info).si_code != SYS_SECCOMP {
            return;
        }
        if SVC_IN_FLIGHT.with(Cell::get) {
            SIGSYS_OCCURRED.with(|occurred| occurred.set(true));
        }

        #[cfg(not(target_arch = "riscv64"))]
        let ucontext = ctx.cast::<libc::ucontext_t>();
        #[cfg(target_arch = "aarch64")]
        {
            (*ucontext).uc_mcontext.regs[0] = (-libc::EPERM) as u64;
        }
        #[cfg(target_arch = "x86_64")]
        {
            let rax = libc::REG_RAX as usize;
            (*ucontext).uc_mcontext.gregs[rax] = i64::from(-libc::EPERM);
        }
        #[cfg(target_arch = "riscv64")]
        {
            let ucontext = ctx.cast::<drdc_uapi::ucontext_t>();
            (*ucontext).uc_mcontext.__gregs[drdc_uapi::REG_A0 as usize] =
                (-libc::EPERM) as libc::c_ulong;
        }
    }
}

pub fn setup_sigsys_handler() {
    unsafe {
        let mut sa: libc::sigaction = std::mem::zeroed();
        sa.sa_flags = libc::SA_SIGINFO;
        sa.sa_sigaction = sigsys_handler as *const () as usize;
        libc::sigemptyset(std::ptr::addr_of_mut!(sa.sa_mask));
        if libc::sigaction(libc::SIGSYS, std::ptr::addr_of!(sa), std::ptr::null_mut()) != 0 {
            let error = std::io::Error::last_os_error();
            log::warn!("Failed to set SIGSYS handler: {error}");
        }
    }
}

const DRIVER_FD_NAME: &str = "anon_inode:[drdc_driver]";
const SU_DRIVER_FD_NAME: &str = "anon_inode:[drdc_driver_su]";

// Global driver fd cache
static DRIVER_FD: OnceLock<RawFd> = OnceLock::new();
static INFO_CACHE: OnceLock<drdc_uapi::drdc_get_info_cmd> = OnceLock::new();

fn scan_driver_fd() -> io::Result<Option<RawFd>> {
    let fd_dir = fs::read_dir("/proc/self/fd")?;
    let mut driver_fd = None;

    for entry in fd_dir.flatten() {
        if let Ok(fd_num) = entry.file_name().to_string_lossy().parse::<i32>() {
            let link_path = format!("/proc/self/fd/{fd_num}");
            if let Ok(target) = fs::read_link(&link_path) {
                let target_str = target.to_string_lossy();
                if target_str == SU_DRIVER_FD_NAME {
                    return Ok(Some(fd_num));
                }
                if target_str == DRIVER_FD_NAME {
                    driver_fd = Some(fd_num);
                }
            }
        }
    }

    Ok(driver_fd)
}

pub fn claim_inherited_driver_fd() -> io::Result<()> {
    if DRIVER_FD.get().is_none()
        && let Some(fd) = scan_driver_fd()?
    {
        let _ = DRIVER_FD.set(fd);
    }
    Ok(())
}

// Get cached driver fd
fn init_driver_fd() -> Option<RawFd> {
    let fd = scan_driver_fd().ok().flatten();
    if fd.is_none() {
        let mut fd = -1;
        with_svc_call(|| unsafe {
            libc::syscall(
                libc::SYS_reboot,
                drdc_uapi::KSU_INSTALL_MAGIC1,
                drdc_uapi::KSU_INSTALL_MAGIC2,
                0,
                &mut fd,
            )
        });
        if take_sigsys_occurred() {
            eprintln!("DRDC driver install syscall was blocked by seccomp");
            log::error!("DRDC driver install syscall was blocked by seccomp");
        }
        if fd >= 0 { Some(fd) } else { None }
    } else {
        fd
    }
}

// ioctl wrapper using libc
fn drdcctl<T>(request: u32, arg: *mut T) -> Result<i32> {
    let fd = *DRIVER_FD.get_or_init(|| init_driver_fd().unwrap_or(-1));
    if fd < 0 {
        bail!("could not retrieve drdc driver fd")
    }
    unsafe {
        let ret = libc::ioctl(fd as libc::c_int, request as i32, arg);
        if ret < 0 {
            bail!("drdcctl failed: {}", io::Error::last_os_error())
        }
        Ok(ret)
    }
}

// API implementations
pub fn get_info() -> drdc_uapi::drdc_get_info_cmd {
    *INFO_CACHE.get_or_init(|| {
        let mut cmd = drdc_uapi::drdc_get_info_cmd {
            version: 0,
            flags: 0,
            features: 0,
            uapi_version: 0,
        };
        if drdcctl(drdc_uapi::KSU_IOCTL_GET_INFO, &raw mut cmd).is_err() {
            let _ = drdcctl(drdc_uapi::KSU_IOCTL_GET_INFO_LEGACY, &raw mut cmd);
        }
        cmd
    })
}

pub fn get_version() -> i32 {
    get_info().version as i32
}

pub fn is_late_load() -> bool {
    get_info().flags & drdc_uapi::KSU_GET_INFO_FLAG_LATE_LOAD != 0
}

pub fn is_lkm() -> bool {
    get_info().flags & drdc_uapi::KSU_GET_INFO_FLAG_LKM != 0
}

/// Whether the kernel runs module preinit actions on boot.
pub fn is_preinit_supported() -> bool {
    get_info().flags & drdc_uapi::KSU_GET_INFO_FLAG_PREINIT != 0
}

pub const fn uapi_version() -> u32 {
    drdc_uapi::KERNEL_SU_UAPI_VERSION
}

pub fn runtime_mode() -> &'static str {
    if is_late_load() {
        "late-load"
    } else if is_lkm() {
        "lkm"
    } else {
        "built-in"
    }
}

pub fn ensure_uapi_version_matched() -> anyhow::Result<()> {
    let kernel_uapi = get_info().uapi_version;
    let userspace_uapi = uapi_version();
    if kernel_uapi != userspace_uapi {
        bail!(
            "UAPI version mismatch: kernel={kernel_uapi}, drdcd={userspace_uapi}. Please update DRDC!"
        );
    }
    Ok(())
}

pub fn grant_root() -> Result<()> {
    drdcctl(drdc_uapi::KSU_IOCTL_GRANT_ROOT, std::ptr::null_mut::<u8>())?;
    Ok(())
}

fn report_event(event: u32) {
    let mut cmd = drdc_uapi::drdc_report_event_cmd { event };
    let _ = drdcctl(drdc_uapi::KSU_IOCTL_REPORT_EVENT, &raw mut cmd);
}

pub fn report_post_fs_data() {
    report_event(drdc_uapi::EVENT_POST_FS_DATA);
}

pub fn report_services() -> Result<bool> {
    let mut cmd = drdc_uapi::drdc_report_event_cmd {
        event: drdc_uapi::EVENT_SERVICES,
    };
    Ok(drdcctl(drdc_uapi::KSU_IOCTL_REPORT_EVENT, &raw mut cmd)? == 1)
}

pub fn report_boot_complete() {
    report_event(drdc_uapi::EVENT_BOOT_COMPLETED);
}

pub fn report_module_mounted() {
    report_event(drdc_uapi::EVENT_MODULE_MOUNTED);
}

pub fn check_kernel_safemode() -> bool {
    let mut cmd = drdc_uapi::drdc_check_safemode_cmd { in_safe_mode: 0 };
    let _ = drdcctl(drdc_uapi::KSU_IOCTL_CHECK_SAFEMODE, &raw mut cmd);
    cmd.in_safe_mode != 0
}

pub fn set_sepolicy(payload: *const u8, payload_len: u64) -> Result<i32> {
    let mut ioctl_cmd = crate::drdc_uapi::drdc_set_sepolicy_cmd {
        data_len: payload_len,
        data: payload as u64,
    };

    drdcctl(drdc_uapi::KSU_IOCTL_SET_SEPOLICY, &raw mut ioctl_cmd)
}

#[derive(Debug, Clone, Copy, Default)]
pub struct FeatureState {
    pub value: u64,
    pub supported: bool,
    /// Forced by boot config (drdc_config), the kernel refuses to change it
    pub forced: bool,
}

/// Get feature value and support status from kernel
pub fn get_feature(feature_id: u32) -> Result<FeatureState> {
    let mut cmd = drdc_uapi::drdc_get_feature_cmd {
        feature_id,
        value: 0,
        supported: 0,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_GET_FEATURE, &raw mut cmd)?;
    let flags = u32::from(cmd.supported);
    Ok(FeatureState {
        value: cmd.value,
        // older kernels report 1 for supported, matching the flag
        supported: flags & drdc_uapi::KSU_FEATURE_FLAG_SUPPORTED != 0,
        forced: flags & drdc_uapi::KSU_FEATURE_FLAG_FORCED != 0,
    })
}

/// Set feature value in kernel
pub fn set_feature(feature_id: u32, value: u64) -> Result<()> {
    let mut cmd = drdc_uapi::drdc_set_feature_cmd { feature_id, value };
    drdcctl(drdc_uapi::KSU_IOCTL_SET_FEATURE, &raw mut cmd)?;
    Ok(())
}

/// Grant or revoke root for a uid by writing its app profile.
///
/// `allow_su = true` with the default root profile is what the manager writes when the user
/// taps "allow"; `allow_su = false` keeps the entry but denies it, which is the revoke path.
/// The allowlist itself stays kernel-owned — this only edits the per-uid policy.
pub fn set_allow_su(uid: i32, allow: bool) -> Result<()> {
    let mut profile: drdc_uapi::app_profile = unsafe { std::mem::zeroed() };
    profile.version = drdc_uapi::KSU_APP_PROFILE_VER;
    profile.curr_uid = uid;
    profile.allow_su = allow;
    // The union is initialised through its root-profile arm; `use_default` makes the kernel
    // fall back to its own default root profile instead of the zeroed fields.
    profile.__bindgen_anon_1.rp_config.use_default = true;
    let mut cmd = drdc_uapi::drdc_set_app_profile_cmd { profile };
    drdcctl(drdc_uapi::KSU_IOCTL_SET_APP_PROFILE, &raw mut cmd)?;
    Ok(())
}

pub fn get_wrapped_fd(fd: RawFd) -> Result<RawFd> {
    let mut cmd = drdc_uapi::drdc_get_wrapper_fd_cmd {
        fd: fd as u32,
        flags: 0,
    };
    let result = drdcctl(drdc_uapi::KSU_IOCTL_GET_WRAPPER_FD, &raw mut cmd)?;
    Ok(result)
}

pub fn get_sulog_fd() -> Result<RawFd> {
    let mut cmd = drdc_uapi::drdc_get_sulog_fd_cmd { flags: 0 };
    let result = drdcctl(drdc_uapi::KSU_IOCTL_GET_SULOG_FD, &raw mut cmd)?;
    Ok(result)
}

/// Get mark status for a process (pid=0 returns total marked count)
pub fn mark_get(pid: i32) -> Result<u32> {
    let mut cmd = drdc_uapi::drdc_manage_mark_cmd {
        operation: drdc_uapi::KSU_MARK_GET,
        pid,
        result: 0,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(cmd.result)
}

/// Mark a process (pid=0 marks all processes)
pub fn mark_set(pid: i32) -> Result<()> {
    let mut cmd = drdc_uapi::drdc_manage_mark_cmd {
        operation: drdc_uapi::KSU_MARK_MARK,
        pid,
        result: 0,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(())
}

/// Unmark a process (pid=0 unmarks all processes)
pub fn mark_unset(pid: i32) -> Result<()> {
    let mut cmd = drdc_uapi::drdc_manage_mark_cmd {
        operation: drdc_uapi::KSU_MARK_UNMARK,
        pid,
        result: 0,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(())
}

/// Refresh mark for all running processes
pub fn mark_refresh() -> Result<()> {
    let mut cmd = drdc_uapi::drdc_manage_mark_cmd {
        operation: drdc_uapi::KSU_MARK_REFRESH,
        pid: 0,
        result: 0,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(())
}

pub fn nuke_ext4_sysfs(mnt: &str) -> anyhow::Result<()> {
    let c_mnt = std::ffi::CString::new(mnt)?;
    let mut ioctl_cmd = drdc_uapi::drdc_nuke_ext4_sysfs_cmd {
        arg: c_mnt.as_ptr() as u64,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_NUKE_EXT4_SYSFS, &raw mut ioctl_cmd)?;
    Ok(())
}

/// Wipe all entries from umount list
pub fn umount_list_wipe() -> Result<()> {
    let mut cmd = drdc_uapi::drdc_add_try_umount_cmd {
        arg: 0,
        flags: 0,
        mode: drdc_uapi::KSU_UMOUNT_WIPE,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_ADD_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

/// Add mount point to umount list
pub fn umount_list_add(path: &str, flags: u32) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = drdc_uapi::drdc_add_try_umount_cmd {
        arg: c_path.as_ptr() as u64,
        flags,
        mode: drdc_uapi::KSU_UMOUNT_ADD,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_ADD_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

/// Delete mount point from umount list
pub fn umount_list_del(path: &str) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = drdc_uapi::drdc_add_try_umount_cmd {
        arg: c_path.as_ptr() as u64,
        flags: 0,
        mode: drdc_uapi::KSU_UMOUNT_DEL,
    };
    drdcctl(drdc_uapi::KSU_IOCTL_ADD_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

/// Set current process's process group to init_group (pgid = 0)
pub fn set_init_pgrp() -> Result<()> {
    drdcctl(
        drdc_uapi::KSU_IOCTL_SET_INIT_PGRP,
        std::ptr::null_mut::<u8>(),
    )?;
    Ok(())
}

/// Look up the addresses of kernel symbols, exported or not.
///
/// Returns one entry per name, `None` when the running kernel has no such
/// symbol.
pub fn lookup_symbols(names: &[&str]) -> Result<Vec<Option<u64>>> {
    let mut result = Vec::with_capacity(names.len());
    for chunk in names.chunks(drdc_uapi::KSU_LOOKUP_SYMBOLS_MAX as usize) {
        let c_names = chunk
            .iter()
            .map(|name| std::ffi::CString::new(*name))
            .collect::<Result<Vec<_>, _>>()?;
        let ptrs: Vec<u64> = c_names.iter().map(|name| name.as_ptr() as u64).collect();
        let mut addrs = vec![0u64; chunk.len()];
        let mut cmd = drdc_uapi::drdc_lookup_symbols_cmd {
            names: ptrs.as_ptr() as u64,
            addrs: addrs.as_mut_ptr() as u64,
            count: chunk.len() as u32,
            flags: 0,
        };
        drdcctl(drdc_uapi::KSU_IOCTL_LOOKUP_SYMBOLS, &raw mut cmd)?;
        result.extend(addrs.into_iter().map(|addr| (addr != 0).then_some(addr)));
    }
    Ok(result)
}

pub fn set_drdc_no_new_privs() -> anyhow::Result<()> {
    let result = drdcctl(
        drdc_uapi::KSU_IOCTL_DISABLE_ESCAPE_TO_ROOT,
        std::ptr::null_mut::<u8>(),
    )?;
    if result != 0 {
        bail!("unexpected result: {result}");
    }
    Ok(())
}
