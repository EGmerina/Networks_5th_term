package org.example.snakeonthenetwork.network;

import me.ippolitov.fit.snakes.SnakesProto;

public class UnicastService {

    private static final Logger logger = LogManager.getLogger(UnicastReceiver.class);
    private final DatagramSocket socket;
    private final MainController mainController;
    public UnicastService(NetworkController networkController) {
    }

    public void start() {
        // Буфер можно взять побольше, чтобы вместить GameState
        byte[] buffer = new byte[4096];

        while (!Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Блокируется, пока не придут данные

                // Обрезаем буфер до реального размера полученных данных
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Парсим Protobuf
                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);

                // Передаем в контроллер (важно передать IP и порт отправителя!)
                mainController.onMessageReceived(message, packet.getAddress(), packet.getPort());

            } catch (IOException e) {
                if (socket.isClosed()) {
                    logger.info("Unicast socket closed, stopping thread.");
                    break;
                }
                logger.error("Error receiving unicast packet", e);
            }
        }
    }

    public void stop() {
    }

    public void send(SnakesProto.GameMessage msg) {
    }
}
