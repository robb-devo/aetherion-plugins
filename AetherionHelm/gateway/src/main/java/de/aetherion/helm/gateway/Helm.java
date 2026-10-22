package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Wires the services together and runs the clocks. */
public final class Helm {
    private static final Logger LOG = LoggerFactory.getLogger("helm");
    public static final String VERSION = "1.0.0";

    public final Config cfg;
    public final Db db;
    public final Auth auth;
    public final Audit audit;
    public final Network net;
    public final Players players;
    public final Events events;
    public final Reports reports;
    public final Moderation mod;
    public final HostMonitor host;
    public final ServerControl ctl;
    public final Alerts alerts;
    public final LiveHub live;
    public final Metrics metrics;
    public final AgentHub agents;
    public final Api api;
    public final long startedAt = System.currentTimeMillis();
    private final ScheduledExecutorService clock = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "helm-clock");
        t.setDaemon(true);
        return t;
    });
    private volatile ObjectNode lastSnapshot;

    public Helm(Config cfg) throws Exception {
        this.cfg = cfg;
        this.db = new Db(Path.of(cfg.dataDir));
        this.auth = new Auth(db);
        this.audit = new Audit(db);
        this.live = new LiveHub(this);
        this.events = new Events(this);
        this.players = new Players(this);
        this.reports = new Reports(this);
        this.alerts = new Alerts(this);
        this.host = new HostMonitor(this);
        this.net = new Network(this);
        this.mod = new Moderation(this);
        this.ctl = new ServerControl(this);
        this.metrics = new Metrics(this);
        this.agents = new AgentHub(this);
        this.api = new Api(this);
        audit.onEntry(live::audit);
    }

    public void start() {
        host.sample();
        agents.start();
        api.start();
        every(2, TimeUnit.SECONDS, () -> {
            host.sample();
            ObjectNode snap = net.snapshot();
            lastSnapshot = snap;
            live.snapshot(snap);
        });
        every(5, TimeUnit.SECONDS, () -> { ctl.poll(); alerts.evaluate(); mod.tick(); });
        every(30, TimeUnit.SECONDS, metrics::sample);
        clock.scheduleWithFixedDelay(guard(ctl::pollSlow), 20, 600, TimeUnit.SECONDS);
        every(3600, TimeUnit.SECONDS, () -> { metrics.prune(); events.prune(); auth.sweep(); });
        LOG.info("Aetherion Helm {} on {}:{} (agents on 127.0.0.1:{})", VERSION, cfg.bind, cfg.port, cfg.agentPort);
    }

    private void every(long n, TimeUnit unit, Runnable r) {
        clock.scheduleWithFixedDelay(guard(r), n, n, unit);
    }

    private static Runnable guard(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                LOG.warn("tick failed", t);
            }
        };
    }

    public ObjectNode snapshot() {
        ObjectNode s = lastSnapshot;
        return s != null ? s : net.snapshot();
    }

    public ZoneId zone() {
        return ZoneId.of(cfg.timezone);
    }

    public String today() {
        return LocalDate.now(zone()).toString();
    }

    public long midnight() {
        return LocalDate.now(zone()).atStartOfDay(zone()).toInstant().toEpochMilli();
    }

    public String serverName(String id) {
        Config.ServerDef d = id == null ? null : cfg.server(id);
        return d == null ? String.valueOf(id) : d.name();
    }
}
