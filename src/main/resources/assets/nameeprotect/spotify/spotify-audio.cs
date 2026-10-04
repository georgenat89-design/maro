using System;
using System.Diagnostics;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

// Shared-mode WASAPI loopback: reads the Windows output mix, never the microphone.
// One long-lived helper publishes seven real FFT bands at about 30 Hz.
public static class NathanAudioBands
{
    private static readonly Guid AudioClientId = new Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
    private static readonly Guid CaptureClientId = new Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317");
    private static readonly Guid EndpointVolumeId = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A");
    private static readonly object CommandLock = new object();
    private static VolumeCommand pendingVolume;
    private static long appliedSequence;

    private sealed class VolumeCommand
    {
        public long Sequence;
        public float Level;
        public bool Muted;
    }

    public static void Run(int durationMs)
    {
        // Only this reader touches stdin. COM calls stay on the capture thread.
        Thread commands = new Thread(ReadCommands);
        commands.IsBackground = true;
        commands.Start();
        Stopwatch lifetime = Stopwatch.StartNew();
        while (durationMs <= 0 || lifetime.ElapsedMilliseconds < durationMs)
        {
            try { Capture(lifetime, durationMs); }
            catch (System.IO.IOException) { return; } // Minecraft closed its pipe.
            catch (Exception error)
            {
                Console.WriteLine("ERROR " + error.Message.Replace('\r', ' ').Replace('\n', ' '));
                Console.WriteLine("VOLUME_ERROR Windows output volume is unavailable");
                Console.WriteLine("LEVELS 0 0 0 0 0 0 0");
                Console.Out.Flush();
                Thread.Sleep(1500);
            }
        }
    }

    private static void ReadCommands()
    {
        try
        {
            string line;
            while ((line = Console.ReadLine()) != null)
            {
                string[] parts = line.Split(' ');
                long sequence;
                float level;
                if (parts.Length != 4 || parts[0] != "VOLUME" ||
                    !Int64.TryParse(parts[1], out sequence) || sequence <= 0 ||
                    !Single.TryParse(parts[2], NumberStyles.Float, CultureInfo.InvariantCulture, out level) ||
                    Single.IsNaN(level) || Single.IsInfinity(level) || (parts[3] != "0" && parts[3] != "1")) continue;
                lock (CommandLock)
                {
                    // Dragging replaces pending work instead of building a command backlog.
                    pendingVolume = new VolumeCommand { Sequence = sequence, Level = Math.Max(0, Math.Min(1, level)), Muted = parts[3] == "1" };
                }
            }
        }
        catch (System.IO.IOException) { }
    }

    private static void PublishVolume(IAudioEndpointVolume volume)
    {
        float level;
        bool muted;
        Check(volume.GetMasterVolumeLevelScalar(out level));
        Check(volume.GetMute(out muted));
        Console.WriteLine("VOLUME " + appliedSequence.ToString(CultureInfo.InvariantCulture) + " " +
            level.ToString("F4", CultureInfo.InvariantCulture) + " " + (muted ? "1" : "0"));
    }

    private static void Check(int result)
    {
        if (result < 0) Marshal.ThrowExceptionForHR(result);
    }

    private static void Release(object instance)
    {
        if (instance != null && Marshal.IsComObject(instance)) Marshal.ReleaseComObject(instance);
    }

    private static void Capture(Stopwatch lifetime, int durationMs)
    {
        IMMDeviceEnumerator devices = null;
        IMMDevice device = null;
        IAudioClient client = null;
        IAudioCaptureClient capture = null;
        IAudioEndpointVolume volume = null;
        IntPtr format = IntPtr.Zero;
        bool started = false;
        try
        {
            devices = (IMMDeviceEnumerator)new MMDeviceEnumerator();
            Check(devices.GetDefaultAudioEndpoint(0, 1, out device)); // render / multimedia
            string deviceId;
            Check(device.GetId(out deviceId));
            try
            {
                object volumeObject;
                Guid volumeId = EndpointVolumeId;
                Check(device.Activate(ref volumeId, 23, IntPtr.Zero, out volumeObject));
                volume = (IAudioEndpointVolume)volumeObject;
                PublishVolume(volume);
            }
            catch (Exception) { Console.WriteLine("VOLUME_ERROR Windows output volume is unavailable"); }
            Console.Out.Flush();
            object audio;
            Guid clientId = AudioClientId;
            Check(device.Activate(ref clientId, 23, IntPtr.Zero, out audio));
            client = (IAudioClient)audio;
            Check(client.GetMixFormat(out format));

            int kind = (ushort)Marshal.ReadInt16(format, 0);
            int channels = (ushort)Marshal.ReadInt16(format, 2);
            int sampleRate = Marshal.ReadInt32(format, 4);
            int frameBytes = (ushort)Marshal.ReadInt16(format, 12);
            int bits = (ushort)Marshal.ReadInt16(format, 14);
            if (kind == 0xfffe) kind = Marshal.ReadInt32(format, 24); // WAVEFORMATEXTENSIBLE subformat
            if (channels < 1 || sampleRate < 8000 || frameBytes < channels ||
                !((kind == 3 && bits == 32) || (kind == 1 && (bits == 16 || bits == 24 || bits == 32))))
                throw new InvalidOperationException("Unsupported Windows output audio format.");

            Guid session = Guid.Empty;
            Check(client.Initialize(0, 0x00020000, 1000000, 0, format, ref session));
            object reader;
            Guid captureId = CaptureClientId;
            Check(client.GetService(ref captureId, out reader));
            capture = (IAudioCaptureClient)reader;
            Check(client.Start());
            started = true;

            BandAnalyzer analyzer = new BandAnalyzer(sampleRate);
            byte[] packet = new byte[16384];
            Stopwatch tick = Stopwatch.StartNew();
            long lastPublished = -40;
            long lastPacket = 0;
            long lastDeviceCheck = 0;
            long lastVolumeCheck = 0;
            StringBuilder output = new StringBuilder(100);
            while (durationMs <= 0 || lifetime.ElapsedMilliseconds < durationMs)
            {
                uint packetFrames;
                Check(capture.GetNextPacketSize(out packetFrames));
                while (packetFrames > 0)
                {
                    IntPtr data;
                    uint frames, flags;
                    ulong devicePosition, performancePosition;
                    Check(capture.GetBuffer(out data, out frames, out flags, out devicePosition, out performancePosition));
                    try
                    {
                        int byteCount = checked((int)frames * frameBytes);
                        if (byteCount > packet.Length) packet = new byte[byteCount];
                        bool silent = (flags & 2) != 0 || data == IntPtr.Zero;
                        if (!silent) Marshal.Copy(data, packet, 0, byteCount);
                        int bytesPerSample = bits / 8;
                        for (int frame = 0; frame < frames; frame++)
                        {
                            float mono = 0;
                            if (!silent)
                            {
                                for (int channel = 0; channel < channels; channel++)
                                {
                                    int offset = frame * frameBytes + channel * bytesPerSample;
                                    if (kind == 3) mono += BitConverter.ToSingle(packet, offset);
                                    else if (bits == 16) mono += BitConverter.ToInt16(packet, offset) / 32768f;
                                    else if (bits == 32) mono += BitConverter.ToInt32(packet, offset) / 2147483648f;
                                    else
                                    {
                                        int value = packet[offset] | packet[offset + 1] << 8 | packet[offset + 2] << 16;
                                        if ((value & 0x800000) != 0) value |= unchecked((int)0xff000000);
                                        mono += value / 8388608f;
                                    }
                                }
                                mono /= channels;
                            }
                            analyzer.Add(mono);
                        }
                        lastPacket = tick.ElapsedMilliseconds;
                    }
                    finally { Check(capture.ReleaseBuffer(frames)); }
                    Check(capture.GetNextPacketSize(out packetFrames));
                }

                long now = tick.ElapsedMilliseconds;
                VolumeCommand command;
                lock (CommandLock) { command = pendingVolume; pendingVolume = null; }
                if (command != null)
                {
                    try
                    {
                        Guid context = Guid.Empty;
                        Check(volume.SetMasterVolumeLevelScalar(command.Level, ref context));
                        Check(volume.SetMute(command.Muted, ref context));
                        appliedSequence = command.Sequence;
                        PublishVolume(volume);
                    }
                    catch (Exception) { Console.WriteLine("VOLUME_ERROR Windows could not change output volume"); }
                    Console.Out.Flush();
                    lastVolumeCheck = now;
                }
                else if (volume != null && now - lastVolumeCheck >= 250)
                {
                    try { PublishVolume(volume); }
                    catch (Exception)
                    {
                        Release(volume); volume = null;
                        Console.WriteLine("VOLUME_ERROR Windows output volume is unavailable");
                    }
                    Console.Out.Flush();
                    lastVolumeCheck = now;
                }
                if (now - lastPublished >= 33)
                {
                    float[] levels = analyzer.Measure(now - lastPacket > 120);
                    output.Clear();
                    output.Append("LEVELS");
                    for (int i = 0; i < levels.Length; i++)
                        output.Append(' ').Append(levels[i].ToString("F4", CultureInfo.InvariantCulture));
                    Console.WriteLine(output.ToString());
                    Console.Out.Flush();
                    lastPublished = now;
                }

                // Reopen when the player changes headphones/speakers while Minecraft stays open.
                if (now - lastDeviceCheck > 2000)
                {
                    IMMDevice current = null;
                    try
                    {
                        Check(devices.GetDefaultAudioEndpoint(0, 1, out current));
                        string currentId;
                        Check(current.GetId(out currentId));
                        if (!String.Equals(currentId, deviceId, StringComparison.Ordinal)) return;
                    }
                    finally { Release(current); }
                    lastDeviceCheck = now;
                }
                Thread.Sleep(10);
            }
        }
        finally
        {
            if (started && client != null) client.Stop();
            if (format != IntPtr.Zero) Marshal.FreeCoTaskMem(format);
            Release(capture);
            Release(volume);
            Release(client);
            Release(device);
            Release(devices);
        }
    }

    // Exposed for a deterministic synthetic-tone probe; reused by the live capture loop.
    public sealed class BandAnalyzer
    {
        private const int Count = 2048;
        private readonly float[] samples = new float[Count];
        private readonly double[] real = new double[Count];
        private readonly double[] imaginary = new double[Count];
        private readonly double[] window = new double[Count];
        private readonly float[] levels = new float[7];
        private readonly int sampleRate;
        private int cursor;
        private static readonly int[] Edges = { 35, 180, 400, 800, 1600, 3200, 7000, 18000 };

        public BandAnalyzer(int rate)
        {
            sampleRate = rate;
            for (int i = 0; i < Count; i++) window[i] = 0.5 - 0.5 * Math.Cos(2 * Math.PI * i / (Count - 1));
        }

        public void Add(float value)
        {
            samples[cursor] = Single.IsNaN(value) || Single.IsInfinity(value) ? 0 : value;
            cursor = (cursor + 1) & (Count - 1);
        }

        public float[] Measure(bool silent)
        {
            if (!silent)
            {
                for (int i = 0; i < Count; i++)
                {
                    real[i] = samples[(cursor + i) & (Count - 1)] * window[i];
                    imaginary[i] = 0;
                }
                Transform();
            }
            for (int band = 0; band < levels.Length; band++)
            {
                float target = 0;
                if (!silent)
                {
                    int start = Math.Max(1, (int)Math.Ceiling((double)Edges[band] * Count / sampleRate));
                    int end = Math.Min(Count / 2, (int)Math.Ceiling((double)Edges[band + 1] * Count / sampleRate));
                    double energy = 0;
                    for (int bin = start; bin < end; bin++) energy += real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
                    double magnitude = Math.Sqrt(energy) * 4 / Count;
                    double decibels = 20 * Math.Log10(Math.Max(1e-8, magnitude));
                    target = (float)Math.Max(0, Math.Min(1, (decibels + 62) / 50));
                }
                // Fast attack preserves drums; slower release makes tiny bars readable.
                levels[band] += (target - levels[band]) * (target > levels[band] ? 0.85f : 0.24f);
                if (levels[band] < 0.002f) levels[band] = 0;
            }
            return levels;
        }

        private void Transform()
        {
            for (int i = 1, j = 0; i < Count; i++)
            {
                int bit = Count >> 1;
                while ((j & bit) != 0) { j ^= bit; bit >>= 1; }
                j ^= bit;
                if (i < j)
                {
                    double value = real[i]; real[i] = real[j]; real[j] = value;
                    value = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = value;
                }
            }
            for (int length = 2; length <= Count; length <<= 1)
            {
                double angle = -2 * Math.PI / length;
                double stepReal = Math.Cos(angle), stepImaginary = Math.Sin(angle);
                for (int start = 0; start < Count; start += length)
                {
                    double wr = 1, wi = 0;
                    for (int offset = 0; offset < length / 2; offset++)
                    {
                        int even = start + offset, odd = even + length / 2;
                        double tr = real[odd] * wr - imaginary[odd] * wi;
                        double ti = real[odd] * wi + imaginary[odd] * wr;
                        real[odd] = real[even] - tr; imaginary[odd] = imaginary[even] - ti;
                        real[even] += tr; imaginary[even] += ti;
                        double nextReal = wr * stepReal - wi * stepImaginary;
                        wi = wr * stepImaginary + wi * stepReal; wr = nextReal;
                    }
                }
            }
        }
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumerator { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        [PreserveSig] int EnumAudioEndpoints(int flow, uint mask, out IntPtr devices);
        [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IMMDevice device);
        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
        [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr callback);
        [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr callback);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        [PreserveSig] int Activate(ref Guid iid, uint context, IntPtr parameters, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
        [PreserveSig] int OpenPropertyStore(uint access, out IntPtr properties);
        [PreserveSig] int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetState(out uint state);
    }

    [ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioClient
    {
        [PreserveSig] int Initialize(int mode, uint flags, long duration, long periodicity, IntPtr format, ref Guid session);
        [PreserveSig] int GetBufferSize(out uint frames);
        [PreserveSig] int GetStreamLatency(out long latency);
        [PreserveSig] int GetCurrentPadding(out uint frames);
        [PreserveSig] int IsFormatSupported(int mode, IntPtr format, out IntPtr closest);
        [PreserveSig] int GetMixFormat(out IntPtr format);
        [PreserveSig] int GetDevicePeriod(out long standard, out long minimum);
        [PreserveSig] int Start();
        [PreserveSig] int Stop();
        [PreserveSig] int Reset();
        [PreserveSig] int SetEventHandle(IntPtr handle);
        [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
    }

    [ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioCaptureClient
    {
        [PreserveSig] int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong devicePosition, out ulong performancePosition);
        [PreserveSig] int ReleaseBuffer(uint frames);
        [PreserveSig] int GetNextPacketSize(out uint frames);
    }

    // Vtable order from endpointvolume.h. All HRESULTs are checked by the caller.
    [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioEndpointVolume
    {
        [PreserveSig] int RegisterControlChangeNotify(IntPtr callback);
        [PreserveSig] int UnregisterControlChangeNotify(IntPtr callback);
        [PreserveSig] int GetChannelCount(out uint channels);
        [PreserveSig] int SetMasterVolumeLevel(float level, ref Guid context);
        [PreserveSig] int SetMasterVolumeLevelScalar(float level, ref Guid context);
        [PreserveSig] int GetMasterVolumeLevel(out float level);
        [PreserveSig] int GetMasterVolumeLevelScalar(out float level);
        [PreserveSig] int SetChannelVolumeLevel(uint channel, float level, ref Guid context);
        [PreserveSig] int SetChannelVolumeLevelScalar(uint channel, float level, ref Guid context);
        [PreserveSig] int GetChannelVolumeLevel(uint channel, out float level);
        [PreserveSig] int GetChannelVolumeLevelScalar(uint channel, out float level);
        [PreserveSig] int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, ref Guid context);
        [PreserveSig] int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }
}
