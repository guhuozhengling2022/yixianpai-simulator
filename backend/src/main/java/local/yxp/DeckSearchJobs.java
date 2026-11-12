package local.yxp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static local.yxp.Models.Request;

final class DeckSearchJobs {
    private static final int CONCURRENT_LIMIT = 2;
    private static final int RETAINED_LIMIT = 16;

    record View(String id, String status, DeckSearch.Report report, String message) {}

    private final DeckSearch search;
    private final Map<String, Job> jobs = new LinkedHashMap<>();

    DeckSearchJobs(DeckSearch search) {
        this.search = search;
    }

    synchronized View start(Request request) {
        if (jobs.values().stream().filter(Job::active).count() >= CONCURRENT_LIMIT) {
            throw new Engine.Rejected("BUSY", "已有求解正在运行，请稍后重试。");
        }
        search.validate(request);
        if (jobs.size() >= RETAINED_LIMIT) {
            var entries = jobs.values().iterator();
            while (entries.hasNext()) {
                if (!entries.next().active()) {
                    entries.remove();
                    break;
                }
            }
        }
        String id = UUID.randomUUID().toString();
        var job = new Job(id);
        jobs.put(id, job);
        Thread.startVirtualThread(() -> {
            try {
                var report = search.search(request, () -> job.cancelled, job::publish);
                job.finish(report);
            } catch (RuntimeException error) {
                error.printStackTrace();
                job.fail();
            }
        });
        return job.view;
    }

    synchronized View get(String id, boolean cancel) {
        var job = jobs.get(id);
        if (job == null) return null;
        return cancel ? job.cancel() : job.view;
    }

    private static final class Job {
        private volatile View view;
        private volatile boolean cancelled;

        Job(String id) {
            view = new View(id, "RUNNING", null, null);
        }

        boolean active() {
            String status = view.status();
            return status.equals("RUNNING") || status.equals("CANCELLING");
        }

        synchronized View cancel() {
            if (active()) {
                cancelled = true;
                view = new View(view.id(), "CANCELLING", view.report(), null);
            }
            return view;
        }

        synchronized void publish(DeckSearch.Report report) {
            view = new View(view.id(), cancelled ? "CANCELLING" : "RUNNING", report, null);
        }

        synchronized void finish(DeckSearch.Report report) {
            view = new View(view.id(), cancelled ? "CANCELLED" : "COMPLETE", report, null);
        }

        synchronized void fail() {
            view = new View(view.id(), "ERROR", view.report(), "求解过程中出现错误。");
        }
    }
}
