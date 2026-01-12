package org.example.snakeonthenetwork.engine;

import me.ippolitov.fit.snakes.SnakesProto;

import java.util.*;

public class GameEngine {

    private final SnakesProto.GameConfig config;
    private final Random random = new Random();

    public GameEngine(SnakesProto.GameConfig config) {
        this.config = config;
    }

    // =========================================================================
    // 1. СОЗДАНИЕ НАЧАЛЬНОГО СОСТОЯНИЯ (ДЛЯ МАСТЕРА)
    // =========================================================================
    public SnakesProto.GameState createInitialState(String masterName) {
        int width = config.getWidth();
        int height = config.getHeight();

        // 1. Создаем Мастера (ID = 1)
        SnakesProto.GamePlayer master = SnakesProto.GamePlayer.newBuilder()
                .setName(masterName)
                .setId(1)
                .setIpAddress("") // Заполнится сетевым модулем позже
                .setPort(0)
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

        // Копируем списки, чтобы изменять их
        List<SnakesProto.GameState.Snake> oldSnakes = currentState.getSnakesList();
        List<SnakesProto.GamePlayer> players = new ArrayList<>(currentState.getPlayers().getPlayersList());
        List<SnakesProto.GameState.Coord> foods = new ArrayList<>(currentState.getFoodsList());

        List<SnakesProto.GameState.Snake> movedSnakes = new ArrayList<>();

        // --- ШАГ 1: Двигаем змей ---
        for (SnakesProto.GameState.Snake snake : oldSnakes) {
            // Если змея зомби — она не управляется (просто летит вперед или стоит, тут упростим: летит)
            if (snake.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
                movedSnakes.add(moveSingleSnake(snake, snake.getHeadDirection(), foods, players));
                continue;
            }

            // Определяем направление
            SnakesProto.Direction currentDir = snake.getHeadDirection();
            SnakesProto.Direction nextDir = currentDir;

            if (playerMoves != null && playerMoves.containsKey(snake.getPlayerId())) {
                SnakesProto.Direction requested = playerMoves.get(snake.getPlayerId());
                // Запрещаем разворот на 180 градусов
                if (!isOpposite(currentDir, requested)) {
                    nextDir = requested;
                }
            }

            // Двигаем и проверяем еду
            movedSnakes.add(moveSingleSnake(snake, nextDir, foods, players));
        }

        // --- ШАГ 2: Проверяем столкновения (Смерть) ---
        List<SnakesProto.GameState.Snake> survivingSnakes = new ArrayList<>();

        for (SnakesProto.GameState.Snake attacker : movedSnakes) {
            if (attacker.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
                survivingSnakes.add(attacker); // Зомби не умирают (пока)
                continue;
            }

            boolean isDead = checkCollision(attacker, movedSnakes);

            if (isDead) {
                // Игрок становится зрителем
                updatePlayerScoreOrRole(players, attacker.getPlayerId(), 0, true);
                // Змея может стать едой (с вероятностью 0.1 по заданию), но пока просто удаляем
            } else {
                survivingSnakes.add(attacker);
            }
        }

        // --- ШАГ 3: Добавляем еду (если съели) ---
        foods = generateFood(width, height, survivingSnakes, foods);

        // --- ШАГ 4: Собираем новый стейт ---
        return currentState.toBuilder()
                .setStateOrder(currentState.getStateOrder() + 1)
                .clearSnakes().addAllSnakes(survivingSnakes)
                .clearFoods().addAllFoods(foods)
                .setPlayers(SnakesProto.GamePlayers.newBuilder().addAllPlayers(players)) // Обновленные очки/роли
                .build();
    }

    // =========================================================================
    // 3. УПРАВЛЕНИЕ ИГРОКАМИ (ADD / REMOVE)
    // =========================================================================

    /**
     * Пытается добавить игрока.
     * Возвращает НОВЫЙ GameState, если удалось.
     * Возвращает СТАРЫЙ GameState, если места нет.
     */
    public int addPlayer(SnakesProto.GameState currentState, String name, SnakesProto.NodeRole role) {
        int width = config.getWidth();
        int height = config.getHeight();

        // 1. Ищем свободное место (попытки найти квадрат 5x5)
        SnakesProto.GameState.Coord headCoord = findFreeSquare(currentState, 5);

        if (headCoord == null) {
            return -1; // Места нет, возвращаем как было
        }

        // 2. Вычисляем новый ID (max + 1)
        int maxId = 0;
        for (var p : currentState.getPlayers().getPlayersList()) {
            if (p.getId() > maxId) maxId = p.getId();
        }
        int newId = maxId + 1;

        // 3. Создаем игрока
        SnakesProto.GamePlayer newPlayer = SnakesProto.GamePlayer.newBuilder()
                .setName(name)
                .setId(newId)
                .setRole(role)
                .setType(SnakesProto.PlayerType.HUMAN)
                .setScore(0)
                .setIpAddress("")
                .setPort(0)
                .build();

        // 4. Создаем змею
        SnakesProto.GameState.Snake newSnake = createSnake(newId, headCoord.getX(), headCoord.getY());

        SnakesProto.GameState newState = currentState.toBuilder()
                .setPlayers(currentState.getPlayers().toBuilder().addPlayers(newPlayer))
                .addSnakes(newSnake)
                .build();
        currentState = newState;

        return newId;
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
        int needed = config.getFoodStatic() + (int) (config.getFoodStatic() * snakes.size());

        while (foods.size() < needed) {
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
                if (setViewer) b.setRole(SnakesProto.NodeRole.VIEWER);
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