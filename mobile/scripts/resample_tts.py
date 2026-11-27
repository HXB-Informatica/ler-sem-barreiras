import array
import os
import wave

src = os.path.join(os.environ["TEMP"], "lsb-tts.wav")
out_dir = r"d:\Projecto Ler sem Barreiras\mobile\android\app\src\androidTest\assets"
os.makedirs(out_dir, exist_ok=True)

with wave.open(src) as wav:
    channels, rate, width, frames = wav.getnchannels(), wav.getframerate(), wav.getsampwidth(), wav.getnframes()
    raw = wav.readframes(frames)
print("tts", channels, rate, width, frames / rate)

pcm = array.array("h")
pcm.frombytes(raw)
if channels == 2:
    pcm = array.array("h", [pcm[i] for i in range(0, len(pcm), 2)])

target = 16000
if rate != target:
    step = rate / target
    resampled = array.array("h")
    index = 0.0
    while int(index) < len(pcm):
        resampled.append(pcm[int(index)])
        index += step
    pcm = resampled

print("peak", max(abs(sample) for sample in pcm), "sec", len(pcm) / target)


def write(path: str, samples: array.array) -> None:
    with wave.open(path, "w") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(target)
        out.writeframes(samples.tobytes())


write(os.path.join(out_dir, "sample-record.wav"), pcm)
mid = len(pcm) // 2
paused = array.array("h")
paused.extend(pcm[:mid])
paused.extend(array.array("h", [0] * 32000))
paused.extend(pcm[mid:])
write(os.path.join(out_dir, "sample-record-pause.wav"), paused)
write(os.path.join(out_dir, "sample-record-omit.wav"), pcm[:mid])
write(r"d:\Projecto Ler sem Barreiras\mobile\sample-record.wav", pcm)
print("written", out_dir)
