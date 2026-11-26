package cn.garymb.ygomobile.rpg.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Pure-Java YGOPro TCP bot client for OCG Timeline RPG Mode.
 * Connects directly to YGOMobile's local LanGameServer (127.0.0.1:7911),
 * loads the opponent's timeline-compliant .ydk deck, and delegates all duel decisions
 * to {@link RpgAiDuelState} + {@link RpgAiStrategy} instead of WindBot's generic Deck=Lucky.
 */
public class RpgAiDuelClient {

    // ===== YGOPro Protocol Constants =====
    public static final int CTOS_RESPONSE     = 0x01;
    public static final int CTOS_UPDATE_DECK  = 0x02;
    public static final int CTOS_HAND_RESULT  = 0x03;
    public static final int CTOS_TP_RESULT    = 0x04;
    public static final int CTOS_PLAYER_INFO  = 0x10;
    public static final int CTOS_JOIN_GAME    = 0x12;
    public static final int CTOS_TIME_CONFIRM = 0x15;
    public static final int CTOS_HS_READY     = 0x22;

    public static final int STOC_GAME_MSG     = 0x01;
    public static final int STOC_ERROR_MSG    = 0x02;
    public static final int STOC_SELECT_HAND  = 0x03;
    public static final int STOC_SELECT_TP    = 0x04;
    public static final int STOC_CHANGE_SIDE  = 0x07;
    public static final int STOC_JOIN_GAME    = 0x12;
    public static final int STOC_DUEL_START   = 0x15;
    public static final int STOC_DUEL_END     = 0x16;
    public static final int STOC_TIME_LIMIT   = 0x18;

    // ===== Engine Message Constants =====
    private static final int MSG_RETRY               = 1;
    private static final int MSG_HINT                = 2;
    private static final int MSG_START               = 4;
    private static final int MSG_WIN                 = 5;
    private static final int MSG_UPDATE_DATA         = 6;
    private static final int MSG_UPDATE_CARD         = 7;
    private static final int MSG_SELECT_BATTLECMD    = 10;
    private static final int MSG_SELECT_IDLECMD      = 11;
    private static final int MSG_SELECT_EFFECTYN     = 12;
    private static final int MSG_SELECT_YESNO        = 13;
    private static final int MSG_SELECT_OPTION       = 14;
    private static final int MSG_SELECT_CARD         = 15;
    private static final int MSG_SELECT_CHAIN        = 16;
    private static final int MSG_SELECT_PLACE        = 18;
    private static final int MSG_SELECT_POSITION     = 19;
    private static final int MSG_SELECT_TRIBUTE      = 20;
    private static final int MSG_SELECT_COUNTER      = 22;
    private static final int MSG_SELECT_SUM          = 23;
    private static final int MSG_SELECT_DISFIELD     = 24;
    private static final int MSG_SORT_CARD           = 25;
    private static final int MSG_SELECT_UNSELECT_CARD = 26;
    private static final int MSG_SHUFFLE_HAND        = 33;
    private static final int MSG_NEW_TURN            = 40;
    private static final int MSG_NEW_PHASE           = 41;
    private static final int MSG_MOVE                = 50;
    private static final int MSG_POS_CHANGE          = 53;
    private static final int MSG_SET                 = 54;
    private static final int MSG_SWAP                = 55;
    private static final int MSG_SUMMONING           = 60;
    private static final int MSG_SPSUMMONING         = 62;
    private static final int MSG_FLIPSUMMONING       = 64;
    private static final int MSG_CHAINING            = 70;
    private static final int MSG_CHAIN_END           = 74;
    private static final int MSG_DRAW                = 90;
    private static final int MSG_DAMAGE              = 91;
    private static final int MSG_RECOVER             = 92;
    private static final int MSG_LPUPDATE            = 94;
    private static final int MSG_PAY_LPCOST          = 100;
    private static final int MSG_ATTACK              = 110;
    private static final int MSG_ROCK_PAPER_SCISSORS = 132;
    private static final int MSG_ANNOUNCE_RACE       = 140;
    private static final int MSG_ANNOUNCE_ATTRIB     = 141;
    private static final int MSG_ANNOUNCE_CARD       = 142;
    private static final int MSG_ANNOUNCE_NUMBER     = 143;
    private static final int MSG_RELOAD_FIELD        = 162;

    private static final List<RpgAiDuelClient> sActiveInstances = new ArrayList<>();

    private final String host;
    private final int port;
    private final int proVersion;
    private final String botName;
    private final List<Integer> mainDeck;
    private final List<Integer> extraDeck;
    private final List<Integer> sideDeck;
    private final RpgAiDuelState state;
    private final RpgAiStrategy strategy;
    private final Random random = new Random();
    private final long actionDelayMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean joinedRoom = false;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread workerThread;

    private byte[] lastQuestionBytes;
    private int repeatQuestionCount = 0;

    public RpgAiDuelClient(String host,
                           int port,
                           int proVersion,
                           AiOpponent opponent,
                           String fillStyle,
                           List<Integer> mainDeck,
                           List<Integer> extraDeck,
                           List<Integer> sideDeck,
                           RpgCatalog catalog,
                           OcgRuleSet ruleSet,
                           String currentTimelineDate,
                           long actionDelayMs) {
        this(host, port, proVersion,
                (opponent != null && opponent.name != null && !opponent.name.isEmpty())
                        ? opponent.name : "OCG RPG AI",
                opponent, fillStyle, mainDeck, extraDeck, sideDeck,
                catalog, ruleSet, currentTimelineDate, actionDelayMs);
    }

    public RpgAiDuelClient(String host,
                           int port,
                           int proVersion,
                           String displayName,
                           AiOpponent opponent,
                           String fillStyle,
                           List<Integer> mainDeck,
                           List<Integer> extraDeck,
                           List<Integer> sideDeck,
                           RpgCatalog catalog,
                           OcgRuleSet ruleSet,
                           String currentTimelineDate,
                           long actionDelayMs) {
        this.host = host;
        this.port = port;
        this.proVersion = proVersion;
        this.botName = (displayName != null && !displayName.isEmpty()) ? displayName : "OCG RPG AI";
        this.mainDeck = mainDeck != null ? new ArrayList<>(mainDeck) : new ArrayList<>();
        this.extraDeck = extraDeck != null ? new ArrayList<>(extraDeck) : new ArrayList<>();
        this.sideDeck = sideDeck != null ? new ArrayList<>(sideDeck) : new ArrayList<>();
        this.state = new RpgAiDuelState(catalog, ruleSet, currentTimelineDate);
        this.strategy = new RpgAiStrategy(this.state, opponent, fillStyle);
        this.actionDelayMs = Math.max(0L, actionDelayMs);
    }

    /** Stops any previously running RPG AI clients and launches a single opponent AI asynchronously. */
    public static synchronized RpgAiDuelClient launchAsync(String host,
                                                           int port,
                                                           int proVersion,
                                                           AiOpponent opponent,
                                                           String aiDeckYdkPath,
                                                           RpgCatalog catalog,
                                                           OcgRuleSet ruleSet,
                                                           String currentTimelineDate) {
        stopActive();
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        parseYdkFile(aiDeckYdkPath, main, extra, side);

        String fillStyle = "GOOD_STUFF";
        if (opponent != null) {
            AiOpponent.EraDeckProfile prof = opponent.getActiveDeckProfile(currentTimelineDate);
            if (prof != null && prof.fillStyle != null && !prof.fillStyle.isEmpty()) {
                fillStyle = prof.fillStyle;
            }
        }
        RpgAiDuelClient client = new RpgAiDuelClient(
                host, port, proVersion, opponent, fillStyle,
                main, extra, side, catalog, ruleSet, currentTimelineDate, 350L);
        sActiveInstances.add(client);
        client.start();
        return client;
    }

    /**
     * Launches TWO pure-Java OCG AI clients (Seat 0 = Side A, Seat 1 = Side B) for live 3D spectate mode.
     * Side B joins slightly after Side A so seat ordering matches A vs B cleanly.
     */
    public static synchronized void launchSpectatePairAsync(String host,
                                                            int port,
                                                            int proVersion,
                                                            String nameA, List<Integer> mainA, List<Integer> extraA, String styleA,
                                                            String nameB, List<Integer> mainB, List<Integer> extraB, String styleB,
                                                            RpgCatalog catalog,
                                                            OcgRuleSet ruleSet,
                                                            String currentTimelineDate) {
        stopActive();
        final RpgAiDuelClient botA = new RpgAiDuelClient(
                host, port, proVersion, nameA, null, styleA,
                mainA, extraA, null, catalog, ruleSet, currentTimelineDate, 450L);
        final RpgAiDuelClient botB = new RpgAiDuelClient(
                host, port, proVersion, nameB, null, styleB,
                mainB, extraB, null, catalog, ruleSet, currentTimelineDate, 450L);
        sActiveInstances.add(botA);
        sActiveInstances.add(botB);
        botA.start();
        Thread delayedB = new Thread(() -> {
            try {
                Thread.sleep(350L);
            } catch (InterruptedException ignored) {
                return;
            }
            if (botA.isRunning()) {
                botB.start();
            }
        }, "RpgAiSpectateSeatB");
        delayedB.setDaemon(true);
        delayedB.start();
    }

    public static synchronized void stopActive() {
        for (RpgAiDuelClient c : sActiveInstances) {
            if (c != null) c.stop();
        }
        sActiveInstances.clear();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        workerThread = new Thread(this::runClientLoop, "RpgAiDuelClient");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void stop() {
        running.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void runClientLoop() {
        try {
            if (!connectWithRetry(10000L)) {
                return;
            }
            // Send player name + initial JOIN_GAME; retry JOIN_GAME every 250ms until host's
            // CTOS_CREATE_GAME finishes creating the room and server replies with STOC_JOIN_GAME.
            sendPlayerInfo(botName);
            sendJoinGame(proVersion, "");
            long lastJoinAttemptMs = System.currentTimeMillis();
            long joinDeadlineMs = lastJoinAttemptMs + 10000L;

            socket.setSoTimeout(250);
            byte[] header = new byte[2];
            while (running.get()) {
                int r;
                try {
                    r = readFully(in, header, 0, 2);
                } catch (SocketTimeoutException timeout) {
                    if (!joinedRoom) {
                        long now = System.currentTimeMillis();
                        if (now > joinDeadlineMs) break;
                        if (now - lastJoinAttemptMs >= 250L) {
                            sendJoinGame(proVersion, "");
                            lastJoinAttemptMs = now;
                        }
                    }
                    continue;
                }
                if (r < 2) break;

                int packetLen = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);
                if (packetLen <= 0 || packetLen > 0x20000) break;

                // Temporarily use generous timeout for reading packet body
                socket.setSoTimeout(5000);
                byte[] body = new byte[packetLen];
                r = readFully(in, body, 0, packetLen);
                socket.setSoTimeout(joinedRoom ? 0 : 250);
                if (r < packetLen) break;

                handleStocPacket(body);
            }
        } catch (Exception ignored) {
        } finally {
            running.set(false);
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean connectWithRetry(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (running.get() && System.currentTimeMillis() < deadline) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 1500);
                s.setTcpNoDelay(true);
                this.socket = s;
                this.in = s.getInputStream();
                this.out = s.getOutputStream();
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException ie) {
                    return false;
                }
            }
        }
        return false;
    }

    private void handleStocPacket(byte[] packet) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int proto = buf.get() & 0xFF;
        switch (proto) {
            case STOC_JOIN_GAME:
                if (!joinedRoom) {
                    joinedRoom = true;
                    sendUpdateDeck(mainDeck, extraDeck, sideDeck);
                    sendSimplePacket(CTOS_HS_READY);
                }
                break;

            case STOC_SELECT_HAND:
                sendSingleByte(CTOS_HAND_RESULT, 1 + random.nextInt(3));
                break;

            case STOC_SELECT_TP:
                sendSingleByte(CTOS_TP_RESULT, strategy.chooseGoFirst() ? 1 : 0);
                break;

            case STOC_CHANGE_SIDE:
                sendUpdateDeck(mainDeck, extraDeck, sideDeck);
                break;

            case STOC_TIME_LIMIT:
                sendSimplePacket(CTOS_TIME_CONFIRM);
                break;

            case STOC_DUEL_START:
                lastQuestionBytes = null;
                repeatQuestionCount = 0;
                break;

            case STOC_DUEL_END:
                running.set(false);
                break;

            case STOC_GAME_MSG:
                handleGameMsg(buf);
                break;

            case STOC_ERROR_MSG:
            default:
                break;
        }
    }

    private void handleGameMsg(ByteBuffer buf) throws IOException {
        if (buf.remaining() < 1) return;
        int msgStartPos = buf.position();
        byte[] rawQuestion = Arrays.copyOfRange(buf.array(), msgStartPos, buf.limit());
        int msgType = buf.get() & 0xFF;

        // Track repeated identical questions (server MSG_RETRY resend) to trigger safe fallback
        boolean isQuestion = isQuestionMessage(msgType);
        boolean useFallback = false;
        if (isQuestion) {
            if (lastQuestionBytes != null && Arrays.equals(lastQuestionBytes, rawQuestion)) {
                repeatQuestionCount++;
                useFallback = repeatQuestionCount >= 1;
            } else {
                lastQuestionBytes = rawQuestion;
                repeatQuestionCount = 0;
            }
        }

        switch (msgType) {
            // ===== State-tracking messages (no response) =====
            case MSG_START:
                state.onStart(buf);
                break;
            case MSG_HINT:
                state.onHint(buf);
                break;
            case MSG_NEW_TURN:
                state.onNewTurn(buf);
                break;
            case MSG_NEW_PHASE:
                state.onNewPhase(buf);
                break;
            case MSG_DAMAGE:
                state.onDamage(buf);
                break;
            case MSG_RECOVER:
                state.onRecover(buf);
                break;
            case MSG_PAY_LPCOST:
                state.onPayLpCost(buf);
                break;
            case MSG_LPUPDATE:
                state.onLpUpdate(buf);
                break;
            case MSG_DRAW:
                state.onDraw(buf);
                break;
            case MSG_SHUFFLE_HAND:
                state.onShuffleHand(buf);
                break;
            case MSG_MOVE:
                state.onMove(buf);
                break;
            case MSG_POS_CHANGE:
                state.onPosChange(buf);
                break;
            case MSG_SET:
                state.onSet(buf);
                break;
            case MSG_SWAP:
                state.onSwap(buf);
                break;
            case MSG_SUMMONING:
                state.onSummoning(buf, true);
                break;
            case MSG_SPSUMMONING:
            case MSG_FLIPSUMMONING:
                state.onSummoning(buf, false);
                break;
            case MSG_CHAINING:
                state.onChaining(buf);
                break;
            case MSG_CHAIN_END:
                state.onChainEnd();
                break;
            case MSG_ATTACK:
                state.onAttack(buf);
                break;
            case MSG_UPDATE_DATA:
                state.onUpdateData(buf);
                break;
            case MSG_UPDATE_CARD:
                state.onUpdateCard(buf);
                break;
            case MSG_RELOAD_FIELD:
                state.onReloadField(buf);
                break;
            case MSG_WIN:
                break;

            // ===== Decision / Response messages =====
            case MSG_SELECT_IDLECMD: {
                pauseBrief();
                RpgAiStrategy.IdleCmdPrompt prompt = strategy.parseIdleCmd(buf);
                int resp = useFallback
                        ? (prompt.canEnd ? 7 : (prompt.canBattle ? 6 : 7))
                        : strategy.chooseIdleCmd(prompt);
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_BATTLECMD: {
                pauseBrief();
                RpgAiStrategy.BattleCmdPrompt prompt = strategy.parseBattleCmd(buf);
                int resp = useFallback
                        ? (prompt.canEnd ? 3 : (prompt.canMain2 ? 2 : 3))
                        : strategy.chooseBattleCmd(prompt);
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_CHAIN: {
                int resp = useFallback ? fallbackChainResponse(buf) : strategy.chooseChain(buf);
                if (resp >= 0) pauseBrief();
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_EFFECTYN: {
                int resp = useFallback ? 0 : strategy.chooseEffectYn(buf);
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_YESNO: {
                int resp = useFallback ? 0 : strategy.chooseYesNo(buf);
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_OPTION: {
                int resp = strategy.chooseOption(buf);
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_CARD: {
                byte[] resp = useFallback ? fallbackSelectCard(buf) : strategy.chooseSelectCard(buf);
                sendResponse(resp);
                break;
            }
            case MSG_SELECT_TRIBUTE: {
                byte[] resp = useFallback ? fallbackSelectCard(buf) : strategy.chooseSelectTribute(buf);
                sendResponse(resp);
                break;
            }
            case MSG_SELECT_POSITION: {
                int resp = strategy.choosePosition(buf);
                sendResponse(RpgAiStrategy.intBytes(resp));
                break;
            }
            case MSG_SELECT_PLACE:
            case MSG_SELECT_DISFIELD: {
                byte[] resp = strategy.choosePlace(buf);
                sendResponse(resp);
                break;
            }
            case MSG_SELECT_SUM: {
                byte[] resp = strategy.chooseSelectSum(buf);
                sendResponse(resp);
                break;
            }
            case MSG_SELECT_UNSELECT_CARD: {
                byte[] resp = strategy.chooseUnselectCard(buf);
                sendResponse(resp);
                break;
            }
            case MSG_SORT_CARD: {
                byte[] resp = strategy.chooseSortCard(buf);
                sendResponse(resp);
                break;
            }
            case MSG_SELECT_COUNTER: {
                byte[] resp = strategy.chooseCounter(buf);
                sendResponse(resp);
                break;
            }
            case MSG_ROCK_PAPER_SCISSORS: {
                sendResponse(RpgAiStrategy.intBytes(1 + random.nextInt(3)));
                break;
            }
            case MSG_ANNOUNCE_RACE:
            case MSG_ANNOUNCE_ATTRIB: {
                sendResponse(RpgAiStrategy.intBytes(strategy.chooseAnnounceBitmask(buf)));
                break;
            }
            case MSG_ANNOUNCE_NUMBER: {
                sendResponse(RpgAiStrategy.intBytes(strategy.chooseAnnounceNumber(buf)));
                break;
            }
            case MSG_ANNOUNCE_CARD: {
                sendResponse(RpgAiStrategy.intBytes(strategy.chooseAnnounceCard(buf)));
                break;
            }
            case MSG_RETRY: {
                // Bare MSG_RETRY fallback (DuelAnalyzer normally resends full liveQuestion instead)
                sendResponse(RpgAiStrategy.intBytes(-1));
                break;
            }
            default:
                break;
        }
    }

    private static boolean isQuestionMessage(int msgType) {
        return (msgType >= MSG_SELECT_BATTLECMD && msgType <= MSG_SELECT_UNSELECT_CARD)
                || msgType == MSG_ROCK_PAPER_SCISSORS
                || (msgType >= MSG_ANNOUNCE_RACE && msgType <= MSG_ANNOUNCE_NUMBER);
    }

    private static int fallbackChainResponse(ByteBuffer data) {
        if (data.remaining() < 11) return -1;
        data.get(); // player
        int count = data.get() & 0xFF;
        data.get(); // specount
        data.getInt();
        data.getInt();
        for (int i = 0; i < count && data.remaining() >= 14; i++) {
            data.get(); // flag
            int forced = data.get() & 0xFF;
            data.position(data.position() + 12);
            if (forced != 0) return i;
        }
        return -1;
    }

    private static byte[] fallbackSelectCard(ByteBuffer data) {
        if (data.remaining() < 5) return RpgAiStrategy.intBytes(0);
        data.get(); // player
        boolean cancelable = (data.get() & 0xFF) != 0;
        int min = data.get() & 0xFF;
        data.get(); // max
        int count = data.get() & 0xFF;
        if (cancelable) return RpgAiStrategy.intBytes(-1);
        int take = Math.min(min, count);
        byte[] resp = new byte[1 + take];
        resp[0] = (byte) take;
        for (int i = 0; i < take; i++) resp[1 + i] = (byte) i;
        return resp;
    }

    private void pauseBrief() {
        if (actionDelayMs <= 0L) return;
        try {
            Thread.sleep(actionDelayMs);
        } catch (InterruptedException ignored) {
        }
    }

    // ===== Wire Packet Writers =====

    private void sendPlayerInfo(String name) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2 + 1 + 40).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (1 + 40));
        buf.put((byte) CTOS_PLAYER_INFO);
        writeUtf16Fixed(buf, name, 20);
        writePacket(buf.array());
    }

    private void sendJoinGame(int version, String pass) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2 + 1 + 48).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (1 + 48));
        buf.put((byte) CTOS_JOIN_GAME);
        buf.putShort((short) version);
        buf.putShort((short) 0);
        buf.putInt(0);
        writeUtf16Fixed(buf, pass != null ? pass : "", 20);
        writePacket(buf.array());
    }

    private void sendUpdateDeck(List<Integer> main, List<Integer> extra, List<Integer> side) throws IOException {
        int mainAndExtra = main.size() + extra.size();
        int sideCount = side.size();
        int bodyLen = 1 + 8 + (mainAndExtra + sideCount) * 4;
        ByteBuffer buf = ByteBuffer.allocate(2 + bodyLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) bodyLen);
        buf.put((byte) CTOS_UPDATE_DECK);
        buf.putInt(mainAndExtra);
        buf.putInt(sideCount);
        for (int c : main) buf.putInt(c);
        for (int c : extra) buf.putInt(c);
        for (int c : side) buf.putInt(c);
        writePacket(buf.array());
    }

    private void sendSimplePacket(int proto) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 1);
        buf.put((byte) proto);
        writePacket(buf.array());
    }

    private void sendSingleByte(int proto, int value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 2);
        buf.put((byte) proto);
        buf.put((byte) value);
        writePacket(buf.array());
    }

    private void sendResponse(byte[] responseData) throws IOException {
        int bodyLen = 1 + responseData.length;
        ByteBuffer buf = ByteBuffer.allocate(2 + bodyLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) bodyLen);
        buf.put((byte) CTOS_RESPONSE);
        buf.put(responseData);
        writePacket(buf.array());
    }

    private synchronized void writePacket(byte[] raw) throws IOException {
        if (out == null) return;
        out.write(raw);
        out.flush();
    }

    private static void writeUtf16Fixed(ByteBuffer buf, String text, int maxChars) {
        String s = text != null ? text : "";
        int limit = Math.min(s.length(), Math.max(0, maxChars - 1));
        for (int i = 0; i < limit; i++) {
            buf.putChar(s.charAt(i));
        }
        for (int i = limit; i < maxChars; i++) {
            buf.putChar('\0');
        }
    }

    private static int readFully(InputStream is, byte[] dst, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = is.read(dst, off + total, len - total);
            if (n < 0) return total;
            total += n;
        }
        return total;
    }

    public static void parseYdkFile(String path, List<Integer> main, List<Integer> extra, List<Integer> side) {
        if (path == null || path.isEmpty()) return;
        File f = new File(path);
        if (!f.exists()) return;
        int section = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#main")) { section = 1; continue; }
                if (line.startsWith("#extra")) { section = 2; continue; }
                if (line.startsWith("!side")) { section = 3; continue; }
                if (line.startsWith("#") || line.startsWith("!")) continue;
                try {
                    int code = Integer.parseInt(line);
                    if (code <= 0) continue;
                    if (section == 1) main.add(code);
                    else if (section == 2) extra.add(code);
                    else if (section == 3) side.add(code);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }
}
