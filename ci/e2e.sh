#!/bin/bash
# Сквозная проверка на эмуляторе: экран как у Tecno Camon 20 Pro (1080×2400), светлая и тёмная тема,
# затем маленький экран 360×640 dp. Скриншоты — в e2e-out/.
set -u
PKG=io.github.peeresla.ods
OUT=e2e-out
mkdir -p "$OUT"
FAIL=0
echo "Масштаб анимаций: $(adb shell settings get global animator_duration_scale) / $(adb shell settings get global transition_animation_scale)" | tee "$OUT/anim-scale.txt"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

run() {
  local tag=$1
  adb shell dumpsys gfxinfo $PKG reset > /dev/null 2>&1 || true
  adb shell am instrument -w -e tag "$tag" -e class $PKG.WalkthroughTest $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrument-$tag.txt"
  grep -q "OK (1 test)" "$OUT/instrument-$tag.txt" || FAIL=1
  echo "== Плавность ($tag) =="
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" | tee "$OUT/gfx-$tag.txt"
}

adb shell wm size 1080x2400
adb shell wm density 440
adb shell cmd uimode night no
run light
adb shell cmd uimode night yes
run dark
adb shell cmd uimode night no

# Экзамен после убийства процесса: два отдельных запуска
kill_step() {
  adb shell am instrument -w -e class "$PKG.ExamKillTest#$1" $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrument-kill-$1.txt"
  grep -q "OK (1 test)" "$OUT/instrument-kill-$1.txt" || FAIL=1
}
kill_step start
adb shell am force-stop $PKG
kill_step resume

adb shell wm size 720x1280
adb shell wm density 320
run small

adb exec-out run-as $PKG tar c -C files screens > "$OUT/screens.tar" && tar xf "$OUT/screens.tar" -C "$OUT" && rm "$OUT/screens.tar"
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt" || true
adb logcat -d -s WalkthroughTest:I > "$OUT/test-log.txt" || true
ls "$OUT/screens" | head -100
exit $FAIL
