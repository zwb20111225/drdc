alias bk := build_drdcd
alias bm := build_manager

build_drdcd:
    cross build --target aarch64-linux-android --release

build_manager: build_drdcd
    cp target/aarch64-linux-android/release/drdcd manager/app/src/main/jniLibs/arm64-v8a/libdrdcd.so
    cd manager && ./gradlew aDebug

clippy:
    cargo fmt
    cross clippy --target aarch64-linux-android --release
