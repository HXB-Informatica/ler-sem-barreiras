#!/usr/bin/env bash
# Arranca o emulador, o Metro e a app React Native para iteração rápida.
# Uso: ./scripts/dev-android.sh
# Ctrl+C pára o Metro. O emulador fica a correr.

set -euo pipefail

AVD_NAME="${AVD_NAME:-LerTablet}"
APP_ID="pt.lersembarreiras.mobile"
LOG_FILE="${EMULATOR_LOG:-${TMPDIR:-/tmp}/ler-tablet-emulator.log}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MOBILE_DIR="$REPO_ROOT/mobile"
METRO_PORT="${METRO_PORT:-8081}"
HOST_MIC_PORT="${HOST_MIC_PORT:-27183}"
STARTED_METRO=""
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
EMULATOR=""
ADB=""
HEADLESS=0
XWAYLAND_LOG="${XWAYLAND_LOG:-${TMPDIR:-/tmp}/ler-tablet-xwayland.log}"

resolve_android_home() {
  local dir=""
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME:-}" ]]; then
    dir="$ANDROID_HOME"
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT:-}" ]]; then
    dir="$ANDROID_SDK_ROOT"
  elif [[ -f "$REPO_ROOT/local.properties" ]]; then
    dir="$(sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" | tail -n1)"
    dir="${dir//\\//}"
  fi
  if [[ -z "$dir" || ! -d "$dir" ]]; then
    dir="${HOME}/Android/Sdk"
  fi
  if [[ ! -d "$dir" ]]; then
    echo "Android SDK não encontrado. Instale o SDK em $HOME/Android/Sdk ou defina ANDROID_HOME." >&2
    exit 1
  fi
  ANDROID_HOME="$dir"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
  EMULATOR="$ANDROID_HOME/emulator/emulator"
  ADB="$ANDROID_HOME/platform-tools/adb"
}

pick_java() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME:-}/bin/javac" ]]; then
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
    return
  fi
  local candidate
  for candidate in \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/default-java
  do
    if [[ -x "$candidate/bin/javac" ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      return
    fi
  done
  echo "JDK com javac não encontrado. Instale o OpenJDK 17." >&2
  exit 1
}

require_sdk() {
  resolve_android_home
  if [[ ! -x "$EMULATOR" || ! -x "$ADB" ]]; then
    echo "Android SDK incompleto em $ANDROID_HOME (faltam emulator ou adb)." >&2
    exit 1
  fi
}

require_kvm() {
  case "$(uname -s)" in
    Linux)
      if [[ ! -e /dev/kvm ]]; then
        echo "KVM não está disponível (/dev/kvm)." >&2
        echo "  sudo apt install qemu-kvm" >&2
        echo "  sudo usermod -aG kvm \$USER" >&2
        exit 1
      fi
      if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
        echo "Sem permissão para /dev/kvm. Adicione o utilizador ao grupo kvm e volte a entrar." >&2
        exit 1
      fi
      ;;
  esac
}

require_node() {
  if ! command -v node >/dev/null || ! command -v npm >/dev/null; then
    echo "Node.js e npm são necessários (versão 22.11 ou superior)." >&2
    exit 1
  fi
}

emulator_pids() {
  {
    pgrep -f "qemu-system-x86_64.*-avd ${AVD_NAME}( |$)" || true
    pgrep -f "/emulator/emulator -avd ${AVD_NAME}( |$)" || true
  } | sort -u
}

avd_exists() {
  if [[ -x "$EMULATOR" ]]; then
    "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME" && return 0
  fi
  [[ -d "${ANDROID_AVD_HOME:-$HOME/.android/avd}/${AVD_NAME}.avd" ]]
}

wait_for_emulator_serial() {
  local attempt serial name
  "$ADB" start-server >/dev/null
  for attempt in $(seq 1 45); do
    if [[ "$attempt" -gt 2 && -z "$(emulator_pids)" ]]; then
      echo "O processo do emulador terminou. Ver $LOG_FILE" >&2
      return 1
    fi
    while read -r serial; do
      [[ -z "$serial" ]] && continue
      name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | awk 'NF { print; exit }' || true)"
      if [[ "$name" == "$AVD_NAME" ]]; then
        echo "$serial"
        return 0
      fi
    done < <("$ADB" devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { print $1 }')
    sleep 2
  done
  echo "O emulador arrancou, mas o adb ainda não o vê. Ver $LOG_FILE" >&2
  return 1
}

wait_for_boot() {
  local serial="$1"
  local attempt
  for attempt in $(seq 1 60); do
    if [[ -z "$(emulator_pids)" ]]; then
      echo "O emulador parou durante o arranque. Ver $LOG_FILE" >&2
      return 1
    fi
    if [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "O emulador não terminou o arranque a tempo. Ver $LOG_FILE" >&2
  return 1
}

kill_failed_emulator() {
  local pids
  pids="$(emulator_pids)"
  if [[ -n "$pids" ]]; then
    kill $pids 2>/dev/null || true
    sleep 1
  fi
}

display_works() {
  local display="${1:-${DISPLAY:-}}"
  [[ -n "$display" ]] || return 1
  command -v xdpyinfo >/dev/null 2>&1 || return 1
  xdpyinfo -display "$display" >/dev/null 2>&1
}

ensure_display() {
  local runtime="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
  local wayland="${WAYLAND_DISPLAY:-wayland-1}"
  local d

  if display_works "${DISPLAY:-}"; then
    echo "Display X11: $DISPLAY"
    return 0
  fi

  if [[ ! -S "$runtime/$wayland" ]]; then
    echo "Sem compositor Wayland. A arrancar o emulador sem janela."
    HEADLESS=1
    return 0
  fi
  export XDG_RUNTIME_DIR="$runtime"
  export WAYLAND_DISPLAY="$wayland"

  for d in 42 43 44 45; do
    if [[ -S "/tmp/.X11-unix/X$d" ]] && ! display_works ":$d"; then
      continue
    fi
    if [[ -S "/tmp/.X11-unix/X$d" ]]; then
      export DISPLAY=":$d"
      echo "A reutilizar Xwayland em $DISPLAY"
      return 0
    fi
    echo "A abrir Xwayland em :$d (o emulador não tem plugin Wayland)..."
    : >"$XWAYLAND_LOG"
    setsid Xwayland ":$d" -geometry 1600x1000 -ac < /dev/null >"$XWAYLAND_LOG" 2>&1 &
    disown || true
    sleep 1
    if display_works ":$d"; then
      export DISPLAY=":$d"
      echo "Xwayland pronto em $DISPLAY"
      return 0
    fi
  done

  echo "Xwayland não arrancou. A usar o emulador sem janela."
  HEADLESS=1
}

launch_emulator() {
  local gpu="$1"
  local -a extra=()
  local qt_platform=xcb
  export QT_SCALE_FACTOR="${QT_SCALE_FACTOR:-0.8}"
  export LD_LIBRARY_PATH="${ANDROID_HOME}/emulator/lib64/qt/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  : >"$LOG_FILE"
  if [[ "$HEADLESS" == 1 ]]; then
    extra+=(-no-window)
    qt_platform=offscreen
  fi
  # O Qt do emulador não tem Wayland; wayland;xcb no ambiente faz o plugin xcb falhar.
  setsid env -u WAYLAND_DISPLAY \
    DISPLAY="${DISPLAY:-:0}" \
    QT_QPA_PLATFORM="$qt_platform" \
    LD_LIBRARY_PATH="$LD_LIBRARY_PATH" \
    "$EMULATOR" \
    -avd "$AVD_NAME" \
    -gpu "$gpu" \
    -accel on \
    -no-snapshot-load \
    -no-boot-anim \
    -no-metrics \
    -crash-report-mode never \
    -allow-host-audio \
    "${extra[@]}" \
    < /dev/null >"$LOG_FILE" 2>&1 &
  disown || true
  sleep 3
  if [[ -z "$(emulator_pids)" ]]; then
    echo "O emulador não arrancou com GPU $gpu. Ver $LOG_FILE" >&2
    tail -n 20 "$LOG_FILE" >&2 || true
    return 1
  fi
  if [[ "$HEADLESS" == 1 ]]; then
    echo "A iniciar $AVD_NAME sem janela (GPU: $gpu). Para ver o ecrã: sudo apt install scrcpy && scrcpy"
  else
    echo "A iniciar $AVD_NAME (GPU: $gpu) no display ${DISPLAY}."
  fi
}

boot_emulator() {
  local gpu="$1"
  launch_emulator "$gpu" || return 1
  EMULATOR_SERIAL="$(wait_for_emulator_serial)" || return 1
  wait_for_boot "$EMULATOR_SERIAL" || return 1
}

prepare_avd_microphone() {
  local conf="${ANDROID_AVD_HOME:-$HOME/.android/avd}/${AVD_NAME}.avd/AVD.conf"
  mkdir -p "$(dirname "$conf")"
  if [[ -f "$conf" ]]; then
    if grep -q '^mic\\available=' "$conf"; then
      sed -i 's/^mic\\available=.*/mic\\available=true/' "$conf"
    else
      printf 'mic\\available=true\n' >>"$conf"
    fi
    if grep -q '^mic\\allow_real_audio=' "$conf"; then
      sed -i 's/^mic\\allow_real_audio=.*/mic\\allow_real_audio=true/' "$conf"
    else
      printf 'mic\\allow_real_audio=true\n' >>"$conf"
    fi
  else
    printf '[perAvd]\nmic\\allow_real_audio=true\nmic\\available=true\n' >"$conf"
  fi
}

pulse_capture_source() {
  local src=""
  if [[ -n "${LSB_PULSE_SOURCE:-}" ]]; then
    echo "$LSB_PULSE_SOURCE"
    return 0
  fi
  src="$(pactl list short sources 2>/dev/null | awk '$2 ~ /HyperX|Cloud_III/ && $2 !~ /monitor/ { print $2; exit }')"
  if [[ -n "$src" ]]; then
    echo "$src"
    return 0
  fi
  src="$(pactl get-default-source 2>/dev/null || true)"
  if [[ -n "$src" && "$src" != *.monitor ]]; then
    echo "$src"
    return 0
  fi
  pactl list short sources 2>/dev/null | awk '$2 ~ /^alsa_input\./ && $2 !~ /monitor/ { print $2; exit }'
}

enable_host_microphone() {
  local serial="$1"
  local src=""
  local stream=""
  prepare_avd_microphone
  if command -v pactl >/dev/null 2>&1; then
    src="$(pulse_capture_source || true)"
    if [[ -n "$src" ]]; then
      export PULSE_SOURCE="$src"
      pactl set-default-source "$src" >/dev/null 2>&1 || true
      pactl set-source-mute "$src" 0 >/dev/null 2>&1 || true
      pactl set-source-volume "$src" 100% >/dev/null 2>&1 || true
      stream="$(pactl list source-outputs short 2>/dev/null | awk '/qemu-system/{ print $1; exit }')"
      if [[ -n "$stream" ]]; then
        pactl move-source-output "$stream" "$src" >/dev/null 2>&1 || true
      fi
    fi
  fi
  "$ADB" -s "$serial" emu avd hostmicon >/dev/null 2>&1 || true
  "$ADB" -s "$serial" shell cmd audio set-microphone-mute false >/dev/null 2>&1 || true
  if [[ -n "$src" ]]; then
    echo "Microfone do computador ligado no emulador ($src)."
  else
    echo "Microfone do computador ligado no emulador."
  fi
}

ensure_emulator() {
  local serial
  if ! avd_exists; then
    echo "O AVD $AVD_NAME não existe. Crie-o no Android Studio ou com avdmanager." >&2
    exit 1
  fi
  require_kvm
  prepare_avd_microphone
  if command -v pactl >/dev/null 2>&1; then
    local pulse_src
    pulse_src="$(pulse_capture_source || true)"
    if [[ -n "$pulse_src" ]]; then
      export PULSE_SOURCE="$pulse_src"
    fi
  fi
  if [[ -z "$(emulator_pids)" ]]; then
    if ! boot_emulator swiftshader_indirect; then
      if [[ "$HEADLESS" != 1 ]]; then
        echo "A janela Qt falhou. A tentar o emulador sem janela..."
        HEADLESS=1
        kill_failed_emulator
        boot_emulator swiftshader_indirect || true
      fi
    fi
    if [[ -z "${EMULATOR_SERIAL:-}" ]]; then
      echo "A GPU software falhou. A tentar host..."
      kill_failed_emulator
      if ! boot_emulator host; then
        echo "O emulador não arrancou. Ver $LOG_FILE" >&2
        tail -n 30 "$LOG_FILE" >&2 || true
        exit 1
      fi
    fi
    serial="$EMULATOR_SERIAL"
  else
    echo "O emulador $AVD_NAME já está a correr."
    serial="$(wait_for_emulator_serial)"
    wait_for_boot "$serial"
  fi
  enable_host_microphone "$serial"
  "$ADB" -s "$serial" reverse "tcp:${METRO_PORT}" "tcp:${METRO_PORT}" >/dev/null
  export ANDROID_SERIAL="$serial"
}

metro_listening() {
  echo >/dev/tcp/127.0.0.1/"$METRO_PORT" 2>/dev/null
}

wait_for_metro() {
  local attempt
  for attempt in $(seq 1 30); do
    if metro_listening; then
      return 0
    fi
    sleep 1
  done
  echo "O Metro não abriu a porta $METRO_PORT." >&2
  return 1
}

stop_metro_if_started() {
  if [[ -n "$STARTED_METRO" ]] && kill -0 "$STARTED_METRO" 2>/dev/null; then
    kill "$STARTED_METRO" 2>/dev/null || true
    wait "$STARTED_METRO" 2>/dev/null || true
  fi
}

host_mic_listening() {
  ss -ltn 2>/dev/null | grep -q ":${HOST_MIC_PORT} "
}

ensure_host_mic() {
  if host_mic_listening; then
    echo "Microfone do computador já está à escuta na porta $HOST_MIC_PORT."
    return 0
  fi
  echo "A ligar o microfone do computador ao emulador..."
  python3 "$REPO_ROOT/scripts/host-mic.py" --port "$HOST_MIC_PORT" \
    >>"${TMPDIR:-/tmp}/ler-host-mic.log" 2>&1 &
  disown || true
  local attempt
  for attempt in $(seq 1 20); do
    if host_mic_listening; then
      return 0
    fi
    sleep 0.2
  done
  echo "Não foi possível abrir o microfone do computador. Ver ${TMPDIR:-/tmp}/ler-host-mic.log" >&2
  return 1
}

ensure_metro() {
  if metro_listening; then
    echo "O Metro já está na porta $METRO_PORT."
    return 0
  fi
  echo "A arrancar o Metro..."
  (
    cd "$MOBILE_DIR"
    exec npx react-native start --port "$METRO_PORT"
  ) &
  STARTED_METRO=$!
  wait_for_metro
}

install_js() {
  cd "$MOBILE_DIR"
  if [[ ! -d node_modules ]]; then
    echo "A instalar dependências npm..."
    npm install
  fi
}

assemble_debug() {
  echo "A compilar a app (ainda sem emulador)..."
  cd "$MOBILE_DIR/android"
  chmod +x gradlew
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Djava.net.preferIPv4Stack=true"
  ./gradlew assembleDebug -PreactNativeDevServerPort="$METRO_PORT"
}

install_and_launch() {
  echo "A instalar a app no emulador..."
  cd "$MOBILE_DIR"
  chmod +x android/gradlew
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Djava.net.preferIPv4Stack=true"
  npx react-native run-android --no-packager --port "$METRO_PORT" --device "$ANDROID_SERIAL"
  "$ADB" -s "$ANDROID_SERIAL" shell pm grant "$APP_ID" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  echo
  echo "App no emulador. Edite ficheiros em mobile/ — o Fast Refresh actualiza sozinho."
  echo "Ctrl+C pára o Metro. O emulador continua."
}

trap stop_metro_if_started EXIT INT TERM

require_sdk
pick_java
require_node
ensure_host_mic
install_js
ensure_metro
assemble_debug
ensure_display
ensure_emulator
install_and_launch

if [[ -n "$STARTED_METRO" ]]; then
  wait "$STARTED_METRO"
fi
