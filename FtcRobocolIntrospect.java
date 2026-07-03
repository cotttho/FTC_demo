import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Arrays;

public final class FtcRobocolIntrospect {
    private static final String[] CLASSES = new String[] {
        "com.qualcomm.robotcore.robocol.Command",
        "com.qualcomm.robotcore.robocol.RobocolDatagram",
        "com.qualcomm.robotcore.robocol.RobocolDatagramSocket",
        "com.qualcomm.robotcore.robocol.RobocolParsable",
        "com.qualcomm.robotcore.robocol.RobocolParsable$MsgType",
        "com.qualcomm.robotcore.robocol.RobocolConfig",
        "com.qualcomm.robotcore.robocol.PeerDiscovery",
        "com.qualcomm.robotcore.robocol.PeerDiscovery$PeerType",
        "com.qualcomm.robotcore.robocol.Heartbeat",
        "com.qualcomm.robotcore.robocol.KeepAlive",
        "org.firstinspires.ftc.robotcore.internal.network.RobotCoreCommandList",
        "com.qualcomm.ftccommon.CommandList",
        "org.firstinspires.ftc.robotcore.internal.network.NetworkConnectionHandler",
        "org.firstinspires.ftc.robotcore.internal.network.RecvLoopRunnable"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("java.version=" + System.getProperty("java.version"));
        System.out.println("java.class.path=" + System.getProperty("java.class.path"));
        System.out.println("user.dir=" + System.getProperty("user.dir"));

        for (String name : CLASSES) {
            inspectClass(name);
        }

        tryBuildCommandPackets();
    }

    private static void inspectClass(String name) {
        System.out.println();
        System.out.println("=== " + name + " ===");
        try {
            Class<?> clazz = Class.forName(name, false, FtcRobocolIntrospect.class.getClassLoader());
            System.out.println("class=" + clazz);
            System.out.println("modifiers=" + Modifier.toString(clazz.getModifiers()));

            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                System.out.println("ctor " + Modifier.toString(ctor.getModifiers()) + " " + ctor.getName() + "(" + params(ctor.getParameterTypes()) + ")");
            }

            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                String line = "field " + Modifier.toString(field.getModifiers()) + " " + field.getType().getName() + " " + field.getName();
                if (shouldReadStaticFieldValue(clazz, field)) {
                    try {
                        line += "=" + String.valueOf(field.get(null));
                    } catch (Throwable t) {
                        line += "=<" + t.getClass().getSimpleName() + ">";
                    }
                }
                System.out.println(line);
            }

            for (Method method : clazz.getDeclaredMethods()) {
                method.setAccessible(true);
                System.out.println("method " + Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getName() + " " + method.getName() + "(" + params(method.getParameterTypes()) + ")");
            }

            printEnumByteValues(clazz);
        } catch (Throwable t) {
            System.out.println("ERROR " + t);
            t.printStackTrace(System.out);
        }
    }

    private static boolean shouldReadStaticFieldValue(Class<?> clazz, Field field) {
        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
            return false;
        }
        Class<?> type = field.getType();
        if (type.isPrimitive() || type == String.class) {
            return true;
        }
        return clazz.isEnum() && type == clazz;
    }

    private static void printEnumByteValues(Class<?> clazz) {
        if (!clazz.isEnum()) {
            return;
        }
        try {
            Method asByte = clazz.getDeclaredMethod("asByte");
            asByte.setAccessible(true);
            Object[] constants = clazz.getEnumConstants();
            for (Object constant : constants) {
                Object value = asByte.invoke(constant);
                System.out.println("enum-byte " + constant + "=" + value);
            }
        } catch (NoSuchMethodException ignored) {
            // Not every enum in the app uses Robocol byte values.
        } catch (Throwable t) {
            System.out.println("enum-byte-error " + t);
        }
    }

    private static String params(Class<?>[] params) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(params[i].getName());
        }
        return builder.toString();
    }

    private static void tryBuildCommandPackets() {
        System.out.println();
        System.out.println("=== command packet probes ===");
        try {
            Class<?> robotCoreCommandList = Class.forName("org.firstinspires.ftc.robotcore.internal.network.RobotCoreCommandList");
            Class<?> commandList = Class.forName("com.qualcomm.ftccommon.CommandList");
            Class<?> commandClass = Class.forName("com.qualcomm.robotcore.robocol.Command");
            Class<?> datagramClass = Class.forName("com.qualcomm.robotcore.robocol.RobocolDatagram");

            printCommandProbe(robotCoreCommandList, commandClass, datagramClass, "CMD_REQUEST_OP_MODE_LIST", "");
            printCommandProbe(commandList, commandClass, datagramClass, "CMD_INIT_OP_MODE", "Stop Robot");
            printCommandProbe(commandList, commandClass, datagramClass, "CMD_RUN_OP_MODE", "Stop Robot");
            printPeerDiscoveryProbe(datagramClass);
            printHeartbeatProbe(datagramClass);
        } catch (Throwable t) {
            System.out.println("packet-probe-error " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void printCommandProbe(
            Class<?> constantsClass,
            Class<?> commandClass,
            Class<?> datagramClass,
            String fieldName,
            String extra) throws Exception {
        Object commandName = constantsClass.getField(fieldName).get(null);
        Constructor<?> commandCtor = commandClass.getConstructor(String.class, String.class);
        Object command = commandCtor.newInstance(commandName, extra);
        System.out.println("command " + fieldName + " name=" + commandName + " extra=" + quote(extra));
        printObjectSummary(command);

        for (String methodName : new String[] {"toByteArray", "toByteArrayForTransmission"}) {
            try {
                Method method = commandClass.getMethod(methodName);
                method.setAccessible(true);
                Object bytes = method.invoke(command);
                if (bytes instanceof byte[]) {
                    printBytes("command." + methodName, (byte[]) bytes);
                }
            } catch (NoSuchMethodException ignored) {
                // Printed in the method list above.
            }
        }

        try {
            Object datagram = newDatagram(datagramClass, command, "192.168.43.1");
            printDatagram(datagramClass, datagram);
        } catch (Throwable t) {
            System.out.println("datagram-probe-error " + fieldName + " " + t);
        }
    }

    private static Object newDatagram(Class<?> datagramClass, Object parsable, String host) throws Exception {
        Class<?> parsableClass = Class.forName("com.qualcomm.robotcore.robocol.RobocolParsable");
        Constructor<?> datagramCtor = datagramClass.getConstructor(parsableClass, InetAddress.class);
        return datagramCtor.newInstance(parsable, InetAddress.getByName(host));
    }

    private static void printDatagram(Class<?> datagramClass, Object datagram) throws Exception {
        printObjectSummary(datagram);
        Method getData = datagramClass.getDeclaredMethod("getData");
        getData.setAccessible(true);
        Object data = getData.invoke(datagram);
        if (data instanceof byte[]) {
            printBytes("datagram.getData", (byte[]) data);
        }
        try {
            Method getPacket = datagramClass.getDeclaredMethod("getPacket");
            getPacket.setAccessible(true);
            Object packet = getPacket.invoke(datagram);
            if (packet instanceof DatagramPacket) {
                DatagramPacket dp = (DatagramPacket) packet;
                byte[] packetData = Arrays.copyOf(dp.getData(), dp.getLength());
                printBytes("datagram.getPacket", packetData);
                System.out.println("datagram.packet.address=" + dp.getAddress() + " port=" + dp.getPort());
            }
        } catch (Throwable t) {
            System.out.println("packet-object-probe-error " + t);
        }
    }

    private static void printPeerDiscoveryProbe(Class<?> datagramClass) throws Exception {
        System.out.println();
        System.out.println("=== peer discovery packet probes ===");
        Class<?> peerClass = Class.forName("com.qualcomm.robotcore.robocol.PeerDiscovery");
        Class<?> peerTypeClass = Class.forName("com.qualcomm.robotcore.robocol.PeerDiscovery$PeerType");
        Object peerType = Enum.valueOf((Class<Enum>) peerTypeClass.asSubclass(Enum.class), "PEER");
        Method forTransmission = peerClass.getMethod("forTransmission", peerTypeClass);
        Object peerDiscovery = forTransmission.invoke(null, peerType);
        printObjectSummary(peerDiscovery);
        printByteMethod(peerClass, peerDiscovery, "toByteArray");
        printByteMethod(peerClass, peerDiscovery, "toByteArrayForTransmission");
        printDatagram(datagramClass, newDatagram(datagramClass, peerDiscovery, "192.168.43.1"));
    }

    private static void printHeartbeatProbe(Class<?> datagramClass) throws Exception {
        System.out.println();
        System.out.println("=== heartbeat packet probes ===");
        Class<?> heartbeatClass = Class.forName("com.qualcomm.robotcore.robocol.Heartbeat");
        Method create = heartbeatClass.getMethod("createWithTimeStamp");
        Object heartbeat = create.invoke(null);
        printObjectSummary(heartbeat);
        printByteMethod(heartbeatClass, heartbeat, "toByteArray");
        printByteMethod(heartbeatClass, heartbeat, "toByteArrayForTransmission");
        printDatagram(datagramClass, newDatagram(datagramClass, heartbeat, "192.168.43.1"));
    }

    private static void printByteMethod(Class<?> clazz, Object target, String methodName) {
        try {
            Method method = clazz.getMethod(methodName);
            method.setAccessible(true);
            Object bytes = method.invoke(target);
            if (bytes instanceof byte[]) {
                printBytes(target.getClass().getSimpleName() + "." + methodName, (byte[]) bytes);
            }
        } catch (NoSuchMethodException ignored) {
            // Printed in the method list above.
        } catch (Throwable t) {
            System.out.println("byte-method-error " + methodName + " " + t);
        }
    }

    private static void printObjectSummary(Object object) {
        if (object == null) {
            System.out.println("object=null");
            return;
        }
        System.out.println("object " + object.getClass().getName() + " " + object);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void printBytes(String label, byte[] data) {
        StringBuilder builder = new StringBuilder();
        int max = Math.min(data.length, 96);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            int value = data[i] & 0xff;
            if (value < 0x10) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(value));
        }
        if (data.length > max) {
            builder.append(" ...");
        }
        System.out.println(label + ".len=" + data.length + " hex=" + builder);
    }
}
