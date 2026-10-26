#!/usr/bin/env bash

# this is the most cursed thing i have ever written i am so unbelievebly sorry
# but it lets us run the ios build on linux so no complaints right :P

set -euo pipefail

KONAN_VERSION="${KONAN_VERSION:?KONAN_VERSION is required}"
OVERLAY_DIR="${OVERLAY_DIR:?OVERLAY_DIR is required}"
CACHE_DIR="${CACHE_DIR:?CACHE_DIR is required}"
KONAN_DATA_DIR="${KONAN_DATA_DIR:-$HOME/.konan}"
THEOS="${THEOS:-$HOME/theos}"
MIN_IOS_VERSION="${MIN_IOS_VERSION:-14.0}"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "[ios-cross] $*"; }

MARKER="$OVERLAY_DIR/.provisioned-$KONAN_VERSION"

BASE="${KONAN_BASE_DIR:-}"
if [[ -z "$BASE" ]]; then
  for d in "$KONAN_DATA_DIR"/kotlin-native-prebuilt-linux-*-"$KONAN_VERSION"; do
    [[ -d "$d" ]] && BASE="$d" && break
  done
fi
[[ -n "$BASE" && -f "$BASE/konan/konan.properties" ]] \
  || die "no Linux Kotlin/Native $KONAN_VERSION distribution found under $KONAN_DATA_DIR (install the matching Kotlin Gradle plugin toolchain first)"

if [[ -f "$MARKER" && -z "${FORCE_PROVISION:-}" ]]; then
  info "overlay already provisioned at $OVERLAY_DIR"
  exit 0
fi

[[ "$OVERLAY_DIR" != "/" && "$OVERLAY_DIR" != "$HOME" ]] \
  || die "refusing to wipe OVERLAY_DIR=$OVERLAY_DIR"

[[ -d "$THEOS/sdks" ]] || die "Theos SDK directory not found at $THEOS/sdks (set THEOS)"
if [[ -z "${SDK_NAME:-}" ]]; then
  SDK_NAME="$(
    find "$THEOS/sdks" -maxdepth 1 -type d -name 'iPhoneOS*.sdk' -printf '%f\n' 2>/dev/null \
      | sort -V | tail -n 1
  )"
fi

if [[ -n "${SDK_NAME:-}" && ! -d "$THEOS/sdks/$SDK_NAME" && -d "$THEOS/sdks/$SDK_NAME.sdk" ]]; then
  SDK_NAME="$SDK_NAME.sdk"
fi
[[ -n "${SDK_NAME:-}" && -d "$THEOS/sdks/$SDK_NAME" ]] \
  || die "no iPhoneOS SDK found under $THEOS/sdks (set SDK_NAME explicitly)"
SDK="$THEOS/sdks/$SDK_NAME"

LLVM_VERSION="$(
  grep -E '^llvmVersion\.macos_x64 *=' "$BASE/konan/konan.properties" \
    | head -n1 | cut -d= -f2 | tr -d ' '
)"
if [[ -z "$LLVM_VERSION" ]]; then
  LLVM_VERSION="$(
    grep -E '^llvmVersion\.macos_arm64 *=' "$BASE/konan/konan.properties" \
      | head -n1 | cut -d= -f2 | tr -d ' '
  )"
fi
LLVM_VERSION="${LLVM_VERSION:-21}"

konan_property() {
  grep -E "^$1 *=" "$BASE/konan/konan.properties" \
    | head -n1 | cut -d= -f2- | sed 's/^ *//; s/ *$//'
}

KONAN_LLVM_HOME="${KONAN_LLVM_HOME:-}"
if [[ -z "$KONAN_LLVM_HOME" && -z "${LLVM_HOME:-}" ]]; then
  deps_url="$(konan_property dependenciesUrl)"
  deps_url="${deps_url:-https://download.jetbrains.com/kotlin/native}"
  llvm_dist="$(konan_property 'llvm\.linux_x64\.user')"
  llvm_dist="${llvm_dist:-llvm-$LLVM_VERSION-x86_64-linux-essentials}"
  llvm_path="$(konan_property "$llvm_dist\\.default")"
  llvm_path="${llvm_path#remote:public:}"
  llvm_path="${llvm_path:-resources/llvm/$LLVM_VERSION-x86_64-linux}"

  llvm_cache="$CACHE_DIR/llvm/$llvm_dist"
  if [[ ! -x "$llvm_cache/bin/clang++" ]]; then
    mkdir -p "$CACHE_DIR/llvm"
    tmp="$(mktemp -d "$CACHE_DIR/llvm/.llvm.XXXXXX")"
    url="$deps_url/$llvm_path/$llvm_dist.tar.gz"
    info "downloading Kotlin/Native LLVM $LLVM_VERSION (one-time, cached)"
    info "  $url"
    if ! curl -fL --retry 3 --retry-delay 2 -o - "$url" \
      | tar -xzf - -C "$tmp" --strip-components=1; then
      rm -rf "$tmp"
      die "failed to download the Kotlin/Native LLVM toolchain from $url"
    fi
    if [[ ! -x "$tmp/bin/clang++" ]]; then
      rm -rf "$tmp"
      die "downloaded Kotlin/Native LLVM archive is missing bin/clang++"
    fi
    rm -rf "$llvm_cache"
    mv "$tmp" "$llvm_cache"
  fi
  info "Kotlin/Native LLVM: $llvm_cache"
  KONAN_LLVM_HOME="$llvm_cache"
fi

llvm_home_bin() {
  local dir
  for dir in \
    "${KONAN_LLVM_HOME:+$KONAN_LLVM_HOME/bin}" \
    "${LLVM_HOME:+$LLVM_HOME/bin}" \
    "/usr/lib/llvm$LLVM_VERSION/bin" \
    "/usr/lib/llvm-$LLVM_VERSION/bin" \
    "/usr/local/opt/llvm@$LLVM_VERSION/bin" \
    "/opt/homebrew/opt/llvm@$LLVM_VERSION/bin"; do
    if [[ -n "$dir" && -x "$dir/$1" ]]; then
      printf '%s' "$dir/$1"
      return 0
    fi
  done
  return 1
}

resolve_tool() {
  local p
  p="$(command -v "$1" 2>/dev/null || true)"
  [[ -n "$p" ]] || die "required tool '$1' not found on PATH"
  printf '%s' "$p"
}

resolve_optional() {
  command -v "$1" 2>/dev/null || true
}

resolve_versioned() {
  local p
  p="$(llvm_home_bin "$1" || true)"
  if [[ -n "$p" ]]; then
    printf '%s' "$p"
    return 0
  fi
  p="$(command -v "$1-$LLVM_VERSION" 2>/dev/null || true)"
  if [[ -n "$p" ]]; then
    printf '%s' "$p"
    return 0
  fi
  resolve_tool "$1"
}

resolve_optional_versioned() {
  local p
  p="$(llvm_home_bin "$1" || true)"
  if [[ -n "$p" ]]; then
    printf '%s' "$p"
    return 0
  fi
  p="$(command -v "$1-$LLVM_VERSION" 2>/dev/null || true)"
  if [[ -n "$p" ]]; then
    printf '%s' "$p"
    return 0
  fi
  resolve_optional "$1"
}

CLANG="$(resolve_versioned clang)"
LLVM_AR="$(resolve_versioned llvm-ar)"
LLVM_RANLIB="$(resolve_versioned llvm-ranlib)"
LLVM_NM="$(resolve_versioned llvm-nm)"
CLANGXX="$(resolve_optional_versioned clang++)"
LLVM_STRIP="$(resolve_optional_versioned llvm-strip)"
LLVM_DSYMUTIL="$(resolve_optional_versioned llvm-dsymutil)"
LLVM_LIBTOOL_DARWIN="$(resolve_optional_versioned llvm-libtool-darwin)"
LLVM_LIPO="$(resolve_optional_versioned llvm-lipo)"
LLVM_INSTALL_NAME_TOOL="$(resolve_optional_versioned llvm-install-name-tool)"

LD64="$(llvm_home_bin ld64.lld || true)"
if [[ -z "$LD64" ]]; then
  for candidate in "ld64.lld-$LLVM_VERSION" ld64.lld ld.lld; do
    found_tool="$(command -v "$candidate" 2>/dev/null || true)"
    if [[ -n "$found_tool" ]]; then
      LD64="$found_tool"
      break
    fi
  done
fi
[[ -n "$LD64" ]] || die "required tool 'ld64.lld' not found on PATH (need an LLVM Mach-O linker)"

RESOURCE_DIR="$("$CLANG" -print-resource-dir 2>/dev/null || true)"
[[ -n "$RESOURCE_DIR" && -d "$RESOURCE_DIR" ]] || die "could not determine clang resource directory"

CLANG_MAJOR="$("$CLANG" --version 2>/dev/null | grep -oE '[0-9]+' | head -n1 || true)"
if [[ -n "$CLANG_MAJOR" && "$CLANG_MAJOR" != "$LLVM_VERSION" ]]; then
  die "clang $CLANG_MAJOR ($CLANG) does not match Kotlin/Native's LLVM $LLVM_VERSION; set LLVM_HOME or KONAN_LLVM_HOME to the exact toolchain, or unset it so this script downloads the one Kotlin/Native declares"
fi

info "base distribution : $BASE"
info "overlay           : $OVERLAY_DIR"
info "Theos SDK         : $SDK_NAME ($SDK)"
info "system clang      : $CLANG ($RESOURCE_DIR) -> lib/clang/$LLVM_VERSION"

MACOS_PREBUILT_BASE_URL="https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-native-prebuilt/$KONAN_VERSION/kotlin-native-prebuilt-$KONAN_VERSION-macos"
IOS_RUNTIME_CACHE="$CACHE_DIR/ios_arm64-$KONAN_VERSION"

rm -rf "$OVERLAY_DIR"
mkdir -p "$OVERLAY_DIR/konan/targets"

info "linking distribution contents"
for entry in "$BASE"/*; do
  name="$(basename "$entry")"
  case "$name" in
    konan|provisioned.ok) continue ;;
  esac
  ln -s "$entry" "$OVERLAY_DIR/$name"
done

for entry in "$BASE"/konan/*; do
  name="$(basename "$entry")"
  case "$name" in
    konan.properties|targets) continue ;;
  esac
  ln -s "$entry" "$OVERLAY_DIR/konan/$name"
done

for entry in "$BASE"/konan/targets/*; do
  ln -s "$entry" "$OVERLAY_DIR/konan/targets/$(basename "$entry")"
done

if [[ ! -f "$IOS_RUNTIME_CACHE/runtime.fingerprint" ]]; then
  mkdir -p "$CACHE_DIR"
  tmp="$(mktemp -d "$CACHE_DIR/.ios-runtime.XXXXXX")"
  downloaded=""
  for arch in aarch64 x86_64; do
    url="$MACOS_PREBUILT_BASE_URL-$arch.tar.gz"
    info "downloading iOS runtime bitcode (one-time, cached) from $url"
    if curl -fL --retry 3 --retry-delay 2 -o - "$url" \
      | tar -xzf - -C "$tmp" --wildcards --no-anchored 'konan/targets/ios_arm64'; then
      downloaded="$arch"
      break
    fi
    info "download from $url failed, trying next host variant"
    rm -rf "$tmp"
    mkdir -p "$tmp"
  done
  if [[ -z "$downloaded" ]]; then
    rm -rf "$tmp"
    die "failed to download the macOS prebuilt for $KONAN_VERSION (tried aarch64 and x86_64)"
  fi
  found="$(find "$tmp" -type d -path '*/konan/targets/ios_arm64' -print -quit)"
  if [[ -z "$found" || ! -f "$found/runtime.fingerprint" ]]; then
    rm -rf "$tmp"
    die "failed to extract konan/targets/ios_arm64 from the macOS prebuilt"
  fi
  rm -rf "$IOS_RUNTIME_CACHE"
  mv "$found" "$IOS_RUNTIME_CACHE"
  rm -rf "$tmp"
fi
info "iOS runtime bitcode: $IOS_RUNTIME_CACHE"

rm -rf "$OVERLAY_DIR/konan/targets/ios_arm64"
cp -R "$IOS_RUNTIME_CACHE" "$OVERLAY_DIR/konan/targets/ios_arm64"

LLVM_DIR="$OVERLAY_DIR/llvm"
mkdir -p "$LLVM_DIR/bin" "$LLVM_DIR/lib/clang"
ln -s "$CLANG" "$LLVM_DIR/bin/clang"
if [[ -n "$CLANGXX" ]]; then
  ln -s "$CLANGXX" "$LLVM_DIR/bin/clang++"
fi
ln -s "$LLVM_AR" "$LLVM_DIR/bin/llvm-ar"
ln -s "$LLVM_RANLIB" "$LLVM_DIR/bin/llvm-ranlib"
ln -s "$LLVM_NM" "$LLVM_DIR/bin/llvm-nm"
ln -s "$LD64" "$LLVM_DIR/bin/ld64.lld"
if [[ -n "$LLVM_STRIP" ]]; then
  ln -s "$LLVM_STRIP" "$LLVM_DIR/bin/llvm-strip"
fi
if [[ -n "$LLVM_DSYMUTIL" ]]; then
  ln -s "$LLVM_DSYMUTIL" "$LLVM_DIR/bin/llvm-dsymutil"
fi
ln -s "$RESOURCE_DIR" "$LLVM_DIR/lib/clang/$LLVM_VERSION"

TOOLCHAIN="$OVERLAY_DIR/toolchain"
mkdir -p "$TOOLCHAIN/usr/bin"
if [[ -n "$LLVM_LIBTOOL_DARWIN" ]]; then
  info "using llvm-libtool-darwin as libtool: $LLVM_LIBTOOL_DARWIN"
  ln -s "$LLVM_LIBTOOL_DARWIN" "$TOOLCHAIN/usr/bin/libtool"
else
  info "llvm-libtool-darwin not found; installing fallback libtool shim"
  cat > "$TOOLCHAIN/usr/bin/libtool" <<EOF
#!/usr/bin/env bash
# Fallback Apple-compatible \`libtool\`, used only when llvm-libtool-darwin is
# unavailable. Good enough for the archive step Kotlin/Native performs:
#   libtool -static -D -o OUT -arch_only arm64 OBJS...
# Unknown options are ignored and only existing files are archived, so extra
# flags that may be passed (e.g. -syslibroot) cannot end up as ar inputs.
out=""
filelist=""
objs=()
while [ \$# -gt 0 ]; do
  case "\$1" in
    -static|-D|-U|-no_warning_for_no_symbols|-warnings_as_errors) shift ;;
    -o) out="\$2"; shift 2 ;;
    -arch_only|-dependency_info|-filelist|-L|-segalign)
      if [ "\$1" = "-filelist" ]; then filelist="\$2"; fi
      shift 2 ;;
    -syslibroot|-l|-framework|-weak_framework|-reexport_framework) shift 2 ;;
    -l*|-framework*) shift ;;
    -*) shift ;;
    *) objs+=("\$1"); shift ;;
  esac
done
if [ -n "\$filelist" ]; then
  list="\${filelist%%,*}"
  while IFS= read -r line || [ -n "\$line" ]; do
    if [ -n "\$line" ]; then objs+=("\$line"); fi
  done < "\$list"
fi
filtered=()
for o in "\${objs[@]}"; do
  if [ -f "\$o" ]; then filtered+=("\$o"); fi
done
if [ -z "\$out" ]; then echo "libtool shim: missing -o output" >&2; exit 1; fi
if [ "\${#filtered[@]}" -eq 0 ]; then echo "libtool shim: no input files" >&2; exit 1; fi
rm -f "\$out"
exec "$LLVM_AR" crs "\$out" "\${filtered[@]}"
EOF
  chmod +x "$TOOLCHAIN/usr/bin/libtool"
fi
ln -s "$LD64" "$TOOLCHAIN/usr/bin/ld"
ln -s "$LD64" "$TOOLCHAIN/usr/bin/ld64.lld"
if [[ -n "$LLVM_STRIP" ]]; then
  ln -s "$LLVM_STRIP" "$TOOLCHAIN/usr/bin/strip"
fi
if [[ -n "$LLVM_DSYMUTIL" ]]; then
  ln -s "$LLVM_DSYMUTIL" "$TOOLCHAIN/usr/bin/dsymutil"
fi
if [[ -n "$LLVM_LIPO" ]]; then
  ln -s "$LLVM_LIPO" "$TOOLCHAIN/usr/bin/lipo"
fi
if [[ -n "$LLVM_INSTALL_NAME_TOOL" ]]; then
  ln -s "$LLVM_INSTALL_NAME_TOOL" "$TOOLCHAIN/usr/bin/install_name_tool"
fi

info "writing konan.properties overrides"
grep -v -E '^(llvmHome\.macos_x64|llvmHome\.macos_arm64|targetSysRoot\.ios_arm64|targetToolchain\.macos_x64-ios_arm64|targetToolchain\.macos_arm64-ios_arm64|additionalToolsDir\.macos_x64|additionalToolsDir\.macos_arm64|minVersion\.ios) *=' \
  "$BASE/konan/konan.properties" > "$OVERLAY_DIR/konan/konan.properties"

{
  echo ""
  echo "# --- Valenced project overlay (generated; do not edit) ---"
  echo "llvmHome.macos_x64 = $LLVM_DIR"
  echo "llvmHome.macos_arm64 = $LLVM_DIR"
  echo "targetSysRoot.ios_arm64 = $SDK"
  echo "targetToolchain.macos_x64-ios_arm64 = $OVERLAY_DIR/toolchain"
  echo "targetToolchain.macos_arm64-ios_arm64 = $OVERLAY_DIR/toolchain"
  echo "additionalToolsDir.macos_x64 = $OVERLAY_DIR/toolchain"
  echo "additionalToolsDir.macos_arm64 = $OVERLAY_DIR/toolchain"
  echo "minVersion.ios = $MIN_IOS_VERSION"
  echo "$SDK.default = $SDK"
  echo "$OVERLAY_DIR/toolchain.default = $OVERLAY_DIR/toolchain"
} >> "$OVERLAY_DIR/konan/konan.properties"

[[ -f "$OVERLAY_DIR/konan/lib/kotlin-native-compiler-embeddable.jar" ]] \
  || die "overlay is missing konan/lib/kotlin-native-compiler-embeddable.jar after provisioning"
[[ -x "$OVERLAY_DIR/toolchain/usr/bin/libtool" ]] \
  || die "overlay is missing an executable toolchain/usr/bin/libtool after provisioning"

touch "$MARKER"
info "done: $OVERLAY_DIR"
