/*
 * ============================================================================
 * 志愿服务时长认证系统 —— 零依赖 HTTP 压测工具（Java 21 单文件）
 * ============================================================================
 *
 * 用法（源码模式直接跑，无需编译、无需 k6/JMeter）：
 *   java deploy/perf/loadtest.java --base http://localhost:8080 --users 50 --seconds 30 --scenario read
 *   java deploy/perf/loadtest.java --users 50 --seconds 30 --scenario write
 *   java deploy/perf/loadtest.java --users 50 --seconds 60 --scenario mixed --think 20
 *
 * 参数（全部可选，默认值见下）：
 *   --base     http://localhost:8080   被测服务地址
 *   --users    50                      并发虚拟用户数（= 线程数）
 *   --seconds  30                      正式压测时长（秒）；预热 2s 不计入统计
 *   --scenario read|write|mixed        场景，默认 mixed（读:写 = 7:3）
 *   --think    10                      每次请求后的随机等待上限（毫秒，0~think 均匀）
 *   --warmup   2                       预热秒数（预热流量不计入统计）
 *   --timeout  20000                   单请求超时（毫秒）
 *   --sessions 0                       登录会话总数，0 表示自动（≈ users，每个角色各 1/3，上限 24）
 *   --password 123456                  测试账号口令（admin / org_admin / student 三个账号）
 *   --selftest                         只自检 JSON 解析与参数解析，不发任何 HTTP 请求
 *
 * 设计要点：
 *   · 判定"成功"必须看响应体里的 code == 0，不能只看 HTTP 200 ——
 *     本系统所有业务异常（名额已满、重复报名、签到窗口未开…）都返回 HTTP 200 + code != 0。
 *     因此统计分三档：成功 / 业务拒绝（code != 0，属规则校验）/ HTTP 失败（网络、4xx、5xx）。
 *   · 用 HttpURLConnection + 每个虚拟用户一个线程的固定线程池，keep-alive 默认开启，
 *     请求头带 Accept-Encoding: gzip（JDK 会自动解压，与 server.compression 配置对应）。
 *   · token 只保存在内存里，任何输出都不打印 token。
 *   · 写场景的幂等处理：先 GET /signups 查该学生已报名的活动，再挑一个未报名的
 *     PUBLISHED 活动 POST /signups；返回 30007（重复报名）就把它标记为已报名并换下一个；
 *     所有开放活动都报满后，退化为"取消一条 PENDING/APPROVED 报名 → 立即重新报名"的
 *     循环写（服务端支持复用 CANCELED 行，见 SignupServiceImpl.createSignup），
 *     这样压测期间既有真实 INSERT 也有真实 UPDATE，而不会一味撞唯一键。
 *   · 签到/签退：从 GET /attendance/mine 取本人 attendanceId 再 POST /attendance/sign-in|sign-out。
 *     注意签到签退有活动时间窗（开始前 30 分钟 ~ 结束后 30 分钟），演示库里 10 场活动
 *     要么已结束要么还没开始，压测期间这两个接口会稳定返回业务拒绝（属预期，不是系统故障）。
 *
 * 输出：总请求数 / 成功率 / RPS / 平均、中位、p95、p99 延迟（毫秒）/ 按接口分组统计 /
 *       业务拒绝原因 TOP / 最后一行一句结论。
 * ============================================================================
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class loadtest {

    // =====================================================================
    // 一、参数
    // =====================================================================
    static String base = "http://localhost:8080";
    static int users = 50;
    static int seconds = 30;
    static String scenario = "mixed";
    static int thinkMs = 10;
    static int warmupSeconds = 2;
    static int timeoutMs = 20000;
    static int sessions = 0;
    static String password = "123456";
    static boolean selfTest = false;

    /** 三个测试账号（登录名与角色一一对应，角色码与 StpInterfaceImpl 一致） */
    static final String[] ACCOUNTS = {"admin", "org_admin", "student"};
    static final String[] ROLES = {"SCHOOL_ADMIN", "ORG_ADMIN", "STUDENT"};

    // =====================================================================
    // 二、接口清单（与后端权限映射对齐）
    // =====================================================================
    /** 只读接口；role 取 ANY / SCHOOL_ADMIN / ORG_ADMIN / STUDENT */
    static final List<Ep> READ_EPS = List.of(
            new Ep("GET /activities", "GET", "/api/v1/activities?page=1&pageSize=10", "ANY", null),
            new Ep("GET /durations", "GET", "/api/v1/durations?page=1&pageSize=10", "ANY", null),
            new Ep("GET /portraits/distribution", "GET", "/api/v1/portraits/distribution", "SCHOOL_ADMIN", null),
            new Ep("GET /analytics/dashboard", "GET", "/api/v1/analytics/dashboard", "ANY", null),
            new Ep("GET /system/users", "GET", "/api/v1/system/users?page=1&pageSize=10", "SCHOOL_ADMIN", null),
            new Ep("GET /signups", "GET", "/api/v1/signups?page=1&pageSize=10", "ANY", null),
            new Ep("GET /attendance/mine", "GET", "/api/v1/attendance/mine?page=1&pageSize=10", "STUDENT", null),
            new Ep("GET /portraits/me", "GET", "/api/v1/portraits/me", "STUDENT", null),
            new Ep("GET /system/notifications", "GET", "/api/v1/system/notifications?page=1&pageSize=10", "ANY", null),
            new Ep("GET /categories", "GET", "/api/v1/categories", "ANY", null)
    );

    static final String EP_LOGIN = "POST /auth/login";
    static final String EP_SIGNUP = "POST /signups";
    static final String EP_SIGNUP_CANCEL = "PUT /signups/{id}/cancel";
    static final String EP_SIGN_IN = "POST /attendance/sign-in";
    static final String EP_SIGN_OUT = "POST /attendance/sign-out";
    static final String EP_SIGNUP_LIST = "GET /signups(写前查重)";
    static final String EP_ACTIVITY_LIST = "GET /activities(取可报名)";
    static final String EP_ATT_LIST = "GET /attendance/mine(取签到 id)";

    record Ep(String name, String method, String path, String role, String body) {
        boolean allowedFor(String roleCode) {
            return "ANY".equals(role) || role.equals(roleCode);
        }
    }

    // =====================================================================
    // 三、统计
    // =====================================================================
    static final class Stat {
        final String name;
        final AtomicLong total = new AtomicLong();
        final AtomicLong ok = new AtomicLong();
        final AtomicLong bizReject = new AtomicLong();
        final AtomicLong httpFail = new AtomicLong();
        /** 延迟样本（纳秒），压测结束后排序取分位数 */
        final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        Stat(String name) {
            this.name = name;
        }

        void record(long latencyNanos, int httpStatus, String bizCode, boolean success) {
            total.incrementAndGet();
            latencies.add(latencyNanos);
            if (httpStatus <= 0 || httpStatus >= 400) {
                httpFail.incrementAndGet();
            } else if (success) {
                ok.incrementAndGet();
            } else {
                bizReject.incrementAndGet();
            }
            if (bizCode != null && !success) {
                rejectReasons.merge(bizCode, 1L, Long::sum);
            }
        }
    }

    static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
    static final Map<String, Long> rejectReasons = new ConcurrentHashMap<>();
    static volatile boolean recording = false;

    static Stat stat(String name) {
        return STATS.computeIfAbsent(name, Stat::new);
    }

    // =====================================================================
    // 四、HTTP
    // =====================================================================
    record Resp(int status, String body, String error, long nanos) {
        boolean httpOk() {
            return status >= 200 && status < 300;
        }
    }

    static Resp http(String method, String path, String token, String jsonBody) {
        long t0 = System.nanoTime();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(base + path).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            // JDK 会自动解压 gzip 响应（与 server.compression.enabled 对应）
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (jsonBody != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = conn.getResponseCode();
            String body = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return new Resp(status, body, null, System.nanoTime() - t0);
        } catch (IOException e) {
            return new Resp(-1, null, e.getClass().getSimpleName() + ": " + e.getMessage(), System.nanoTime() - t0);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    /** 发一个请求并记账；bizCode 为响应体里的 code（解析失败记 null） */
    static boolean call(String statName, String method, String path, String token, String body) {
        Resp resp = http(method, path, token, body);
        Integer code = null;
        if (resp.httpOk() && resp.body() != null && !resp.body().isBlank()) {
            try {
                Object parsed = Json.parse(resp.body());
                Object c = (parsed instanceof Map<?, ?> m) ? m.get("code") : null;
                if (c instanceof Number num) {
                    code = num.intValue();
                }
            } catch (RuntimeException ignored) {
                // 响应不是 JSON（如网关错误页），按业务失败计
            }
        }
        boolean ok = resp.httpOk() && code != null && code == 0;
        if (recording) {
            Stat s = stat(statName);
            String reason = ok ? null : (code != null ? String.valueOf(code) : ("HTTP " + resp.status()));
            s.record(resp.nanos(), resp.status(), reason, ok);
            if (!ok && code != null) {
                String msg = extractMessage(resp.body());
                if (msg != null) {
                    messageHints.putIfAbsent(String.valueOf(code), msg);
                }
            }
        }
        lastCode.set(ok ? 0 : (code != null ? code : -resp.status()));
        return ok;
    }

    static final Map<String, String> messageHints = new ConcurrentHashMap<>();
    static final ThreadLocal<Integer> lastCode = ThreadLocal.withInitial(() -> 0);

    static String extractMessage(String body) {
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map<?, ?> m && m.get("message") instanceof String s) {
                return s;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    // =====================================================================
    // 五、会话与共享状态
    // =====================================================================
    record Session(String role, String token) {
    }

    static final List<Session> SESSIONS = Collections.synchronizedList(new ArrayList<>());

    /** 写场景共享的只读快照（学生已报名活动 → signupId、可取消的 signupId、开放活动、签到记录 id） */
    static final class WriteState {
        final Map<Long, Long> mySignupByActivity = new ConcurrentHashMap<>();  // activityId -> signupId
        final Map<Long, String> mySignupStatus = new ConcurrentHashMap<>();    // signupId -> status
        final List<Long> openActivities = Collections.synchronizedList(new ArrayList<>());
        final List<Long> attendanceIds = Collections.synchronizedList(new ArrayList<>());
        volatile long refreshedAt = 0L;
        final AtomicBoolean refreshing = new AtomicBoolean(false);

        boolean stale() {
            return System.currentTimeMillis() - refreshedAt > 5000L
                    || openActivities.isEmpty() || attendanceIds.isEmpty();
        }

        void refresh(String studentToken) {
            if (!refreshing.compareAndSet(false, true)) {
                return;
            }
            try {
                mySignupByActivity.clear();
                mySignupStatus.clear();
                openActivities.clear();
                attendanceIds.clear();
                // 1) 我的报名
                Resp r1 = http("GET", "/api/v1/signups?page=1&pageSize=100", studentToken, null);
                call(EP_SIGNUP_LIST, "GET", "/api/v1/signups?page=1&pageSize=100", studentToken, null);
                for (Object row : dataList(r1.body())) {
                    if (row instanceof Map<?, ?> m) {
                        Long aid = asLong(m.get("activityId"));
                        Long sid = asLong(m.get("id"));
                        Object status = m.get("status");
                        if (aid != null && sid != null) {
                            mySignupByActivity.put(aid, sid);
                            mySignupStatus.put(sid, status == null ? "" : status.toString());
                        }
                    }
                }
                // 2) 开放活动（排除名额已满：enrolled >= capacity 且 capacity > 0）
                Resp r2 = http("GET", "/api/v1/activities?page=1&pageSize=50", studentToken, null);
                call(EP_ACTIVITY_LIST, "GET", "/api/v1/activities?page=1&pageSize=50", studentToken, null);
                for (Object row : dataList(r2.body())) {
                    if (row instanceof Map<?, ?> m && "PUBLISHED".equals(String.valueOf(m.get("status")))) {
                        Long id = asLong(m.get("id"));
                        long enrolled = m.get("enrolled") instanceof Number n ? n.longValue() : 0L;
                        long capacity = m.get("capacity") instanceof Number n ? n.longValue() : 0L;
                        if (id != null && (capacity <= 0 || enrolled < capacity)) {
                            openActivities.add(id);
                        }
                    }
                }
                // 3) 我的签到记录
                Resp r3 = http("GET", "/api/v1/attendance/mine?page=1&pageSize=100", studentToken, null);
                call(EP_ATT_LIST, "GET", "/api/v1/attendance/mine?page=1&pageSize=100", studentToken, null);
                for (Object row : dataList(r3.body())) {
                    if (row instanceof Map<?, ?> m) {
                        Long id = asLong(m.get("id"));
                        if (id != null) {
                            attendanceIds.add(id);
                        }
                    }
                }
                refreshedAt = System.currentTimeMillis();
            } finally {
                refreshing.set(false);
            }
        }
    }

    static List<?> dataList(String body) {
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map<?, ?> m && m.get("data") instanceof Map<?, ?> data
                    && data.get("list") instanceof List<?> list) {
                return list;
            }
        } catch (RuntimeException ignored) {
        }
        return List.of();
    }

    static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // =====================================================================
    // 六、虚拟用户
    // =====================================================================
    static volatile long deadlineNanos = Long.MAX_VALUE;
    static final AtomicBoolean stop = new AtomicBoolean(false);
    static final AtomicLong readCount = new AtomicLong();
    static final AtomicLong writeCount = new AtomicLong();
    static final AtomicLong mixedReadOk = new AtomicLong();
    static final AtomicLong mixedWriteOk = new AtomicLong();
    static final AtomicLong noWriteTarget = new AtomicLong();

    static void userLoop(int index, long seed) {
        Random rnd = new Random(seed);
        Session session = SESSIONS.get(index % SESSIONS.size());
        WriteState writeState = new WriteState();
        Session studentSession = null;
        for (Session s : SESSIONS) {
            if ("STUDENT".equals(s.role())) {
                studentSession = s;
                break;
            }
        }
        while (!stop.get() && System.nanoTime() < deadlineNanos) {
            boolean doWrite;
            switch (scenario) {
                case "read" -> doWrite = false;
                case "write" -> doWrite = true;
                default -> doWrite = rnd.nextInt(10) < 3;   // mixed：7:3
            }
            if (doWrite) {
                writeCount.incrementAndGet();
                if (runWriteIteration(studentSession, writeState, rnd)) {
                    mixedWriteOk.incrementAndGet();
                }
            } else {
                readCount.incrementAndGet();
                if (runReadIteration(session, rnd)) {
                    mixedReadOk.incrementAndGet();
                }
            }
            if (thinkMs > 0) {
                sleep(rnd.nextInt(thinkMs + 1));
            }
        }
    }

    static boolean runReadIteration(Session session, Random rnd) {
        List<Ep> pool = new ArrayList<>(READ_EPS.size());
        for (Ep ep : READ_EPS) {
            if (ep.allowedFor(session.role())) {
                pool.add(ep);
            }
        }
        Ep ep = pool.get(rnd.nextInt(pool.size()));
        return call(ep.name(), ep.method(), ep.path(), session.token(), ep.body());
    }

    /**
     * 一次写迭代：先试"报名"（查重 → 挑未报名活动 → POST），全部报满后退化为
     * "取消一条可取消报名 → 立即重新报名"；随后做一次签到 + 一次签退。
     */
    static boolean runWriteIteration(Session studentSession, WriteState st, Random rnd) {
        if (studentSession == null) {
            return false;
        }
        if (st.stale()) {
            st.refresh(studentSession.token());
        }
        boolean ok = false;

        // ---- 1) 报名：优先找还没报过的开放活动 ----
        Long target = null;
        synchronized (st.openActivities) {
            for (Long id : st.openActivities) {
                if (!st.mySignupByActivity.containsKey(id)) {
                    target = id;
                    break;
                }
            }
        }
        if (target != null) {
            ok = call(EP_SIGNUP, "POST", "/api/v1/signups", studentSession.token(),
                    "{\"activityId\":" + target + ",\"reason\":\"perf-test\"}");
            if (ok) {
                st.mySignupByActivity.put(target, -1L);
            } else if (lastCode.get() == 30007) {
                st.mySignupByActivity.putIfAbsent(target, 0L);
            } else if (lastCode.get() == 30005 || lastCode.get() == 30006) {
                // 已截止 / 名额已满：从开放列表中移除，避免反复撞（5 秒后整体刷新会再拉一次）
                st.openActivities.remove(target);
            }
            return ok;
        }

        // ---- 2) 退化为取消 + 重新报名（服务端支持复用 CANCELED 行）----
        Long cancelId = null;
        Long cancelActivity = null;
        for (Map.Entry<Long, Long> e : st.mySignupByActivity.entrySet()) {
            Long sid = e.getValue();
            String status = sid == null ? "" : st.mySignupStatus.getOrDefault(sid, "");
            if (("PENDING".equals(status) || "APPROVED".equals(status)) && sid != null && sid > 0) {
                cancelId = sid;
                cancelActivity = e.getKey();
                break;
            }
        }
        if (cancelId != null) {
            boolean canceled = call(EP_SIGNUP_CANCEL, "PUT", "/api/v1/signups/" + cancelId + "/cancel",
                    studentSession.token(), null);
            boolean reSignup = call(EP_SIGNUP, "POST", "/api/v1/signups", studentSession.token(),
                    "{\"activityId\":" + cancelActivity + ",\"reason\":\"perf-test\"}");
            if (reSignup) {
                st.mySignupStatus.put(cancelId, "PENDING");
            }
            ok = canceled && reSignup;
        } else {
            // 没有任何可写目标（全部 COMPLETED / REJECTED），只打签到签退，避免空转
            noWriteTarget.incrementAndGet();
        }

        // ---- 3) 签到 + 签退（演示库时间窗未开时会收到业务拒绝，属预期）----
        Long attendanceId = null;
        synchronized (st.attendanceIds) {
            if (!st.attendanceIds.isEmpty()) {
                attendanceId = st.attendanceIds.get(rnd.nextInt(st.attendanceIds.size()));
            }
        }
        if (attendanceId != null) {
            call(EP_SIGN_IN, "POST", "/api/v1/attendance/sign-in", studentSession.token(),
                    "{\"attendanceId\":" + attendanceId + "}");
            call(EP_SIGN_OUT, "POST", "/api/v1/attendance/sign-out", studentSession.token(),
                    "{\"attendanceId\":" + attendanceId + "}");
        }
        return ok;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =====================================================================
    // 七、登录
    // =====================================================================
    static void loginAll() {
        int total = sessions > 0 ? sessions : Math.min(Math.max(users, 3), 24);
        int perRole = Math.max(1, total / 3);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, perRole * 3));
        CountDownLatch done = new CountDownLatch(perRole * 3);
        for (int role = 0; role < 3; role++) {
            final int r = role;
            for (int i = 0; i < perRole; i++) {
                pool.execute(() -> {
                    try {
                        Resp resp = http("POST", "/api/v1/auth/login", null,
                                "{\"username\":\"" + ACCOUNTS[r] + "\",\"password\":\"" + password + "\"}");
                        String token = null;
                        try {
                            Object parsed = Json.parse(resp.body());
                            if (parsed instanceof Map<?, ?> m && m.get("data") instanceof Map<?, ?> data
                                    && data.get("token") instanceof String t) {
                                token = t;
                            }
                        } catch (RuntimeException ignored) {
                        }
                        if (token == null) {
                            System.out.println("[登录失败] " + ACCOUNTS[r] + " HTTP=" + resp.status()
                                    + " " + (resp.error() != null ? resp.error() : "响应中没有 token"));
                        } else {
                            SESSIONS.add(new Session(ROLES[r], token));
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
        }
        try {
            done.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        System.out.println("登录完成：" + SESSIONS.size() + " 个会话（"
                + countRole("SCHOOL_ADMIN") + " 学校管理员 / " + countRole("ORG_ADMIN") + " 组织管理员 / "
                + countRole("STUDENT") + " 学生）");
        if (SESSIONS.stream().noneMatch(s -> "STUDENT".equals(s.role()))) {
            System.out.println("⚠️ 没有学生会话，写场景将不可用（检查 student 账号是否存在/可登录）");
        }
    }

    static long countRole(String role) {
        return SESSIONS.stream().filter(s -> role.equals(s.role())).count();
    }

    // =====================================================================
    // 八、主流程
    // =====================================================================
    public static void main(String[] args) throws Exception {
        // 先设 JDK 的 HTTP 连接缓存上限（默认每目标 5 条，50 线程下会反复重建连接）
        System.setProperty("http.maxConnections", "256");
        if (parseArgs(args)) {
            return;
        }
        if (selfTest) {
            JsonSelfTest.run();
            System.out.println("自检通过（未发任何 HTTP 请求）");
            return;
        }
        if (!"read".equals(scenario) && !"write".equals(scenario) && !"mixed".equals(scenario)) {
            System.out.println("scenario 只能是 read / write / mixed，收到：" + scenario);
            return;
        }
        System.out.println("==== 压测开始 ====");
        System.out.printf("目标=%s  场景=%s  并发=%d  时长=%ds  思考时间=0~%dms  预热=%ds%n",
                base, scenario, users, seconds, thinkMs, warmupSeconds);
        System.out.println("提示：判定成功以响应体 code==0 为准；业务拒绝（名额已满/重复报名/签到窗口未开等）单独统计。");

        loginAll();
        if (SESSIONS.isEmpty()) {
            System.out.println("没有任何可用会话，压测终止（请确认后端已启动、账号口令正确）。");
            return;
        }

        runPhase(Math.max(0, warmupSeconds), false);
        STATS.clear();
        rejectReasons.clear();
        readCount.set(0);
        writeCount.set(0);
        mixedReadOk.set(0);
        mixedWriteOk.set(0);
        System.gc();

        long t0 = System.nanoTime();
        runPhase(seconds, true);
        long elapsedNanos = System.nanoTime() - t0;
        report(elapsedNanos);
    }

    static void runPhase(int secs, boolean record) throws InterruptedException {
        recording = record;
        deadlineNanos = System.nanoTime() + secs * 1_000_000_000L;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < users; i++) {
            final int idx = i;
            final long seed = 20260927L * (idx + 1);
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                userLoop(idx, seed);
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        if (secs > 0) {
            pool.awaitTermination(secs + 60L, TimeUnit.SECONDS);
        }
    }

    // =====================================================================
    // 九、结果
    // =====================================================================
    static void report(long elapsedNanos) {
        double secs = Math.max(0.001, elapsedNanos / 1e9);
        List<Stat> stats = new ArrayList<>(STATS.values());
        stats.sort((a, b) -> Long.compare(b.total.get(), a.total.get()));

        long total = 0;
        long ok = 0;
        long biz = 0;
        long fail = 0;
        List<Long> all = new ArrayList<>();
        for (Stat s : stats) {
            total += s.total.get();
            ok += s.ok.get();
            biz += s.bizReject.get();
            fail += s.httpFail.get();
            synchronized (s.latencies) {
                all.addAll(s.latencies);
            }
        }
        long[] sorted = toSortedArray(all);
        System.out.println();
        System.out.println("====================== 压测结果 ======================");
        System.out.printf("目标 / 场景        : %s / %s%n", base, scenario);
        System.out.printf("并发 / 时长        : %d 用户 / %.1f s%n", users, secs);
        System.out.printf("总请求数           : %d%n", total);
        System.out.printf("成功(code=0)       : %d (%.2f%%)%n", ok, pct(ok, total));
        System.out.printf("业务拒绝(code!=0)  : %d (%.2f%%)%n", biz, pct(biz, total));
        System.out.printf("HTTP失败(网络/4xx/5xx): %d (%.2f%%)%n", fail, pct(fail, total));
        System.out.printf("RPS                : %.1f req/s%n", total / secs);
        System.out.printf("延迟(毫秒)         : avg %.1f  p50 %.1f  p95 %.1f  p99 %.1f  max %.1f%n",
                avg(sorted), pct(sorted, 50), pct(sorted, 95), pct(sorted, 99), max(sorted));
        if (scenario.equals("mixed") || scenario.equals("write")) {
            System.out.printf("读迭代 / 写迭代     : %d / %d%n", readCount.get(), writeCount.get());
            if (noWriteTarget.get() > 0) {
                System.out.printf("⚠️ 写场景有 %d 次迭代没有可写目标（无开放活动可报名，也没有 PENDING/APPROVED 的报名可取消），已退化为只打签到签退；如需持续压写请准备更多 PUBLISHED 活动%n",
                        noWriteTarget.get());
            }
        }
        System.out.println();
        System.out.println("---- 按接口分组（按请求量降序）----");
        System.out.printf("%-34s %8s %8s %8s %8s %8s %8s %8s %8s%n",
                "接口", "请求", "成功", "业务拒", "HTTP败", "avg", "p50", "p95", "p99");
        for (Stat s : stats) {
            long[] arr = toSortedArray(snapshot(s));
            System.out.printf("%-34s %8d %8d %8d %8d %8.1f %8.1f %8.1f %8.1f%n",
                    s.name, s.total.get(), s.ok.get(), s.bizReject.get(), s.httpFail.get(),
                    avg(arr), pct(arr, 50), pct(arr, 95), pct(arr, 99));
        }
        if (!rejectReasons.isEmpty()) {
            System.out.println();
            System.out.println("---- 失败原因 TOP5（业务 code 或 HTTP 状态 : message : 次数）----");
            rejectReasons.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(e -> System.out.printf("  %s : %s : %d%n",
                            e.getKey(), messageHints.getOrDefault(e.getKey(), "-"), e.getValue()));
        }

        // 最慢接口（按 p95，取请求量 >= 总请求 5% 的接口，避免抽样过少的接口夺冠）
        Stat slowest = null;
        double slowestP95 = -1;
        for (Stat s : stats) {
            if (s.total.get() < Math.max(10, total / 20)) {
                continue;
            }
            long[] arr = toSortedArray(snapshot(s));
            double p95 = pct(arr, 95);
            if (p95 > slowestP95) {
                slowestP95 = p95;
                slowest = s;
            }
        }
        String conclusion = String.format(Locale.ROOT,
                "%d 并发 %ds 完成 %d 请求，HTTP 失败 %d，业务拒绝 %d（%s），RPS %.1f，p95 %.1fms；"
                        + "最慢接口 %s（p95 %.1fms）%s",
                users, seconds, total, fail, biz,
                rejectReasons.containsKey("10000") ? "含 10000 系统繁忙，需查后端日志" : "均为规则校验",
                total / secs, pct(sorted, 95),
                slowest == null ? "-" : slowest.name, slowestP95,
                fail == 0 && !rejectReasons.containsKey("10000") ? "，无系统级失败" : "；⚠️ 存在系统级失败，需查后端日志");
        System.out.println();
        System.out.println("结论：" + conclusion);
    }

    static List<Long> snapshot(Stat s) {
        synchronized (s.latencies) {
            return new ArrayList<>(s.latencies);
        }
    }

    static long[] toSortedArray(List<Long> list) {
        long[] arr = new long[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        Arrays.sort(arr);
        return arr;
    }

    static double avg(long[] sorted) {
        if (sorted.length == 0) {
            return 0;
        }
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        return sum / (double) sorted.length / 1e6;
    }

    static double pct(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx] / 1e6;
    }

    static double max(long[] sorted) {
        return sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6;
    }

    static double pct(long a, long b) {
        return b == 0 ? 0 : a * 100.0 / b;
    }

    // =====================================================================
    // 十、参数解析
    // =====================================================================
    /** @return true 表示已处理完（如 --help）无需继续 */
    static boolean parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String value = null;
            int eq = a.indexOf('=');
            if (eq > 0) {
                value = a.substring(eq + 1);
                a = a.substring(0, eq);
            }
            switch (a) {
                case "--base" -> base = value != null ? value : next(args, ++i);
                case "--users" -> users = parseInt(value != null ? value : next(args, ++i), users);
                case "--seconds" -> seconds = parseInt(value != null ? value : next(args, ++i), seconds);
                case "--scenario" -> scenario = (value != null ? value : next(args, ++i)).toLowerCase(Locale.ROOT);
                case "--think" -> thinkMs = parseInt(value != null ? value : next(args, ++i), thinkMs);
                case "--warmup" -> warmupSeconds = parseInt(value != null ? value : next(args, ++i), warmupSeconds);
                case "--timeout" -> timeoutMs = parseInt(value != null ? value : next(args, ++i), timeoutMs);
                case "--sessions" -> sessions = parseInt(value != null ? value : next(args, ++i), sessions);
                case "--password" -> password = value != null ? value : next(args, ++i);
                case "--selftest" -> selfTest = true;
                case "--help", "-h" -> {
                    System.out.println(HELP);
                    return true;
                }
                default -> {
                    System.out.println("未知参数：" + a + "\n" + HELP);
                    return true;
                }
            }
        }
        if (users < 1) {
            users = 1;
        }
        if (seconds < 1) {
            seconds = 1;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return false;
    }

    static String next(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("参数缺少取值");
        }
        return args[i];
    }

    static int parseInt(String s, int dft) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            System.out.println("参数不是整数：" + s + "，沿用默认值 " + dft);
            return dft;
        }
    }

    static final String HELP = """
            用法：java loadtest.java [--base http://localhost:8080] [--users 50] [--seconds 30]
                                     [--scenario read|write|mixed] [--think 10] [--warmup 2]
                                     [--timeout 20000] [--sessions 0] [--password 123456]
            示例：java deploy/perf/loadtest.java --users 50 --seconds 30 --scenario read
            """;

    // =====================================================================
    // 十一、极简 JSON 解析（只依赖 JDK；够解析 R / PageResult 结构）
    // =====================================================================
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json p = new Json(text);
            p.ws();
            Object v = p.value();
            p.ws();
            return v;
        }

        private Object value() {
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            ws();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                ws();
                map.put(key, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw err("对象里期望 , 或 }");
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            ws();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                ws();
                list.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw err("数组里期望 , 或 ]");
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw err("非法转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String num = s.substring(start, i);
            if (num.isEmpty()) {
                throw err("非法字符 " + peek());
            }
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return Double.parseDouble(num);
            }
            long v = Long.parseLong(num);
            return (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) ? (Object) (int) v : (Object) v;
        }

        private Object literal(String text, Object v) {
            if (!s.startsWith(text, i)) {
                throw err("期望字面量 " + text);
            }
            i += text.length();
            return v;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw err("JSON 意外结束");
            }
            return s.charAt(i);
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw err("期望 " + c);
            }
            i++;
        }

        private RuntimeException err(String msg) {
            return new IllegalStateException("JSON 解析失败(位置 " + i + ")：" + msg + " 原文=" + s);
        }
    }

    /** --selftest：验证 JSON 解析与参数解析，不发 HTTP */
    static final class JsonSelfTest {
        static void run() {
            Object o = Json.parse("""
                    {"code":0,"message":"ok","data":{"total":2,"list":[
                      {"id":11,"activityId":8,"status":"APPROVED","name":"张三 \\"引号\\" 换行\\n"},
                      {"id":12,"activityId":9,"status":null}
                    ]}}""");
            Map<?, ?> root = (Map<?, ?>) o;
            if (!Integer.valueOf(0).equals(root.get("code"))) {
                throw new IllegalStateException("code 解析错误");
            }
            Map<?, ?> data = (Map<?, ?>) root.get("data");
            if (!Integer.valueOf(2).equals(data.get("total"))) {
                throw new IllegalStateException("total 解析错误");
            }
            List<?> list = (List<?>) data.get("list");
            if (list.size() != 2) {
                throw new IllegalStateException("list 解析错误");
            }
            Map<?, ?> first = (Map<?, ?>) list.get(0);
            if (!Long.valueOf(11L).equals(asLong(first.get("id"))) || !Long.valueOf(8L).equals(asLong(first.get("activityId")))) {
                throw new IllegalStateException("id/activityId 解析错误");
            }
            if (!((String) first.get("name")).contains("\"引号\"")) {
                throw new IllegalStateException("字符串转义解析错误");
            }
            if (((Map<?, ?>) list.get(1)).get("status") != null) {
                throw new IllegalStateException("null 解析错误");
            }
            // double/负号/科学计数法
            Map<?, ?> m2 = (Map<?, ?>) Json.parse("{\"a\":-1.5,\"b\":1e3,\"c\":true,\"d\":false}");
            if (((Number) m2.get("a")).doubleValue() != -1.5 || ((Number) m2.get("b")).doubleValue() != 1000.0
                    || !Boolean.TRUE.equals(m2.get("c")) || !Boolean.FALSE.equals(m2.get("d"))) {
                throw new IllegalStateException("数字/布尔解析错误");
            }
            // dataList 提取
            if (dataList("""
                    {"code":0,"data":{"total":1,"list":[{"id":1}]}}""").size() != 1) {
                throw new IllegalStateException("dataList 提取错误");
            }
            // 参数解析
            if (parseArgs(new String[]{"--users", "7", "--scenario=read", "--seconds=3"})) {
                throw new IllegalStateException("参数解析返回了 help");
            }
            if (users != 7 || seconds != 3 || !"read".equals(scenario)) {
                throw new IllegalStateException("参数解析结果错误: users=" + users + " seconds=" + seconds + " scenario=" + scenario);
            }
        }
    }
}
