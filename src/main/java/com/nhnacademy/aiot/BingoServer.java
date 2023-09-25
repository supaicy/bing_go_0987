package com.nhnacademy.aiot;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BingoServer {

    public static final String FONT_RED = "\u001B[31m";
    public static final String FONT_BLUE = "\u001B[34m";
    // 출력 후 색 초기화
    public static final String RESET = "\u001B[0m";

    private final int MAXIMUM_PLAYER = 2;
    private final int BOARD_SIZE = 5;

    List<Player> players = new ArrayList<>(MAXIMUM_PLAYER);

    static AtomicInteger playerIndex = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {

        BingoServer server = new BingoServer();
        server.joinPlayer();

        log.info("모든 플레이어가 입장");

        log.info("게임 판을 생성합니다");
        server.initializeGame();
        log.info("게임판 생성 완료");


        log.info("게임 순서 정하기 시작");

        try {
            server.decideTurn();
        } catch (IOException e) {

            e.printStackTrace();
        }
        log.info("게임 순서 정하기 완료");

        log.info("게임시작");
        int turn = 0;
        try {
            while (true) {

                Player currentPlayer = server.players.get(turn);

                try {
                    server.chooseNumber(currentPlayer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                turn++;
                if (turn % server.MAXIMUM_PLAYER == 0) {
                    turn = 0;
                }

                if (server.isFinished()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("연결 에러");
        }

        log.info("게임 종료");


    }

    /**
     * <pre>
     *  1.서버 생성
     * 2.각 플레이어 입장 후 닉네임 설정료
     * 3.서버의 플레이어 리스트에 플레이어 추가
     * </pre>
     */
    public void joinPlayer() {
        try (ServerSocket serverSocket = new ServerSocket(8081);) {
            CountDownLatch countDownLatch = new CountDownLatch(MAXIMUM_PLAYER);
            for (int i = 0; i < MAXIMUM_PLAYER; i++) {
                Socket socket = serverSocket.accept();
                Thread playerThread = new Thread(() -> {
                    try {
                        Player player = new Player(socket);

                        sendMessageToPlayer(player, "닉네임을 입력해주세요");
                        player.setNickname(player.getReader().readLine());
                        sendMessageToPlayer(player, player.getNickname() + "님 안녕하세요");

                        players.add(BingoServer.playerIndex.getAndIncrement(), player);
                        sendMessageToPlayer(player, "다른 플레이어를 기다리는 중");
                        Thread.sleep(1000);
                    } catch (IOException e) {
                        System.err.println(e);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        countDownLatch.countDown();
                    }
                });
                playerThread.start();
            }
            countDownLatch.await();
        } catch (IOException | InterruptedException e) {
            System.err.println(e);
        }

    }

    /**
     * <H3>빙고판 생성</H3>
     *
     * <pre>
     * 1.참가자마다 N x N으로 나뉘어져 있는 bingo판이 주어진다.
     * 2.참가자는 각 칸에 주어진 숫자범위에서 숫자들을 골라 마음대로 배치한다.
     * 3.참가자들은 각자 마음에 드는 숫자를 하나씩 선택한다.
     * 4.자신이 선택 숫자는 파란색, 다른 참가자가 선택한 숫자는 붉은색으로 표시한다.
     * </pre>
     */
    public void initializeGame() {
        int playerCount = 0;
        CountDownLatch countDownLatch = new CountDownLatch(MAXIMUM_PLAYER);
        for (Player player : players) {

            Thread t = new Thread(() -> {

                int[][] bingoBoard = new int[BOARD_SIZE][BOARD_SIZE];

                try {
                    sendMessageToPlayer(player, "1부터 25중 배치할 모든 숫자를 중복 없이 차례대로 입력해주세요.");
                    String[] numbers = player.getReader().readLine().split(" ");

                    while (!isValidInput(player, numbers)) {
                        numbers = player.getReader().readLine().split(" ");
                    }

                    int idx = 0;
                    for (int i = 0; i < BOARD_SIZE; i++) {
                        for (int j = 0; j < BOARD_SIZE; j++) {
                            bingoBoard[i][j] = Integer.parseInt(numbers[idx++]);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }

                player.setBingoBoard(bingoBoard);
                try {
                    printBingoBoard(player);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
            t.start();
            playerCount++;
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    /**
     * 게임 승리 조건
     *
     * @throws IOException
     */
    public boolean isFinished() throws IOException {
        int winCount = 0;
        Player winner = null;
        for (Player player : players) {
            int[][] bingoBoard = player.getBingoBoard();

            int leftDiagCount = 0;
            int rightDiagCount = 0;

            for (int i = 0; i < BOARD_SIZE; i++) {
                // 가로세로 정답
                int rowCount = 0;
                int colCount = 0;

                // 대각선 정답 확인
                if (bingoBoard[i][i] <= 0) {
                    leftDiagCount++;
                }
                if (bingoBoard[i][4 - i] <= 0) {

                    rightDiagCount++;
                }

                for (int j = 0; j < BOARD_SIZE; j++) {
                    if (bingoBoard[i][j] <= 0) {
                        rowCount++;
                    }
                    if (bingoBoard[j][i] <= 0) {
                        colCount++;
                    }

                }
                if (rowCount == BOARD_SIZE || colCount == BOARD_SIZE || rightDiagCount == BOARD_SIZE
                        || leftDiagCount == BOARD_SIZE) {
                    winner = player;
                    winCount++;
                } else {

                }
            }


        }

        if (winCount == MAXIMUM_PLAYER) {
            broadcastMessage("모든 플레이어가 승리");
            return true;
        } else if (winCount == 1) {
            broadcastMessage(winner.getNickname() + "님이 승리하셨습니다");
            return true;
        } else {
            return false;
        }

    }

    /**
     * <H3>게임순서 정하기</H3>
     *
     * <pre>
     * 두 참가자의 배치가 끝나면 서버에서는 게임 시작을 위해 먼저할 참가자를 정한다.
     * (선택사항?)
     * 1.우선 입장한 참가자가 우선할 수 있다.
     * 2.두 참가자에게 가위, 바위, 보를 시킬 수 있다.
     * </pre>
     *
     * @throws IOException
     */
    public void decideTurn() throws IOException {
        // 우선 입장한 참가자
        for (int i = 0; i < MAXIMUM_PLAYER; i++) {
            players.get(i).setTurn(i);
            broadcastMessage((i + 1) + "번 : " + players.get(i).getNickname());
        }
    }

    /**
     * <H3>빙고판 출력</H3>
     *
     * <pre>
     * 1. 참가자가 번호를 선택하면, 각 참가자의 bingo판에 O 또는 X를 표시하여 출력한다.
     * 2. 참가자는 자신의 bingo판만 볼 수 있다.
     * </pre>
     *
     * @throws IOException
     */
    public void printBingoBoard(Player player) throws IOException {
        int[][] board = player.getBingoBoard();
        sendMessageToPlayer(player, " ================");
        for (int i = 0; i < board.length; i++) {

            StringBuilder sb = new StringBuilder();
            sb.append("| ");
            for (int j = 0; j < board[i].length; j++) {
                if (board[i][j] == 0) {
                    sb.append(FONT_BLUE + "OO " + RESET);
                } else if (board[i][j] == -1) {
                    sb.append(FONT_RED + "XX " + RESET);
                } else {
                    if (10 > board[i][j]) {
                        sb.append("0" + board[i][j] + " ");
                    } else {
                        sb.append(board[i][j] + " ");
                    }
                }
            }
            sendMessageToPlayer(player, sb.toString() + "|");
        }
        sendMessageToPlayer(player, " ================");
    }


    /**
     * <H3>번호 선택</H3>
     *
     * <pre>
     * 차례가 돌아온 참가자에게는 번호 선택 알림이 출력된다.
     * --선택할 번호는?--
     * </pre>
     *
     * @throws IOException
     */
    public void chooseNumber(Player player) throws IOException {
        while (true) {

            broadcastMessage(player.getNickname() + "님의 차례입니다");
            sendMessageToPlayer(player, "==번호를 선택해주세요==");

            int choiceNum;

            try {
                choiceNum = Integer.parseInt(player.getReader().readLine());
            } catch (NumberFormatException e) {
                sendMessageToPlayer(player, "숫자만 입력하세요");
                continue;
            }

            int[] numberPosition = findNumberPosition(player.getBingoBoard(), choiceNum);
            if (numberPosition == null) {
                sendMessageToPlayer(player, "올바른 숫자를 입력하세요");
                continue;
            }
            int row = numberPosition[0];
            int col = numberPosition[1];
            player.markNumber(row, col, 0);

            for (Player otherPlayer : players) {
                if (otherPlayer != player) {
                    numberPosition = findNumberPosition(otherPlayer.getBingoBoard(), choiceNum);
                    row = numberPosition[0];
                    col = numberPosition[1];
                    otherPlayer.markNumber(row, col, -1);
                    printBingoBoard(otherPlayer);
                }
            }
            printBingoBoard(player);
            break;
        }
    }

    public int[] findNumberPosition(int[][] bingoBoard, int number) {
        for (int row = 0; row < bingoBoard.length; row++) {
            for (int col = 0; col < bingoBoard.length; col++) {
                if (bingoBoard[row][col] == number) {
                    return new int[]{row, col, -1}; // 번호가 있다면 row, col 반환
                }
            }
        }
        return null; // 번호를 못 찾으면 null 반환
    }


    /**
     * 요
     * <H3>모든 플레이어에게 메시지 출력</H3>
     *
     * <pre>
     * message를 입력 받아 모든 Player에게 메시지 출력 메서드
     * </pre>
     *
     * @param message
     * @throws IOException
     */
    public void broadcastMessage(String message) throws IOException {
        for (int i = 0; i < MAXIMUM_PLAYER; i++) {
            sendMessageToPlayer(players.get(i), message);
        }
    }

    /**
     * <H3>한 명의 플레이어에게 메시지 출력</H3>
     *
     * <pre>
     * Player와 message를 입력 받아 해당 Player게 메시지 출력 메서드
     * </pre>
     *
     * @param player
     * @param message
     * @throws IOException
     */
    public void sendMessageToPlayer(Player player, String message) throws IOException {
        player.getWriter().write(message + System.lineSeparator());
        player.getWriter().flush();
    }


    public boolean isValidInput(Player player, String[] input) {
        try {
            // 입력 갯수가 잘못된 경우
            if (input.length != BOARD_SIZE * BOARD_SIZE) {
                sendMessageToPlayer(player, "숫자를 정확히 25개 입력해야 합니다. 다시 입력해주세요.");
                return false;
            }

            int[] inputNumbers = new int[BOARD_SIZE * BOARD_SIZE];
            for (int i = 0; i < BOARD_SIZE * BOARD_SIZE; i++) {
                try {
                    inputNumbers[i] = Integer.parseInt(input[i]);
                } catch (NumberFormatException e) {
                    // 숫자 형식이 아닌 경우
                    sendMessageToPlayer(player, "숫자 형식이 아닙니다. 다시 입력해주세요.");
                    return false;
                }
            }

            // 중복된 숫자를 입력한 경우
            if (Arrays.stream(inputNumbers).distinct().toArray().length < BOARD_SIZE * BOARD_SIZE) {
                sendMessageToPlayer(player, "중복된 숫자를 입력하셨습니다. 다시 입력해주세요.");
                return false;
            }

            // 범위를 벗어난 숫자가 있는 경우
            for (int i = 0; i < BOARD_SIZE * BOARD_SIZE; i++) {
                if (inputNumbers[i] < 1 || inputNumbers[i] > BOARD_SIZE * BOARD_SIZE) {
                    sendMessageToPlayer(player, "범위를 벗어난 숫자가 있습니다. 다시 입력해주세요.");
                    return false;
                }
            }
        } catch (IOException e) {

        }
        return true;
    }

}


