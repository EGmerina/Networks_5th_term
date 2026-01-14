package org.example.snakeonthenetwork.engine;

import me.ippolitov.fit.snakes.SnakesProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.snakeonthenetwork.controller.MainController;

import java.net.InetSocketAddress;
import java.util.*;

public class GameEngine {

    private final SnakesProto.GameConfig config;
    private final Random random = new Random();
    private static final Logger logger = LogManager.getLogger(GameEngine.class);
    private MainController controller;

    public GameEngine(SnakesProto.GameConfig config, MainController mainController) {
        this.config = config;
        this.controller = mainController;
    }

    // =========================================================================
    // 1. СОЗДАНИЕ НАЧАЛЬНОГО СОСТОЯНИЯ (ДЛЯ МАСТЕРА)
    // =========================================================================
    public SnakesProto.GameState createInitialState(String masterName, int unicastPort) {
        int width = config.getWidth();
        int height = config.getHeight();

        // 1. Создаем Мастера (ID = 1)
        SnakesProto.GamePlayer master = SnakesProto.GamePlayer.newBuilder()
                .setName(masterName)
                .setId(1)
                .setIpAddress("") // Заполнится сетевым модулем позже
                .setPort(unicastPort)
                .setRole(SnakesProto.NodeRole.MASTER)
                .setType(SnakesProto.PlayerType.HUMAN)
                .setScore(0)
                .build();

        // 2. Создаем Змею Мастера (по центру)
        SnakesProto.GameState.Snake masterSnake = createSnake(1, width / 2, height / 2);

        // 3. Генерируем еду
        List<SnakesProto.GameState.Snake> snakes = new ArrayList<>();
        snakes.add(masterSnake);
        List<SnakesProto.GameState.Coord> foods = generateFood(width, height, snakes, new ArrayList<>());

        // 4. Собираем всё в State
        return SnakesProto.GameState.newBuilder()
                .setStateOrder(0)
                .setPlayers(SnakesProto.GamePlayers.newBuilder().addPlayers(master))
                .addSnakes(masterSnake)
                .addAllFoods(foods)
                .build();
    }

    // =========================================================================
    // 2. ГЛАВНЫЙ ИГРОВОЙ ЦИКЛ (UPDATE)
    // =========================================================================
    public SnakesProto.GameState update(SnakesProto.GameState currentState, Map<Integer, SnakesProto.Direction> playerMoves) {
        int width = config.getWidth();
        int height = config.getHeight();

        // Копируем списки
        List<SnakesProto.GameState.Snake> oldSnakes = currentState.getSnakesList();
        List<SnakesProto.GamePlayer> players = new ArrayList<>(currentState.getPlayers().getPlayersList());
        List<SnakesProto.GameState.Coord> foods = new ArrayList<>(currentState.getFoodsList());

        List<SnakesProto.GameState.Snake> movedSnakes = new ArrayList<>();

        // --- ШАГ 1: Двигаем змей ---
        for (SnakesProto.GameState.Snake snake : oldSnakes) {
            if (snake.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
                movedSnakes.add(moveSingleSnake(snake, snake.getHeadDirection(), foods, players));
                continue;
            }

            SnakesProto.Direction currentDir = snake.getHeadDirection();
            SnakesProto.Direction nextDir = currentDir;

            if (playerMoves != null && playerMoves.containsKey(snake.getPlayerId())) {
                SnakesProto.Direction requested = playerMoves.get(snake.getPlayerId());
                if (!isOpposite(currentDir, requested)) {
                    nextDir = requested;
                }
            }
            movedSnakes.add(moveSingleSnake(snake, nextDir, foods, players));
        }

        // --- ШАГ 2: Проверяем столкновения (Смерть) ---
        List<SnakesProto.GameState.Snake> survivingSnakes = new ArrayList<>();

        int countAliveSnakes = 0;

        for (SnakesProto.GameState.Snake attacker : movedSnakes) {
//            if (attacker.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
//                survivingSnakes.add(attacker);
//                continue;
//            }

            boolean isDead = checkCollision(attacker, movedSnakes);

            if (isDead) {

                updatePlayerScoreOrRole(players, attacker.getPlayerId(), 0, true);  //TODO не становтся зрителем

                // 2. Превращение змеи в еду (вероятность 0.5 для каждой клетки)
                List<SnakesProto.GameState.Coord> deadBody = getAbsoluteCoords(attacker);
                for (SnakesProto.GameState.Coord coord : deadBody) {
                    if (random.nextDouble() < 0.5) {
                        // Проверяем, нет ли там уже еды (опционально, но полезно)
                        boolean alreadyFood = false;
                        for (SnakesProto.GameState.Coord f : foods) {
                            if (f.getX() == coord.getX() && f.getY() == coord.getY()) {
                                alreadyFood = true;
                                break;
                            }
                        }
                        if (!alreadyFood) {
                            foods.add(coord);
                        }
                    }
                }
            } else {
                survivingSnakes.add(attacker);
                if (attacker.getState() != SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
                    countAliveSnakes += 1;
                }

            }

        }
        logger.info("alive snakes : {}", countAliveSnakes);
        if (countAliveSnakes == 0) {
            logger.trace("there are not alive snakes!");
            controller.gameOver();
        }

        // --- ШАГ 2.5: РОТАЦИЯ РОЛЕЙ (Master -> Deputy -> Normal) ---
        // Выполняем после цикла смертей, чтобы состояние игроков было финальным для этого тика
        //    ensureRoles(players);

        // --- ШАГ 3: Добавляем еду ---
        foods = generateFood(width, height, survivingSnakes, foods);

        // --- ШАГ 4: Собираем новый стейт ---
        return currentState.toBuilder()
                .setStateOrder(currentState.getStateOrder() + 1)
                .clearSnakes().addAllSnakes(survivingSnakes)
                .clearFoods().addAllFoods(foods)
                .setPlayers(SnakesProto.GamePlayers.newBuilder().addAllPlayers(players))
                .build();
    }

    private void ensureRoles(List<SnakesProto.GamePlayer> players) {
        // 1. Ищем текущего живого Мастера и Заместителя
        SnakesProto.GamePlayer master = null;
        SnakesProto.GamePlayer deputy = null;

        // ВАЖНО: Мы ищем именно по роли в списке, который уже обновлен (мертвые стали VIEWER)
        for (SnakesProto.GamePlayer p : players) {
            if (p.getRole() == SnakesProto.NodeRole.MASTER) master = p;
            else if (p.getRole() == SnakesProto.NodeRole.DEPUTY) deputy = p;
        }


        // 2. Если Мастера нет (он умер и стал VIEWER или вышел), назначаем Заместителя
        if (master == null) {

            if (deputy != null) {
                // Повышаем Deputy до Master
                logger.info("deouty to master");
                changePlayerRole(players, deputy.getId(), SnakesProto.NodeRole.MASTER);
                //TODO тут логика неправильная, если мы deputy мы вообще сюда не попадаем

                // Теперь deputy стал мастером, значит позиция DEPUTY свободна
                deputy = null;
            } else {
                // Если и Заместителя нет, можно попробовать назначить любого NORMAL (экстренный случай)
                // Но по спецификации: Master умирает -> Deputy становится Master.
                // Если Deputy не было, то Master выбирается из живых (обычно тот, у кого id меньше или счет больше)
                int bestCandidate = findBestCandidate(players);
                if (bestCandidate != -1) {
                    changePlayerRole(players, bestCandidate, SnakesProto.NodeRole.MASTER);
                }
            }
        }

        // 3. Если Заместителя нет (он умер, вышел или только что стал Мастером)
        if (deputy == null) {
            int bestCandidate = findBestCandidate(players);
            if (bestCandidate != -1) {
                changePlayerRole(players, bestCandidate, SnakesProto.NodeRole.DEPUTY);
            }
        }
    }

    // Вспомогательный метод для смены роли конкретного игрока
    private void changePlayerRole(List<SnakesProto.GamePlayer> players, int playerId, SnakesProto.NodeRole newRole) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId() == playerId) {
                players.set(i, players.get(i).toBuilder().setRole(newRole).build());
                logger.info("ROLE {} !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", newRole);
                controller.sendChangeRole(playerId, newRole);
                return;
            }
        }

    }

    // Поиск кандидата на повышение (среди NORMAL). Выбираем первого попавшегося или по счету.
    private int findBestCandidate(List<SnakesProto.GamePlayer> players) {
        // Можно добавить логику "самый большой счет", но пока берем первого NORMAL
        for (SnakesProto.GamePlayer p : players) {
            if (p.getRole() == SnakesProto.NodeRole.NORMAL) {
                return p.getId();
            }
        }
        return -1; // Кандидатов нет
    }


    // =========================================================================
    // 3. УПРАВЛЕНИЕ ИГРОКАМИ (ADD / REMOVE)
    // =========================================================================

    public record PlayerIdAndState(SnakesProto.GameState newState, int playerId, String error) {
    }

    // Изменяем сигнатуру метода
    public PlayerIdAndState addPlayer(SnakesProto.GameState currentState, String name, SnakesProto.NodeRole role, InetSocketAddress platerAddress) {
        int width = config.getWidth();
        int height = config.getHeight();

        SnakesProto.GameState.Coord headCoord = findFreeSquare(currentState, 5);

        if (headCoord == null) {
            // Возвращаем старый стейт и ошибку -1
            return new PlayerIdAndState(currentState, -1, "No space for new snake");
        }

        int maxId = 0;
        for (var p : currentState.getPlayers().getPlayersList()) {
            if (p.getId() > maxId) maxId = p.getId();
        }
        int newId = maxId + 1;

//        SnakesProto.NodeRole newRole = role;
//        if (role != SnakesProto.NodeRole.VIEWER && currentState.getPlayers().getPlayersCount() == 1) {
//            newRole = SnakesProto.NodeRole.DEPUTY;
//        }


        SnakesProto.GamePlayer newPlayer = SnakesProto.GamePlayer.newBuilder()
                .setName(name)
                .setId(newId)
                .setRole(role)
                .setType(SnakesProto.PlayerType.HUMAN)
                .setScore(0)
                .setIpAddress(platerAddress.getAddress().getHostName())
                .setPort(platerAddress.getPort())
                .build();

        SnakesProto.GameState.Snake newSnake = createSnake(newId, headCoord.getX(), headCoord.getY());

        SnakesProto.GameState newState = currentState.toBuilder()
                .setPlayers(currentState.getPlayers().toBuilder().addPlayers(newPlayer))
                .addSnakes(newSnake)
                .build();

        // ВОЗВРАЩАЕМ НОВЫЙ СТЕЙТ
        return new PlayerIdAndState(newState, newId, null);
    }

    public SnakesProto.GameState removePlayer(SnakesProto.GameState currentState, int playerId) {
        // Удаляем из списка игроков
        SnakesProto.GamePlayers.Builder playersBuilder = SnakesProto.GamePlayers.newBuilder();
        for (var p : currentState.getPlayers().getPlayersList()) {
            if (p.getId() != playerId) {
                playersBuilder.addPlayers(p);
            }
        }

        // Змею делаем ZOMBIE (чтобы она не исчезла мгновенно, а осталась препятствием)
        List<SnakesProto.GameState.Snake> newSnakes = new ArrayList<>();
        for (SnakesProto.GameState.Snake s : currentState.getSnakesList()) {
            if (s.getPlayerId() == playerId) {
                newSnakes.add(s.toBuilder()
                        .setState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE)
                        .build());
            } else {
                newSnakes.add(s);
            }
        }

        return currentState.toBuilder()
                .setPlayers(playersBuilder)
                .clearSnakes().addAllSnakes(newSnakes)
                .build();
    }

    public SnakesProto.GameState updateRole(SnakesProto.GameState currentState, int playerId, SnakesProto.NodeRole newRole) {
        SnakesProto.GamePlayers.Builder playersBuilder = currentState.getPlayers().toBuilder();
        boolean found = false;

        // Меняем роль в списке
        for (int i = 0; i < playersBuilder.getPlayersCount(); i++) {
            if (playersBuilder.getPlayers(i).getId() == playerId) {
                playersBuilder.setPlayers(i, playersBuilder.getPlayers(i).toBuilder().setRole(newRole).build());
                found = true;
                break;
            }
        }

        SnakesProto.GameState tempState = currentState.toBuilder().setPlayers(playersBuilder).build();

        // Если стал VIEWER — удаляем змею
        if (found && newRole == SnakesProto.NodeRole.VIEWER) {
            List<SnakesProto.GameState.Snake> snakes = new ArrayList<>();
            for (var s : tempState.getSnakesList()) {
                if (s.getPlayerId() != playerId) snakes.add(s);
            }
            return tempState.toBuilder().clearSnakes().addAllSnakes(snakes).build();
        }

        return tempState;
    }

    // =========================================================================
    // 4. ВСПОМОГАТЕЛЬНАЯ ЛОГИКА (ДВИЖЕНИЕ И КОЛЛИЗИИ)
    // =========================================================================

    private SnakesProto.GameState.Snake moveSingleSnake(SnakesProto.GameState.Snake snake,
                                                        SnakesProto.Direction dir,
                                                        List<SnakesProto.GameState.Coord> foods,
                                                        List<SnakesProto.GamePlayer> players) {
        // Распаковка координат
        List<SnakesProto.GameState.Coord> absCoords = getAbsoluteCoords(snake);
        SnakesProto.GameState.Coord head = absCoords.get(0);

        // Вычисляем новую голову (с учетом тора)
        SnakesProto.GameState.Coord newHead = shift(head, dir);

        // Проверяем еду
        boolean ate = false;
        Iterator<SnakesProto.GameState.Coord> it = foods.iterator();
        while (it.hasNext()) {
            SnakesProto.GameState.Coord f = it.next();
            if (f.getX() == newHead.getX() && f.getY() == newHead.getY()) {
                ate = true;
                it.remove(); // Еда съедена
                updatePlayerScoreOrRole(players, snake.getPlayerId(), 1, false); // +1 очко
                break;
            }
        }

        // Двигаем тело
        List<SnakesProto.GameState.Coord> newAbsCoords = new ArrayList<>();
        newAbsCoords.add(newHead);
        newAbsCoords.addAll(absCoords);
        if (!ate) {
            newAbsCoords.remove(newAbsCoords.size() - 1); // Убираем хвост, если не поели
        }

        // Упаковка обратно
        return snake.toBuilder()
                .clearPoints().addAllPoints(convertToOffsets(newAbsCoords))
                .setHeadDirection(dir)
                .build();
    }

    private boolean checkCollision(SnakesProto.GameState.Snake attacker, List<SnakesProto.GameState.Snake> allSnakes) {
        SnakesProto.GameState.Coord head = getAbsoluteCoords(attacker).get(0);

        for (SnakesProto.GameState.Snake target : allSnakes) {
            List<SnakesProto.GameState.Coord> targetBody = getAbsoluteCoords(target);

            // Если проверяем сами себя, пропускаем голову (индекс 0), чтобы не врезаться сразу
            int startIndex = (attacker == target) ? 1 : 0;

            for (int i = startIndex; i < targetBody.size(); i++) {
                SnakesProto.GameState.Coord b = targetBody.get(i);
                if (head.getX() == b.getX() && head.getY() == b.getY()) {
                    return true; // Врезался!
                }
            }
        }
        return false;
    }

    // =========================================================================
    // 5. МАТЕМАТИКА И УТИЛИТЫ (PRIVATE)
    // =========================================================================

    // Превращаем относительные смещения в абсолютные координаты
    private List<SnakesProto.GameState.Coord> getAbsoluteCoords(SnakesProto.GameState.Snake snake) {
        List<SnakesProto.GameState.Coord> res = new ArrayList<>();
        if (snake.getPointsCount() == 0) return res;

        int cx = snake.getPoints(0).getX();
        int cy = snake.getPoints(0).getY();
        res.add(coord(cx, cy));

        for (int i = 1; i < snake.getPointsCount(); i++) {
            SnakesProto.GameState.Coord off = snake.getPoints(i);
            cx = (cx + off.getX() + config.getWidth()) % config.getWidth();
            cy = (cy + off.getY() + config.getHeight()) % config.getHeight();
            res.add(coord(cx, cy));
        }
        return res;
    }

    // Превращаем абсолютные координаты обратно в смещения
    private List<SnakesProto.GameState.Coord> convertToOffsets(List<SnakesProto.GameState.Coord> abs) {
        List<SnakesProto.GameState.Coord> res = new ArrayList<>();
        if (abs.isEmpty()) return res;

        res.add(abs.get(0)); // Первая точка как есть

        for (int i = 1; i < abs.size(); i++) {
            int prevX = abs.get(i - 1).getX();
            int prevY = abs.get(i - 1).getY();
            int currX = abs.get(i).getX();
            int currY = abs.get(i).getY();

            // Считаем кратчайшее смещение с учетом тора
            int dx = currX - prevX;
            int dy = currY - prevY;

            if (dx > config.getWidth() / 2) dx -= config.getWidth();
            if (dx < -config.getWidth() / 2) dx += config.getWidth();

            if (dy > config.getHeight() / 2) dy -= config.getHeight();
            if (dy < -config.getHeight() / 2) dy += config.getHeight();

            res.add(coord(dx, dy));
        }
        return res;
    }

    // Сдвиг координаты на 1 клетку
    private SnakesProto.GameState.Coord shift(SnakesProto.GameState.Coord c, SnakesProto.Direction d) {
        int x = c.getX();
        int y = c.getY();
        switch (d) {
            case UP -> y -= 1;
            case DOWN -> y += 1;
            case LEFT -> x -= 1;
            case RIGHT -> x += 1;
        }
        // Заворачиваем координаты (Тор)
        x = (x + config.getWidth()) % config.getWidth();
        y = (y + config.getHeight()) % config.getHeight();
        return coord(x, y);
    }

    // Генерация еды
    private List<SnakesProto.GameState.Coord> generateFood(int w, int h, List<SnakesProto.GameState.Snake> snakes, List<SnakesProto.GameState.Coord> foods) {
        int needed = config.getFoodStatic() + snakes.size();

        while (foods.size() < needed) { //TODO for
            // Ищем свободную клетку (не занятую змеями и другой едой)
            SnakesProto.GameState.Coord f = findFreePoint(w, h, snakes, foods);
            foods.add(f);
        }
        return foods;
    }

    private SnakesProto.GameState.Coord findFreePoint(int w, int h, List<SnakesProto.GameState.Snake> snakes, List<SnakesProto.GameState.Coord> foods) {
        for (int k = 0; k < 100; k++) {
            int x = random.nextInt(w);
            int y = random.nextInt(h);
            boolean busy = false;

            for (var f : foods)
                if (f.getX() == x && f.getY() == y) {
                    busy = true;
                    break;
                }
            if (busy) continue;

            for (var s : snakes) {
                for (var p : getAbsoluteCoords(s)) {
                    if (p.getX() == x && p.getY() == y) {
                        busy = true;
                        break;
                    }
                }
                if (busy) break;
            }
            if (!busy) return coord(x, y);
        }
        return coord(0, 0);
    }

    private SnakesProto.GameState.Coord findFreeSquare(SnakesProto.GameState state, int size) {
        int w = config.getWidth();
        int h = config.getHeight();
        for (int i = 0; i < 50; i++) {
            int x = random.nextInt(w);
            int y = random.nextInt(h);
            if (isSquareFree(state, x, y, size)) return coord(x, y);
        }
        return null;
    }

    private boolean isSquareFree(SnakesProto.GameState state, int cx, int cy, int size) {
        int half = size / 2;
        int w = config.getWidth();
        int h = config.getHeight();

        for (var s : state.getSnakesList()) {
            for (var p : getAbsoluteCoords(s)) {
                // Простая проверка без учета тора для квадрата спавна
                if (Math.abs(p.getX() - cx) <= half && Math.abs(p.getY() - cy) <= half) return false;
            }
        }
        return true;
    }

    private SnakesProto.GameState.Snake createSnake(int id, int headX, int headY) {
        return SnakesProto.GameState.Snake.newBuilder()
                .setPlayerId(id)
                .addPoints(coord(headX, headY))
                .addPoints(coord(0, 1)) // Хвост вниз
                .setHeadDirection(SnakesProto.Direction.UP)
                .setState(SnakesProto.GameState.Snake.SnakeState.ALIVE)
                .build();
    }

    private void updatePlayerScoreOrRole(List<SnakesProto.GamePlayer> players, int id, int scoreAdd, boolean setViewer) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId() == id) {
                var b = players.get(i).toBuilder();
                if (scoreAdd > 0) b.setScore(b.getScore() + scoreAdd);
//                if (setViewer && players.get(i).getRole() != SnakesProto.NodeRole.MASTER && players.get(i).getRole() != SnakesProto.NodeRole.DEPUTY) {
//                    b.setRole(SnakesProto.NodeRole.VIEWER);
//                }
                players.set(i, b.build());
                return;
            }
        }
    }

    private boolean isOpposite(SnakesProto.Direction a, SnakesProto.Direction b) {
        return (a == SnakesProto.Direction.UP && b == SnakesProto.Direction.DOWN) ||
                (a == SnakesProto.Direction.DOWN && b == SnakesProto.Direction.UP) ||
                (a == SnakesProto.Direction.LEFT && b == SnakesProto.Direction.RIGHT) ||
                (a == SnakesProto.Direction.RIGHT && b == SnakesProto.Direction.LEFT);
    }

    private SnakesProto.GameState.Coord coord(int x, int y) {
        return SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y).build();
    }
}