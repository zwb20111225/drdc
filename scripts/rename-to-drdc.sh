#!/bin/sh
# rename-to-drdc.sh — KernelSU -> DRDC (冬日渡川) identifier rename
#
# Run:  sh scripts/rename-to-drdc.sh apply [--no-manager]
#       sh scripts/rename-to-drdc.sh verify [--no-manager]
#
# Renamed (runtime-visible; kernel / uapi / userspace / manager stay consistent):
#   paths           /data/adb/ksu*        -> /data/adb/drdc*  (.ksurc -> .drdcrc)
#   daemon          ksud                  -> drdcd  (binary, paths, argv0)
#   anon inodes     [ksu_driver]          -> [drdc_driver]  (+_su, +[ksu_fdwrapper])
#   install magic   0xDEADBEEF 0xCAFEBABE -> 0x44524443 0x57494E54  ("DRDC"/"WINT")
#   prctl magic     0xDEADBEEF (manager)  -> 0x44524443
#   ioctl type      'K' (uapi/supercall.h)-> 'D'
#   allowlist/feature FILE magic 0x7f4b5355 -> 0x7f445244
#   LKM capsule     KSULKM1               -> DRDCLKM1
#   module artifact kernelsu.ko           -> drdc.ko
#   identifiers     ksu_*, ksud (lower)   -> drdc_*, drdcd
#   brand text      KernelSU              -> DRDC
#   signing default 0x033b + upstream hash-> 0x0330 + DRDC hash (kernel/Kbuild)
#   workspace       userspace/ksud|ksuinit-> userspace/drdcd|drdcinit (root Cargo.toml)
#
# Deliberately KEPT (build-system names, never runtime-visible):
#   CONFIG_KSU*, KSU_PACKAGE_NAME, KSU_EXPECTED_SIZE/HASH, KSU_IS_PR_BUILD,
#   KSU_VERSION, KSU_* make variables and ccflags -D defines, KSUD_PATH macro name,
#   KSU_* module-script environment variable names
#
# Deliberately KEPT (ecosystem / legal / JNI):
#   /data/adb/modules, post-fs-data.d, service.d, module registry URLs
#   (modules.kernelsu.org, kernelsu.org templates, mui.kernelsu.org),
#   java package me.weishu.kernelsu (Kotlin/Java/JNI symbols), Kotlin class names,
#   deep-link scheme "ksu", upstream doc links, LICENSE files,
#   userspace/meta-overlayfs, website/, js/, docs/, fastlane/, kernel/setup.sh
#
# Protected from bulk rename (third-party / upstream functional URLs):
#   github.com/KernelSU2/*  (java-properties, adb_client, ksu_props, rustix)
#   github.com/tiann/KernelSU (setup.sh clone, comment links)
#   kernelsu.org
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
cd "$ROOT"

MODE=${1:-apply}
NO_MANAGER=0
for a in "$@"; do
    case "$a" in
        --no-manager) NO_MANAGER=1 ;;
    esac
done

# ---------------------------------------------------------------------------
# collected source files (text only; prebuilt binaries never touched)
# ---------------------------------------------------------------------------
collect_files() {
    dirs=""
    for d in kernel uapi userspace/ksud userspace/drdcd userspace/ksuinit userspace/drdcinit; do
        if [ -d "$d" ]; then dirs="$dirs $d"; fi
    done
    # shellcheck disable=SC2086
    find $dirs -type f \
        \( -name '*.c' -o -name '*.h' -o -name '*.S' -o -name '*.rs' -o -name '*.toml' \
           -o -name '*.sh' -o -name '*.py' -o -name 'Kbuild' -o -name 'Makefile' \
           -o -name 'Kconfig' -o -name 'banner' \) \
        ! -path 'kernel/preinit/runner/nolibc/*' \
        ! -path 'kernel/setup.sh' \
        ! -name 'LICENSE*'
    if [ -f Cargo.toml ]; then echo Cargo.toml; fi
    if [ -f Cargo.lock ]; then echo Cargo.lock; fi
    if [ -f justfile ]; then echo justfile; fi
}

sed_collected() {
    collect_files | while IFS= read -r f; do
        sed -i "$@" "$f"
    done
}

# ---------------------------------------------------------------------------
# apply
# ---------------------------------------------------------------------------
apply() {
    echo "== phase 1: values scoped to a single file =="
    if [ -f uapi/supercall.h ]; then
        sed -i "s/'K'/'D'/g" uapi/supercall.h
        echo "   uapi/supercall.h: ioctl type 'K' -> 'D' (22 ioctls)"
    fi
    if [ -f kernel/Kbuild ]; then
        sed -i \
            -e 's/0x033b/0x0330/' \
            -e 's/c371061b19d8c7d7d6133c6a9bafe198fa944e50c1b31c9d8daa8d7f1fc2d2d6/a339bd48cd5706135778273537704eddd4386e0c9fe50f70a81696c14dc859fe/' \
            kernel/Kbuild
        echo "   kernel/Kbuild: sign defaults -> 0x0330 / a339bd48..."
    fi

    echo "== phase 2: bulk rename (kernel / uapi / userspace / workspace files) =="
    sed_collected \
        -e 's#github\.com/KernelSU2#github.com/@@GH2@@#g' \
        -e 's#github\.com/tiann/KernelSU#github.com/tiann/@@GH1@@#g' \
        -e 's#kernelsu\.org#@@KSORG@@#g' \
        -e 's#/ksu_props#/@@PROPS@@#g' \
        -e 's/me\.weishu\.kernelsu\.pr/com.drdc.winter.pr/g' \
        -e 's/me\.weishu\.kernelsu/com.drdc.winter/g' \
        -e 's/0xDEADBEEF/0x44524443/g' \
        -e 's/0xdeadbeef/0x44524443/g' \
        -e 's/0xCAFEBABE/0x57494E54/g' \
        -e 's/0xcafebabe/0x57494E54/g' \
        -e 's/0x7f4b5355/0x7f445244/g' \
        -e 's/KSULKM1/DRDCLKM1/g' \
        -e 's/kernelsu/drdc/g' \
        -e 's/KernelSU/DRDC/g' \
        -e 's/ksud/drdcd/g' \
        -e 's/ksu/drdc/g' \
        -e 's#@@GH2@@#KernelSU2#g' \
        -e 's#@@GH1@@#KernelSU#g' \
        -e 's#@@KSORG@@#kernelsu.org#g' \
        -e 's#@@PROPS@@#ksu_props#g'
    n=$(collect_files | wc -l)
    echo "   done: $n files"

    echo "== phase 3: file / dir renames =="
    mv_path() {
        if [ -e "$1" ] && [ ! -e "$2" ]; then
            mv "$1" "$2"
            echo "   $1 -> $2"
        fi
    }
    mv_path kernel/include/ksu.h              kernel/include/drdc.h
    mv_path kernel/runtime/ksud.h             kernel/runtime/drdcd.h
    mv_path kernel/runtime/ksud_boot.h        kernel/runtime/drdcd_boot.h
    mv_path kernel/runtime/ksud_integration.c kernel/runtime/drdcd_integration.c
    mv_path uapi/ksu.h                        uapi/drdc.h
    mv_path userspace/ksud                    userspace/drdcd
    mv_path userspace/ksuinit                 userspace/drdcinit
    mv_path userspace/drdcd/src/ksucalls.rs   userspace/drdcd/src/drdccalls.rs
    mv_path userspace/drdcd/src/ksu_uapi.rs   userspace/drdcd/src/drdc_uapi.rs
    mv_path userspace/drdcd/src/ksu_uapi.h    userspace/drdcd/src/drdc_uapi.h

    if [ "$NO_MANAGER" = "0" ]; then
        echo "== phase 4: manager (targeted; java package + JNI stay) =="
        # 4a Kotlin / Java: literal strings only
        find manager/app/src -type f \( -name '*.kt' -o -name '*.java' \) -print |
        while IFS= read -r f; do
            sed -i \
                -e 's#/data/adb/ksu#/data/adb/drdc#g' \
                -e 's#/data/adb/ksud#/data/adb/drdcd#g' \
                -e 's/libksud\.so/libdrdcd.so/g' \
                -e 's/System\.loadLibrary("kernelsu")/System.loadLibrary("drdc")/g' \
                -e 's/"KernelSUMagica"/"DRDCMagica"/g' \
                -e 's/"KernelSU"/"DRDC"/g' \
                -e 's#"KernelSU/#"DRDC/#g' \
                -e 's/KernelSU_bugreport_/DRDC_bugreport_/g' \
                -e 's/KernelSU_module_action_log/DRDC_module_action_log/g' \
                -e 's/KernelSU_install_log_/DRDC_install_log_/g' \
                -e 's/KernelSU_script_log/DRDC_script_log/g' \
                -e 's/KernelSU deep link/DRDC deep link/g' \
                -e 's/\.ksurc/.drdcrc/g' \
                -e 's/\bksud\b/drdcd/g' \
                "$f"
        done
        # 4b res strings mentioning ksud (all locales)
        find manager/app/src/main/res -name 'strings.xml' -print |
        while IFS= read -r f; do
            sed -i \
                -e 's#/data/adb/ksu#/data/adb/drdc#g' \
                -e 's/\.ksurc/.drdcrc/g' \
                -e 's/\bksud\b/drdcd/g' \
                "$f"
        done
        # 4c cpp: lowercase blanket, JNI symbols protected; maxdepth 1 skips the uapi include dir
        find manager/app/src/main/cpp -maxdepth 1 -type f \( -name '*.cc' -o -name '*.h' -o -name '*.cpp' -o -name '*.cxx' \) -print |
        while IFS= read -r f; do
            sed -i \
                -e 's/Java_me_weishu_kernelsu/@@JNI@@/g' \
                -e 's/kernelsu/drdc/g' \
                -e 's/KernelSU/DRDC/g' \
                -e 's/ksud/drdcd/g' \
                -e 's/ksu/drdc/g' \
                -e 's/0xDEADBEEF/0x44524443/g' \
                -e 's/0xdeadbeef/0x44524443/g' \
                -e 's/0xCAFEBABE/0x57494E54/g' \
                -e 's/0xcafebabe/0x57494E54/g' \
                -e 's/@@JNI@@/Java_me_weishu_kernelsu/g' \
                "$f"
        done
        mv_path manager/app/src/main/cpp/ksu.cc manager/app/src/main/cpp/drdc.cc
        mv_path manager/app/src/main/cpp/ksu.h  manager/app/src/main/cpp/drdc.h
        # 4d CMake: project + target + sources
        if [ -f manager/app/src/main/cpp/CMakeLists.txt ]; then
            sed -i \
                -e 's/project("kernelsu")/project("drdc")/' \
                -e 's/add_library(kernelsu/add_library(drdc/' \
                -e 's/target_include_directories(kernelsu/target_include_directories(drdc/' \
                -e 's/target_link_libraries(kernelsu/target_link_libraries(drdc/' \
                -e 's/ksu\.cc/drdc.cc/' \
                manager/app/src/main/cpp/CMakeLists.txt
        fi
        # 4e misc one-file edits
        if [ -f manager/app/src/main/jniLibs/.gitignore ]; then
            sed -i 's/libksud\.so/libdrdcd.so/' manager/app/src/main/jniLibs/.gitignore
        fi
        if [ -f manager/settings.gradle.kts ]; then
            sed -i 's/rootProject\.name = "KernelSU"/rootProject.name = "DRDC"/' manager/settings.gradle.kts
        fi
        if [ -f manager/app/build.gradle.kts ]; then
            sed -i \
                -e 's#"me\.weishu\.kernelsu\.pr" else "me\.weishu\.kernelsu"#"com.drdc.winter.pr" else "com.drdc.winter"#' \
                -e 's#"KernelSU PR" else "KernelSU"#"冬日渡川 PR" else "冬日渡川"#' \
                -e 's/libksud/libdrdcd/g' \
                manager/app/build.gradle.kts
        fi
        echo "   manager targeted edits done"
    else
        echo "== phase 4: manager SKIPPED (--no-manager) =="
    fi

    echo "== phase 5: verify =="
    verify "$NO_MANAGER" || exit 1
}

# ---------------------------------------------------------------------------
# verify
# ---------------------------------------------------------------------------
verify() {
    skipmgr=${1:-0}
    fail=0
    ok()  { echo "  [OK]   $1"; }
    bad() { echo "  [FAIL] $1"; fail=1; }

    # magics
    if grep -rq "0xDEADBEEF" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "0xDEADBEEF still present"; else ok "0xDEADBEEF gone"; fi
    if grep -rq "0xdeadbeef" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "0xdeadbeef still present"; else ok "0xdeadbeef gone"; fi
    if grep -rq "0xCAFEBABE" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "0xCAFEBABE still present"; else ok "0xCAFEBABE gone"; fi
    if grep -rq "0x7f4b5355" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "0x7f4b5355 still present"; else ok "0x7f4b5355 gone"; fi
    if grep -q "0x44524443" uapi/supercall.h; then ok "install magic1 = 0x44524443"; else bad "install magic1 missing"; fi
    if grep -q "0x57494E54" uapi/supercall.h; then ok "install magic2 = 0x57494E54"; else bad "install magic2 missing"; fi
    if grep -q "0x7f445244" kernel/policy/allowlist.c; then ok "allowlist FILE_MAGIC = 0x7f445244"; else bad "allowlist FILE_MAGIC"; fi
    if grep -q "0x7f445244" userspace/drdcd/src/feature.rs; then ok "feature config magic = 0x7f445244"; else bad "feature config magic"; fi
    if grep -q "DRDCLKM1" userspace/drdcd/src/lkm_image.rs; then ok "capsule magic = DRDCLKM1"; else bad "capsule magic"; fi
    if grep -q "0x0330" kernel/Kbuild && grep -q "a339bd48" kernel/Kbuild; then ok "Kbuild signing defaults updated"; else bad "Kbuild signing defaults"; fi
    # ioctl type
    if grep -q "'D'" uapi/supercall.h && ! grep -q "'K'" uapi/supercall.h; then ok "ioctl type = 'D'"; else bad "ioctl type not renamed"; fi
    # paths
    if grep -rq "/data/adb/ksu" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "/data/adb/ksu still present"; else ok "/data/adb/ksu gone"; fi
    if grep -rq "ksurc" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "ksurc still present"; else ok "ksurc -> drdcrc"; fi
    if grep -q '"/data/adb/drdcd"' kernel/runtime/drdcd.h; then ok "KSUD_PATH value = /data/adb/drdcd"; else bad "KSUD_PATH value"; fi
    # inode names
    if grep -rq "\[ksu_driver" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "[ksu_driver] still present"; else ok "[ksu_driver] gone"; fi
    if grep -rq "\[ksu_fdwrapper" kernel uapi userspace/drdcd userspace/drdcinit 2>/dev/null; then bad "[ksu_fdwrapper] still present"; else ok "[ksu_fdwrapper] gone"; fi
    # protected URLs intact
    if grep -q "github.com/KernelSU2/ksu_props" Cargo.lock; then ok "Cargo.lock ksu_props URL intact"; else bad "Cargo.lock URL damaged"; fi
    if grep -q "github.com/KernelSU2/ksu_props" userspace/drdcd/Cargo.toml; then ok "ksud Cargo.toml URL intact"; else bad "ksud Cargo.toml URL damaged"; fi
    if grep -q "git clone https://github.com/tiann/KernelSU" kernel/setup.sh; then ok "kernel/setup.sh untouched"; else bad "kernel/setup.sh damaged"; fi
    if grep -rIq "@@" kernel uapi userspace/drdcd userspace/drdcinit Cargo.toml Cargo.lock justfile 2>/dev/null; then bad "placeholder @@ left behind"; else ok "no leftover placeholders"; fi
    # workspace
    if grep -q 'userspace/drdcd' Cargo.toml && grep -q 'userspace/drdcinit' Cargo.toml; then ok "root Cargo.toml workspace renamed"; else bad "root Cargo.toml workspace"; fi
    if grep -q 'name = "drdcd"' Cargo.lock && grep -q 'name = "drdcinit"' Cargo.lock; then ok "Cargo.lock package names renamed"; else bad "Cargo.lock package names"; fi
    if grep -q 'libdrdcd\.so' justfile; then ok "justfile -> libdrdcd.so"; else bad "justfile"; fi
    # build artifacts
    if grep -q 'obj-$(CONFIG_KSU) += drdc.o' kernel/Kbuild; then ok "Kbuild builds drdc.o"; else bad "Kbuild obj name"; fi
    if grep -q 'drdc.ko' kernel/Makefile; then ok "Makefile checks drdc.ko"; else bad "Makefile"; fi
    if grep -q 'KSU_PACKAGE_NAME' kernel/Kbuild; then ok "KSU_PACKAGE_NAME kept"; else bad "KSU_PACKAGE_NAME lost"; fi
    if grep -q 'com.drdc.winter' kernel/Kbuild; then ok "Kbuild package default = com.drdc.winter"; else bad "Kbuild package default"; fi
    if grep -q 'CONFIG_KSU' kernel/Kbuild; then ok "CONFIG_KSU kept"; else bad "CONFIG_KSU lost"; fi
    # file moves
    if [ -f uapi/drdc.h ] && [ ! -e uapi/ksu.h ]; then ok "uapi/drdc.h in place"; else bad "uapi/drdc.h"; fi
    if [ -d userspace/drdcd ] && [ ! -e userspace/ksud ]; then ok "userspace/drdcd in place"; else bad "userspace/drdcd"; fi
    if [ -d userspace/drdcinit ] && [ ! -e userspace/ksuinit ]; then ok "userspace/drdcinit in place"; else bad "userspace/drdcinit"; fi
    if [ -f kernel/runtime/drdcd.h ] && [ -f kernel/runtime/drdcd_integration.c ]; then ok "kernel runtime renamed"; else bad "kernel runtime files"; fi
    if [ -f userspace/drdcd/src/drdccalls.rs ] && [ -f userspace/drdcd/src/drdc_uapi.rs ] && [ -f userspace/drdcd/src/drdc_uapi.h ]; then ok "drdcd src renamed"; else bad "drdcd src files"; fi
    # manager
    if [ "$skipmgr" = "0" ]; then
        if grep -rq "libksud" manager/app/src 2>/dev/null; then bad "libksud still referenced in manager"; else ok "manager libksud gone"; fi
        if grep -rq "uapi/ksu\.h" manager/app/src/main/cpp 2>/dev/null; then bad "uapi/ksu.h include still present"; else ok "manager include -> uapi/drdc.h"; fi
        if grep -rq 'loadLibrary("kernelsu")' manager/app/src 2>/dev/null; then bad "loadLibrary(kernelsu) still present"; else ok "loadLibrary(drdc)"; fi
        if grep -rq "0xDEADBEEF" manager/app/src 2>/dev/null; then bad "manager 0xDEADBEEF still present"; else ok "manager magic gone"; fi
        if grep -rq "\[ksu_driver" manager/app/src/main/cpp 2>/dev/null; then bad "manager [ksu_driver] still present"; else ok "manager inode renamed"; fi
        if grep -rq "Java_me_weishu_kernelsu" manager/app/src/main/cpp 2>/dev/null; then ok "JNI symbols intact"; else bad "JNI symbols damaged"; fi
        if [ -f manager/app/src/main/cpp/drdc.h ] && [ -f manager/app/src/main/cpp/drdc.cc ] && [ ! -e manager/app/src/main/cpp/ksu.h ]; then ok "manager cpp files renamed"; else bad "manager cpp files"; fi
        if grep -q 'add_library(drdc' manager/app/src/main/cpp/CMakeLists.txt && grep -q 'target_link_libraries(drdc' manager/app/src/main/cpp/CMakeLists.txt; then ok "CMake target = drdc"; else bad "CMake target"; fi
        if grep -q 'com.drdc.winter' manager/app/build.gradle.kts && grep -q 'namespace = "me.weishu.kernelsu"' manager/app/build.gradle.kts; then ok "gradle: pkg default new, namespace kept"; else bad "gradle package defaults"; fi
        if grep -q 'drdc_driver' manager/app/src/main/cpp/drdc.cc; then ok "manager [drdc_driver] present"; else bad "manager [drdc_driver] missing"; fi
        if grep -q 'rootProject.name = "DRDC"' manager/settings.gradle.kts; then ok "settings.gradle.kts root name"; else bad "settings.gradle.kts"; fi
    fi

    if [ "$fail" = "0" ]; then echo "== ALL CHECKS PASSED =="; else echo "== VERIFY FAILED =="; fi
    return "$fail"
}

case "$MODE" in
    apply)  apply ;;
    verify) verify "$NO_MANAGER" ;;
    *) echo "usage: $0 {apply|verify} [--no-manager]"; exit 2 ;;
esac
