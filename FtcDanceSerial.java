import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

public final class FtcDanceSerial {
    private static final String DEFAULT_DEVICE = "/dev/ttyS1";
    private static final int MODULE_ADDRESS = 173;
    private static final int HOST_ADDRESS = 0;
    private static final int RESPONSE_BIT = 0x8000;
    private static final int ACK = 0x7f01;
    private static final int NACK = 0x7f02;
    private static final int FAIL_SAFE = 0x7f05;
    private static final int QUERY_INTERFACE = 0x7f07;

    private RandomAccessFile serial;
    private InputStream in;
    private OutputStream out;
    private int nextMessage = 1;
    private int dekaBase = 0;

    public static void main(String[] args) throws Exception {
        String device = getArg(args, "--device", DEFAULT_DEVICE);
        int durationMs = Integer.parseInt(getArg(args, "--duration-ms", "20000"));
        double powerFraction = Double.parseDouble(getArg(args, "--power", "0.20"));

        if (durationMs <= 0 || durationMs > 30000) {
            throw new IllegalArgumentException("duration must be in 1..30000 ms");
        }
        if (powerFraction <= 0.0 || powerFraction > 0.30) {
            throw new IllegalArgumentException("power must be in 0.0..0.30");
        }

        FtcDanceSerial runner = new FtcDanceSerial();
        runner.run(device, durationMs, powerFraction);
    }

    private void run(String device, int durationMs, double powerFraction) throws Exception {
        int power = (int) Math.round(32767.0 * powerFraction);
        System.out.println("Configuring " + device + " at 460800 baud");
        configureSerial(device);

        serial = new RandomAccessFile(device, "rw");
        in = new FileInputStream(serial.getFD());
        out = new FileOutputStream(serial.getFD());

        boolean configured = false;
        try {
            drainInput(250);
            queryDekaInterface();
            System.out.println("DEKA base command number: " + dekaBase);

            for (int motor = 0; motor < 4; motor++) {
                setMotorMode(motor);
                setMotorEnable(motor, true);
                setMotorPower(motor, 0);
            }
            configured = true;

            int[][] pattern = new int[][] {
                { power,  power,  power,  power },
                {-power, -power, -power, -power },
                { power, -power,  power, -power },
                {-power,  power, -power,  power },
                { power,      0, -power,      0 },
                {     0, -power,      0,  power },
                { power, -power, -power,  power },
                {-power,  power,  power, -power },
                { power,      0,  power,      0 },
                {     0, -power,      0, -power }
            };

            long start = System.currentTimeMillis();
            long deadline = start + durationMs;
            System.out.println("Starting bounded dance for " + durationMs + " ms at power " + power);
            for (int i = 0; i < pattern.length && System.currentTimeMillis() < deadline; i++) {
                setAllPowers(pattern[i]);
                sleepUntil(Math.min(deadline, start + ((long) i + 1L) * durationMs / pattern.length));
            }
        } finally {
            if (configured) {
                System.out.println("Stopping all motors");
            }
            stopAllQuietly();
            closeQuietly();
        }
    }

    private static String getArg(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static void configureSerial(String device) throws Exception {
        String[][] commands = new String[][] {
            {"/system/bin/stty", "-F", device, "460800", "raw", "-echo", "-ixon", "-ixoff", "min", "0", "time", "1"},
            {"/system/bin/toybox", "stty", "-F", device, "460800", "raw", "-echo", "-ixon", "-ixoff", "min", "0", "time", "1"},
            {"stty", "-F", device, "460800", "raw", "-echo", "-ixon", "-ixoff", "min", "0", "time", "1"}
        };

        Exception last = null;
        for (String[] command : commands) {
            try {
                int status = runCommand(command);
                if (status == 0) {
                    return;
                }
                last = new RuntimeException(Arrays.toString(command) + " exited " + status);
            } catch (Exception e) {
                last = e;
            }
        }
        System.out.println("Warning: unable to run stty for serial configuration; continuing with existing UART settings: " + last);
    }

    private static int runCommand(String[] command) throws Exception {
        Process process = Runtime.getRuntime().exec(command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(process.getInputStream(), output);
        copy(process.getErrorStream(), output);
        int status = process.waitFor();
        if (output.size() > 0) {
            System.out.println(new String(output.toByteArray(), "UTF-8").trim());
        }
        return status;
    }

    private static void copy(InputStream input, ByteArrayOutputStream output) throws Exception {
        byte[] buffer = new byte[256];
        while (input.available() > 0) {
            int read = input.read(buffer);
            if (read <= 0) {
                break;
            }
            output.write(buffer, 0, read);
        }
    }

    private void queryDekaInterface() throws Exception {
        byte[] response = transact(QUERY_INTERFACE, "DEKA\0".getBytes("UTF-8"), QUERY_INTERFACE | RESPONSE_BIT);
        if (response.length < 4) {
            throw new RuntimeException("Short DEKA query response: " + response.length);
        }
        dekaBase = u16(response, 0);
        int commandCount = u16(response, 2);
        if (dekaBase == 0 || commandCount < 16) {
            throw new RuntimeException("Unexpected DEKA response base=" + dekaBase + " count=" + commandCount);
        }
    }

    private void setMotorMode(int motor) throws Exception {
        sendAcked(dekaBase + 8, new byte[] {(byte) motor, 0, 0});
    }

    private void setMotorEnable(int motor, boolean enabled) throws Exception {
        sendAcked(dekaBase + 10, new byte[] {(byte) motor, (byte) (enabled ? 1 : 0)});
    }

    private void setMotorPower(int motor, int power) throws Exception {
        byte[] payload = new byte[] {(byte) motor, (byte) (power & 0xff), (byte) ((power >> 8) & 0xff)};
        sendAcked(dekaBase + 15, payload);
    }

    private void setAllPowers(int[] powers) throws Exception {
        for (int motor = 0; motor < 4; motor++) {
            setMotorPower(motor, powers[motor]);
        }
    }

    private void stopAllQuietly() {
        for (int motor = 0; motor < 4; motor++) {
            try {
                if (dekaBase != 0) {
                    setMotorPower(motor, 0);
                }
            } catch (Exception e) {
                System.out.println("Zero power failed for motor " + motor + ": " + e);
            }
        }
        try {
            sendAcked(FAIL_SAFE, new byte[0]);
        } catch (Exception e) {
            System.out.println("Fail-safe command failed: " + e);
        }
    }

    private void sendAcked(int packetId, byte[] payload) throws Exception {
        transact(packetId, payload, ACK);
    }

    private byte[] transact(int packetId, byte[] payload, int expectedPacketId) throws Exception {
        int message = nextMessage();
        byte[] request = packet(packetId, message, 0, payload);
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            out.write(request);
            out.flush();
            long deadline = System.currentTimeMillis() + 700;
            while (System.currentTimeMillis() < deadline) {
                Packet packet = readPacket(deadline);
                if (packet == null) {
                    break;
                }
                if (packet.reference != message) {
                    continue;
                }
                if (packet.packetId == NACK) {
                    int reason = packet.payload.length > 0 ? (packet.payload[0] & 0xff) : -1;
                    throw new RuntimeException("NACK for 0x" + hex(packetId) + " reason=" + reason);
                }
                if (packet.packetId == expectedPacketId) {
                    return packet.payload;
                }
                last = new RuntimeException("Unexpected packet 0x" + hex(packet.packetId) + " for 0x" + hex(packetId));
            }
        }
        throw new RuntimeException("Timed out waiting for 0x" + hex(expectedPacketId) + " after 0x" + hex(packetId), last);
    }

    private int nextMessage() {
        int result = nextMessage++ & 0xff;
        if (nextMessage > 255) {
            nextMessage = 1;
        }
        return result == 0 ? nextMessage() : result;
    }

    private static byte[] packet(int packetId, int message, int reference, byte[] payload) {
        int length = 11 + payload.length;
        byte[] packet = new byte[length];
        packet[0] = 0x44;
        packet[1] = 0x4b;
        packet[2] = (byte) (length & 0xff);
        packet[3] = (byte) ((length >> 8) & 0xff);
        packet[4] = (byte) MODULE_ADDRESS;
        packet[5] = (byte) HOST_ADDRESS;
        packet[6] = (byte) message;
        packet[7] = (byte) reference;
        packet[8] = (byte) (packetId & 0xff);
        packet[9] = (byte) ((packetId >> 8) & 0xff);
        System.arraycopy(payload, 0, packet, 10, payload.length);
        packet[length - 1] = checksum(packet, length - 1);
        return packet;
    }

    private Packet readPacket(long deadline) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            int first = readByte(deadline);
            if (first < 0) {
                return null;
            }
            if (first != 0x44) {
                continue;
            }
            int second = readByte(deadline);
            if (second != 0x4b) {
                continue;
            }
            int len0 = readByte(deadline);
            int len1 = readByte(deadline);
            if (len0 < 0 || len1 < 0) {
                return null;
            }
            int length = len0 | (len1 << 8);
            if (length < 11 || length > 1024) {
                continue;
            }
            byte[] data = new byte[length];
            data[0] = 0x44;
            data[1] = 0x4b;
            data[2] = (byte) len0;
            data[3] = (byte) len1;
            for (int i = 4; i < length; i++) {
                int value = readByte(deadline);
                if (value < 0) {
                    return null;
                }
                data[i] = (byte) value;
            }
            if (checksum(data, length - 1) != data[length - 1]) {
                continue;
            }
            return new Packet(u16(data, 8), data[6] & 0xff, data[7] & 0xff, Arrays.copyOfRange(data, 10, length - 1));
        }
        return null;
    }

    private int readByte(long deadline) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            if (in.available() > 0) {
                int value = in.read();
                if (value >= 0) {
                    return value;
                }
            }
            Thread.sleep(5);
        }
        return -1;
    }

    private void drainInput(long ms) throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline && in.available() > 0) {
            in.read();
        }
    }

    private static byte checksum(byte[] data, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum = (sum + data[i]) & 0xff;
        }
        return (byte) sum;
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static String hex(int value) {
        return Integer.toHexString(value & 0xffff);
    }

    private static void sleepUntil(long timestampMs) throws InterruptedException {
        while (true) {
            long remaining = timestampMs - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            Thread.sleep(Math.min(remaining, 100));
        }
    }

    private void closeQuietly() {
        try {
            if (serial != null) {
                serial.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Packet {
        final int packetId;
        final int message;
        final int reference;
        final byte[] payload;

        Packet(int packetId, int message, int reference, byte[] payload) {
            this.packetId = packetId;
            this.message = message;
            this.reference = reference;
            this.payload = payload;
        }
    }
}
