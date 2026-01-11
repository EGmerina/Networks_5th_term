package org.example.snakeonthenetwork.network;

public class MulticastService {
    private static final int MULTICAST_PORT = 9192;
    private static final String MULTICAST_GROUP = "239.192.0.4";


    private static final Logger logger = LogManager.getLogger(MulticastReceiver.class);
    private final MulticastSocket socket;
    private final InetSocketAddress groupAddress;
    private final NetworkInterface netIf;
    private final MainController mainController;
    public MulticastService(NetworkController networkController) {
    }

    public void start() {
        byte[] buffer = new byte[4096];

        // Обязательно присоединяемся к группе перед началом цикла
        try {
            socket.joinGroup(groupAddress, netIf);
            logger.info("Joined multicast group " + groupAddress);
        } catch (IOException e) {
            logger.error("Failed to join multicast group", e);
            return;
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Слушаем порт 9192

                // Не обрабатываем свои же пакеты (по желанию)
                // if (isMyPacket(packet)) continue;

                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);

                // Анонсы передаем в контроллер
                mainController.onMessageReceived(message, packet.getAddress(), packet.getPort());

            } catch (IOException e) {
                if (socket.isClosed()) break;
                logger.error("Error receiving multicast packet", e);
            }
        }

        // Выходим из группы при остановке
        try {
            socket.leaveGroup(groupAddress, netIf);
        } catch (IOException e) {
            logger.error("Error leaving group", e);
        }
    }

    public void stop() {
    }
}
